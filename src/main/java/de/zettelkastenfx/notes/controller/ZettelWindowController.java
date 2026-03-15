package de.zettelkastenfx.notes.controller;

import de.zettelkastenfx.base.BaseIcon;
import de.zettelkastenfx.base.util.PersistenceUtil;
import de.zettelkastenfx.bibliography.model.*;
import de.zettelkastenfx.bibliography.persistence.BibliographyRepository;
import de.zettelkastenfx.bibliography.ui.BibliographyEditorShell;
import de.zettelkastenfx.bibliography.ui.TypeChoice;
import de.zettelkastenfx.bibliography.ui.lookup.AiChoiceProvider;
import de.zettelkastenfx.bibliography.ui.lookup.AuthorSuggestion;
import de.zettelkastenfx.bibliography.ui.lookup.BibliographyLookupProvider;
import de.zettelkastenfx.bibliography.ui.lookup.TitleSuggestion;
import de.zettelkastenfx.notes.editor.NoteEditorPane;
import de.zettelkastenfx.notes.editor.format.InlineCssStyleUtil;
import de.zettelkastenfx.notes.model.*;
import de.zettelkastenfx.persistence.NoteRepository;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.stage.Popup;
import javafx.stage.Stage;

import java.util.*;

public class ZettelWindowController {

  private final Stage stage;
  private final ZettelWindowView view;
  private boolean titleEditing;

  private String titleTextBeforeEdit = "";

  private final NoteRepository noteRepository;
  private final BibliographyRepository bibliographyRepository;

  private Note currentNote;     // null = noch nicht gespeichert
  private boolean loading;

  // BottomToolbar / Zettelwechsel
  private boolean updatingNoteIdField;

  // Maximal zulässige Zettel-ID (wird von uns gepflegt; später ggf. aus DB)
  private int maxNoteId = 0;

  // Temporärer Zustand für frisch angelegte Folgezettel:
  // Der Verweis wird sofort am Quellzettel gespeichert, der Zielzettel aber erst,
  // wenn er später gültig befüllt wurde.
  private Integer pendingFollowUpTargetNoteId;

  // Verhindert, dass direkt nach internem Zettelwechsel ein frisch geöffneter,
  // noch leerer Zettel sofort wieder als "ungültig" behandelt wird.
  private boolean suppressInvalidCleanupOnce;

  // für BottomToolbar
  private final Deque<Integer> backHistory = new ArrayDeque<>();
  private final Deque<Integer> forwardHistory = new ArrayDeque<>();
  private boolean historyNavigation;

  // Popup zum manuellen Bearbeiten von Verweisen
  private final Popup linkEditPopup = new Popup();
  private final VBox linkEditRoot = new VBox(10);

  private TextField linkEditNoteIdField;
  private Label linkEditTitleLabel;
  private Label linkEditStatusLabel;
  private Button linkEditReverseButton;

  private ToggleButton linkEditSerieButton;
  private ToggleButton linkEditIdeaButton;
  private ToggleButton linkEditQuestionButton;
  private ToggleButton linkEditCritiqueButton;
  private ToggleButton linkEditTaskButton;

  private boolean updatingLinkEditUi;

  public ZettelWindowController(Stage stage, ZettelWindowView view) {
    this.stage = stage;
    this.view = view;
    this.noteRepository = PersistenceUtil.getNoteRepository();
    this.bibliographyRepository = PersistenceUtil.getBibliographyRepository();

    openInitialNote();
    wireEvents();
  }

  /**
   * Wird von ZettelWindow aufgerufen.
   */
  public void applyInitialState() {
    // Startlayout: drei Bereiche ungefähr gleich
    Platform.runLater(() -> view.getWorkAreaSplitPane().setDividerPositions(0.33, 0.66));
    refreshServiceAreaForCurrentState();
  }

  private void wireEvents() {
    NoteEditorPane editor = view.getNoteEditorPane();

    wireTitleEditing(editor);
    wireGlobalTitleCommitHandlers(editor);
    wireBodyEditingAndContextMenu(editor);
    wireGlobalBodyDeactivateHandlers(editor);

    // Toolbar: neue Seiten
    view.getToolButtonPageAdd().setOnAction(event -> createNewEmptyNote());
    view.getToolButtonPageCopy().setOnAction(event -> createCopyOfCurrentNote());
    view.getToolButtonPageWithBibliography().setOnAction(event -> createCopyBibliography());
    view.getToolButtonLinkEdit().setOnAction(event -> toggleLinkEditPopup());
    view.getToolButtonToggleServiceArea().setOnAction(event -> toggleServiceArea());


    view.getServiceAreaMagnifyLinkButton().setOnAction(event -> handleServiceAreaModeToggle(ZettelWindowView.ServiceAreaMode.MAGNIFY_LINK));
    view.getServiceAreaKeyButton().setOnAction(event -> handleServiceAreaModeToggle(ZettelWindowView.ServiceAreaMode.KEY));
    view.getServiceAreaBookButton().setOnAction(event -> handleServiceAreaModeToggle(ZettelWindowView.ServiceAreaMode.BOOK));
    view.getServiceAreaListButton().setOnAction(event -> handleServiceAreaModeToggle(ZettelWindowView.ServiceAreaMode.LIST));
    view.getServiceAreaClusterButton().setOnAction(event -> handleServiceAreaModeToggle(ZettelWindowView.ServiceAreaMode.PERFORMANCE_ANALYSIS));

    // Dropdown für Folgezettel
    view.getMiFollowing().setOnAction(e -> createTypedFollowUpNote(LinkType.SERIE));
    view.getMiIdea().setOnAction(e -> createTypedFollowUpNote(LinkType.IDEE));
    view.getMiQuestion().setOnAction(e -> createTypedFollowUpNote(LinkType.FRAGE));
    view.getMiCritique().setOnAction(e -> createTypedFollowUpNote(LinkType.KRITIK));
    view.getMiTask().setOnAction(e -> createTypedFollowUpNote(LinkType.AUFGABE));

    // Menü (oben): "Datei -> Schließen" schließt nur dieses Fenster
    view.getMenuFile().getItems().getLast().setOnAction(event -> stage.close());

    // Menü (oben): vorerst Dummies
    view.getMenuFile().getItems().getFirst().setOnAction(event -> System.out.println("Neu (kommt später)"));
    view.getMenuFile().getItems().getLast().setOnAction(event -> stage.close());

    view.getKeywordsTablePane().setOnKeywordsCommitted(() -> {
      saveCurrentIfValid();   // erst persistieren
      refreshKeywordCounts(); // dann counts aktualisieren
    });

    wireBottomToolbar();
    wireHeaderBarNavigation();
    initLinkEditPopup();

    stage.setOnCloseRequest(e -> {
      linkEditPopup.hide();
      saveOrDiscardCurrentNoteBeforeLeave();
    });
  }

  private void wireTitleEditing(NoteEditorPane editor) {
    editor.getTitleEditorArea().setEditable(false);
    editor.getTitleEditorArea().setFocusTraversable(false);
    editor.getTitleEditorArea().setMouseTransparent(true);

    editor.getTitleStack().setOnMouseClicked(event -> beginTitleEdit(editor));

    editor.getTitleEditorArea().focusedProperty().addListener((obs, oldFocused, newFocused) -> {
      if (!newFocused) {
        commitTitle(editor);
      }
    });

    editor.getTitleEditorArea().setOnKeyPressed(event -> {
      if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.TAB) {
        commitTitle(editor);
        InlineCssStyleUtil.clearHeadingForSelection(editor.getBodyArea());
        focusBodyForEditing(editor);
        event.consume();
      } else if (event.getCode() == KeyCode.ESCAPE) {
        cancelTitle(editor);
        event.consume();
      }
    });
  }

  private void wireBodyEditingAndContextMenu(NoteEditorPane editor) {
    editor.getBodyArea().setEditable(false);

    editor.getBodyArea().addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
      if (!editor.getBodyContainer().getStyleClass().contains("note-body-active")) {
        editor.getBodyContainer().getStyleClass().add("note-body-active");
      }

      editor.getBodyArea().setEditable(true);
      editor.getBodyArea().requestFocus();
    });

    editor.getBodyArea().focusedProperty().addListener((obs, oldFocused, newFocused) -> {
      if (newFocused) {
        if (!editor.getBodyContainer().getStyleClass().contains("note-body-active")) {
          editor.getBodyContainer().getStyleClass().add("note-body-active");
        }
        editor.getBodyArea().setEditable(true);
      } else {
        editor.getBodyContainer().getStyleClass().remove("note-body-active");
        editor.getBodyArea().setEditable(false);
        editor.getFormattingContextMenu().hide();
      }
    });

    editor.getBodyArea().setOnContextMenuRequested(event -> {
      boolean hasSelection = editor.getBodyArea().getSelection() != null
                                 && editor.getBodyArea().getSelection().getLength() > 0;

      if (hasSelection) {
        editor.getFormattingContextMenu().show(editor.getBodyArea(), event.getScreenX(), event.getScreenY());
      } else {
        editor.getFormattingContextMenu().hide();
      }

      event.consume();
    });

    editor.getBodyArea().addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
      if (event.isPrimaryButtonDown()) {
        editor.getFormattingContextMenu().hide();
      }
    });
  }

  private void wireGlobalBodyDeactivateHandlers(NoteEditorPane editor) {
    stage.focusedProperty().addListener((obs, oldFocused, newFocused) -> {
      if (!newFocused) {
        deactivateBody(editor);
        if (stage.getScene() != null) {
          stage.getScene().getRoot().requestFocus();
        }
      }
    });

    stage.sceneProperty().addListener((obs, oldScene, newScene) -> {
      if (newScene == null) {
        return;
      }

      newScene.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
        if (!editor.getBodyArea().isEditable()) {
          return;
        }

        Object target = event.getTarget();
        if (target instanceof javafx.scene.Node clickedNode) {
          if (!isNodeInside(clickedNode, editor.getBodyContainer())) {
            deactivateBody(editor);
            newScene.getRoot().requestFocus();
          }
        } else {
          deactivateBody(editor);
          newScene.getRoot().requestFocus();
        }
      });
    });
  }

  private void wireGlobalTitleCommitHandlers(NoteEditorPane editor) {
    stage.focusedProperty().addListener((obs, oldFocused, newFocused) -> {
      if (!newFocused) {
        commitTitle(editor);
        if (stage.getScene() != null) {
          stage.getScene().getRoot().requestFocus();
        }
      }
    });

    stage.sceneProperty().addListener((obs, oldScene, newScene) -> {
      if (newScene == null) {
        return;
      }

      newScene.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
        if (!titleEditing) {
          return;
        }

        Object target = event.getTarget();
        if (target instanceof javafx.scene.Node clickedNode) {
          if (!isNodeInside(clickedNode, editor.getTitleStack())) {
            commitTitle(editor);
            newScene.getRoot().requestFocus();
          }
        } else {
          commitTitle(editor);
          newScene.getRoot().requestFocus();
        }
      });
    });
  }

  private void wireBottomToolbar() {
    var bottom = view.getBottomToolbar();

    // Enter im Feld -> Zettelwechsel
    bottom.noteIdField().setText(String.valueOf(currentNote.getId()));
    bottom.noteIdField().setOnAction(e -> handleNoteIdCommit());

    // Klick woanders hin / Fokusverlust -> ebenfalls wechseln
    bottom.noteIdField().focusedProperty().addListener((obs, wasFocused, isFocused) -> {
      if (wasFocused && !isFocused) {
        handleNoteIdCommit();
      }
    });

    bottom.backButton().setOnAction(e -> navigateBack());
    bottom.forwardButton().setOnAction(e -> navigateForward());

    // BookToggle
    var toggle = view.getBottomToolbar().bookToggle();

    // initialer Zustand
    view.getNoteEditorPane().setBibliographyVisible(toggle.isSelected());

    // Umschalten
    toggle.selectedProperty().addListener((obs, was, is) -> renderBibliographyHost());
    renderBibliographyHost();

    updateHistoryButtons();
    syncBottomToolbarFromState();
  }

  private void wireHeaderBarNavigation() {
    var editor = view.getNoteEditorPane();
    editor.setOnHeaderPrev(this::openPrevNoteWrap);
    editor.setOnHeaderNext(this::openNextNoteWrap);
  }

  /**
   * Initialisiert das Popup zum manuellen Bearbeiten von Verweisen.
   */
  private void initLinkEditPopup() {
    linkEditRoot.setPadding(new Insets(14));
    linkEditRoot.setSpacing(10);
    linkEditRoot.setFillWidth(true);
    linkEditRoot.setPrefWidth(460);
    linkEditRoot.setMinWidth(460);
    linkEditRoot.setMaxWidth(460);
    linkEditRoot.setStyle(
        "-fx-background-color: -fx-control-inner-background;" +
            "-fx-border-color: -fx-box-border;" +
            "-fx-border-width: 1;" +
            "-fx-background-radius: 10;" +
            "-fx-border-radius: 10;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.18), 18, 0.18, 0, 4);"
    );

    linkEditPopup.setAutoHide(false);
    linkEditPopup.getContent().setAll(linkEditRoot);

    buildLinkEditPopupContent();
  }

  /**
   * Baut den Inhalt des Link-Edit-Popups auf.
   */
  private void buildLinkEditPopupContent() {
    Label heading = new Label("Verweise anlegen und entfernen");
    heading.setStyle(
        "-fx-font-size: 14px;" +
            "-fx-font-weight: bold;"
    );

    Separator separator = new Separator();

    HBox row1 = new HBox(6);
    row1.setAlignment(Pos.CENTER_LEFT);

    linkEditNoteIdField = new TextField();
    linkEditNoteIdField.setPromptText("Zettelnummer");
    linkEditNoteIdField.setPrefColumnCount(8);
    linkEditNoteIdField.setPrefWidth(110);
    linkEditNoteIdField.setTextFormatter(new TextFormatter<String>(change -> {
      String next = change.getControlNewText();
      return next.matches("\\d*") ? change : null;
    }));
    linkEditNoteIdField.textProperty().addListener((obs, oldValue, newValue) -> refreshLinkEditSelectionState());

    Button btnPrev = BaseIcon.BULLET_ARROW_LEFT.button();
    Button btnNext = BaseIcon.BULLET_ARROW_RIGHT.button();
    Button btnGoSuccessor = BaseIcon.BULLET_GO.button("nächster Folgezettel");
    linkEditReverseButton = BaseIcon.ARROW_REFRESH.button("Verweis umdrehen");
    Button btnClose = BaseIcon.CROSS.button();

    btnPrev.setOnAction(e -> selectPreviousExistingNoteInPopup());
    btnNext.setOnAction(e -> selectNextExistingNoteInPopup());
    btnGoSuccessor.setOnAction(e -> selectNextSuccessorInPopup());
    linkEditReverseButton.setOnAction(e -> reverseSelectedLinkInPopup());
    btnClose.setOnAction(e -> closeLinkEditPopup());

    btnPrev.setFocusTraversable(false);
    btnNext.setFocusTraversable(false);
    btnGoSuccessor.setFocusTraversable(false);
    linkEditReverseButton.setFocusTraversable(false);
    btnClose.setFocusTraversable(false);

    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);

    row1.getChildren().addAll(
        linkEditNoteIdField,
        btnPrev,
        btnNext,
        btnGoSuccessor,
        linkEditReverseButton,
        spacer,
        btnClose
    );

    HBox row2 = new HBox(8);
    row2.setAlignment(Pos.CENTER_LEFT);
    row2.setPadding(new Insets(2, 0, 2, 0));

    linkEditTitleLabel = new Label("—");
    linkEditTitleLabel.setWrapText(false);
    linkEditTitleLabel.setMaxWidth(Double.MAX_VALUE);
    linkEditTitleLabel.setEllipsisString("…");
    linkEditTitleLabel.setStyle(
        "-fx-font-size: 12px;" +
            "-fx-font-weight: bold;"
    );
    HBox.setHgrow(linkEditTitleLabel, Priority.ALWAYS);

    linkEditStatusLabel = new Label("");
    linkEditStatusLabel.setStyle("-fx-text-fill: -fx-text-inner-color;");

    row2.getChildren().addAll(linkEditTitleLabel, linkEditStatusLabel);

    HBox row3 = new HBox(6);
    row3.setAlignment(Pos.CENTER_LEFT);
    row3.setPadding(new Insets(4, 0, 0, 0));

    ToggleGroup group = new ToggleGroup();

    linkEditSerieButton = createLinkTypeToggleButton(BaseIcon.PAGE_LINK, "Folgezettel", LinkType.SERIE, group);
    linkEditIdeaButton = createLinkTypeToggleButton(BaseIcon.PAGE_BULB_ON, "Idee", LinkType.IDEE, group);
    linkEditQuestionButton = createLinkTypeToggleButton(BaseIcon.QUESTION, "Frage", LinkType.FRAGE, group);
    linkEditCritiqueButton = createLinkTypeToggleButton(BaseIcon.PAGE_LIGHTNING, "Kritik", LinkType.KRITIK, group);
    linkEditTaskButton = createLinkTypeToggleButton(BaseIcon.PAGE_EDIT, "Aufgabe", LinkType.AUFGABE, group);

    row3.getChildren().addAll(
        linkEditSerieButton,
        linkEditIdeaButton,
        linkEditQuestionButton,
        linkEditCritiqueButton,
        linkEditTaskButton
    );

    linkEditRoot.getChildren().setAll(heading, separator, row1, row2, row3);
  }

  /**
   * Erzeugt einen ToggleButton für einen Verweistyp.
   *
   * @param icon Icon des Buttons
   * @param tooltip Tooltiptext
   * @param linkType zugehöriger Verweistyp
   * @param group gemeinsame Toggle-Gruppe
   * @return konfigurierter ToggleButton
   */
  private ToggleButton createLinkTypeToggleButton(BaseIcon icon,
                                                  String tooltip,
                                                  LinkType linkType,
                                                  ToggleGroup group) {
    ToggleButton button = new ToggleButton();
    button.getStyleClass().add("icon-button");
    button.setGraphic(icon.imageView());
    button.setTooltip(new Tooltip(tooltip));
    button.setFocusTraversable(false);
    button.setToggleGroup(group);
    button.setMinWidth(34);
    button.setPrefWidth(34);
    button.setMinHeight(30);
    button.setPrefHeight(30);
    button.setStyle("-fx-background-radius: 6;");
    button.setOnAction(e -> handleLinkTypeToggle(linkType, button.isSelected()));
    return button;
  }

  /**
   * Zeigt das Popup an oder blendet es aus.
   */
  private void toggleLinkEditPopup() {
    if (linkEditPopup.isShowing()) {
      closeLinkEditPopup();
      return;
    }
    openLinkEditPopup();
  }

  /**
   * Öffnet das Popup mittig über dem Zettelkastenfenster.
   */
  private void openLinkEditPopup() {
    if (currentNote == null || currentNote.getId() <= 0) {
      return;
    }

    refreshCurrentSuccessorsFromRepository();
    setLinkEditSelectedNoteId(currentNote.getId());
    refreshLinkEditSelectionState();

    if (!linkEditPopup.isShowing()) {
      linkEditPopup.show(stage);
    }

    centerLinkEditPopup();
    Platform.runLater(() -> {
      centerLinkEditPopup();
      linkEditNoteIdField.requestFocus();
      linkEditNoteIdField.positionCaret(linkEditNoteIdField.getText().length());
    });
  }

  /**
   * Schließt das Popup.
   */
  private void closeLinkEditPopup() {
    linkEditPopup.hide();
  }

  /**
   * Zentriert das Popup im aktuellen Fenster.
   */
  private void centerLinkEditPopup() {
    if (!linkEditPopup.isShowing()) {
      return;
    }

    double popupWidth = linkEditRoot.getWidth() > 0 ? linkEditRoot.getWidth() : linkEditRoot.prefWidth(-1);
    double popupHeight = linkEditRoot.getHeight() > 0 ? linkEditRoot.getHeight() : linkEditRoot.prefHeight(-1);

    double x = stage.getX() + (stage.getWidth() - popupWidth) / 2.0;
    double y = stage.getY() + (stage.getHeight() - popupHeight) / 2.0;

    linkEditPopup.setX(x);
    linkEditPopup.setY(y);
  }

  /**
   * Setzt die aktuell im Popup ausgewählte Zettelnummer.
   *
   * @param noteId auszuwählende Zettelnummer
   */
  private void setLinkEditSelectedNoteId(int noteId) {
    updatingLinkEditUi = true;
    try {
      linkEditNoteIdField.setText(noteId <= 0 ? "" : Integer.toString(noteId));
    } finally {
      updatingLinkEditUi = false;
    }
  }

  /**
   * Aktualisiert Titelanzeige, Statusmeldung und Verweistyp-Buttons
   * anhand der aktuell im Popup ausgewählten Zettelnummer.
   */
  private void refreshLinkEditSelectionState() {
    if (linkEditNoteIdField == null || updatingLinkEditUi) {
      return;
    }

    Integer selectedNoteId = parseLinkEditSelectedNoteId();
    if (selectedNoteId == null || selectedNoteId <= 0) {
      linkEditTitleLabel.setText("—");
      linkEditStatusLabel.setText("Zettelnummer eingeben");
      linkEditStatusLabel.setStyle("-fx-text-fill: #8a6d3b;");
      selectLinkTypeToggle(null);
      setLinkTypeButtonsDisabled(true);
      if (linkEditReverseButton != null) {
        linkEditReverseButton.setDisable(true);
      }
      return;
    }

    boolean exists = noteRepository.existsNote(selectedNoteId);
    if (!exists) {
      linkEditTitleLabel.setText("—");
      linkEditStatusLabel.setText("Zettel existiert nicht");
      linkEditStatusLabel.setStyle("-fx-text-fill: #b94a48;");
      selectLinkTypeToggle(null);
      setLinkTypeButtonsDisabled(true);
      if (linkEditReverseButton != null) {
        linkEditReverseButton.setDisable(true);
      }
      return;
    }

    String title = noteRepository.loadNoteTitle(selectedNoteId).orElse("");
    linkEditTitleLabel.setText(title.isBlank() ? "—" : title);

    if (currentNote != null && currentNote.getId() > 0 && selectedNoteId == currentNote.getId()) {
      linkEditStatusLabel.setText("Selbstverweis ist nicht erlaubt");
      linkEditStatusLabel.setStyle("-fx-text-fill: #b94a48;");
      selectLinkTypeToggle(null);
      setLinkTypeButtonsDisabled(true);
      if (linkEditReverseButton != null) {
        linkEditReverseButton.setDisable(true);
      }
      return;
    }

    linkEditStatusLabel.setText("");
    linkEditStatusLabel.setStyle("-fx-text-fill: -fx-text-inner-color;");
    setLinkTypeButtonsDisabled(false);
    if (linkEditReverseButton != null) {
      linkEditReverseButton.setDisable(false);
    }

    selectLinkTypeToggle(findCurrentLinkTypeTo(selectedNoteId));
  }

  /**
   * Liest die aktuell im Popup ausgewählte Zettelnummer.
   *
   * @return ausgewählte Zettelnummer oder {@code null}
   */
  private Integer parseLinkEditSelectedNoteId() {
    if (linkEditNoteIdField == null) {
      return null;
    }

    String text = linkEditNoteIdField.getText();
    if (text == null || text.isBlank()) {
      return null;
    }

    try {
      return Integer.parseInt(text);
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  /**
   * Wählt im Popup den vorherigen existierenden Zettel aus.
   * Gibt es keinen kleineren mehr, wird zum höchsten existierenden Zettel umgebrochen.
   */
  private void selectPreviousExistingNoteInPopup() {
    int maxExisting = noteRepository.maxNoteId();
    if (maxExisting <= 0) {
      refreshLinkEditSelectionState();
      return;
    }

    Integer currentSelection = parseLinkEditSelectedNoteId();
    int start = currentSelection == null || currentSelection <= 0 ? maxExisting + 1 : currentSelection;

    OptionalInt previous = noteRepository.findPreviousExistingNoteId(start);
    if (previous.isPresent()) {
      setLinkEditSelectedNoteId(previous.getAsInt());
    } else {
      setLinkEditSelectedNoteId(maxExisting);
    }

    refreshLinkEditSelectionState();
  }

  /**
   * Wählt im Popup den nächsten existierenden Zettel aus.
   * Gibt es keinen größeren mehr, wird zum kleinsten existierenden Zettel umgebrochen.
   */
  private void selectNextExistingNoteInPopup() {
    Integer currentSelection = parseLinkEditSelectedNoteId();
    int start = currentSelection == null || currentSelection <= 0 ? 0 : currentSelection;

    OptionalInt next = noteRepository.findNextExistingNoteId(start);
    if (next.isPresent()) {
      setLinkEditSelectedNoteId(next.getAsInt());
    } else {
      OptionalInt first = noteRepository.findNextExistingNoteId(0);
      first.ifPresent(this::setLinkEditSelectedNoteId);
    }

    refreshLinkEditSelectionState();
  }

  /**
   * Springt im Popup zyklisch durch die Folgezettel des aktuell geöffneten Zettels.
   */
  private void selectNextSuccessorInPopup() {
    refreshCurrentSuccessorsFromRepository();

    List<Integer> successorIds = currentNote.getSuccessors().stream()
                                     .map(NoteLink::toNoteId)
                                     .distinct()
                                     .sorted()
                                     .toList();

    if (successorIds.isEmpty()) {
      linkEditStatusLabel.setText("Keine Folgezettel vorhanden");
      return;
    }

    Integer selected = parseLinkEditSelectedNoteId();
    int nextId = successorIds.getFirst();

    if (selected != null && successorIds.contains(selected)) {
      for (Integer candidate : successorIds) {
        if (candidate > selected) {
          nextId = candidate;
          break;
        }
      }
    }

    setLinkEditSelectedNoteId(nextId);
    refreshLinkEditSelectionState();
  }

  /**
   * Dreht den aktuell ausgewählten Verweis im Link-Edit-Popup um.
   * Aus dem Verweis vom aktuell sichtbaren Zettel auf den ausgewählten Zettel
   * wird ein Verweis in Gegenrichtung. Der bestehende Verweistyp bleibt erhalten.
   */
  private void reverseSelectedLinkInPopup() {
    if (currentNote == null || currentNote.getId() <= 0) {
      return;
    }

    Integer selectedNoteId = parseLinkEditSelectedNoteId();
    if (selectedNoteId == null || selectedNoteId <= 0) {
      linkEditStatusLabel.setText("Zettelnummer eingeben");
      linkEditStatusLabel.setStyle("-fx-text-fill: #8a6d3b;");
      return;
    }

    if (!noteRepository.existsNote(selectedNoteId)) {
      linkEditStatusLabel.setText("Zettel existiert nicht");
      linkEditStatusLabel.setStyle("-fx-text-fill: #b94a48;");
      return;
    }

    if (selectedNoteId == currentNote.getId()) {
      linkEditStatusLabel.setText("Selbstverweis ist nicht erlaubt");
      linkEditStatusLabel.setStyle("-fx-text-fill: #b94a48;");
      return;
    }

    LinkType currentType = findCurrentLinkTypeTo(selectedNoteId);
    if (currentType == null) {
      linkEditStatusLabel.setText("Kein Verweis zum Umdrehen vorhanden");
      linkEditStatusLabel.setStyle("-fx-text-fill: #8a6d3b;");
      return;
    }

    boolean reversed = noteRepository.reverseSuccessorLink(currentNote.getId(), selectedNoteId);
    if (!reversed) {
      linkEditStatusLabel.setText("Verweis konnte nicht umgedreht werden");
      linkEditStatusLabel.setStyle("-fx-text-fill: #b94a48;");
      return;
    }

    refreshCurrentSuccessorsFromRepository();
    syncInfoPopup(currentNote);
    refreshLinkEditSelectionState();

    linkEditStatusLabel.setText("Verweis wurde umgedreht");
    linkEditStatusLabel.setStyle("-fx-text-fill: -fx-text-inner-color;");
  }

  /**
   * Reagiert auf das Umschalten eines Verweistyp-Buttons.
   *
   * @param linkType betroffener Verweistyp
   * @param selected neuer Auswahlzustand des Buttons
   */
  private void handleLinkTypeToggle(LinkType linkType, boolean selected) {
    if (updatingLinkEditUi || currentNote == null || currentNote.getId() <= 0) {
      return;
    }

    Integer targetId = parseLinkEditSelectedNoteId();
    if (targetId == null || targetId <= 0 || !noteRepository.existsNote(targetId)) {
      refreshLinkEditSelectionState();
      return;
    }

    if (targetId == currentNote.getId()) {
      linkEditStatusLabel.setText("Selbstverweis ist nicht erlaubt");
      linkEditStatusLabel.setStyle("-fx-text-fill: #b94a48;");
      selectLinkTypeToggle(null);
      setLinkTypeButtonsDisabled(true);
      return;
    }

    if (!selected) {
      LinkType currentType = findCurrentLinkTypeTo(targetId);
      if (currentType != null && currentType == linkType) {
        noteRepository.removeSuccessorLink(currentNote.getId(), targetId);
        refreshCurrentSuccessorsFromRepository();
        syncInfoPopup(currentNote);
      }
      refreshLinkEditSelectionState();
      return;
    }

    noteRepository.addSuccessorLink(currentNote.getId(), targetId, linkType);
    refreshCurrentSuccessorsFromRepository();
    syncInfoPopup(currentNote);
    refreshLinkEditSelectionState();
  }

  /**
   * Aktualisiert die im aktuellen Zettel gehaltene Folgezettel-Liste
   * aus der Datenbank.
   */
  private void refreshCurrentSuccessorsFromRepository() {
    if (currentNote == null || currentNote.getId() <= 0) {
      return;
    }
    currentNote.setSuccessors(noteRepository.loadSuccessors(currentNote.getId()));
  }

  /**
   * Ermittelt den aktuellen Verweistyp vom geöffneten Zettel auf den gewählten Zielzettel.
   *
   * @param targetId Zielzettel
   * @return aktueller Verweistyp oder {@code null}
   */
  private LinkType findCurrentLinkTypeTo(int targetId) {
    if (currentNote == null || currentNote.getSuccessors() == null) {
      return null;
    }

    for (NoteLink link : currentNote.getSuccessors()) {
      if (link != null && link.toNoteId() == targetId) {
        return link.type();
      }
    }
    return null;
  }

  /**
   * Synchronisiert die Toggle-Auswahl im Popup mit dem aktuellen Verweistyp.
   *
   * @param selectedType aktuell ausgewählter Typ oder {@code null}
   */
  private void selectLinkTypeToggle(LinkType selectedType) {
    updatingLinkEditUi = true;
    try {
      linkEditSerieButton.setSelected(selectedType == LinkType.SERIE);
      linkEditIdeaButton.setSelected(selectedType == LinkType.IDEE);
      linkEditQuestionButton.setSelected(selectedType == LinkType.FRAGE);
      linkEditCritiqueButton.setSelected(selectedType == LinkType.KRITIK);
      linkEditTaskButton.setSelected(selectedType == LinkType.AUFGABE);
    } finally {
      updatingLinkEditUi = false;
    }
  }

  /**
   * Aktiviert oder deaktiviert alle Verweistyp-Buttons des Popups gemeinsam.
   *
   * @param disabled {@code true}, wenn die Buttons deaktiviert werden sollen
   */
  private void setLinkTypeButtonsDisabled(boolean disabled) {
    if (linkEditSerieButton == null) {
      return;
    }

    linkEditSerieButton.setDisable(disabled);
    linkEditIdeaButton.setDisable(disabled);
    linkEditQuestionButton.setDisable(disabled);
    linkEditCritiqueButton.setDisable(disabled);
    linkEditTaskButton.setDisable(disabled);
  }

  private void beginTitleEdit(NoteEditorPane editor) {
    if (titleEditing) {
      editor.getTitleEditorArea().requestFocus();
      return;
    }

    titleEditing = true;
    titleTextBeforeEdit = editor.getTitleEditorArea().getText();

    editor.getTitleEditorArea().setMouseTransparent(false);
    editor.getTitleEditorArea().setFocusTraversable(true);
    editor.getTitleEditorArea().setEditable(true);

    editor.getTitleEditorArea().requestFocus();
    editor.getTitleEditorArea().positionCaret(editor.getTitleEditorArea().getText().length());
  }

  private void commitTitle(NoteEditorPane editor) {
    if (!titleEditing) {
      return;
    }
    titleEditing = false;

    String normalized = editor.getTitleEditorArea().getText() == null ? "" : editor.getTitleEditorArea().getText().trim();
    if (normalized.isEmpty()) {
      editor.getTitleEditorArea().clear(); // Prompt "Überschrift" erscheint wieder
    }

    editor.getTitleEditorArea().setEditable(false);
    editor.getTitleEditorArea().setMouseTransparent(true);
    editor.getTitleEditorArea().setFocusTraversable(false);

    if (!loading) {
      saveCurrentIfValid();
    }
  }

  private void cancelTitle(NoteEditorPane editor) {
    if (!titleEditing) {
      return;
    }
    titleEditing = false;

    editor.getTitleEditorArea().setText(titleTextBeforeEdit);

    editor.getTitleEditorArea().setEditable(false);
    editor.getTitleEditorArea().setMouseTransparent(true);
    editor.getTitleEditorArea().setFocusTraversable(false);
  }

  private void applyCurrentNoteToUi() {
    if (currentNote == null) {
      return;
    }

    currentNote.applyTo(view.getNoteEditorPane(), view.getKeywordsTablePane());
    syncInfoPopup(currentNote);
    renderBibliographyHost();
    syncBottomToolbar(currentNote);
    view.getNoteEditorPane().setBibliographyVisible(
        view.getBottomToolbar().bookToggle().isSelected()
    );
  }

  /**
   * Synchronisiert die Daten des Info-Popups mit dem aktuell sichtbaren Zettel.
   * Dabei werden ausgehende und eingehende Referenzen sowie Projekte geladen.
   *
   * @param note aktuell sichtbarer Zettel
   */
  private void syncInfoPopup(Note note) {
    if (note == null || note.getId() <= 0) {
      view.getNoteEditorPane().setInfoData(List.of(), List.of(), List.of());
      return;
    }

    List<NoteReferenceInfo> outgoing = noteRepository.loadOutgoingReferenceInfos(note.getId());
    List<NoteReferenceInfo> incoming = noteRepository.loadIncomingReferenceInfos(note.getId());
    List<String> projects = note.getProjects() == null ? List.of() : List.copyOf(note.getProjects());

    view.getNoteEditorPane().setInfoData(outgoing, incoming, projects);
  }

  /**
   * Öffnet den initialen Zettel
   */
  private void openInitialNote() {
    maxNoteId = noteRepository.maxNoteId();

    if (maxNoteId <= 0) {
      currentNote = new Note();
      applyCurrentNoteToUi();
      refreshKeywordCounts();
      return;
    }

    onNoteActivated(1);
  }

  private boolean isNodeInside(Node node, Node container) {
    Node current = node;
    while (current != null) {
      if (current == container) {
        return true;
      }
      current = current.getParent();
    }
    return false;
  }

  private void deactivateBody(NoteEditorPane editor) {
    editor.getBodyContainer().getStyleClass().remove("note-body-active");
    editor.getBodyArea().setEditable(false);
    editor.getFormattingContextMenu().hide();
    saveCurrentIfValid();
  }

  private void refreshKeywordCounts() {
    var counts = noteRepository.loadKeywordCounts();
    view.getKeywordsTablePane().applyCounts(counts);
  }

  private void handleNoteIdCommit() {
    if (updatingNoteIdField) return;

    var field = view.getBottomToolbar().noteIdField();
    String txt = field.getText();

    if (txt == null || txt.isBlank()) {
      // Wenn leer, wieder den aktuellen anzeigen (falls vorhanden)
      syncBottomToolbarFromState();
      return;
    }

    int requested;
    try {
      requested = Integer.parseInt(txt.trim());
    } catch (NumberFormatException ex) {
      syncBottomToolbarFromState();
      return;
    }

    int clamped = clampToAllowedRange(requested);

    // Wenn maxNoteId==0, gibt's (noch) nichts zu öffnen
    if (clamped <= 0) {
      syncBottomToolbarFromState();
      return;
    }

    // Feld ggf. automatisch auf die zulässige Nummer korrigieren
    if (clamped != requested) {
      setNoteIdField(clamped);
    }

    // Wenn bereits aktuell: nichts tun
    if (currentNote != null && currentNote.getId() == clamped) {
      return;
    }

    // Zettelwechsel auslösen (History wird hierbei gepflegt)
    openNoteViaToolbar(clamped);
  }

  private int clampToAllowedRange(int requested) {
    if (maxNoteId <= 0) return 0;

    if (requested < 1) return 1;
    if (requested > maxNoteId) return maxNoteId;
    return requested;
  }

  private void setNoteIdField(int id) {
    updatingNoteIdField = true;
    try {
      view.getBottomToolbar().noteIdField().setText(Integer.toString(id));
    } finally {
      updatingNoteIdField = false;
    }
  }

  private void syncBottomToolbarFromState() {
    var field = view.getBottomToolbar().noteIdField();
    if (field.isFocused() && !updatingNoteIdField) {
      return;
    }
    if (currentNote != null && currentNote.getId() > 0) {
      setNoteIdField(currentNote.getId());
    }
  }

  /**
   * Öffnet einen Zettel über die zentrale Navigationslogik.
   * Vor dem tatsächlichen Wechsel wird der aktuelle Zettel gespeichert
   * oder verworfen, falls dies beim Verlassen nötig ist.
   *
   * @param id ID des zu öffnenden Zettels
   */
  private void openNoteViaToolbar(int id) {
    if (currentNote != null && currentNote.getId() == id) {
      return;
    }

    saveOrDiscardCurrentNoteBeforeLeave();

    if (!historyNavigation) {
      if (currentNote != null && currentNote.getId() > 0 && currentNote.getId() != id) {
        backHistory.push(currentNote.getId());
        forwardHistory.clear();
      }
    }

    onNoteActivated(id);
    updateHistoryButtons();
  }

  private void onNoteActivated(int requestedId) {
    loading = true;
    try {
      // 1) Max-ID frisch holen (für Clamp + Anzeige "von")
      int max = noteRepository.maxNoteId();
      maxNoteId = max;

      if (max <= 0) return;

      int id = Math.max(1, Math.min(requestedId, max));

      var opt = noteRepository.load(id);
      if (opt.isEmpty()) return;

      currentNote = opt.get();

      // UI befüllen aus der Note
      currentNote.applyTo(view.getNoteEditorPane(), view.getKeywordsTablePane());
      syncInfoPopup(currentNote);
      renderBibliographyHost();

      // BottomToolbar befüllen (nur Anzeige, Feld bleibt editierbar)
      setNoteIdField(currentNote.getId());

      // Counts nach dem Laden sofort aktualisieren
      refreshKeywordCounts();

      view.getNoteEditorPane().setOnNavigateToNote(this::openNoteViaToolbar);
      view.getNoteEditorPane().setBibliographyVisible(view.getBottomToolbar().bookToggle().isSelected());
      if (linkEditPopup.isShowing()) {
        refreshCurrentSuccessorsFromRepository();
        refreshLinkEditSelectionState();
        Platform.runLater(this::centerLinkEditPopup);
      }
    } finally {
      loading = false;
    }
  }

  private void updateHistoryButtons() {
    var bottom = view.getBottomToolbar();
    bottom.backButton().setDisable(backHistory.isEmpty());
    bottom.forwardButton().setDisable(forwardHistory.isEmpty());
  }

  private void navigateBack() {
    if (backHistory.isEmpty()) return;

    Integer current = currentNote.getId();
    if (current > 0) {
      forwardHistory.push(current);
    }

    int target = backHistory.pop();
    historyNavigation = true;
    try {
      openNoteViaToolbar(target);
    } finally {
      historyNavigation = false;
    }
  }

  private void navigateForward() {
    if (forwardHistory.isEmpty()) return;

    Integer current = currentNote.getId();
    if (current > 0) {
      backHistory.push(current);
    }

    int target = forwardHistory.pop();
    historyNavigation = true;
    try {
      openNoteViaToolbar(target);
    } finally {
      historyNavigation = false;
    }
  }

  /**
   * erstellt einen neuen Zettel mit maxId+1
   */
  private void createNewEmptyNote() {
    saveOrDiscardCurrentNoteBeforeLeave(); // aktuellen ggf. sichern

    currentNote = new Note();
    currentNote.setId(allocateNextNoteId());

    // UI leeren (über Note, damit nichts “verstreut” bleibt)
    showCurrentDraftNote();

    setNoteIdField(currentNote.getId());
    updateHistoryButtons();
  }

  /**
   * Kopiert den aktuellen Zettel in einen neuen Zettel.
   */
  private void createCopyOfCurrentNote() {
    var editor = view.getNoteEditorPane();

    String title = normalize(editor.getTitleEditorArea().getText());
    String bodyPlain = normalize(editor.getBodyArea().getText());

    if (title.isEmpty() || bodyPlain.isEmpty()) {
      return;
    }

    // alten Zettel abspeichern
    saveOrDiscardCurrentNoteBeforeLeave();

    // Daten zwischenspeichern
    // TODO: später ergänzen
    byte[] bodyBlob = java.util.Arrays.copyOf(currentNote.getBodyBlob(), currentNote.getBodyBlob().length);
    String bodyCodec = currentNote.getBodyCodec();
    List<String> kws = new java.util.ArrayList<>(currentNote.getKeywords());

    Note tempNote = new Note();
    tempNote.setId(allocateNextNoteId());
    tempNote.setTitle(title);
    tempNote.setBodyBlob(bodyBlob);
    tempNote.setBodyCodec(bodyCodec);
    tempNote.setKeywords(kws);

    currentNote = tempNote;
    int savedId = noteRepository.save(currentNote);
    currentNote.setId(savedId);
    maxNoteId = Math.max(maxNoteId, savedId);

    applyCurrentNoteToUi();
    InlineCssStyleUtil.clearHeadingForSelection(editor.getBodyArea());

    refreshKeywordCounts();
    syncBottomToolbar(currentNote);
  }

  /**
   * Zentrale Berechnung der Id
   *
   * @return MaxId (db) + 1
   */
  private int allocateNextNoteId() {
    // DB-Stand + lokaler Stand, damit auch “unsaved” neue Zettel mitgezählt werden
    int dbMax = noteRepository.maxNoteId();
    maxNoteId = Math.max(maxNoteId, dbMax);
    maxNoteId++;
    return maxNoteId;
  }

  /**
   * Erstellt einen neuen leeren Zettel und übernimmt dabei die bibliographische
   * Verknüpfung des aktuell geöffneten Zettels.
   * Es werden ausschließlich bibliographyType und bibliographyRefId übernommen,
   * nicht jedoch Titel, Inhalt oder Keywords.
   */
  private void createCopyBibliography() {
    saveOrDiscardCurrentNoteBeforeLeave();

    Note newNote = new Note();
    newNote.setId(allocateNextNoteId());

    copyBibliographyReference(currentNote, newNote);

    currentNote = newNote;

    applyCurrentNoteToUi();
    InlineCssStyleUtil.clearHeadingForSelection(view.getNoteEditorPane().getBodyArea());

    setNoteIdField(currentNote.getId());
    updateHistoryButtons();
  }

  /**
   * Legt einen neuen Folgezettel mit dem angegebenen Linktyp an.
   * Zuerst wird der aktuelle Zettel gespeichert. Anschließend wird am
   * aktuellen Zettel sofort ein Verweis auf die neue Ziel-ID angelegt.
   * Danach wird der neue Zielzettel als leerer Entwurf geöffnet.
   *
   * Bleibt dieser Zielzettel beim Verlassen ungültig und ist er zugleich
   * der letzte Zettel, wird der Verweis wieder entfernt.
   *
   * @param linkType Typ des Folgezettels
   */
  private void createTypedFollowUpNote(LinkType linkType) {
    if (linkType == null) {
      return;
    }

    saveOrDiscardCurrentNoteBeforeLeave();

    if (currentNote == null || currentNote.getId() <= 0) {
      return;
    }

    int sourceId = currentNote.getId();
    int targetId = allocateNextNoteId();

    noteRepository.addSuccessorLink(sourceId, targetId, linkType);

    List<NoteLink> successors = new ArrayList<>(currentNote.getSuccessors());
    successors.removeIf(link -> link != null && link.toNoteId() == targetId);
    successors.add(new NoteLink(targetId, linkType));
    currentNote.setSuccessors(successors);

    pendingFollowUpTargetNoteId = targetId;

    currentNote = new Note();
    currentNote.setId(targetId);

    showCurrentDraftNote();

    setNoteIdField(currentNote.getId());
    updateHistoryButtons();
  }

  /**
   * Zeigt den aktuell in {@code currentNote} liegenden Entwurfszettel an und
   * unterdrückt genau einmal die Invalid-Behandlung direkt nach dem internen
   * Wechsel, damit neu angelegte leere Zettel nicht sofort wieder verworfen
   * werden.
   */
  private void showCurrentDraftNote() {
    suppressInvalidCleanupOnce = true;

    applyCurrentNoteToUi();
    InlineCssStyleUtil.clearHeadingForSelection(view.getNoteEditorPane().getBodyArea());

    Platform.runLater(() -> suppressInvalidCleanupOnce = false);
  }

  /**
   * Behandelt einen ungültigen Zettel beim tatsächlichen Verlassen.
   * Ein ungültiger Zettel wird nur dann verworfen, wenn er der letzte Zettel ist.
   * Wurde zu diesem Zettel ein Folgezettel-Link angelegt, werden alle eingehenden
   * Verweise auf diesen Zettel entfernt, sobald der Zettel verworfen wird.
   */
  private void handleInvalidCurrentNoteBeforeLeave() {
    if (currentNote == null || currentNote.getId() <= 0) {
      clearPendingFollowUpStateIfCurrentMatches();
      return;
    }

    if (suppressInvalidCleanupOnce) {
      return;
    }

    int currentId = currentNote.getId();

    boolean noteExists = noteRepository.existsNote(currentId);
    boolean deletedBecauseLast = false;

    if (noteExists) {
      deletedBecauseLast = noteRepository.deleteNoteIfItIsLast(currentId);
      if (deletedBecauseLast) {
        noteRepository.removeAllIncomingLinksToNote(currentId);
        clearPendingFollowUpStateIfCurrentMatches();
        maxNoteId = Math.max(noteRepository.maxNoteId(), 0);
        closeLinkEditPopup();
        return;
      }
    } else {
      boolean isLastOrBeyondLast = currentId >= noteRepository.maxNoteId();
      if (isLastOrBeyondLast) {
        noteRepository.removeAllIncomingLinksToNote(currentId);
        clearPendingFollowUpStateIfCurrentMatches();
        maxNoteId = Math.max(noteRepository.maxNoteId(), 0);
        closeLinkEditPopup();
        return;
      }
    }

    clearPendingFollowUpStateIfCurrentMatches();
  }

  /**
   * Löscht lediglich den Pending-Zustand eines Folgezettels, ohne den Link
   * in der Datenbank zu entfernen.
   */
  private void clearPendingFollowUpStateIfCurrentMatches() {
    if (currentNote == null || pendingFollowUpTargetNoteId == null) {
      return;
    }
    if (currentNote.getId() != pendingFollowUpTargetNoteId) {
      return;
    }

    pendingFollowUpTargetNoteId = null;
  }

  /**
   * Markiert einen zuvor nur vorgemerkten Folgezettel als erfolgreich
   * gespeichert. Danach bleibt der Verweis dauerhaft bestehen.
   */
  private void clearPendingFollowUpAfterSuccessfulSave() {
    if (currentNote == null || pendingFollowUpTargetNoteId == null) {
      return;
    }
    if (currentNote.getId() != pendingFollowUpTargetNoteId) {
      return;
    }

    pendingFollowUpTargetNoteId = null;
  }

  /**
   * Überträgt die bibliographische Referenz vom Quellzettel auf den Zielzettel.
   * Wird kein gültiger Quellzettel übergeben oder enthält dieser keine
   * Bibliographie-Verknüpfung, bleibt der Zielzettel bibliographisch leer.
   *
   * @param source Quellzettel
   * @param target Zielzettel
   */
  private void copyBibliographyReference(Note source, Note target) {
    if (target == null) {
      return;
    }

    if (source == null
            || source.getBibliographyType() == null
            || source.getBibliographyType() == BibliographyType.USER
            || source.getBibliographyRefId() == null
            || source.getBibliographyRefId() <= 0) {
      target.setBibliographyType(BibliographyType.USER);
      target.setBibliographyRefId(null);
      target.setBibliographyEntry(null);
      return;
    }

    target.setBibliographyType(source.getBibliographyType());
    target.setBibliographyRefId(source.getBibliographyRefId());
    target.setBibliographyEntry(source.getBibliographyEntry());
  }

  /**
   * Speichert den aktuellen Zettel, sofern er gültig ist.
   * Ungültige Zettel werden hier bewusst nicht gelöscht oder verworfen,
   * da diese Methode auch bei Zwischenaktionen während des Editierens
   * aufgerufen wird.
   */
  private void saveCurrentIfValid() {
    if (loading) {
      return;
    }

    var editor = view.getNoteEditorPane();

    String title = editor.getTitleEditorArea().getText() == null ? "" : editor.getTitleEditorArea().getText().trim();
    String bodyPlain = editor.getBodyArea().getText() == null ? "" : editor.getBodyArea().getText().trim();

    if (title.isEmpty() || bodyPlain.isEmpty()) {
      return;
    }

    if (currentNote.getId() <= 0) {
      currentNote.setId(allocateNextNoteId());
    }

    currentNote.captureFrom(view.getNoteEditorPane(), view.getKeywordsTablePane());

    int savedId = noteRepository.save(currentNote);
    currentNote.setId(savedId);
    maxNoteId = Math.max(maxNoteId, savedId);

    clearPendingFollowUpAfterSuccessfulSave();

    setNoteIdField(savedId);
    refreshKeywordCounts();

    refreshCurrentSuccessorsFromRepository();
    syncInfoPopup(currentNote);
    renderBibliographyHost();
    syncBottomToolbar(currentNote);

    if (linkEditPopup.isShowing()) {
      refreshLinkEditSelectionState();
      Platform.runLater(this::centerLinkEditPopup);
    }
  }

  /**
   * Wird verwendet, wenn der aktuelle Zettel tatsächlich verlassen wird.
   * Gültige Zettel werden gespeichert.
   * Ungültige Zettel werden nur dann verworfen, wenn sie der letzte Zettel sind.
   */
  private void saveOrDiscardCurrentNoteBeforeLeave() {
    if (loading) {
      return;
    }

    var editor = view.getNoteEditorPane();

    String title = editor.getTitleEditorArea().getText() == null ? "" : editor.getTitleEditorArea().getText().trim();
    String bodyPlain = editor.getBodyArea().getText() == null ? "" : editor.getBodyArea().getText().trim();

    if (title.isEmpty() || bodyPlain.isEmpty()) {
      handleInvalidCurrentNoteBeforeLeave();
      return;
    }

    saveCurrentIfValid();
  }

  /**
   * Synchronisiert Eingabefeld im der Bottom-Toolbar
   */
  private void syncBottomToolbar(Note note) {
    var bottom = view.getBottomToolbar();

    // Feld befüllen, aber Eingabe bleibt frei (nur Rückkopplung verhindern)
    updatingNoteIdField = true;
    try {
      bottom.noteIdField().setText(note.getId() == 0 ? "" : Integer.toString(note.getId()));
    } finally {
      updatingNoteIdField = false;
    }
  }

  private static String normalize(String s) {
    return s == null ? "" : s.trim();
  }

  private void focusBodyForEditing(NoteEditorPane editor) {
    if (!editor.getBodyContainer().getStyleClass().contains("note-body-active")) {
      editor.getBodyContainer().getStyleClass().add("note-body-active");
    }
    editor.getBodyArea().setEditable(true);
    editor.getBodyArea().requestFocus();
  }

  /**
   * Öffnet den vorherigen Zettel mit Umlauf am Anfang.
   */
  private void openPrevNoteWrap() {
    int max = noteRepository.maxNoteId();
    if (max <= 0) {
      return;
    }

    int cur = (currentNote != null) ? currentNote.getId() : 0;
    if (cur <= 0) {
      cur = 1;
    }

    int target = (cur <= 1) ? max : (cur - 1);
    openNoteViaToolbar(target);
  }

  /**
   * Öffnet den nächsten Zettel mit Umlauf am Ende.
   */
  private void openNextNoteWrap() {
    int max = noteRepository.maxNoteId();
    if (max <= 0) {
      return;
    }

    int cur = (currentNote != null) ? currentNote.getId() : 0;
    if (cur <= 0) {
      cur = 1;
    }

    int target = (cur >= max) ? 1 : (cur + 1);
    openNoteViaToolbar(target);
  }

  private BibliographyEditorShell createBibliographyShell() {
    var shell = new BibliographyEditorShell();

    shell.setLookupProvider(new BibliographyLookupProvider() {
      @Override
      public List<AuthorSuggestion> findAuthors(
          TypeChoice type,
          String lastNamePrefix,
          Integer selectedEntryId
      ) {
        BibliographyType bType = toBibliographyType(type);
        return bibliographyRepository.findAuthorSuggestions(bType, lastNamePrefix, selectedEntryId);
      }

      @Override
      public List<TitleSuggestion> findTitles(
          TypeChoice type,
          String query,
          List<Author> requiredAuthors
      ) {
        BibliographyType bType = toBibliographyType(type);

        var hits = (bType == BibliographyType.COLLECTION && (requiredAuthors == null || requiredAuthors.isEmpty()))
                       ? bibliographyRepository.findCollectionSuggestions(query)
                       : bibliographyRepository.findTitleSuggestions(bType, query, requiredAuthors);

        return hits.stream()
                   .map(t -> new TitleSuggestion(
                       t.entryId(),
                       t.title(),
                       t.authors()
                   ))
                   .toList();
      }

      @Override
      public TitleSuggestion resolveExactTitle(
          TypeChoice type,
          String title,
          List<Author> requiredAuthors
      ) {
        BibliographyType bType = toBibliographyType(type);

        var hit = (bType == BibliographyType.COLLECTION)
                      ? bibliographyRepository.resolveExactCollectionTitle(title)
                      : bibliographyRepository.resolveExactTitle(bType, title, requiredAuthors);

        return hit == null ? null : new TitleSuggestion(
            hit.entryId(),
            hit.title(),
            hit.authors()
        );
      }

      @Override
      public TitleSuggestion resolveUniqueTitleForAuthors(
          TypeChoice type,
          List<Author> requiredAuthors
      ) {
        BibliographyType bType = toBibliographyType(type);
        var hit = bibliographyRepository.resolveUniqueTitleForAuthors(bType, requiredAuthors);
        return hit == null ? null : new TitleSuggestion(
            hit.entryId(),
            hit.title(),
            hit.authors()
        );
      }

      @Override
      public List<String> findSeries(TypeChoice type, String query) {
        BibliographyType bType = toBibliographyType(type);
        return bibliographyRepository.findSeriesSuggestions(bType, query)
                   .stream()
                   .map(BibliographyRepository.LookupSeries::value)
                   .toList();
      }

      @Override
      public Object loadEntry(TypeChoice type, Integer entryId) {
        if (entryId == null || entryId <= 0) {
          return null;
        }

        BibliographyType bType = toBibliographyType(type);
        return bibliographyRepository.load(entryId, bType);
      }
    });

    shell.setAiChoiceProvider(new AiChoiceProvider() {
      @Override
      public List<String> findProviders(String prefix, String modelFilter, int limit) {
        return bibliographyRepository.findAiProviders(prefix, modelFilter, limit);
      }

      @Override
      public List<String> findModels(String prefix, String providerFilter, int limit) {
        return bibliographyRepository.findAiModels(prefix, providerFilter, limit);
      }

      @Override
      public List<String> loadRecentProviders(int limit) {
        return bibliographyRepository.loadRecentAiProviders(limit);
      }

      @Override
      public List<String> loadRecentModels(int limit) {
        return bibliographyRepository.loadRecentAiModels(limit);
      }
    });

    shell.setTypeChoices(
        TypeChoice.NONE,
        TypeChoice.BOOK,
        TypeChoice.COLLECTION,
        TypeChoice.ARTICLE_IN_COLLECTION,
        TypeChoice.JOURNAL,
        TypeChoice.JOURNAL_ARTICLE,
        TypeChoice.THESIS,
        TypeChoice.INTERNET,
        TypeChoice.EDITION,
        TypeChoice.AI
    );

    shell.setOnDelete(this::unlinkCurrentBibliography);
    shell.setOnAdd(() -> saveAndLinkBibliography(shell));

    shell.setOnRequestCreateCollection(() -> openCollectionEditor(shell));
    shell.setOnRequestCreateJournal(() -> openJournalEditor(shell));

    shell.setOnNoneSelected(this::unlinkCurrentBibliography);
    return shell;
  }

  private void showBibliographyShell(BibliographyEditorShell shell) {
    var host = view.getNoteEditorPane().getBibliographyHost();
    host.getChildren().clear();
    host.getChildren().add(shell.getRoot());
  }

  private void showBibliographyError(String header, String content) {
    Alert alert = new Alert(Alert.AlertType.ERROR);
    alert.initOwner(stage);
    alert.setTitle("Bibliographische Angabe");
    alert.setHeaderText(header);
    alert.setContentText(content);
    alert.showAndWait();
  }

  private void showBibliographyWarning(String header, String content) {
    Alert alert = new Alert(Alert.AlertType.WARNING);
    alert.initOwner(stage);
    alert.setTitle("Bibliographische Angabe");
    alert.setHeaderText(header);
    alert.setContentText(content);
    alert.showAndWait();
  }

  private boolean confirmMissingRequiredFields(String typeLabel, String missingText) {
    Alert alert = new Alert(Alert.AlertType.WARNING);
    alert.initOwner(stage);
    alert.setTitle("Bibliographische Angabe");
    alert.setHeaderText(typeLabel + " kann noch nicht gespeichert werden");
    alert.setContentText("Es fehlen Pflichtangaben:\n\n" + missingText);
    alert.showAndWait();
    return false;
  }

  private enum ArticleWithoutCollectionDecision {
    SAVE_WITHOUT_COLLECTION,
    CREATE_COLLECTION,
    CANCEL
  }

  private ArticleWithoutCollectionDecision askArticleWithoutCollectionDecision() {
    ButtonType saveWithout = new ButtonType("Ohne Sammelband speichern", ButtonBar.ButtonData.YES);
    ButtonType createCollection = new ButtonType("Sammelband jetzt anlegen", ButtonBar.ButtonData.OTHER);
    ButtonType cancel = new ButtonType("Nicht speichern", ButtonBar.ButtonData.CANCEL_CLOSE);

    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
    alert.initOwner(stage);
    alert.setTitle("Artikel in Sammelband");
    alert.setHeaderText("Es ist kein Sammelband eingetragen");
    alert.setContentText(
        "Soll der Artikel trotzdem ohne verknüpften Sammelband gespeichert werden,\n" +
            "oder möchtest du zuerst den Sammelband anlegen?"
    );
    alert.getButtonTypes().setAll(saveWithout, createCollection, cancel);

    ButtonType result = alert.showAndWait().orElse(cancel);

    if (result == saveWithout) {
      return ArticleWithoutCollectionDecision.SAVE_WITHOUT_COLLECTION;
    }
    if (result == createCollection) {
      return ArticleWithoutCollectionDecision.CREATE_COLLECTION;
    }
    return ArticleWithoutCollectionDecision.CANCEL;
  }

  private boolean validateBookForSave(BookEntry entry) {
    List<String> missing = new ArrayList<>();

    if (!hasTitle(entry.getTitle())) {
      missing.add("• Buchtitel");
    }
    if (!hasAuthors(entry.getAuthors())) {
      missing.add("• mindestens ein Autor");
    }

    if (!missing.isEmpty()) {
      showBibliographyWarning(
          "Buch kann noch nicht gespeichert werden",
          "Es fehlen Pflichtangaben:\n\n" + String.join("\n", missing)
      );
      return false;
    }
    return true;
  }

  private boolean validateCollectionForSave(CollectionEntry entry) {
    List<String> missing = new ArrayList<>();

    if (!hasTitle(entry.getTitle())) {
      missing.add("• Titel des Sammelbands");
    }
    if (!hasAuthors(entry.getAuthors())) {
      missing.add("• mindestens ein Autor/Herausgeber");
    }

    if (!missing.isEmpty()) {
      showBibliographyWarning(
          "Sammelband kann noch nicht gespeichert werden",
          "Es fehlen Pflichtangaben:\n\n" + String.join("\n", missing)
      );
      return false;
    }
    return true;
  }

  private boolean validateArticleForSave(ArticleInCollectionEntry entry) {
    List<String> missing = new ArrayList<>();

    if (!hasTitle(entry.getTitle())) {
      missing.add("• Artikeltitel");
    }
    if (!hasAuthors(entry.getAuthors())) {
      missing.add("• mindestens ein Autor");
    }
    if (!hasCollectionTitle(entry.getCollectionTitle())) {
      missing.add("• Sammelband");
    }

    if (!missing.isEmpty()) {
      showBibliographyWarning(
          "Artikel in Sammelband kann noch nicht gespeichert werden",
          "Es fehlen Pflichtangaben:\n\n" + String.join("\n", missing)
      );
      return false;
    }

    return true;
  }

  /**
   * Prüft, ob eine Zeitschrift die nötigen Pflichtangaben zum Speichern enthält.
   *
   * @param entry zu prüfender Zeitschrifteneintrag.
   * @return {@code true}, wenn die Pflichtangaben vollständig sind.
   */
  private boolean validateJournalForSave(JournalEntry entry) {
    List<String> missing = new ArrayList<>();

    if (!hasTitle(entry.getTitle())) {
      missing.add("• Titel der Zeitschrift");
    }

    if (!missing.isEmpty()) {
      showBibliographyWarning(
          "Zeitschrift kann noch nicht gespeichert werden",
          "Es fehlen Pflichtangaben:\n\n" + String.join("\n", missing)
      );
      return false;
    }
    return true;
  }

  /**
   * Prüft, ob ein Zeitschriftenartikel die nötigen Pflichtangaben zum Speichern enthält.
   *
   * @param entry zu prüfender Zeitschriftenartikel.
   * @return {@code true}, wenn die Pflichtangaben vollständig sind.
   */
  private boolean validateJournalArticleForSave(JournalArticleEntry entry) {
    List<String> missing = new ArrayList<>();

    if (!hasTitle(entry.getTitle())) {
      missing.add("• Artikeltitel");
    }
    if (!hasAuthors(entry.getAuthors())) {
      missing.add("• mindestens ein Autor");
    }
    if (!hasTitle(entry.getJournalTitle())) {
      missing.add("• Zeitschrift");
    }

    if (!missing.isEmpty()) {
      showBibliographyWarning(
          "Zeitschriftenartikel kann noch nicht gespeichert werden",
          "Es fehlen Pflichtangaben:\n\n" + String.join("\n", missing)
      );
      return false;
    }
    return true;
  }

  /**
   * Prüft, ob eine Thesis die nötigen Pflichtangaben zum Speichern enthält.
   *
   * @param entry zu prüfender Thesis-Eintrag
   * @return {@code true}, wenn die Pflichtangaben vollständig sind
   */
  private boolean validateThesisForSave(ThesisEntry entry) {
    List<String> missing = new ArrayList<>();

    if (!hasTitle(entry.getTitle())) {
      missing.add("• Titel der Arbeit");
    }
    if (!hasAuthors(entry.getAuthors())) {
      missing.add("• mindestens ein Autor");
    }

    if (!missing.isEmpty()) {
      showBibliographyWarning(
          "Thesis kann noch nicht gespeichert werden",
          "Es fehlen Pflichtangaben:\n\n" + String.join("\n", missing)
      );
      return false;
    }
    return true;
  }

  /**
   * Prüft, ob eine Internetquelle die nötigen Pflichtangaben zum Speichern enthält.
   *
   * @param entry zu prüfender Internet-Eintrag
   * @return {@code true}, wenn die Pflichtangaben vollständig sind
   */
  private boolean validateInternetForSave(InternetEntry entry) {
    List<String> missing = new ArrayList<>();

    if (!hasTitle(entry.getTitle())) {
      missing.add("• Titel");
    }
    if (!hasAuthors(entry.getAuthors())) {
      missing.add("• mindestens ein Autor");
    }
    if (norm(entry.getUrl()).isBlank()) {
      missing.add("• URL");
    }

    if (!missing.isEmpty()) {
      showBibliographyWarning(
          "Internetquelle kann noch nicht gespeichert werden",
          "Es fehlen Pflichtangaben:\n\n" + String.join("\n", missing)
      );
      return false;
    }
    return true;
  }

  /**
   * Prüft, ob eine Edition die nötigen Pflichtangaben zum Speichern enthält.
   *
   * @param entry zu prüfender Editions-Eintrag
   * @return {@code true}, wenn die Pflichtangaben vollständig sind
   */
  private boolean validateEditionForSave(EditionEntry entry) {
    List<String> missing = new ArrayList<>();

    if (!hasTitle(entry.getTitle())) {
      missing.add("• Titel der Edition");
    }
    if (!hasAuthors(entry.getAuthors())) {
      missing.add("• mindestens ein Herausgeber / Bearbeiter");
    }

    if (!missing.isEmpty()) {
      showBibliographyWarning(
          "Edition kann noch nicht gespeichert werden",
          "Es fehlen Pflichtangaben:\n\n" + String.join("\n", missing)
      );
      return false;
    }
    return true;
  }

  /**
   * Prüft, ob eine KI-Quelle die nötigen Pflichtangaben zum Speichern enthält.
   *
   * @param entry zu prüfender KI-Eintrag
   * @return {@code true}, wenn die Pflichtangaben vollständig sind
   */
  private boolean validateAiForSave(AiEntry entry) {
    List<String> missing = new ArrayList<>();

    if (!hasTitle(entry.getTitle())) {
      missing.add("• Titel");
    }
    if (norm(entry.getProvider()).isBlank()) {
      missing.add("• Anbieter");
    }
    if (norm(entry.getModel()).isBlank()) {
      missing.add("• Modell");
    }

    if (!missing.isEmpty()) {
      showBibliographyWarning(
          "KI-Quelle kann noch nicht gespeichert werden",
          "Es fehlen Pflichtangaben:\n\n" + String.join("\n", missing)
      );
      return false;
    }
    return true;
  }

  private void showRepositorySaveFailed(BibliographyType type) {
    String details = bibliographyRepository.getLastErrorMessage();
    if (details == null || details.isBlank()) {
      details = "Der Eintrag konnte nicht gespeichert werden.";
    }

    showBibliographyError(
        "Speichern fehlgeschlagen (" + type.name() + ")",
        details
    );
  }

  private boolean currentNoteExistsInDatabase() {
    if (currentNote == null || currentNote.getId() <= 0) {
      return false;
    }
    return currentNote.getId() <= noteRepository.maxNoteId();
  }

  private boolean ensureCurrentNotePersistedForBibliography() {
    if (currentNote == null) {
      return false;
    }
    if (currentNoteExistsInDatabase()) {
      return true;
    }

    saveCurrentIfValid();
    return currentNoteExistsInDatabase();
  }

  private void showCreateBibliographyButton() {
    var host = view.getNoteEditorPane().getBibliographyHost();
    host.getChildren().clear();

    var btnCreate = new Button("Bibliographische Angabe erstellen");
    btnCreate.getStyleClass().add("biblio-create-button");
    btnCreate.setOnAction(e -> showBibliographyEditor());

    var row = new HBox(btnCreate);
    row.setAlignment(javafx.geometry.Pos.CENTER);
    row.setMaxWidth(Double.MAX_VALUE);
    host.getChildren().add(row);
  }

  private void showUnresolvedBibliographyButton() {
    var host = view.getNoteEditorPane().getBibliographyHost();
    host.getChildren().clear();

    var btnOpen = new Button("Bibliographische Angabe öffnen");
    btnOpen.getStyleClass().add("biblio-create-button");
    btnOpen.setOnAction(e -> showBibliographyEditor());

    var row = new HBox(btnOpen);
    row.setAlignment(javafx.geometry.Pos.CENTER);
    row.setMaxWidth(Double.MAX_VALUE);
    host.getChildren().add(row);
  }

  private BibliographyEntry resolveCurrentBibliographyEntry() {
    if (currentNote == null) {
      return null;
    }

    if (currentNote.getBibliographyType() == BibliographyType.USER
            || currentNote.getBibliographyRefId() == null) {
      return null;
    }

    BibliographyEntry entry = currentNote.getBibliographyEntry();
    if (entry == null) {
      entry = bibliographyRepository.load(
          currentNote.getBibliographyRefId(),
          currentNote.getBibliographyType()
      );
      currentNote.setBibliographyEntry(entry);
    }

    return entry;
  }

  private void renderBibliographyHost() {
    boolean visible = view.getBottomToolbar().bookToggle().isSelected();
    view.getNoteEditorPane().setBibliographyVisible(visible);

    var host = view.getNoteEditorPane().getBibliographyHost();
    host.getChildren().clear();

    if (!visible || currentNote == null) {
      return;
    }

    if (currentNote.getBibliographyType() == BibliographyType.USER
            || currentNote.getBibliographyRefId() == null) {
      showCreateBibliographyButton();
      return;
    }

    BibliographyEntry entry = resolveCurrentBibliographyEntry();

    if (entry == null) {
      showUnresolvedBibliographyButton();
      return;
    }

    var shell = createBibliographyShell();

    switch (currentNote.getBibliographyType()) {
      case BOOK -> {
        if (entry instanceof BookEntry be) {
          shell.showBook(be, false);
          showBibliographyShell(shell);
          return;
        }
      }
      case COLLECTION -> {
        if (entry instanceof CollectionEntry ce) {
          shell.showCollection(ce, false);
          showBibliographyShell(shell);
          return;
        }
      }
      case ARTICLE_IN_COLLECTION -> {
        if (entry instanceof ArticleInCollectionEntry ae) {
          shell.showArticleInCollection(ae, false);
          showBibliographyShell(shell);
          return;
        }
      }
      case JOURNAL -> {
        if (entry instanceof JournalEntry je) {
          shell.showJournal(je, false);
          showBibliographyShell(shell);
          return;
        }
      }
      case JOURNAL_ARTICLE -> {
        if (entry instanceof JournalArticleEntry jae) {
          shell.showJournalArticle(jae, false);
          showBibliographyShell(shell);
          return;
        }
      }
      case THESIS -> {
        if (entry instanceof ThesisEntry te) {
          shell.showThesis(te, false);
          showBibliographyShell(shell);
          return;
        }
      }
      case INTERNET -> {
        if (entry instanceof InternetEntry ie) {
          shell.showInternet(ie, false);
          showBibliographyShell(shell);
          return;
        }
      }
      case EDITION -> {
        if (entry instanceof EditionEntry ee) {
          shell.showEdition(ee, false);
          showBibliographyShell(shell);
          return;
        }
      }
      case AI -> {
        if (entry instanceof AiEntry ae) {
          shell.showAi(ae, false);
          showBibliographyShell(shell);
          return;
        }
      }
    }

    showUnresolvedBibliographyButton();
  }

  private void showBibliographyEditor() {
    var shell = createBibliographyShell();

    if (currentNote != null) {
      BibliographyEntry entry = resolveCurrentBibliographyEntry();

      switch (currentNote.getBibliographyType()) {
        case BOOK -> {
          if (entry instanceof BookEntry be) {
            shell.showBook(be, true);
            showBibliographyShell(shell);
            return;
          }
        }
        case COLLECTION -> {
          if (entry instanceof CollectionEntry ce) {
            shell.showCollection(ce, true);
            showBibliographyShell(shell);
            return;
          }
        }
        case ARTICLE_IN_COLLECTION -> {
          if (entry instanceof ArticleInCollectionEntry ae) {
            shell.showArticleInCollection(ae, true);
            showBibliographyShell(shell);
            return;
          }
        }
        case JOURNAL -> {
          if (entry instanceof JournalEntry je) {
            shell.showJournal(je, true);
            showBibliographyShell(shell);
            return;
          }
        }
        case JOURNAL_ARTICLE -> {
          if (entry instanceof JournalArticleEntry jae) {
            shell.showJournalArticle(jae, true);
            showBibliographyShell(shell);
            return;
          }
        }
        case THESIS -> {
          if (entry instanceof ThesisEntry te) {
            shell.showThesis(te, false);
            showBibliographyShell(shell);
            return;
          }
        }
        case INTERNET -> {
          if (entry instanceof InternetEntry ie) {
            shell.showInternet(ie, false);
            showBibliographyShell(shell);
            return;
          }
        }
        case EDITION -> {
          if (entry instanceof EditionEntry ee) {
            shell.showEdition(ee, false);
            showBibliographyShell(shell);
            return;
          }
        }
        case AI -> {
          if (entry instanceof AiEntry ae) {
            shell.showAi(ae, false);
            showBibliographyShell(shell);
            return;
          }
        }
      }
    }

    shell.showNewBookEditor();
    showBibliographyShell(shell);
  }

  private void applyLinkedBibliography(BibliographyType type, BibliographyEntry entry, int entryId) {
    noteRepository.linkBibliography(currentNote.getId(), type, entryId);

    currentNote.setBibliographyType(type);
    currentNote.setBibliographyRefId(entryId);
    currentNote.setBibliographyEntry(entry);

    renderBibliographyHost();
  }

  private boolean hasTitle(String text) {
    return text != null && !text.trim().isBlank();
  }

  private boolean hasAuthors(List<Author> authors) {
    return authors != null && !authors.isEmpty();
  }

  private boolean hasCollectionTitle(String text) {
    return text != null && !text.trim().isBlank();
  }

  private void openCollectionEditor(BibliographyEditorShell articleShell) {
    var collectionShell = createBibliographyShell();
    collectionShell.setTypeChoices(TypeChoice.COLLECTION);
    collectionShell.setOnDelete(() -> showBibliographyShell(articleShell));
    collectionShell.setOnNoneSelected(() -> showBibliographyShell(articleShell));
    collectionShell.setOnAdd(() -> saveCollectionForArticle(collectionShell, articleShell));

    Integer collectionId = articleShell.getCollectionEntryId();

    if (collectionId != null && collectionId > 0) {
      BibliographyEntry loaded = bibliographyRepository.load(collectionId, BibliographyType.COLLECTION);
      if (loaded instanceof CollectionEntry ce) {
        collectionShell.showCollection(ce, true);
        showBibliographyShell(collectionShell);
        return;
      }
    }

    CollectionEntry fresh = new CollectionEntry();
    fresh.setTitle(articleShell.getCollectionTitle());
    collectionShell.showCollection(fresh, true);
    showBibliographyShell(collectionShell);
  }

  private void saveCollectionForArticle(BibliographyEditorShell collectionShell,
                                        BibliographyEditorShell articleShell) {
    CollectionEntry entry;

    Integer existingId = articleShell.getCollectionEntryId();
    if (existingId != null && existingId > 0) {
      BibliographyEntry loaded = bibliographyRepository.load(existingId, BibliographyType.COLLECTION);
      if (loaded instanceof CollectionEntry ce) {
        entry = ce;
        entry.setId(existingId);
      } else {
        entry = new CollectionEntry();
      }
    } else {
      entry = new CollectionEntry();
    }

    collectionShell.writeIntoCollectionEntry(entry);

    if (!validateCollectionForSave(entry)) {
      return;
    }

    int entryId = bibliographyRepository.save(entry, BibliographyType.COLLECTION);
    if (entryId <= 0) {
      showRepositorySaveFailed(BibliographyType.COLLECTION);
      return;
    }

    entry.setId(entryId);
    articleShell.setCollectionEntryId(entryId);
    articleShell.setCollectionTitle(entry.getTitle());

    showBibliographyShell(articleShell);
  }

  /**
   * Öffnet den Zeitschrifteneditor aus dem JournalArticle-Formular heraus.
   *
   * @param articleShell Ursprungsshell des Zeitschriftenartikels.
   */
  private void openJournalEditor(BibliographyEditorShell articleShell) {
    var journalShell = createBibliographyShell();
    journalShell.setTypeChoices(TypeChoice.JOURNAL);
    journalShell.setOnDelete(() -> showBibliographyShell(articleShell));
    journalShell.setOnNoneSelected(() -> showBibliographyShell(articleShell));
    journalShell.setOnAdd(() -> saveJournalForArticle(journalShell, articleShell));

    Integer journalId = articleShell.getJournalEntryId();

    if (journalId != null && journalId > 0) {
      BibliographyEntry loaded = bibliographyRepository.load(journalId, BibliographyType.JOURNAL);
      if (loaded instanceof JournalEntry je) {
        journalShell.showJournal(je, true);
        showBibliographyShell(journalShell);
        return;
      }
    }

    JournalEntry fresh = new JournalEntry();
    fresh.setTitle(articleShell.getJournalTitle());
    journalShell.showJournal(fresh, true);
    showBibliographyShell(journalShell);
  }

  /**
   * Speichert eine Zeitschrift aus dem JournalArticle-Kontext heraus
   * und übernimmt die Verknüpfung zurück in den Artikel.
   *
   * @param journalShell Editor-Shell der Zeitschrift.
   * @param articleShell Ursprungsshell des Zeitschriftenartikels.
   */
  private void saveJournalForArticle(BibliographyEditorShell journalShell,
                                     BibliographyEditorShell articleShell) {
    JournalEntry entry;

    Integer existingId = articleShell.getJournalEntryId();
    if (existingId != null && existingId > 0) {
      BibliographyEntry loaded = bibliographyRepository.load(existingId, BibliographyType.JOURNAL);
      if (loaded instanceof JournalEntry je) {
        entry = je;
        entry.setId(existingId);
      } else {
        entry = new JournalEntry();
      }
    } else {
      entry = new JournalEntry();
    }

    journalShell.writeIntoJournalEntry(entry);

    if (!validateJournalForSave(entry)) {
      return;
    }

    int entryId = bibliographyRepository.save(entry, BibliographyType.JOURNAL);
    if (entryId <= 0) {
      showRepositorySaveFailed(BibliographyType.JOURNAL);
      return;
    }

    entry.setId(entryId);
    articleShell.setJournalEntryId(entryId);
    articleShell.setJournalTitle(entry.getTitle());

    showBibliographyShell(articleShell);
  }

  private void unlinkCurrentBibliography() {
    if (currentNote == null) {
      return;
    }

    if (currentNoteExistsInDatabase()) {
      noteRepository.unlinkBibliography(currentNote.getId());
    }

    currentNote.setBibliographyType(BibliographyType.USER);
    currentNote.setBibliographyRefId(null);
    currentNote.setBibliographyEntry(null);

    renderBibliographyHost();
  }

  private void saveAndLinkBibliography(BibliographyEditorShell shell) {
    if (currentNote == null) {
      return;
    }

    if (shell.getTypeChoice() == TypeChoice.NONE) {
      unlinkCurrentBibliography();
      return;
    }

    if (!ensureCurrentNotePersistedForBibliography()) {
      return;
    }

    switch (shell.getTypeChoice()) {
      case BOOK -> saveAndLinkBook(shell);
      case COLLECTION -> saveAndLinkCollection(shell);
      case ARTICLE_IN_COLLECTION -> saveAndLinkArticleInCollection(shell);
      case JOURNAL -> saveAndLinkJournal(shell);
      case JOURNAL_ARTICLE -> saveAndLinkJournalArticle(shell);
      case THESIS -> saveAndLinkThesis(shell);
      case INTERNET -> saveAndLinkInternet(shell);
      case EDITION -> saveAndLinkEdition(shell);
      case AI -> saveAndLinkAi(shell);
      default -> {}
    }
  }

  private void saveAndLinkBook(BibliographyEditorShell shell) {
    BookEntry entry;

    if (currentNote.getBibliographyType() == BibliographyType.BOOK
            && currentNote.getBibliographyEntry() instanceof BookEntry be
            && currentNote.getBibliographyRefId() != null) {
      entry = be;
      entry.setId(currentNote.getBibliographyRefId());
    } else {
      entry = new BookEntry();
    }

    shell.writeIntoBookEntry(entry);
    if (!handleExistingEntryDecision(shell, BibliographyType.BOOK, entry)) {
      return;
    }

    if (!validateBookForSave(entry)) {
      return;
    }

    int entryId = bibliographyRepository.save(entry, BibliographyType.BOOK);
    if (entryId <= 0) {
      showRepositorySaveFailed(BibliographyType.BOOK);
      return;
    }

    entry.setId(entryId);
    applyLinkedBibliography(BibliographyType.BOOK, entry, entryId);
  }

  private void saveAndLinkCollection(BibliographyEditorShell shell) {
    CollectionEntry entry;

    if (currentNote.getBibliographyType() == BibliographyType.COLLECTION
            && currentNote.getBibliographyEntry() instanceof CollectionEntry ce
            && currentNote.getBibliographyRefId() != null) {
      entry = ce;
      entry.setId(currentNote.getBibliographyRefId());
    } else {
      entry = new CollectionEntry();
    }

    shell.writeIntoCollectionEntry(entry);
    if (!handleExistingEntryDecision(shell, BibliographyType.COLLECTION, entry)) {
      return;
    }

    if (!validateCollectionForSave(entry)) {
      return;
    }

    int entryId = bibliographyRepository.save(entry, BibliographyType.COLLECTION);
    if (entryId <= 0) {
      showRepositorySaveFailed(BibliographyType.COLLECTION);
      return;
    }

    entry.setId(entryId);
    applyLinkedBibliography(BibliographyType.COLLECTION, entry, entryId);
  }

  private void saveAndLinkArticleInCollection(BibliographyEditorShell shell) {
    ArticleInCollectionEntry entry;

    if (currentNote.getBibliographyType() == BibliographyType.ARTICLE_IN_COLLECTION
            && currentNote.getBibliographyEntry() instanceof ArticleInCollectionEntry ae
            && currentNote.getBibliographyRefId() != null) {
      entry = ae;
      entry.setId(currentNote.getBibliographyRefId());
    } else {
      entry = new ArticleInCollectionEntry();
    }

    shell.writeIntoArticleInCollectionEntry(entry);
    if (!handleExistingEntryDecision(shell, BibliographyType.ARTICLE_IN_COLLECTION, entry)) {
      return;
    }

    if (!validateArticleForSave(entry)) {
      return;
    }

    int entryId = bibliographyRepository.save(entry, BibliographyType.ARTICLE_IN_COLLECTION);
    if (entryId <= 0) {
      showRepositorySaveFailed(BibliographyType.ARTICLE_IN_COLLECTION);
      return;
    }

    entry.setId(entryId);
    applyLinkedBibliography(BibliographyType.ARTICLE_IN_COLLECTION, entry, entryId);
  }

  /**
   * Speichert eine Zeitschrift und verknüpft sie mit der aktuellen Notiz.
   *
   * @param shell Editor-Shell der Zeitschrift.
   */
  private void saveAndLinkJournal(BibliographyEditorShell shell) {
    JournalEntry entry;

    if (currentNote.getBibliographyType() == BibliographyType.JOURNAL
            && currentNote.getBibliographyEntry() instanceof JournalEntry je
            && currentNote.getBibliographyRefId() != null) {
      entry = je;
      entry.setId(currentNote.getBibliographyRefId());
    } else {
      entry = new JournalEntry();
    }

    shell.writeIntoJournalEntry(entry);
    if (!handleExistingEntryDecision(shell, BibliographyType.JOURNAL, entry)) {
      return;
    }

    if (!validateJournalForSave(entry)) {
      return;
    }

    int entryId = bibliographyRepository.save(entry, BibliographyType.JOURNAL);
    if (entryId <= 0) {
      showRepositorySaveFailed(BibliographyType.JOURNAL);
      return;
    }

    entry.setId(entryId);
    applyLinkedBibliography(BibliographyType.JOURNAL, entry, entryId);
  }

  /**
   * Speichert einen Zeitschriftenartikel und verknüpft ihn mit der aktuellen Notiz.
   *
   * @param shell Editor-Shell des Zeitschriftenartikels.
   */
  private void saveAndLinkJournalArticle(BibliographyEditorShell shell) {
    JournalArticleEntry entry;

    if (currentNote.getBibliographyType() == BibliographyType.JOURNAL_ARTICLE
            && currentNote.getBibliographyEntry() instanceof JournalArticleEntry jae
            && currentNote.getBibliographyRefId() != null) {
      entry = jae;
      entry.setId(currentNote.getBibliographyRefId());
    } else {
      entry = new JournalArticleEntry();
    }

    shell.writeIntoJournalArticleEntry(entry);
    if (!handleExistingEntryDecision(shell, BibliographyType.JOURNAL_ARTICLE, entry)) {
      return;
    }

    if (!validateJournalArticleForSave(entry)) {
      return;
    }

    int entryId = bibliographyRepository.save(entry, BibliographyType.JOURNAL_ARTICLE);
    if (entryId <= 0) {
      showRepositorySaveFailed(BibliographyType.JOURNAL_ARTICLE);
      return;
    }

    entry.setId(entryId);
    applyLinkedBibliography(BibliographyType.JOURNAL_ARTICLE, entry, entryId);
  }

  /**
   * Speichert eine Thesis und verknüpft sie mit der aktuellen Notiz.
   *
   * @param shell Editor-Shell der Thesis
   */
  private void saveAndLinkThesis(BibliographyEditorShell shell) {
    ThesisEntry entry;

    if (currentNote.getBibliographyType() == BibliographyType.THESIS
            && currentNote.getBibliographyEntry() instanceof ThesisEntry te
            && currentNote.getBibliographyRefId() != null) {
      entry = te;
      entry.setId(currentNote.getBibliographyRefId());
    } else {
      entry = new ThesisEntry();
    }

    shell.writeIntoThesisEntry(entry);

    if (!validateThesisForSave(entry)) {
      return;
    }
    if (!handleExistingEntryDecision(shell, BibliographyType.THESIS, entry)) {
      return;
    }

    int entryId = bibliographyRepository.save(entry, BibliographyType.THESIS);
    if (entryId <= 0) {
      showRepositorySaveFailed(BibliographyType.THESIS);
      return;
    }

    entry.setId(entryId);
    applyLinkedBibliography(BibliographyType.THESIS, entry, entryId);
  }

  /**
   * Speichert eine Internetquelle und verknüpft sie mit der aktuellen Notiz.
   *
   * @param shell Editor-Shell der Internetquelle
   */
  private void saveAndLinkInternet(BibliographyEditorShell shell) {
    InternetEntry entry;

    if (currentNote.getBibliographyType() == BibliographyType.INTERNET
            && currentNote.getBibliographyEntry() instanceof InternetEntry ie
            && currentNote.getBibliographyRefId() != null) {
      entry = ie;
      entry.setId(currentNote.getBibliographyRefId());
    } else {
      entry = new InternetEntry();
    }

    shell.writeIntoInternetEntry(entry);

    if (!validateInternetForSave(entry)) {
      return;
    }
    if (!handleExistingEntryDecision(shell, BibliographyType.INTERNET, entry)) {
      return;
    }

    int entryId = bibliographyRepository.save(entry, BibliographyType.INTERNET);
    if (entryId <= 0) {
      showRepositorySaveFailed(BibliographyType.INTERNET);
      return;
    }

    entry.setId(entryId);
    applyLinkedBibliography(BibliographyType.INTERNET, entry, entryId);
  }

  /**
   * Speichert eine Edition und verknüpft sie mit der aktuellen Notiz.
   *
   * @param shell Editor-Shell der Edition
   */
  private void saveAndLinkEdition(BibliographyEditorShell shell) {
    EditionEntry entry;

    if (currentNote.getBibliographyType() == BibliographyType.EDITION
            && currentNote.getBibliographyEntry() instanceof EditionEntry ee
            && currentNote.getBibliographyRefId() != null) {
      entry = ee;
      entry.setId(currentNote.getBibliographyRefId());
    } else {
      entry = new EditionEntry();
    }

    shell.writeIntoEditionEntry(entry);

    if (!validateEditionForSave(entry)) {
      return;
    }
    if (!handleExistingEntryDecision(shell, BibliographyType.EDITION, entry)) {
      return;
    }

    int entryId = bibliographyRepository.save(entry, BibliographyType.EDITION);
    if (entryId <= 0) {
      showRepositorySaveFailed(BibliographyType.EDITION);
      return;
    }

    entry.setId(entryId);
    applyLinkedBibliography(BibliographyType.EDITION, entry, entryId);
  }

  /**
   * Speichert eine KI-Quelle und verknüpft sie mit der aktuellen Notiz.
   *
   * @param shell Editor-Shell der KI-Quelle
   */
  private void saveAndLinkAi(BibliographyEditorShell shell) {
    AiEntry entry;

    if (currentNote.getBibliographyType() == BibliographyType.AI
            && currentNote.getBibliographyEntry() instanceof AiEntry ae
            && currentNote.getBibliographyRefId() != null) {
      entry = ae;
      entry.setId(currentNote.getBibliographyRefId());
    } else {
      entry = new AiEntry();
    }

    shell.writeIntoAiEntry(entry);

    if (!validateAiForSave(entry)) {
      return;
    }
    if (!handleExistingEntryDecision(shell, BibliographyType.AI, entry)) {
      return;
    }

    int entryId = bibliographyRepository.save(entry, BibliographyType.AI);
    if (entryId <= 0) {
      showRepositorySaveFailed(BibliographyType.AI);
      return;
    }

    entry.setId(entryId);
    bibliographyRepository.rememberAiProviderModel(entry.getProvider(), entry.getModel());
    applyLinkedBibliography(BibliographyType.AI, entry, entryId);
  }

  private BibliographyType toBibliographyType(TypeChoice typeChoice) {
    return switch (typeChoice) {
      case BOOK -> BibliographyType.BOOK;
      case COLLECTION -> BibliographyType.COLLECTION;
      case ARTICLE_IN_COLLECTION -> BibliographyType.ARTICLE_IN_COLLECTION;
      case JOURNAL -> BibliographyType.JOURNAL;
      case JOURNAL_ARTICLE -> BibliographyType.JOURNAL_ARTICLE;
      case THESIS -> BibliographyType.THESIS;
      case INTERNET -> BibliographyType.INTERNET;
      case EDITION -> BibliographyType.EDITION;
      case AI -> BibliographyType.AI;
      default -> BibliographyType.USER;
    };
  }

  private enum ExistingEntrySaveDecision {
    UPDATE_EXISTING,
    CREATE_NEW,
    CANCEL
  }

  private ExistingEntrySaveDecision askExistingEntrySaveDecision(String typeLabel) {
    ButtonType update = new ButtonType("Vorhandenen Eintrag aktualisieren", ButtonBar.ButtonData.YES);
    ButtonType createNew = new ButtonType("Neuen Eintrag anlegen", ButtonBar.ButtonData.OTHER);
    ButtonType cancel = new ButtonType("Abbrechen", ButtonBar.ButtonData.CANCEL_CLOSE);

    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
    alert.initOwner(stage);
    alert.setTitle("Bibliographische Angabe");
    alert.setHeaderText(typeLabel + " wurde aus einem vorhandenen Eintrag übernommen");
    alert.setContentText(
        "Der Eintrag wurde verändert.\n\n" +
            "Soll der bestehende Datensatz aktualisiert werden oder soll ein neuer Datensatz angelegt werden?"
    );
    alert.getButtonTypes().setAll(update, createNew, cancel);

    ButtonType result = alert.showAndWait().orElse(cancel);
    if (result == update) return ExistingEntrySaveDecision.UPDATE_EXISTING;
    if (result == createNew) return ExistingEntrySaveDecision.CREATE_NEW;
    return ExistingEntrySaveDecision.CANCEL;
  }

  private boolean handleExistingEntryDecision(BibliographyEditorShell shell,
                                              BibliographyType type,
                                              BibliographyEntry entry) {
    Integer sourceId = shell.getSourceEntryId(shell.getTypeChoice());
    if (sourceId == null || sourceId <= 0) {
      return true;
    }

    BibliographyEntry loaded = bibliographyRepository.load(sourceId, type);
    if (loaded == null) {
      return true;
    }

    boolean changed = switch (type) {
      case BOOK -> !sameBook((BookEntry) loaded, (BookEntry) entry);
      case COLLECTION -> !sameCollection((CollectionEntry) loaded, (CollectionEntry) entry);
      case ARTICLE_IN_COLLECTION -> !sameArticleInCollection((ArticleInCollectionEntry) loaded, (ArticleInCollectionEntry) entry);
      case JOURNAL -> !sameJournal((JournalEntry) loaded, (JournalEntry) entry);
      case JOURNAL_ARTICLE -> !sameJournalArticle((JournalArticleEntry) loaded, (JournalArticleEntry) entry);
      case THESIS -> !sameThesis((ThesisEntry) loaded, (ThesisEntry) entry);
      case INTERNET -> !sameInternet((InternetEntry) loaded, (InternetEntry) entry);
      case EDITION -> !sameEdition((EditionEntry) loaded, (EditionEntry) entry);
      case AI -> !sameAi((AiEntry) loaded, (AiEntry) entry);
      default -> false;
    };

    if (!changed) {
      entry.setId(sourceId);
      return true;
    }

    ExistingEntrySaveDecision decision = askExistingEntrySaveDecision(type.name());

    if (decision == ExistingEntrySaveDecision.CANCEL) {
      return false;
    }

    if (decision == ExistingEntrySaveDecision.UPDATE_EXISTING) {
      entry.setId(sourceId);
    } else {
      entry.setId(null);
    }

    return true;
  }

  private boolean sameAuthors(List<Author> a, List<Author> b) {
    if (a == null) a = List.of();
    if (b == null) b = List.of();
    if (a.size() != b.size()) return false;

    for (int i = 0; i < a.size(); i++) {
      String aLn = a.get(i).getLastName() == null ? "" : a.get(i).getLastName().trim();
      String aFn = a.get(i).getFirstName() == null ? "" : a.get(i).getFirstName().trim();
      String bLn = b.get(i).getLastName() == null ? "" : b.get(i).getLastName().trim();
      String bFn = b.get(i).getFirstName() == null ? "" : b.get(i).getFirstName().trim();

      if (!aLn.equalsIgnoreCase(bLn) || !aFn.equalsIgnoreCase(bFn)) {
        return false;
      }
    }
    return true;
  }

  private boolean sameBook(BookEntry a, BookEntry b) {
    return Objects.equals(norm(a.getTitle()), norm(b.getTitle()))
               && Objects.equals(norm(a.getSubtitle()), norm(b.getSubtitle()))
               && Objects.equals(norm(a.getPublisher()), norm(b.getPublisher()))
               && Objects.equals(norm(a.getPlace()), norm(b.getPlace()))
               && Objects.equals(norm(a.getYear()), norm(b.getYear()))
               && Objects.equals(norm(a.getIsbn()), norm(b.getIsbn()))
               && Objects.equals(norm(a.getRun()), norm(b.getRun()))
               && Objects.equals(norm(a.getSeries()), norm(b.getSeries()))
               && Objects.equals(norm(a.getSeriesNumber()), norm(b.getSeriesNumber()))
               && sameAuthors(a.getAuthors(), b.getAuthors());
  }

  private boolean sameCollection(CollectionEntry a, CollectionEntry b) {
    return Objects.equals(norm(a.getTitle()), norm(b.getTitle()))
               && Objects.equals(norm(a.getSubtitle()), norm(b.getSubtitle()))
               && Objects.equals(norm(a.getPublisher()), norm(b.getPublisher()))
               && Objects.equals(norm(a.getPlace()), norm(b.getPlace()))
               && Objects.equals(norm(a.getYear()), norm(b.getYear()))
               && Objects.equals(norm(a.getSeries()), norm(b.getSeries()))
               && sameAuthors(a.getAuthors(), b.getAuthors());
  }

  private boolean sameArticleInCollection(ArticleInCollectionEntry a, ArticleInCollectionEntry b) {
    return Objects.equals(norm(a.getTitle()), norm(b.getTitle()))
               && Objects.equals(norm(a.getPages()), norm(b.getPages()))
               && Objects.equals(a.getCollectionEntryId(), b.getCollectionEntryId())
               && Objects.equals(norm(a.getCollectionTitle()), norm(b.getCollectionTitle()))
               && sameAuthors(a.getAuthors(), b.getAuthors());
  }

  /**
   * Prüft, ob zwei Zeitschriften bibliographisch als gleich behandelt werden sollen.
   *
   * @param a erste Zeitschrift
   * @param b zweite Zeitschrift
   * @return {@code true}, wenn beide inhaltlich gleich sind
   */
  private boolean sameJournal(JournalEntry a, JournalEntry b) {
    if (a == b) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }

    return norm(a.getTitle()).equals(norm(b.getTitle()))
               && norm(a.getSubtitle()).equals(norm(b.getSubtitle()))
               && norm(a.getIssn()).equals(norm(b.getIssn()))
               && norm(a.getPublisher()).equals(norm(b.getPublisher()))
               && norm(a.getPlace()).equals(norm(b.getPlace()))
               && norm(a.getStartYear()).equals(norm(b.getStartYear()))
               && norm(a.getEndYear()).equals(norm(b.getEndYear()))
               && norm(a.getNote()).equals(norm(b.getNote()));
  }

  /**
   * Prüft, ob zwei Zeitschriftenartikel bibliographisch als gleich behandelt werden sollen.
   *
   * @param a erster Zeitschriftenartikel
   * @param b zweiter Zeitschriftenartikel
   * @return {@code true}, wenn beide inhaltlich gleich sind
   */
  private boolean sameJournalArticle(JournalArticleEntry a, JournalArticleEntry b) {
    if (a == b) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }

    return norm(a.getTitle()).equals(norm(b.getTitle()))
               && norm(a.getSubtitle()).equals(norm(b.getSubtitle()))
               && norm(a.getPublisher()).equals(norm(b.getPublisher()))
               && norm(a.getJournalTitle()).equals(norm(b.getJournalTitle()))
               && Objects.equals(a.getJournalEntryId(), b.getJournalEntryId())
               && norm(a.getVolume()).equals(norm(b.getVolume()))
               && norm(a.getIssue()).equals(norm(b.getIssue()))
               && norm(a.getYear()).equals(norm(b.getYear()))
               && norm(a.getPages()).equals(norm(b.getPages()))
               && norm(a.getDoi()).equals(norm(b.getDoi()))
               && sameAuthors(a.getAuthors(), b.getAuthors());
  }

  /**
   * Prüft, ob zwei Thesis-Einträge bibliographisch als gleich behandelt werden sollen.
   *
   * @param a erste Thesis
   * @param b zweite Thesis
   * @return {@code true}, wenn beide inhaltlich gleich sind
   */
  private boolean sameThesis(ThesisEntry a, ThesisEntry b) {
    if (a == b) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }

    return norm(a.getTitle()).equals(norm(b.getTitle()))
               && norm(a.getSubtitle()).equals(norm(b.getSubtitle()))
               && Objects.equals(a.getThesisType(), b.getThesisType())
               && norm(a.getUniversity()).equals(norm(b.getUniversity()))
               && norm(a.getPlace()).equals(norm(b.getPlace()))
               && norm(a.getYear()).equals(norm(b.getYear()))
               && norm(a.getAdvisor()).equals(norm(b.getAdvisor()))
               && norm(a.getSeries()).equals(norm(b.getSeries()))
               && norm(a.getNote()).equals(norm(b.getNote()))
               && sameAuthors(a.getAuthors(), b.getAuthors());
  }

  /**
   * Prüft, ob zwei Internetquellen bibliographisch als gleich behandelt werden sollen.
   *
   * @param a erste Internetquelle
   * @param b zweite Internetquelle
   * @return {@code true}, wenn beide inhaltlich gleich sind
   */
  private boolean sameInternet(InternetEntry a, InternetEntry b) {
    if (a == b) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }

    return norm(a.getTitle()).equals(norm(b.getTitle()))
               && norm(a.getHostName()).equals(norm(b.getHostName()))
               && norm(a.getUrl()).equals(norm(b.getUrl()))
               && norm(a.getPublished()).equals(norm(b.getPublished()))
               && Objects.equals(a.getAccessedAt(), b.getAccessedAt())
               && sameAuthors(a.getAuthors(), b.getAuthors());
  }

  /**
   * Prüft, ob zwei Editionen bibliographisch als gleich behandelt werden sollen.
   *
   * @param a erste Edition
   * @param b zweite Edition
   * @return {@code true}, wenn beide inhaltlich gleich sind
   */
  private boolean sameEdition(EditionEntry a, EditionEntry b) {
    if (a == b) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }

    return norm(a.getTitle()).equals(norm(b.getTitle()))
               && norm(a.getPublisher()).equals(norm(b.getPublisher()))
               && norm(a.getPlace()).equals(norm(b.getPlace()))
               && norm(a.getYear()).equals(norm(b.getYear()))
               && norm(a.getSeries()).equals(norm(b.getSeries()))
               && norm(a.getVolume()).equals(norm(b.getVolume()))
               && sameAuthors(a.getAuthors(), b.getAuthors());
  }

  /**
   * Prüft, ob zwei KI-Quellen bibliographisch als gleich behandelt werden sollen.
   *
   * @param a erste KI-Quelle
   * @param b zweite KI-Quelle
   * @return {@code true}, wenn beide inhaltlich gleich sind
   */
  private boolean sameAi(AiEntry a, AiEntry b) {
    if (a == b) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }

    return norm(a.getTitle()).equals(norm(b.getTitle()))
               && norm(a.getProvider()).equals(norm(b.getProvider()))
               && norm(a.getModel()).equals(norm(b.getModel()))
               && norm(a.getContext()).equals(norm(b.getContext()))
               && norm(a.getPromptTitle()).equals(norm(b.getPromptTitle()))
               && Objects.equals(a.getUsedAt(), b.getUsedAt());
  }

  private String norm(String s) {
    return s == null ? "" : s.trim();
  }

  /**
   * Klappt den rechten Arbeitsbereich ein oder wieder aus.
   * Beim erneuten Öffnen wird der aktuell gewählte Zettel im nächsten Schritt
   * wieder für den aktiven Modus synchronisiert.
   */
  private void toggleServiceArea() {
    boolean wasCollapsed = view.isServiceAreaCollapsed();
    view.toggleServiceAreaCollapsed();

    if (wasCollapsed && view.getActiveServiceAreaMode() != null) {
      refreshServiceAreaForCurrentState();
    }
  }

  /**
   * Reagiert auf das Umschalten eines Modus in der rechten Seitenleiste.
   * Es kann immer nur ein Modus gleichzeitig aktiv sein.
   * Wird der bereits aktive Modus erneut geklickt, wird er wieder deaktiviert.
   *
   * @param mode angeklickter Modus
   */
  private void handleServiceAreaModeToggle(ZettelWindowView.ServiceAreaMode mode) {
    if (view.getActiveServiceAreaMode() == mode) {
      view.setActiveServiceAreaMode(null);
      view.setServiceAreaContent(new Pane());
      return;
    }

    view.setActiveServiceAreaMode(mode);
    refreshServiceAreaForCurrentState();
  }

  /**
   * Aktualisiert den sichtbaren Inhalt des rechten Arbeitsbereichs
   * passend zum aktuell gewählten Modus.
   * Für den aktuellen Ausbauschritt werden noch Platzhalteransichten verwendet.
   */
  private void refreshServiceAreaForCurrentState() {
    ZettelWindowView.ServiceAreaMode mode = view.getActiveServiceAreaMode();
    if (mode == null) {
      view.setServiceAreaContent(new Pane());
      return;
    }

    view.setServiceAreaContent(view.createModePlaceholder(mode));
  }


}


