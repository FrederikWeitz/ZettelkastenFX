package de.zettelkastenfx.notes.editor;

import de.zettelkastenfx.base.BaseIcon;
import de.zettelkastenfx.base.util.DateUtil;
import de.zettelkastenfx.notes.editor.format.NoteFormattingMenuContent;
import de.zettelkastenfx.notes.editor.media.EditorImageAssetService;
import de.zettelkastenfx.notes.editor.web.TinyMceEditorPane;
import de.zettelkastenfx.notes.model.NoteReferenceInfo;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Point2D;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.Tooltip;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.FileChooser;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

public class NoteEditorPane extends VBox {

  private final StackPane titleStack;
  private final TextArea titleEditorArea;
  private final TinyMceEditorPane webBodyEditor;
  private final ContextMenu formattingContextMenu;
  private final HBox formattingBarContainer;
  private final Region formattingDragHandle;
  private final StackPane bodyContainer;
  private final NoteFormattingMenuContent formattingMenuContent;
  private final BorderPane headerBar;
  private final Label lblHeaderNoteNo;
  private final Label lblHeaderCreatedAt;
  private final Button btnHeaderPrev;
  private final Button btnHeaderNext;
  private final Button btnHeaderInfo;
  private final Popup infoPopup = new Popup();
  private final VBox infoPopupRoot = new VBox(8);
  private final VBox bibliographyHost;
  private Popup imageDropPopup;

  private boolean bodySourceMode;
  private boolean suppressNextCtrlMParagraphInsert;
  private IntConsumer onNavigateToNote = _ -> {};
  private List<NoteReferenceInfo> infoOutgoing = List.of();
  private List<NoteReferenceInfo> infoIncoming = List.of();
  private List<String> infoProjects = List.of();

  public NoteEditorPane() {
    getStyleClass().add("note-editor-pane");
    setSpacing(0);

    headerBar = new BorderPane();
    headerBar.getStyleClass().add("note-header-bar");

    var left = new HBox(6);
    left.getStyleClass().add("note-header-left");
    var lblLeftTitle = new Label("Zettel");
    lblLeftTitle.getStyleClass().add("note-header-title");
    lblHeaderNoteNo = new Label("—");
    lblHeaderNoteNo.getStyleClass().add("note-header-value");
    left.getChildren().addAll(lblLeftTitle, lblHeaderNoteNo);

    var center = new HBox(6);
    center.getStyleClass().add("note-header-center");
    var lblCenterTitle = new Label("Erstellt");
    lblCenterTitle.getStyleClass().add("note-header-title");
    lblHeaderCreatedAt = new Label("—");
    lblHeaderCreatedAt.getStyleClass().add("note-header-value");
    center.getChildren().addAll(lblCenterTitle, lblHeaderCreatedAt);

    var right = new HBox();
    right.getStyleClass().add("note-header-right");
    btnHeaderInfo = BaseIcon.INFORMATION.button();
    btnHeaderInfo.getStyleClass().addAll("icon-button", "note-info-button");
    btnHeaderInfo.setFocusTraversable(false);
    btnHeaderPrev = BaseIcon.BULLET_ARROW_LEFT.button(null);
    btnHeaderNext = BaseIcon.BULLET_ARROW_RIGHT.button(null);
    btnHeaderPrev.getStyleClass().add("note-header-nav");
    btnHeaderNext.getStyleClass().add("note-header-nav");
    btnHeaderPrev.setFocusTraversable(false);
    btnHeaderNext.setFocusTraversable(false);
    HBox.setMargin(btnHeaderInfo, new Insets(0, 0, 0, 10));
    right.getChildren().addAll(btnHeaderPrev, btnHeaderNext, btnHeaderInfo);

    headerBar.setLeft(left);
    headerBar.setCenter(center);
    headerBar.setRight(right);

    infoPopupRoot.getStyleClass().add("note-info-popup");
    infoPopup.getContent().add(infoPopupRoot);
    infoPopup.setAutoHide(false);
    infoPopupRoot.setOnMouseExited(_ -> infoPopup.hide());
    btnHeaderInfo.setOnAction(_ -> {
      if (infoPopup.isShowing()) {
        infoPopup.hide();
      } else {
        showInfoPopup();
      }
    });

    titleEditorArea = new TextArea();
    titleEditorArea.getStyleClass().add("note-title-area");
    titleEditorArea.setPromptText("Überschrift");
    titleEditorArea.setWrapText(false);
    titleEditorArea.setPrefRowCount(1);
    final double titleRowHeight = 24;
    titleEditorArea.setMinHeight(titleRowHeight);
    titleEditorArea.setPrefHeight(titleRowHeight);
    titleEditorArea.setMaxHeight(titleRowHeight);
    titleEditorArea.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    titleEditorArea.setTextFormatter(new TextFormatter<String>(change -> {
      String inserted = change.getText();
      if (inserted != null && inserted.contains("\n")) {
        change.setText(inserted.replace("\n", " "));
      }
      if (inserted != null && inserted.contains("\r")) {
        change.setText(change.getText().replace("\r", ""));
      }
      return change;
    }));
    titleStack = new StackPane(titleEditorArea);
    titleStack.getStyleClass().add("note-title-stack");
    StackPane.setAlignment(titleEditorArea, Pos.TOP_LEFT);

    webBodyEditor = new TinyMceEditorPane();
    webBodyEditor.setOnContentChanged(this::refreshOpenInfoPopup);
    webBodyEditor.setOnSourceModeChanged(sourceMode -> bodySourceMode = sourceMode);

    bodyContainer = new StackPane(webBodyEditor);
    bodyContainer.getStyleClass().add("note-body-container");
    VBox.setVgrow(bodyContainer, Priority.ALWAYS);

    bibliographyHost = new VBox();
    bibliographyHost.getStyleClass().add("note-bibliography-host");
    bibliographyHost.setVisible(false);
    bibliographyHost.setManaged(false);

    formattingBarContainer = new HBox();
    formattingBarContainer.getStyleClass().add("note-formatting-bar");
    formattingBarContainer.setMinHeight(Region.USE_COMPUTED_SIZE);
    formattingBarContainer.setPrefHeight(Region.USE_COMPUTED_SIZE);

    formattingMenuContent = new NoteFormattingMenuContent();
    webBodyEditor.setOnTableContextChanged(formattingMenuContent::setTableControlsVisible);
    formattingMenuContent.setOnTableActionRequested(this::handleTableAction);
    formattingMenuContent.setOnFormatCommandRequested(this::handleWebFormatCommand);
    formattingMenuContent.setOnImageDropPopupRequested(this::showImageDropPopup);
    installBodyEditorShortcuts();
    formattingDragHandle = new Region();
    formattingDragHandle.getStyleClass().add("note-formatting-drag-handle");
    formattingDragHandle.setCursor(Cursor.HAND);
    formattingContextMenu = new ContextMenu();
    installFormattingDragHandle();
    formattingBarContainer.getChildren().setAll(formattingDragHandle, formattingMenuContent.getRoot());
    formattingContextMenu.getItems().setAll(new CustomMenuItem(formattingBarContainer, false));
    formattingContextMenu.getStyleClass().add("note-formatting-context-menu");
    formattingContextMenu.setOnShowing(_ -> formattingMenuContent.syncFromSelection());

    getChildren().addAll(headerBar, titleStack, bodyContainer, bibliographyHost);
  }

  public TinyMceEditorPane getWebBodyEditor() {
    return webBodyEditor;
  }

  public StackPane getTitleStack() {
    return titleStack;
  }

  public TextArea getTitleEditorArea() {
    return titleEditorArea;
  }

  public ContextMenu getFormattingContextMenu() {
    return formattingContextMenu;
  }

  public void toggleBodySourceMode() {
    webBodyEditor.toggleSourceMode();
    bodySourceMode = webBodyEditor.isSourceMode();
  }

  public void commitBodySourceViewIfActive() {
    webBodyEditor.commitSourceViewIfActive();
    bodySourceMode = false;
  }

  public void discardBodySourceView() {
    bodySourceMode = false;
  }

  public boolean isBodySourceMode() {
    return bodySourceMode || webBodyEditor.isSourceMode();
  }

  public void prepareBodyFormattingContext() {
    formattingMenuContent.setTableControlsVisible(webBodyEditor.isTableContextActive());
    formattingMenuContent.syncFromSelection();
  }

  public void setBodyHtml(String html) {
    webBodyEditor.setHtmlContent(html);
  }

  public String getBodyHtml() {
    return webBodyEditor.getHtmlContent();
  }

  public String getBodyPlainText() {
    return webBodyEditor.getPlainText();
  }

  public void requestBodyEditorFocus() {
    webBodyEditor.requestEditorFocus();
  }

  public StackPane getBodyContainer() {
    return bodyContainer;
  }

  public BorderPane getHeaderBar() {
    return headerBar;
  }

  public VBox getBibliographyHost() {
    return bibliographyHost;
  }

  public void refreshImageReferences() {
    // Bilder werden im WebView direkt als HTML-Bilder gerendert.
  }

  private boolean handleWebFormatCommand(String command, String value) {
    if ("image".equals(command)) {
      insertImageViaFileChooser();
      return true;
    }
    if ("insertTable".equals(command)) {
      String[] parts = value == null ? new String[] {"1", "1"} : value.split("x", 2);
      int rows = parsePositiveInt(parts.length > 0 ? parts[0] : "1", 1);
      int columns = parsePositiveInt(parts.length > 1 ? parts[1] : "1", 1);
      webBodyEditor.insertTable(rows, columns);
      return true;
    }
    webBodyEditor.executeCommand(command, value);
    return true;
  }

  private void handleTableAction(NoteFormattingMenuContent.TableAction action) {
    if (webBodyEditor.isTableContextActive()) {
      webBodyEditor.executeCommand("tableAction", action.name());
    }
  }

  /**
   * Verdrahtet Editor-Shortcuts auf denselben Formatbefehlspfad, den auch das Formatierfenster verwendet.
   */
  private void installBodyEditorShortcuts() {
    webBodyEditor.getWebView().addEventFilter(KeyEvent.KEY_TYPED, event -> {
      if (isCtrlMParagraphInsert(event)) {
        suppressNextCtrlMParagraphInsert = false;
        event.consume();
      }
    });
    webBodyEditor.getWebView().addEventFilter(KeyEvent.KEY_RELEASED, event -> {
      if (event.getCode() == KeyCode.M) {
        suppressNextCtrlMParagraphInsert = false;
      }
    });
    webBodyEditor.getWebView().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
      if (event.isAltDown()
          && !event.isControlDown()
          && !event.isMetaDown()
          && !event.isShiftDown()
          && event.getCode() == KeyCode.S) {
        webBodyEditor.requestKeyFilterTextFromSelection();
        event.consume();
        return;
      }

      if (!event.isControlDown() || event.isAltDown() || event.isMetaDown() || event.isShiftDown()) {
        return;
      }

      boolean handled = switch (event.getCode()) {
        case B -> handleWebFormatCommand("bold", null);
        case I -> handleWebFormatCommand("italic", null);
        case U -> handleWebFormatCommand("underline", null);
        case D -> handleWebFormatCommand("strike", null);
        case L -> handleWebFormatCommand("align", "left");
        case R -> handleWebFormatCommand("align", "right");
        case M -> {
          boolean applied = handleWebFormatCommand("align", "center");
          suppressNextCtrlMParagraphInsert = applied;
          yield applied;
        }
        case J -> handleWebFormatCommand("align", "justify");
        case DIGIT1, NUMPAD1 -> handleWebFormatCommand("h1", null);
        case DIGIT2, NUMPAD2 -> handleWebFormatCommand("h2", null);
        case Q -> handleWebFormatCommand("citation", null);
        case S -> handleWebFormatCommand("separator", null);
        default -> false;
      };

      if (handled) {
        event.consume();
      }
    });
  }

  /**
   * Erkennt das von WebView nach Strg+M gelieferte Return-Zeichen, das sonst einen neuen Absatz einfuegt.
   *
   * @param event das zu pruefende Tastaturereignis
   * @return {@code true}, wenn das Ereignis zum nativen Absatzeinschub von Strg+M gehoert
   */
  private boolean isCtrlMParagraphInsert(KeyEvent event) {
    if (!suppressNextCtrlMParagraphInsert) {
      return false;
    }
    String character = event.getCharacter();
    return "\r".equals(character) || "\n".equals(character);
  }

  private int parsePositiveInt(String value, int fallback) {
    try {
      return Math.max(1, Integer.parseInt(value));
    } catch (RuntimeException ex) {
      return fallback;
    }
  }

  private void insertImageViaFileChooser() {
    FileChooser chooser = new FileChooser();
    chooser.setInitialDirectory(EditorImageAssetService.ensureImageDirectory().toFile());
    chooser.setTitle("Bild auswählen");
    chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
        "Bilddateien",
        "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.webp", "*.tif", "*.tiff"
    ));
    java.io.File selected = chooser.showOpenDialog(getScene() == null ? null : getScene().getWindow());
    if (selected == null) {
      return;
    }
    Path imagePath = selected.toPath().toAbsolutePath().normalize();
    if (EditorImageAssetService.isSupportedImageFile(imagePath)) {
      webBodyEditor.insertImage(imagePath.toUri().toString());
    }
  }

  /**
   * Oeffnet das Sonder-Popup des Bildbuttons fuer Drag-and-Drop-Importe in den Bildordner.
   *
   * @param screenPosition Position unterhalb des Bildbuttons in Bildschirmkoordinaten
   */
  private void showImageDropPopup(Point2D screenPosition) {
    if (imageDropPopup != null) {
      imageDropPopup.hide();
    }

    Popup popup = new Popup();
    popup.setAutoHide(false);
    VBox root = new VBox(8);
    root.getStyleClass().add("note-image-drop-popup");
    root.setPadding(new Insets(10));
    root.setPrefWidth(360);

    Label title = new Label("Bilder hier ablegen");
    title.getStyleClass().add("note-image-drop-title");
    Button close = new Button("schließen");
    close.setFocusTraversable(false);
    Region titleSpacer = new Region();
    HBox.setHgrow(titleSpacer, Priority.ALWAYS);
    HBox titleRow = new HBox(8, title, titleSpacer, close);
    titleRow.setAlignment(Pos.CENTER_LEFT);

    Label status = new Label("Noch keine Bilder ausgewählt");
    status.getStyleClass().add("note-image-drop-status");

    ObservableList<Path> droppedImages = FXCollections.observableArrayList();
    ListView<Path> imageList = new ListView<>(droppedImages);
    imageList.setPrefHeight(140);

    Button copy = new Button("kopieren");
    Button move = new Button("verschieben");
    copy.disableProperty().bind(Bindings.isEmpty(droppedImages));
    move.disableProperty().bind(Bindings.isEmpty(droppedImages));
    HBox actions = new HBox(8, copy, move);
    actions.setAlignment(Pos.CENTER_RIGHT);

    root.getChildren().addAll(titleRow, imageList, status, actions);
    root.setOnDragOver(event -> handleImageDropDragOver(event));
    root.setOnDragDropped(event -> handleImageDrop(event, droppedImages, status));
    copy.setOnAction(_ -> transferDroppedImages(droppedImages, status, false, popup));
    move.setOnAction(_ -> transferDroppedImages(droppedImages, status, true, popup));
    close.setOnAction(_ -> popup.hide());

    popup.getContent().setAll(root);
    imageDropPopup = popup;
    double x = screenPosition == null ? 0.0 : screenPosition.getX();
    double y = screenPosition == null ? 0.0 : screenPosition.getY();
    if (getScene() == null) {
      popup.show(this, x, y);
    } else {
      popup.show(getScene().getWindow(), x, y);
    }
  }

  /**
   * Akzeptiert Drag-Ereignisse, sobald mindestens eine unterstuetzte Bilddatei enthalten ist.
   *
   * @param event Drag-Ereignis
   */
  private void handleImageDropDragOver(DragEvent event) {
    if (supportedImageFiles(event.getDragboard()).isEmpty()) {
      return;
    }
    event.acceptTransferModes(TransferMode.COPY);
    event.consume();
  }

  /**
   * Uebernimmt abgelegte Bilddateien in die Popup-Liste, ohne sie in den Zettel einzufuegen.
   *
   * @param event Drag-Drop-Ereignis
   * @param droppedImages bisher gesammelte Bilddateien
   * @param status Statusanzeige
   */
  private void handleImageDrop(DragEvent event, ObservableList<Path> droppedImages, Label status) {
    List<Path> files = supportedImageFiles(event.getDragboard());
    if (files.isEmpty()) {
      event.setDropCompleted(false);
      event.consume();
      status.setText("Keine unterstützten Bilddateien abgelegt");
      return;
    }
    droppedImages.addAll(files);
    status.setText(droppedImages.size() + " Bild(er) vorgemerkt");
    event.setDropCompleted(true);
    event.consume();
  }

  /**
   * Liefert die unterstuetzten Bilddateien eines Dragboards.
   *
   * @param dragboard Dragboard
   * @return Bilddateien aus dem Dragboard
   */
  private List<Path> supportedImageFiles(Dragboard dragboard) {
    if (dragboard == null || !dragboard.hasFiles()) {
      return List.of();
    }
    List<Path> supported = new ArrayList<>();
    for (java.io.File file : dragboard.getFiles()) {
      Path path = file.toPath().toAbsolutePath().normalize();
      if (EditorImageAssetService.isSupportedImageFile(path)) {
        supported.add(path);
      }
    }
    return supported;
  }

  /**
   * Kopiert oder verschiebt alle im Popup gesammelten Bilder in den Bildordner.
   *
   * @param droppedImages gesammelte Bilddateien
   * @param status Statusanzeige
   * @param move ob verschoben statt kopiert wird
   * @param popup Popup, das fuer den manuellen Abschluss offen bleibt
   */
  private void transferDroppedImages(ObservableList<Path> droppedImages, Label status, boolean move, Popup popup) {
    try {
      int count = droppedImages.size();
      for (Path image : List.copyOf(droppedImages)) {
        if (move) {
          EditorImageAssetService.moveIntoImageDirectory(image);
        } else {
          EditorImageAssetService.copyIntoImageDirectory(image);
        }
      }
      droppedImages.clear();
      status.setText(count + " Bild(er) " + (move ? "verschoben" : "kopiert"));
    } catch (RuntimeException exception) {
      status.setText(exception.getMessage() == null ? "Bildtransfer fehlgeschlagen" : exception.getMessage());
    }
  }

  private void installFormattingDragHandle() {
    final double[] dragOffset = new double[2];
    formattingDragHandle.setOnMousePressed(event -> {
      dragOffset[0] = event.getScreenX() - formattingContextMenu.getX();
      dragOffset[1] = event.getScreenY() - formattingContextMenu.getY();
      event.consume();
    });
    formattingDragHandle.setOnMouseDragged(event -> {
      formattingContextMenu.setX(event.getScreenX() - dragOffset[0]);
      formattingContextMenu.setY(event.getScreenY() - dragOffset[1]);
      event.consume();
    });
  }

  public void setHeaderNoteNumber(Integer noteId) {
    lblHeaderNoteNo.setText(noteId == null || noteId <= 0 ? "—" : Integer.toString(noteId));
  }

  public void setHeaderCreatedAt(Instant createdAt) {
    if (createdAt == null) {
      lblHeaderCreatedAt.setText("—");
      return;
    }
    var dt = LocalDateTime.ofInstant(createdAt, java.time.ZoneId.systemDefault());
    lblHeaderCreatedAt.setText(DateUtil.formatHeaderDate(dt));
  }

  public void setBibliographyVisible(boolean visible) {
    bibliographyHost.setVisible(visible);
    bibliographyHost.setManaged(visible);
  }

  public void setInfoData(List<NoteReferenceInfo> outgoing,
                          List<NoteReferenceInfo> incoming,
                          List<String> projects) {
    this.infoOutgoing = outgoing == null ? List.of() : List.copyOf(outgoing);
    this.infoIncoming = incoming == null ? List.of() : List.copyOf(incoming);
    this.infoProjects = projects == null ? List.of() : List.copyOf(projects);
    if (infoPopup.isShowing()) {
      refreshOpenInfoPopup();
    }
  }

  public void setOnNavigateToNote(IntConsumer onNavigateToNote) {
    this.onNavigateToNote = onNavigateToNote == null ? _ -> {} : onNavigateToNote;
  }

  private void showInfoPopup() {
    rebuildInfoPopupContent();
    var b = btnHeaderInfo.localToScreen(btnHeaderInfo.getBoundsInLocal());
    if (b == null) {
      return;
    }
    infoPopup.show(btnHeaderInfo, b.getMaxX() - 260, b.getMaxY() + 6);
    javafx.application.Platform.runLater(() -> {
      double w = infoPopupRoot.getWidth();
      if (w <= 0) {
        w = infoPopupRoot.prefWidth(-1);
      }
      double centerX = (b.getMinX() + b.getMaxX()) / 2.0;
      infoPopup.setX(centerX - w / 2.0);
      infoPopup.setY(b.getMaxY() + 6);
    });
  }

  private void refreshOpenInfoPopup() {
    if (!infoPopup.isShowing()) {
      return;
    }
    rebuildInfoPopupContent();
  }

  private void rebuildInfoPopupContent() {
    infoPopupRoot.getChildren().clear();
    infoPopupRoot.getChildren().add(sectionLine("Wörter", Integer.toString(computeWordCount())));
    infoPopupRoot.getChildren().add(sectionReferenceLinks("Folgezettel", infoOutgoing));
    infoPopupRoot.getChildren().add(sectionReferenceLinks("Verweist auf diesen Zettel", infoIncoming));
    if (infoProjects != null && !infoProjects.isEmpty()) {
      infoPopupRoot.getChildren().add(sectionTexts("Projekte", infoProjects));
    }
  }

  private int computeWordCount() {
    String text = getBodyPlainText();
    if (text == null || text.isBlank()) {
      return 0;
    }
    return text.trim().split("\\s+").length;
  }

  private Node sectionLine(String title, String value) {
    var row = new HBox(8);
    row.getStyleClass().add("note-info-row");
    var key = new Label(title + ":");
    key.getStyleClass().add("note-info-key");
    var label = new Label(value);
    label.getStyleClass().add("note-info-value");
    row.getChildren().addAll(key, label);
    return row;
  }

  private Node sectionReferenceLinks(String title, List<NoteReferenceInfo> references) {
    var box = new VBox(4);
    box.getStyleClass().add("note-info-section");
    var head = new Label(title);
    head.getStyleClass().add("note-info-section-title");
    box.getChildren().add(head);
    if (references == null || references.isEmpty()) {
      box.getChildren().clear();
      return box;
    }
    var flow = new FlowPane(6, 6);
    for (NoteReferenceInfo reference : references) {
      if (reference == null) {
        continue;
      }
      var link = new Hyperlink(Integer.toString(reference.noteId()));
      link.getStyleClass().add("note-info-link");
      link.setTooltip(new Tooltip(reference.tooltipText()));
      link.setOnAction(_ -> {
        infoPopup.hide();
        onNavigateToNote.accept(reference.noteId());
      });
      flow.getChildren().add(link);
    }
    box.getChildren().add(flow);
    return box;
  }

  private Node sectionTexts(String title, List<String> items) {
    var box = new VBox(4);
    box.getStyleClass().add("note-info-section");
    var head = new Label(title);
    head.getStyleClass().add("note-info-section-title");
    box.getChildren().add(head);
    for (String item : items) {
      if (item == null || item.isBlank()) {
        continue;
      }
      var label = new Label(item);
      label.getStyleClass().add("note-info-value");
      box.getChildren().add(label);
    }
    return box;
  }

  public void setOnHeaderPrev(Runnable r) {
    btnHeaderPrev.setOnAction(_ -> {
      if (r != null) {
        r.run();
      }
    });
  }

  public void setOnHeaderNext(Runnable r) {
    btnHeaderNext.setOnAction(_ -> {
      if (r != null) {
        r.run();
      }
    });
  }
}
