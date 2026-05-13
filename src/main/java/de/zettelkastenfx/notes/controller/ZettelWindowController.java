package de.zettelkastenfx.notes.controller;

import de.zettelkastenfx.base.BaseIcon;
import de.zettelkastenfx.base.util.PersistenceUtil;
import de.zettelkastenfx.bibliography.api.BibliographyService;
import de.zettelkastenfx.bibliography.api.BibliographyServiceImpl;
import de.zettelkastenfx.bibliography.model.*;
import de.zettelkastenfx.bibliography.ui.BibliographyEditorShell;
import de.zettelkastenfx.bibliography.ui.BibliographyEditorShellFactory;
import de.zettelkastenfx.bibliography.ui.MediaManagementDialog;
import de.zettelkastenfx.export.ui.ManualExportDialog;
import de.zettelkastenfx.notes.controller.ZettelWindowView.KeyTableRow;
import de.zettelkastenfx.notes.editor.NoteEditorPane;
import de.zettelkastenfx.notes.editor.format.InlineCssStyleUtil;
import de.zettelkastenfx.notes.editor.web.TinyMceContextMenuEvent;
import de.zettelkastenfx.notes.keywords.KeywordsTablePane;
import de.zettelkastenfx.notes.model.LinkType;
import de.zettelkastenfx.notes.model.Note;
import de.zettelkastenfx.notes.model.NoteLink;
import de.zettelkastenfx.notes.model.NoteReferenceInfo;
import de.zettelkastenfx.persistence.InlineCssRtfxBlobCodec;
import de.zettelkastenfx.persistence.HtmlBodyCodec;
import de.zettelkastenfx.persistence.NoteRepository;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.awt.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ZettelWindowController {

  private static final int MEDIA_REFERENCE_SEARCH_LIMIT = 500;

  private final Stage stage;
  private final ZettelWindowView view;
  private boolean titleEditing;

  private String titleTextBeforeEdit = "";

  private final NoteRepository noteRepository;
  private final BibliographyService bibliographyService;
  private final BibliographyEditorShellFactory bibliographyShellFactory;

  private Note currentNote;     // null = noch nicht gespeichert
  private boolean loading;

  // BottomToolbar / Zettelwechsel
  private boolean updatingNoteIdField;
  private boolean openingNoteFromLockedList;

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
  private double linkEditPopupDragOffsetX; // für das Drag des Popups
  private double linkEditPopupDragOffsetY; // für das Drag des Popups

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
  private enum LinkEditStatusTone {
    NEUTRAL,
    WARNING,
    ERROR
  }

  private enum DuplicateBibliographyDecision {
    UPDATE_EXISTING,
    SAVE_AS_NEW,
    CANCEL
  }

  private enum LinkedBibliographyDecision {
    UPDATE_LINKED_ENTRY,
    SAVE_AS_NEW,
    CANCEL
  }

  private final Set<String> collapsedMagnifyIncomingBranches = new HashSet<>();
  private final Set<String> collapsedMagnifyOutgoingBranches = new HashSet<>();
  private Integer lastMagnifyRenderedSourceNoteId;
  private final Map<String, MediaTypeDefinition> bookMediaTypesByNormalizedText = new LinkedHashMap<>();
  private boolean suppressBookMediaTypeCallbacks;
  private boolean suppressBookAuthorCallbacks;
  private boolean keyContextFilterUsesAiContext;
  private Integer selectedBookEntryId;
  private BibliographyEditorShell activeEmbeddedBibliographyShell;
  private final PauseTransition bookRefreshDebounce = new PauseTransition(Duration.millis(140));
  private final PauseTransition listRefreshDebounce = new PauseTransition(Duration.millis(120));
  private final ExecutorService bookRefreshExecutor = Executors.newSingleThreadExecutor(this::createBookRefreshThread);
  private long bookServiceRefreshRequestId;
  private record MagnifyBranchReference(NoteReferenceInfo reference, boolean incoming) {}
  private record BookAuthorFilter(String lastName, String firstName) {}
  private record BookRefreshRequest(
      long requestId,
      ZettelWindowView.BookMediaTableSortState mediaSortState,
      ZettelWindowView.BookNoteTableSortState noteSortState,
      String mediaTypeBibName,
      String titleQuery,
      boolean resolvedMediaType,
      boolean notesVisible,
      Integer selectedEntryId,
      ZettelWindowView.BookAuthorMode authorMode,
      List<BookAuthorFilter> authorFilters
  ) {}
  private record BookRefreshResult(
      List<ZettelWindowView.BookMediaRow> mediaRows,
      List<ZettelWindowView.BookNoteRow> noteRows,
      String errorMessage
  ) {}

  private static final class ListNoteData {
    private final int noteId;
    private final String title;
    private final byte[] bodyBlob;
    private final String bodyCodec;
    private final LinkedHashSet<String> keywords = new LinkedHashSet<>();

    /**
     * Erzeugt einen LIST-Datensatz für einen Zettel.
     *
     * @param noteId Zettelnummer
     * @param title Überschrift
     * @param bodyBlob codierter Inhalt
     * @param bodyCodec Name des verwendeten Codecs
     */
    private ListNoteData(int noteId, String title, byte[] bodyBlob, String bodyCodec) {
      this.noteId = noteId;
      this.title = title == null ? "" : title;
      this.bodyBlob = bodyBlob == null ? new byte[0] : bodyBlob;
      this.bodyCodec = bodyCodec == null ? "" : bodyCodec;
    }
  }

  public ZettelWindowController(Stage stage, ZettelWindowView view) {
    this.stage = stage;
    this.view = view;
    this.noteRepository = PersistenceUtil.getNoteRepository();
    this.bibliographyService = new BibliographyServiceImpl(PersistenceUtil.getDataSource());
    this.bibliographyShellFactory = new BibliographyEditorShellFactory(
        bibliographyService,
        PersistenceUtil.getDataSource()
    );

    bookRefreshDebounce.setOnFinished(_ -> requestAsyncBookRefresh());
    listRefreshDebounce.setOnFinished(_ -> refreshListWindow());
    rebuildBookMediaTypeCache();

    openInitialNote();
    wireEvents();
  }

  /**
   * Wird von ZettelWindow aufgerufen.
   */
  public void applyInitialState() {
    // Startlayout: drei Bereiche ungefähr gleich
    Platform.runLater(() -> view.getWorkAreaSplitPane().setDividerPositions(0.33, 0.66));
    syncMagnifyLinkSourceNoteFromCurrentNote();
    refreshServiceAreaForCurrentState();
  }

  private void wireEvents() {
    NoteEditorPane editor = view.getNoteEditorPane();

    wireTitleEditing(editor);
    wireGlobalTitleCommitHandlers(editor);
    wireBodyEditingAndContextMenu(editor);
    wireGlobalBodyDeactivateHandlers(editor);

    // Toolbar: neue Seiten
    view.getToolButtonPageAdd().setOnAction(_ -> createNewEmptyNote());
    view.getToolButtonPageCopy().setOnAction(_ -> createCopyOfCurrentNote());
    view.getToolButtonPageWithBibliography().setOnAction(_ -> createCopyBibliography());
    view.getToolButtonLinkEdit().setOnAction(_ -> toggleLinkEditPopup());
    view.getToolButtonToggleServiceArea().setOnAction(_ -> toggleServiceArea());
    view.getToolButtonBibliography().setOnAction(_ -> openMediaManagementDialog());

    // Toolbar: Service-Fenster
    view.getServiceAreaMagnifyLinkButton().setOnAction(_ -> handleServiceAreaModeToggle(ZettelWindowView.ServiceAreaMode.MAGNIFY_LINK));
    view.getServiceAreaKeyButton().setOnAction(_ -> handleServiceAreaModeToggle(ZettelWindowView.ServiceAreaMode.KEY));
    view.getServiceAreaBookButton().setOnAction(_ -> handleServiceAreaModeToggle(ZettelWindowView.ServiceAreaMode.BOOK));
    view.getServiceAreaListButton().setOnAction(_ -> handleServiceAreaModeToggle(ZettelWindowView.ServiceAreaMode.LIST));
    view.getServiceAreaClusterButton().setOnAction(_ -> handleServiceAreaModeToggle(ZettelWindowView.ServiceAreaMode.PERFORMANCE_ANALYSIS));

    // Verdrahtet Service-Fenster-Inhalte
    wireMagnifyLinkEvents();
    wireKeyEvents();
    wireListEvents();
    wireBookEvents();

    // Dropdown für Folgezettel
    view.getMiFollowing().setOnAction(_ -> createTypedFollowUpNote(LinkType.SERIE));
    view.getMiIdea().setOnAction(_ -> createTypedFollowUpNote(LinkType.IDEE));
    view.getMiQuestion().setOnAction(_ -> createTypedFollowUpNote(LinkType.FRAGE));
    view.getMiCritique().setOnAction(_ -> createTypedFollowUpNote(LinkType.KRITIK));
    view.getMiTask().setOnAction(_ -> createTypedFollowUpNote(LinkType.AUFGABE));

    // Menü (oben): "Datei -> Schließen" schließt nur dieses Fenster
    view.getMenuFile().getItems().getLast().setOnAction(_ -> stage.close());

    // Menü (oben): Exportfenster
    view.getMenuFile().getItems().getFirst().setText("Export");
    view.getMenuFile().getItems().getFirst().setOnAction(_ -> openManualExportDialog());
    view.getMenuFile().getItems().getLast().setOnAction(_ -> stage.close());

    view.getKeywordsTablePane().setOnKeywordsCommitted(() -> {
      saveCurrentIfValid();

      if (currentNote != null) {
        view.getKeywordsTablePane().setKeywords(currentNote.getKeywords());
      }

      refreshKeywordViews();
      refreshListWindow();
      view.clearKeyActionStatus();
    });
    view.getKeywordsTablePane().setKeywordSuggestionProvider(this::loadKeywordSuggestionsForInlineEdit);
    view.setKeyAltClickHandler(this::addKeywordToCurrentNote);

    wireBottomToolbar();
    wireHeaderBarNavigation();
    initLinkEditPopup();

    stage.setOnCloseRequest(_ -> {
      linkEditPopup.hide();
      shutdownBookRefreshExecutor();
      saveOrDiscardCurrentNoteBeforeLeave();
    });
  }

  /**
   * Erzeugt einen Daemon-Thread fuer die asynchrone BOOK-Aktualisierung.
   *
   * @param task auszufuehrende Aufgabe
   * @return konfigurierte Thread-Instanz
   */
  private Thread createBookRefreshThread(Runnable task) {
    Thread thread = new Thread(task, "zettelkasten-book-refresh");
    thread.setDaemon(true);
    return thread;
  }

  /**
   * Beendet den Hintergrundexecutor fuer den BOOK-Refresh.
   */
  private void shutdownBookRefreshExecutor() {
    bookRefreshExecutor.shutdownNow();
  }

  /**
   * Oeffnet das Fenster fuer den manuellen Zettelexport.
   */
  private void openManualExportDialog() {
    new ManualExportDialog(stage, noteRepository, bibliographyService).show();
  }

  /**
   * Verdrahtet die Bedienelemente des integrierten MAGNIFY_LINK-Fensters.
   */
  private void wireMagnifyLinkEvents() {
    view.getMagnifyLinkNoteIdField().setOnAction(_ -> refreshMagnifyLinkWindow());

    view.getMagnifyLinkNoteIdField().focusedProperty().addListener((_, oldValue, newValue) -> {
      if (oldValue && !newValue) {
        refreshMagnifyLinkWindow();
      }
    });

    view.getMagnifyLinkIncomingDepthSlider().valueProperty().addListener((_, _, _) -> refreshMagnifyLinkWindow());
    view.getMagnifyLinkOutgoingDepthSlider().valueProperty().addListener((_, _, _) -> refreshMagnifyLinkWindow());
    view.getMagnifyLinkDirectedTraversalButton().setOnAction(_ -> refreshMagnifyLinkWindow());

    view.getMagnifyLinkSerieButton().setOnAction(_ -> refreshMagnifyLinkWindow());
    view.getMagnifyLinkIdeaButton().setOnAction(_ -> refreshMagnifyLinkWindow());
    view.getMagnifyLinkQuestionButton().setOnAction(_ -> refreshMagnifyLinkWindow());
    view.getMagnifyLinkCritiqueButton().setOnAction(_ -> refreshMagnifyLinkWindow());
    view.getMagnifyLinkTaskButton().setOnAction(_ -> refreshMagnifyLinkWindow());

    view.getMagnifyLinkFilterField().textProperty().addListener((_, _, _) -> refreshMagnifyLinkWindow());
  }

  /**
   * Verdrahtet die Bedienelemente des LIST-Fensters.
   */
  private void wireListEvents() {
    view.getListPageKeyButton().setOnAction(_ -> {
      view.setListMode(ZettelWindowView.ListMode.PAGE_KEY);
      refreshListWindow();
    });

    view.getListDepthSlider().valueProperty().addListener((_, _, _) -> {
      view.setListMode(ZettelWindowView.ListMode.PAGE_KEY);
      refreshListWindow();
    });

    view.getListFilterField().focusedProperty().addListener((_, _, newValue) -> {
      if (newValue) {
        view.setListMode(ZettelWindowView.ListMode.FILTER);
        refreshListWindow();
      }
    });

    view.getListFilterField().textProperty().addListener((_, _, _) -> {
      if (view.getActiveServiceAreaMode() == ZettelWindowView.ServiceAreaMode.LIST) {
        requestListRefresh();
      }
    });

    view.getListFullTextToggleButton().setOnAction(_ -> {
      view.setListMode(ZettelWindowView.ListMode.FILTER);
      refreshListWindow();
    });

    view.getListLockToggleButton().setOnAction(_ -> {
      view.updateListLockIcon();
      if (view.getListLockToggleButton().isSelected()) {
        listRefreshDebounce.stop();
      } else {
        refreshListWindow(true);
        view.scrollListToTop();
      }
    });

    view.getListFilterTable().setRowFactory(_ -> {
      TableRow<ZettelWindowView.ListFilterRow> row = new TableRow<>();
      row.itemProperty().addListener((_, _, newItem) -> {
        row.getStyleClass().remove("zettel-list-filter-body-match-row");
        if (newItem != null && newItem.isBodyMatchOnly()) {
          row.getStyleClass().add("zettel-list-filter-body-match-row");
        }
      });
      row.setOnMouseClicked(event -> {
        if (event.getButton() == MouseButton.PRIMARY && !row.isEmpty()) {
          openNoteFromList(row.getItem().getNoteId());
        }
      });
      return row;
    });
  }

  /**
   * Verdrahtet die Bedienelemente des BOOK-Fensters.
   * Schritt 3 ergänzt:
   * - Autorenfilter mit Vorschlagsdropdown
   * - Zetteltabelle
   * - Medienauswahl per Zeilenklick
   * - Zettelsprung aus der unteren Tabelle
   */
  private void wireBookEvents() {
    view.getBookShowNotesToggleButton().selectedProperty().addListener((_, _, selected) -> {
      view.setBookNotesVisible(selected);
      requestAsyncBookRefresh();
    });

    view.getBookShowMediaColumnToggleButton().setOnAction(_ -> requestAsyncBookRefresh());

    view.getBookAuthorModeButton().setOnAction(_ -> {
      cycleBookAuthorMode();
      wireBookAuthorRowEvents();
      requestAsyncBookRefresh();
    });

    view.getBookMediaTypeField().textProperty().addListener((_, _, newValue) -> {
      if (suppressBookMediaTypeCallbacks) {
        return;
      }
      handleBookMediaTypeTextChanged(newValue);
    });

    view.getBookMediaTypeField().focusedProperty().addListener((_, _, focused) -> {
      if (focused) {
        if (!safeTrim(view.getBookMediaTypeField().getText()).isBlank()) {
          showBookMediaTypeSuggestions(view.getBookMediaTypeField().getText());
        }
        return;
      }

      view.getBookMediaTypeMenu().hide();
      commitBookMediaTypeSelection();
    });

    view.getBookMediaTypeField().setOnMouseClicked(_ -> {
      if (!view.getBookMediaTypeField().isFocused()) {
        view.getBookMediaTypeField().requestFocus();
      }
      if (!safeTrim(view.getBookMediaTypeField().getText()).isBlank()) {
        showBookMediaTypeSuggestions(view.getBookMediaTypeField().getText());
      }
    });

    view.getBookMediaTypeField().setOnKeyPressed(event -> {
      switch (event.getCode()) {
        case DOWN, ENTER -> {
          showBookMediaTypeSuggestions(view.getBookMediaTypeField().getText());
          if (event.getCode() == KeyCode.ENTER) {
            commitBookMediaTypeSelection();
          }
        }
        case ESCAPE -> view.getBookMediaTypeMenu().hide();
        default -> {
        }
      }
    });

    view.getBookTitleField().textProperty().addListener((_, _, _) -> requestBookRefresh());

    view.getBookMediaTable().setPlaceholder(new Label("Keine Medien gefunden"));
    view.getBookNotesTable().setPlaceholder(new Label("Keine Zettel gefunden"));

    view.getBookMediaTable().setRowFactory(_ -> {
      TableRow<ZettelWindowView.BookMediaRow> row = new TableRow<>();

      row.setOnMouseClicked(event -> {
        if (event.getButton() != MouseButton.PRIMARY || row.isEmpty()) {
          return;
        }

        ZettelWindowView.BookMediaRow clicked = row.getItem();
        if (clicked == null) {
          return;
        }

        if (Objects.equals(selectedBookEntryId, clicked.getEntryId())) {
          selectedBookEntryId = null;
          view.selectBookMediaRow(null);
        } else {
          selectedBookEntryId = clicked.getEntryId();
          view.selectBookMediaRow(selectedBookEntryId);
        }

        requestAsyncBookRefresh();
      });

      return row;
    });

    view.getBookNotesTable().setRowFactory(_ -> {
      TableRow<ZettelWindowView.BookNoteRow> row = new TableRow<>();
      row.setOnMouseClicked(event -> {
        if (event.getButton() == MouseButton.PRIMARY && !row.isEmpty()) {
          openNoteViaToolbar(row.getItem().getNoteId());
        }
      });
      return row;
    });

    wireBookAuthorRowEvents();
  }

  /**
   * Schaltet zyklisch durch die Autorenmodi des BOOK-Fensters.
   * OFF -> SINGLE -> MULTI -> OFF
   */
  private void cycleBookAuthorMode() {
    ZettelWindowView.BookAuthorMode nextMode = switch (view.getActiveBookAuthorMode()) {
      case OFF -> ZettelWindowView.BookAuthorMode.SINGLE;
      case SINGLE -> ZettelWindowView.BookAuthorMode.MULTI;
      case MULTI -> ZettelWindowView.BookAuthorMode.OFF;
    };

    view.setBookAuthorMode(nextMode);

    if (nextMode == ZettelWindowView.BookAuthorMode.OFF) {
      view.setBookAuthorRows(List.of(new ZettelWindowView.BookAuthorRow("", "")));
      return;
    }

    if (nextMode == ZettelWindowView.BookAuthorMode.SINGLE) {
      view.setBookAuthorRows(List.of(new ZettelWindowView.BookAuthorRow("", "")));
      return;
    }

    view.setBookAuthorRows(List.of(
        new ZettelWindowView.BookAuthorRow("", ""),
        new ZettelWindowView.BookAuthorRow("", "")
    ));
    ensureBookMultiAuthorTrailingBlankRow();
  }

  /**
   * Verdrahtet die aktuell sichtbaren Autorenzeilen des BOOK-Fensters.
   * Vorschläge erscheinen ausschließlich am Nachnamen-Feld.
   * Im Mehrfachmodus erweitert sich die letzte belegte Zeile automatisch
   * um eine neue leere Folgezeile.
   */
  private void wireBookAuthorRowEvents() {
    for (ZettelWindowView.BookAuthorRow row : view.getBookAuthorRowModels()) {
      if (row == null || row.getLastNameField() == null || row.getFirstNameField() == null) {
        continue;
      }

      row.getLastNameField().textProperty().addListener((_, _, newValue) -> {
        if (suppressBookAuthorCallbacks) {
          return;
        }

        row.setLastName(newValue == null ? "" : newValue);
        ensureBookMultiAuthorTrailingBlankRow();
        showBookAuthorSuggestions(row);
        requestBookRefresh();
      });

      row.getFirstNameField().textProperty().addListener((_, _, newValue) -> {
        if (suppressBookAuthorCallbacks) {
          return;
        }

        row.setFirstName(newValue == null ? "" : newValue);
        ensureBookMultiAuthorTrailingBlankRow();
        requestBookRefresh();
      });

      row.getLastNameField().focusedProperty().addListener((_, _, focused) -> {
        if (focused) {
          if (!safeTrim(row.getLastNameField().getText()).isBlank()) {
            showBookAuthorSuggestions(row);
          }
          return;
        }

        if (row.getLastNameSuggestionMenu() != null) {
          row.getLastNameSuggestionMenu().hide();
        }
      });

      row.getLastNameField().setOnMouseClicked(_ -> {
        if (!row.getLastNameField().isFocused()) {
          row.getLastNameField().requestFocus();
        }
        showBookAuthorSuggestions(row);
      });

      row.getLastNameField().setOnKeyPressed(event -> {
        switch (event.getCode()) {
          case DOWN, ENTER -> showBookAuthorSuggestions(row);
          case ESCAPE -> {
            if (row.getLastNameSuggestionMenu() != null) {
              row.getLastNameSuggestionMenu().hide();
            }
          }
          default -> {
          }
        }
      });
    }
  }

  /**
   * Stellt im Mehrfach-Autorenmodus sicher, dass am Ende immer genau eine
   * leere Folgezeile vorhanden ist.
   * Der Bereich startet mit zwei Zeilen und wächst danach nur bei Bedarf.
   */
  private void ensureBookMultiAuthorTrailingBlankRow() {
    if (view.getActiveBookAuthorMode() != ZettelWindowView.BookAuthorMode.MULTI) {
      return;
    }

    List<ZettelWindowView.BookAuthorRow> snapshot = new ArrayList<>(view.snapshotBookAuthorRows());
    if (snapshot.isEmpty()) {
      snapshot = new ArrayList<>(List.of(
          new ZettelWindowView.BookAuthorRow("", ""),
          new ZettelWindowView.BookAuthorRow("", "")
      ));
    }

    while (snapshot.size() < 2) {
      snapshot.add(new ZettelWindowView.BookAuthorRow("", ""));
    }

    int lastFilledIndex = -1;
    for (int i = 0; i < snapshot.size(); i++) {
      ZettelWindowView.BookAuthorRow row = snapshot.get(i);
      if (row == null) {
        continue;
      }

      if (!safeTrim(row.getLastName()).isBlank() || !safeTrim(row.getFirstName()).isBlank()) {
        lastFilledIndex = i;
      }
    }

    int targetSize = Math.max(2, lastFilledIndex + 2);

    if (snapshot.size() == targetSize) {
      return;
    }

    List<ZettelWindowView.BookAuthorRow> resizedRows = new ArrayList<>();
    for (int i = 0; i < targetSize; i++) {
      if (i < snapshot.size()) {
        ZettelWindowView.BookAuthorRow row = snapshot.get(i);
        resizedRows.add(new ZettelWindowView.BookAuthorRow(
            row == null ? "" : row.getLastName(),
            row == null ? "" : row.getFirstName()
        ));
      } else {
        resizedRows.add(new ZettelWindowView.BookAuthorRow("", ""));
      }
    }

    suppressBookAuthorCallbacks = true;
    try {
      view.setBookAuthorRows(resizedRows);
    } finally {
      suppressBookAuthorCallbacks = false;
    }

    wireBookAuthorRowEvents();
  }

  /**
   * Zeigt Autorenvorschläge für das Nachnamen-Feld einer BOOK-Autorenzeile.
   * Vorschläge werden lokal dedupliziert und nach Präfixtreffern bevorzugt sortiert.
   *
   * @param row betroffene Autorenzeile
   */
  private void showBookAuthorSuggestions(ZettelWindowView.BookAuthorRow row) {
    if (row == null || row.getLastNameField() == null || row.getLastNameSuggestionMenu() == null) {
      return;
    }

    String prefix = safeTrim(row.getLastNameField().getText());
    if (prefix.isBlank()) {
      row.getLastNameSuggestionMenu().hide();
      return;
    }

    String mediaTypeBibName = view.hasBookResolvedMediaTypeSelection()
                                  ? view.getBookResolvedMediaTypeBibName()
                                  : "";

    List<de.zettelkastenfx.bibliography.ui.lookup.AuthorSuggestion> suggestions =
        bibliographyService.findAuthorSuggestions(
            mediaTypeBibName,
            prefix,
            selectedBookEntryId
        );

    String firstNameFilter = row.getFirstNameField() == null
                                 ? safeTrim(row.getFirstName())
                                 : safeTrim(row.getFirstNameField().getText());

    List<de.zettelkastenfx.bibliography.ui.lookup.AuthorSuggestion> preparedSuggestions =
        prepareBookAuthorSuggestions(suggestions, prefix, firstNameFilter);

    if (preparedSuggestions.isEmpty()) {
      row.getLastNameSuggestionMenu().hide();
      return;
    }

    List<CustomMenuItem> items = new ArrayList<>();
    for (de.zettelkastenfx.bibliography.ui.lookup.AuthorSuggestion suggestion : preparedSuggestions) {
      if (suggestion == null) {
        continue;
      }

      String labelText = buildAuthorSuggestionLabel(suggestion);
      Label label = new Label(labelText);

      CustomMenuItem item = new CustomMenuItem(label, true);
      item.setOnAction(_ -> applyBookAuthorSuggestion(row, suggestion));
      items.add(item);
    }

    if (items.isEmpty()) {
      row.getLastNameSuggestionMenu().hide();
      return;
    }

    row.getLastNameSuggestionMenu().getItems().setAll(items);
    showContextMenuSafely(row.getLastNameSuggestionMenu(), row.getLastNameField());
  }

  /**
   * Bereitet BOOK-Autorenvorschläge für die Anzeige auf.
   * Dabei werden Dubletten über Nachname/Vorname entfernt, optional über den
   * aktuellen Vornamen-Teilstring verfeinert und Präfixtreffer bevorzugt sortiert.
   *
   * @param rawSuggestions rohe Service-Vorschläge
   * @param lastNamePrefix aktueller Nachnamen-Text
   * @param firstNameFilter aktueller Vornamen-Text derselben Zeile
   * @return sortierte und deduplizierte Vorschläge
   */
  private List<de.zettelkastenfx.bibliography.ui.lookup.AuthorSuggestion> prepareBookAuthorSuggestions(
      List<de.zettelkastenfx.bibliography.ui.lookup.AuthorSuggestion> rawSuggestions,
      String lastNamePrefix,
      String firstNameFilter
  ) {
    if (rawSuggestions == null || rawSuggestions.isEmpty()) {
      return List.of();
    }

    String normalizedLastNamePrefix = safeTrim(lastNamePrefix).toLowerCase(Locale.ROOT);
    String normalizedFirstNameFilter = safeTrim(firstNameFilter).toLowerCase(Locale.ROOT);

    Map<String, de.zettelkastenfx.bibliography.ui.lookup.AuthorSuggestion> deduplicated =
        new LinkedHashMap<>();

    for (de.zettelkastenfx.bibliography.ui.lookup.AuthorSuggestion suggestion : rawSuggestions) {
      if (suggestion == null) {
        continue;
      }

      String lastName = safeTrim(suggestion.lastName());
      String firstName = safeTrim(suggestion.firstName());

      if (lastName.isBlank() && firstName.isBlank()) {
        continue;
      }

      if (!normalizedFirstNameFilter.isBlank()
              && !firstName.toLowerCase(Locale.ROOT).contains(normalizedFirstNameFilter)) {
        continue;
      }

      String key = (lastName + "\u0000" + firstName).toLowerCase(Locale.ROOT);
      deduplicated.putIfAbsent(key, suggestion);
    }

    List<de.zettelkastenfx.bibliography.ui.lookup.AuthorSuggestion> prepared =
        new ArrayList<>(deduplicated.values());

    prepared.sort(
        Comparator
            .comparingInt((de.zettelkastenfx.bibliography.ui.lookup.AuthorSuggestion suggestion) ->
                              authorSuggestionRank(suggestion, normalizedLastNamePrefix, normalizedFirstNameFilter))
            .thenComparing(suggestion -> safeTrim(suggestion.lastName()).toLowerCase(Locale.ROOT))
            .thenComparing(suggestion -> safeTrim(suggestion.firstName()).toLowerCase(Locale.ROOT))
    );

    return prepared;
  }

  /**
   * Bewertet einen Autorenvorschlag für die BOOK-Sortierung.
   * Zuerst wird der Nachname gewichtet, danach optional der Vorname.
   * Nachname:
   * 0 = beginnt mit Präfix
   * 1 = enthält Präfix
   * 2 = sonstiger Fallback
   * Vorname:
   * +0 = kein Vornamenfilter oder beginnt mit Filter
   * +1 = enthält Filter
   * +2 = sonstiger Fallback
   *
   * @param suggestion Autorenvorschlag
   * @param normalizedLastNamePrefix normalisiertes Nachnamen-Präfix
   * @param normalizedFirstNameFilter normalisierter Vornamen-Filter
   * @return Sortierrang
   */
  private int authorSuggestionRank(de.zettelkastenfx.bibliography.ui.lookup.AuthorSuggestion suggestion,
                                   String normalizedLastNamePrefix,
                                   String normalizedFirstNameFilter) {
    String lastName = safeTrim(suggestion == null ? "" : suggestion.lastName()).toLowerCase(Locale.ROOT);
    String firstName = safeTrim(suggestion == null ? "" : suggestion.firstName()).toLowerCase(Locale.ROOT);

    int lastNameRank;
    if (normalizedLastNamePrefix == null || normalizedLastNamePrefix.isBlank()) {
      lastNameRank = 2;
    } else if (lastName.startsWith(normalizedLastNamePrefix)) {
      lastNameRank = 0;
    } else if (lastName.contains(normalizedLastNamePrefix)) {
      lastNameRank = 1;
    } else {
      lastNameRank = 2;
    }

    int firstNameRank;
    if (normalizedFirstNameFilter == null || normalizedFirstNameFilter.isBlank()) {
      firstNameRank = 0;
    } else if (firstName.startsWith(normalizedFirstNameFilter)) {
      firstNameRank = 0;
    } else if (firstName.contains(normalizedFirstNameFilter)) {
      firstNameRank = 1;
    } else {
      firstNameRank = 2;
    }

    return lastNameRank * 10 + firstNameRank;
  }

  /**
   * Übernimmt einen Autorenvorschlag in Nachname und Vorname einer Zeile.
   *
   * @param row betroffene Autorenzeile
   * @param suggestion gewählter Vorschlag
   */
  private void applyBookAuthorSuggestion(ZettelWindowView.BookAuthorRow row,
                                         de.zettelkastenfx.bibliography.ui.lookup.AuthorSuggestion suggestion) {
    if (row == null || suggestion == null || row.getLastNameField() == null || row.getFirstNameField() == null) {
      return;
    }

    suppressBookAuthorCallbacks = true;
    try {
      row.getLastNameField().setText(safeTrim(suggestion.lastName()));
      row.getFirstNameField().setText(safeTrim(suggestion.firstName()));
      row.setLastName(safeTrim(suggestion.lastName()));
      row.setFirstName(safeTrim(suggestion.firstName()));
    } finally {
      suppressBookAuthorCallbacks = false;
    }

    if (row.getLastNameSuggestionMenu() != null) {
      row.getLastNameSuggestionMenu().hide();
    }

    ensureBookMultiAuthorTrailingBlankRow();
    requestAsyncBookRefresh();
  }

  /**
   * Baut den Anzeigetext eines Autorenvorschlags.
   *
   * @param suggestion Autorenvorschlag
   * @return Anzeigename
   */
  private String buildAuthorSuggestionLabel(de.zettelkastenfx.bibliography.ui.lookup.AuthorSuggestion suggestion) {
    String lastName = safeTrim(suggestion.lastName());
    String firstName = safeTrim(suggestion.firstName());

    if (!lastName.isBlank() && !firstName.isBlank()) {
      return lastName + ", " + firstName;
    }
    if (!lastName.isBlank()) {
      return lastName;
    }
    return firstName;
  }

  /**
   * Öffnet ein ContextMenu defensiv nur dann direkt, wenn der Anchor bereits
   * an einer Scene und einem Window hängt.
   * Andernfalls wird der Öffnungsversuch auf den nächsten UI-Zyklus verschoben.
   *
   * @param menu anzuzeigendes Menü
   * @param anchor Verankerungsknoten
   */
  private void showContextMenuSafely(ContextMenu menu, Node anchor) {
    if (menu == null || anchor == null) {
      return;
    }

    if (anchor.getScene() == null || anchor.getScene().getWindow() == null) {
      Platform.runLater(() -> {
        if (anchor.getScene() == null || anchor.getScene().getWindow() == null) {
          return;
        }
        if (!anchor.isVisible()) {
          return;
        }
        if (!menu.isShowing()) {
          menu.show(anchor, Side.BOTTOM, 0, 0);
        }
      });
      return;
    }

    if (!menu.isShowing()) {
      menu.show(anchor, Side.BOTTOM, 0, 0);
    }
  }

  /**
   * Fordert eine verzögerte Aktualisierung des BOOK-Fensters an.
   * Mehrere schnelle Texteingaben werden dadurch zu einem Refresh gebündelt.
   */
  private void requestBookRefresh() {
    if (view.getActiveServiceAreaMode() != ZettelWindowView.ServiceAreaMode.BOOK) {
      return;
    }

    bookRefreshDebounce.stop();
    bookRefreshDebounce.playFromStart();
  }

  /**
   * Baut den normalisierten BOOK-Medientyp-Cache aus der Bibliographie-Fassade auf.
   * Gespeichert werden Anzeigename und technischer Medientypname.
   */
  private void rebuildBookMediaTypeCache() {
    bookMediaTypesByNormalizedText.clear();

    for (MediaTypeDefinition type : bibliographyService.listMediaTypes()) {
      if (type == null) {
        continue;
      }

      String normalizedName = safeTrim(type.name()).toLowerCase(Locale.ROOT);
      String normalizedBibName = safeTrim(type.bibName()).toLowerCase(Locale.ROOT);

      if (!normalizedName.isBlank()) {
        bookMediaTypesByNormalizedText.putIfAbsent(normalizedName, type);
      }
      if (!normalizedBibName.isBlank()) {
        bookMediaTypesByNormalizedText.putIfAbsent(normalizedBibName, type);
      }
    }
  }

  /**
   * Prueft einen Eintrag gegen eine vorab gesicherte Autorenfilter-Situation.
   *
   * @param entry bibliographischer Eintrag
   * @param authorMode aktiver BOOK-Autorenmodus
   * @param filters gesicherte Autorenfilter
   * @return {@code true}, wenn alle belegten Autorenfilter passen
   */
  private boolean matchesBookAuthorFilters(
      DynamicBibliographyEntry entry,
      ZettelWindowView.BookAuthorMode authorMode,
      List<BookAuthorFilter> filters
  ) {
    if (filters == null || filters.isEmpty() || authorMode == ZettelWindowView.BookAuthorMode.OFF) {
      return true;
    }

    List<PersonRef> persons = extractPreviewPersons(entry);
    if (persons.isEmpty()) {
      return filters.stream().noneMatch(this::isBookAuthorFilterFilled);
    }

    for (BookAuthorFilter filter : filters) {
      if (!isBookAuthorFilterFilled(filter)) {
        continue;
      }

      boolean rowMatched = persons.stream().anyMatch(person -> matchesBookAuthorFilter(person, filter));
      if (!rowMatched) {
        return false;
      }
    }

    return true;
  }

  /**
   * Prueft, ob ein gesicherter BOOK-Autorenfilter Inhalt enthaelt.
   *
   * @param filter Autorenfilter
   * @return {@code true}, wenn Vor- oder Nachname befuellt ist
   */
  private boolean isBookAuthorFilterFilled(BookAuthorFilter filter) {
    if (filter == null) {
      return false;
    }

    return !safeTrim(filter.lastName()).isBlank() || !safeTrim(filter.firstName()).isBlank();
  }

  /**
   * Zählt die aktuell tatsächlich belegten BOOK-Autorenfilterzeilen.
   *
   * @return Anzahl aktiver Autorenfilter
   */
  private int countActiveBookAuthorFilters() {
    return countActiveBookAuthorFilters(snapshotBookAuthorFilters());
  }

  /**
   * Zaehlt belegte Autorenfilter in einer gesicherten Filtersituation.
   *
   * @param filters gesicherte Autorenfilter
   * @return Anzahl aktiver Autorenfilter
   */
  private int countActiveBookAuthorFilters(List<BookAuthorFilter> filters) {
    int count = 0;

    if (filters == null) {
      return 0;
    }

    for (BookAuthorFilter filter : filters) {
      if (isBookAuthorFilterFilled(filter)) {
        count++;
      }
    }

    return count;
  }

  /**
   * Prüft, ob aktuell ein Titelfilter im BOOK-Fenster aktiv ist.
   *
   * @return {@code true}, wenn das Titelfeld befüllt ist
   */
  private boolean hasActiveBookTitleFilter() {
    return !safeTrim(view.getBookTitleField().getText()).isBlank();
  }

  /**
   * Prüft, ob eine Person zu einem gesicherten BOOK-Autorenfilter passt.
   *
   * @param person Person des Eintrags
   * @param filter Autorenfilter
   * @return {@code true}, wenn die Person passt
   */
  private boolean matchesBookAuthorFilter(PersonRef person, BookAuthorFilter filter) {
    if (person == null || filter == null) {
      return false;
    }

    String personLastName = safeTrim(person.lastName()).toLowerCase(Locale.ROOT);
    String personFirstName = safeTrim(person.firstName()).toLowerCase(Locale.ROOT);
    String filterLastName = safeTrim(filter.lastName()).toLowerCase(Locale.ROOT);
    String filterFirstName = safeTrim(filter.firstName()).toLowerCase(Locale.ROOT);

    if (!filterLastName.isBlank() && !personLastName.contains(filterLastName)) {
      return false;
    }

    return filterFirstName.isBlank() || personFirstName.contains(filterFirstName);
  }

  /**
   * Sichert die aktuellen BOOK-Autorenfilter für eine asynchrone Aktualisierung.
   *
   * @return unveränderliche Filterliste
   */
  private List<BookAuthorFilter> snapshotBookAuthorFilters() {
    List<BookAuthorFilter> filters = new ArrayList<>();
    for (ZettelWindowView.BookAuthorRow row : view.getBookAuthorRowModels()) {
      if (row == null) {
        continue;
      }
      String lastName = row.getLastNameField() == null ? row.getLastName() : row.getLastNameField().getText();
      String firstName = row.getFirstNameField() == null ? row.getFirstName() : row.getFirstNameField().getText();
      filters.add(new BookAuthorFilter(safeTrim(lastName), safeTrim(firstName)));
    }
    return List.copyOf(filters);
  }

  /**
   * Extrahiert alle Personen des wichtigsten Personenfeldes für BOOK-Filterung.
   *
   * @param entry bibliographischer Eintrag
   * @return Personenliste des führenden Personenfeldes
   */
  private List<PersonRef> extractPreviewPersons(DynamicBibliographyEntry entry) {
    if (entry == null || entry.getMediaTypeId() == null || entry.getMediaTypeId() <= 0) {
      return List.of();
    }

    MediaAttributeDefinition personAttribute = bibliographyService
                                                   .listAttributesForMediaType(entry.getMediaTypeId())
                                                   .stream()
                                                   .filter(attribute -> attribute != null && attribute.fieldDefinition() != null)
                                                   .filter(attribute -> isPersonDatatype(attribute.fieldDefinition().datatype()))
                                                   .filter(attribute -> attribute.isIdentify() || attribute.isNecessary())
                                                   .min(Comparator.comparingInt(this::personPreviewPriority)
                                                                  .thenComparingInt(attribute -> attribute.fieldDefinition().id()))
                                                   .orElse(null);

    if (personAttribute == null || personAttribute.fieldDefinition() == null) {
      return List.of();
    }

    String bibtexName = safeTrim(personAttribute.fieldDefinition().bibtexName());
    if (bibtexName.isBlank()) {
      return List.of();
    }

    return entry.findValue(bibtexName)
               .filter(PersonBibValue.class::isInstance)
               .map(PersonBibValue.class::cast)
               .map(PersonBibValue::value)
               .orElse(List.of());
  }

  /**
   * Baut die BOOK-Zetteltabelle aus sichtbaren Medien und einer gesicherten Auswahl auf.
   *
   * @param visibleMediaRows aktuell sichtbare Medienzeilen
   * @param usageRows bereits geladene Bibliographie-Nutzungszeilen
   * @param selectedEntryId gesicherte Medienauswahl
   * @return Zettelzeilen fuer die untere Tabelle
   */
  private List<ZettelWindowView.BookNoteRow> buildBookNoteRows(
      List<ZettelWindowView.BookMediaRow> visibleMediaRows,
      List<NoteRepository.BibliographyUsageRow> usageRows,
      Integer selectedEntryId
  ) {
    Set<Integer> relevantEntryIds = new LinkedHashSet<>();

    if (selectedEntryId != null && selectedEntryId > 0) {
      relevantEntryIds.add(selectedEntryId);
    } else {
      for (ZettelWindowView.BookMediaRow row : visibleMediaRows) {
        if (row != null && row.getEntryId() > 0) {
          relevantEntryIds.add(row.getEntryId());
        }
      }
    }

    if (relevantEntryIds.isEmpty()) {
      return List.of();
    }

    List<ZettelWindowView.BookNoteRow> rows = new ArrayList<>();
    for (NoteRepository.BibliographyUsageRow usageRow : usageRows) {
      if (usageRow == null) {
        continue;
      }

      if (!relevantEntryIds.contains(usageRow.bibliographyEntryId())) {
        continue;
      }

      rows.add(new ZettelWindowView.BookNoteRow(
          usageRow.noteId(),
          safeTrim(usageRow.title())
      ));
    }

    rows.sort(Comparator.comparingInt(ZettelWindowView.BookNoteRow::getNoteId));
    return rows;
  }

  /**
   * Reagiert auf Änderungen im BOOK-Medientypfeld.
   * Bei exakter Auflösung wird der Typ direkt übernommen, sonst nur das
   * Vorschlagsmenü aktualisiert.
   *
   * @param rawText aktueller Feldtext
   */
  private void handleBookMediaTypeTextChanged(String rawText) {
    MediaTypeDefinition resolved = resolveBookMediaType(rawText);
    if (resolved != null) {
      applyBookMediaTypeSelection(resolved);
      return;
    }

    String normalized = rawText == null ? "" : rawText.trim();
    if (normalized.isBlank()) {
      view.setBookResolvedMediaTypeSelection(false, "");
      requestBookRefresh();
      view.getBookMediaTypeMenu().hide();
      return;
    }

    view.setBookResolvedMediaTypeSelection(false, "");
    showBookMediaTypeSuggestions(rawText);
    requestBookRefresh();
  }

  /**
   * Zeigt gefilterte Medientyp-Vorschläge für das BOOK-Feld an.
   * Bei leerem Suchtext bleibt das Menü geschlossen.
   * Treffer mit Präfixpassung werden vor reinen Enthält-Treffern bevorzugt.
   *
   * @param query aktueller Suchtext
   */
  private void showBookMediaTypeSuggestions(String query) {
    String normalized = safeTrim(query).toLowerCase(Locale.ROOT);
    if (normalized.isBlank()) {
      view.getBookMediaTypeMenu().hide();
      return;
    }

    if (bookMediaTypesByNormalizedText.isEmpty()) {
      rebuildBookMediaTypeCache();
    }

    LinkedHashSet<MediaTypeDefinition> prefixHits = new LinkedHashSet<>();
    LinkedHashSet<MediaTypeDefinition> containsHits = new LinkedHashSet<>();

    for (MediaTypeDefinition type : bookMediaTypesByNormalizedText.values()) {
      if (type == null) {
        continue;
      }

      String displayName = safeTrim(type.name()).toLowerCase(Locale.ROOT);
      String bibName = safeTrim(type.bibName()).toLowerCase(Locale.ROOT);

      boolean starts = displayName.startsWith(normalized) || bibName.startsWith(normalized);
      boolean contains = displayName.contains(normalized) || bibName.contains(normalized);

      if (starts) {
        prefixHits.add(type);
      } else if (contains) {
        containsHits.add(type);
      }
    }

    List<MediaTypeDefinition> hits = new ArrayList<>(prefixHits);
    hits.addAll(containsHits);

    if (hits.isEmpty()) {
      view.getBookMediaTypeMenu().hide();
      return;
    }

    List<CustomMenuItem> items = new ArrayList<>();
    for (MediaTypeDefinition type : hits) {
      if (type == null) {
        continue;
      }

      String labelText = safeTrim(type.name());
      if (!safeTrim(type.bibName()).isBlank()
              && !safeTrim(type.bibName()).equalsIgnoreCase(labelText)) {
        labelText = labelText + " (" + safeTrim(type.bibName()) + ")";
      }

      Label label = new Label(labelText);
      CustomMenuItem item = new CustomMenuItem(label, true);
      item.setOnAction(_ -> applyBookMediaTypeSelection(type));
      items.add(item);
    }

    if (items.isEmpty()) {
      view.getBookMediaTypeMenu().hide();
      return;
    }

    view.getBookMediaTypeMenu().getItems().setAll(items);
    showContextMenuSafely(view.getBookMediaTypeMenu(), view.getBookMediaTypeField());
  }

  /**
   * Übernimmt einen ausgewählten BOOK-Medientyp in das Eingabefeld und
   * aktualisiert die BOOK-Ansicht.
   *
   * @param mediaType ausgewählter Medientyp
   */
  private void applyBookMediaTypeSelection(MediaTypeDefinition mediaType) {
    suppressBookMediaTypeCallbacks = true;
    try {
      view.getBookMediaTypeField().setText(mediaType == null ? "" : safeTrim(mediaType.name()));
    } finally {
      suppressBookMediaTypeCallbacks = false;
    }

    if (mediaType == null) {
      view.setBookResolvedMediaTypeSelection(false, "");
    } else {
      view.setBookResolvedMediaTypeSelection(true, mediaType.bibName());
    }

    view.getBookMediaTypeMenu().hide();
    requestAsyncBookRefresh();
  }

  /**
   * Schließt die Texteingabe des BOOK-Medientypfeldes ab.
   * Nur exakte Treffer werden als Auswahl übernommen.
   */
  private void commitBookMediaTypeSelection() {
    MediaTypeDefinition resolved = resolveBookMediaType(view.getBookMediaTypeField().getText());
    if (resolved != null) {
      applyBookMediaTypeSelection(resolved);
      return;
    }

    String currentText = safeTrim(view.getBookMediaTypeField().getText());
    if (currentText.isBlank()) {
      view.setBookResolvedMediaTypeSelection(false, "");
      requestAsyncBookRefresh();
    }
  }

  /**
   * Löst den aktuellen BOOK-Feldtext gegen einen Medientyp auf.
   *
   * @param rawText Eingabetext
   * @return passender Medientyp oder {@code null}
   */
  private MediaTypeDefinition resolveBookMediaType(String rawText) {
    String normalized = safeTrim(rawText).toLowerCase(Locale.ROOT);
    if (normalized.isBlank()) {
      return null;
    }

    if (bookMediaTypesByNormalizedText.isEmpty()) {
      rebuildBookMediaTypeCache();
    }

    return bookMediaTypesByNormalizedText.get(normalized);
  }

  /**
   * Baut den Statustext des BOOK-Fensters passend zur aktuellen Filtersituation.
   *
   * @param mediaRows sichtbare Medien
   * @param noteRows sichtbare Zettel
   * @return Statustext für die BOOK-Zeile
   */
  private String buildBookStatusText(List<ZettelWindowView.BookMediaRow> mediaRows,
                                     List<ZettelWindowView.BookNoteRow> noteRows) {
    int mediaCount = mediaRows == null ? 0 : mediaRows.size();
    int noteCount = noteRows == null ? 0 : noteRows.size();

    String resolvedMediaTypeName = safeTrim(view.getBookMediaTypeField().getText());
    boolean resolvedMediaType = view.hasBookResolvedMediaTypeSelection();
    int activeAuthorFilters = countActiveBookAuthorFilters();
    boolean activeTitleFilter = hasActiveBookTitleFilter();

    List<String> activeFilterParts = new ArrayList<>();
    if (activeTitleFilter) {
      activeFilterParts.add("Titelfilter aktiv");
    }
    if (activeAuthorFilters > 0) {
      activeFilterParts.add(activeAuthorFilters + " Autorenfilter aktiv");
    }

    String filterSuffix = activeFilterParts.isEmpty()
                              ? ""
                              : ", " + String.join(", ", activeFilterParts);

    if (mediaCount == 0) {
      if (resolvedMediaType && !resolvedMediaTypeName.isBlank()) {
        return "Keine Medien gefunden für Medium „" + resolvedMediaTypeName + "“" + filterSuffix;
      }
      return "Keine Medien gefunden" + filterSuffix;
    }

    if (selectedBookEntryId != null && selectedBookEntryId > 0) {
      return mediaCount + " Medien gefunden, "
                 + noteCount + " Zettel zum ausgewählten Medium"
                 + filterSuffix;
    }

    if (resolvedMediaType && !resolvedMediaTypeName.isBlank()) {
      return mediaCount + " Medien gefunden in „" + resolvedMediaTypeName + "“, "
                 + noteCount + " Zettel sichtbar"
                 + filterSuffix;
    }

    return mediaCount + " Medien gefunden, "
               + noteCount + " Zettel sichtbar"
               + filterSuffix;
  }

  private void wireTitleEditing(NoteEditorPane editor) {
    editor.getTitleEditorArea().setEditable(false);
    editor.getTitleEditorArea().setFocusTraversable(false);
    editor.getTitleEditorArea().setMouseTransparent(true);

    editor.getTitleStack().setOnMouseClicked(_ -> beginTitleEdit(editor));

    editor.getTitleEditorArea().focusedProperty().addListener((_, _, newFocused) -> {
      if (!newFocused) {
        commitTitle(editor);
      }
    });

    editor.getTitleEditorArea().setOnKeyPressed(event -> {
      if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.TAB) {
        commitTitle(editor);
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

    editor.getBodyContainer().addEventHandler(MouseEvent.MOUSE_PRESSED, _ -> {
      if (!editor.getBodyContainer().getStyleClass().contains("note-body-active")) {
        editor.getBodyContainer().getStyleClass().add("note-body-active");
      }
      editor.requestBodyEditorFocus();
    });

    editor.getWebBodyEditor().getWebView().focusedProperty().addListener((_, _, newFocused) -> {
      if (newFocused) {
        if (!editor.getBodyContainer().getStyleClass().contains("note-body-active")) {
          editor.getBodyContainer().getStyleClass().add("note-body-active");
        }
      } else {
        editor.getBodyContainer().getStyleClass().remove("note-body-active");
        editor.getFormattingContextMenu().hide();
      }
    });

    editor.getWebBodyEditor().getWebView().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
      if (event.getCode() == KeyCode.X
          && event.isAltDown()
          && !event.isControlDown()
          && !event.isMetaDown()
          && !event.isShiftDown()) {
        editor.toggleBodySourceMode();
        focusBodyForEditing(editor);
        event.consume();
        return;
      }
    });

    editor.getWebBodyEditor().addEventHandler(TinyMceContextMenuEvent.TINY_MCE_CONTEXT_MENU, event -> {
      toggleBodyFormattingContextMenu(editor, event.getScreenX(), event.getScreenY());
      event.consume();
    });

    editor.getWebBodyEditor().getWebView().addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> {
      toggleBodyFormattingContextMenu(editor, event.getScreenX(), event.getScreenY());
      event.consume();
    });

    editor.getBodyContainer().addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> {
      toggleBodyFormattingContextMenu(editor, event.getScreenX(), event.getScreenY());
      event.consume();
    });

    editor.getBodyContainer().addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
      if (event.isPrimaryButtonDown()) {
        editor.getFormattingContextMenu().hide();
      }
    });
  }

  /**
   * Oeffnet oder schliesst das gemeinsame Formatierungsfenster fuer den Body-Editor.
   *
   * @param editor Zettel-Editor
   * @param screenX Bildschirm-X-Position
   * @param screenY Bildschirm-Y-Position
   */
  private void toggleBodyFormattingContextMenu(NoteEditorPane editor, double screenX, double screenY) {
    if (!editor.getBodyContainer().getStyleClass().contains("note-body-active")) {
      editor.getBodyContainer().getStyleClass().add("note-body-active");
    }
    if (editor.getFormattingContextMenu().isShowing()) {
      editor.getFormattingContextMenu().hide();
      return;
    }
    editor.prepareBodyFormattingContext();
    editor.getFormattingContextMenu().show(editor.getWebBodyEditor(), screenX, screenY);
  }

  private void wireGlobalBodyDeactivateHandlers(NoteEditorPane editor) {
    stage.focusedProperty().addListener((_, _, newFocused) -> {
      if (!newFocused) {
        deactivateBody(editor);
        if (stage.getScene() != null) {
          stage.getScene().getRoot().requestFocus();
        }
      }
    });

    stage.sceneProperty().addListener((_, _, newScene) -> {
      if (newScene == null) {
        return;
      }

      newScene.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
        if (!editor.getBodyContainer().getStyleClass().contains("note-body-active")) {
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
    stage.focusedProperty().addListener((_, _, newFocused) -> {
      if (!newFocused) {
        commitTitle(editor);
        if (stage.getScene() != null) {
          stage.getScene().getRoot().requestFocus();
        }
      }
    });

    stage.sceneProperty().addListener((_, _, newScene) -> {
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
    bottom.noteIdField().setOnAction(_ -> handleNoteIdCommit());

    // Klick woanders hin / Fokusverlust -> ebenfalls wechseln
    bottom.noteIdField().focusedProperty().addListener((_, wasFocused, isFocused) -> {
      if (wasFocused && !isFocused) {
        handleNoteIdCommit();
      }
    });

    bottom.backButton().setOnAction(_ -> navigateBack());
    bottom.forwardButton().setOnAction(_ -> navigateForward());

    // BookToggle
    var toggle = view.getBottomToolbar().bookToggle();

    // initialer Zustand
    view.getNoteEditorPane().setBibliographyVisible(toggle.isSelected());

    // Umschalten
    toggle.selectedProperty().addListener((_, _, _) -> renderBibliographyHost(false));
    renderBibliographyHost(false);

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
    linkEditRoot.getStyleClass().add("zettel-link-edit-popup");

    linkEditPopup.setAutoHide(false);
    linkEditPopup.getContent().setAll(linkEditRoot);

    buildLinkEditPopupContent();
  }

  /**
   * Baut den Inhalt des Link-Edit-Popups auf.
   */
  private void buildLinkEditPopupContent() {
    Label heading = new Label("Verweise anlegen und entfernen");
    heading.getStyleClass().add("zettel-link-edit-heading");

    Region headingSpacer = new Region();
    HBox.setHgrow(headingSpacer, Priority.ALWAYS);

    HBox titleBar = new HBox(8, heading, headingSpacer);
    titleBar.getStyleClass().add("zettel-link-edit-title-bar");
    titleBar.setAlignment(Pos.CENTER_LEFT);

    enableLinkEditPopupDragging(titleBar);

    Separator separator = new Separator();

    HBox row1 = new HBox(6);
    row1.getStyleClass().add("zettel-link-edit-row");
    row1.setAlignment(Pos.CENTER_LEFT);

    linkEditNoteIdField = new TextField();
    linkEditNoteIdField.setPromptText("Zettelnummer");
    linkEditNoteIdField.setPrefColumnCount(8);
    linkEditNoteIdField.setPrefWidth(110);
    linkEditNoteIdField.setTextFormatter(new TextFormatter<String>(change -> {
      String next = change.getControlNewText();
      return next.matches("\\d*") ? change : null;
    }));
    linkEditNoteIdField.textProperty().addListener((_, _, _) -> refreshLinkEditSelectionState());
    linkEditNoteIdField.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
      if (event.getCode() == KeyCode.ENTER) {
        refreshLinkEditSelectionState();
        event.consume();
      }
    });
    linkEditNoteIdField.addEventFilter(KeyEvent.KEY_RELEASED, event -> {
      if (event.getCode() == KeyCode.ENTER) {
        event.consume();
      }
    });

    Button btnPrev = BaseIcon.BULLET_ARROW_LEFT.button();
    Button btnNext = BaseIcon.BULLET_ARROW_RIGHT.button();
    Button btnGoSuccessor = BaseIcon.BULLET_GO.button("nächster Folgezettel");
    linkEditReverseButton = BaseIcon.ARROW_REFRESH.button("Verweis umdrehen");
    Button btnClose = BaseIcon.CROSS.button();

    btnPrev.setOnAction(_ -> selectPreviousExistingNoteInPopup());
    btnNext.setOnAction(_ -> selectNextExistingNoteInPopup());
    btnGoSuccessor.setOnAction(_ -> selectNextSuccessorInPopup());
    linkEditReverseButton.setOnAction(_ -> reverseSelectedLinkInPopup());
    btnClose.setOnAction(_ -> closeLinkEditPopup());

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
    row2.getStyleClass().add("zettel-link-edit-meta-row");
    row2.setAlignment(Pos.CENTER_LEFT);
    row2.setPadding(new Insets(2, 0, 2, 0));

    linkEditTitleLabel = new Label("—");
    linkEditTitleLabel.getStyleClass().add("zettel-link-edit-title");
    linkEditTitleLabel.setWrapText(false);
    linkEditTitleLabel.setMaxWidth(Double.MAX_VALUE);
    linkEditTitleLabel.setEllipsisString("…");
    HBox.setHgrow(linkEditTitleLabel, Priority.ALWAYS);

    linkEditStatusLabel = new Label("");
    linkEditStatusLabel.getStyleClass().addAll(
        "zettel-link-edit-status",
        "zettel-link-edit-status-neutral"
    );

    row2.getChildren().addAll(linkEditTitleLabel, linkEditStatusLabel);

    HBox row3 = new HBox(6);
    row3.getStyleClass().add("zettel-link-edit-toggle-row");
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

    linkEditRoot.getChildren().setAll(titleBar, separator, row1, row2, row3);
  }

  /**
   * Macht die Titelzeile des Link-Edit-Popups zum Drag-Bereich.
   * Das Popup kann dadurch per Maus über die Titelzeile verschoben werden.
   *
   * @param dragHandle Knoten, über den das Popup verschoben werden soll
   */
  private void enableLinkEditPopupDragging(Node dragHandle) {
    if (dragHandle == null) {
      return;
    }

    dragHandle.setOnMousePressed(event -> {
      linkEditPopupDragOffsetX = event.getScreenX() - linkEditPopup.getX();
      linkEditPopupDragOffsetY = event.getScreenY() - linkEditPopup.getY();
    });

    dragHandle.setOnMouseDragged(event -> {
      if (!linkEditPopup.isShowing()) {
        return;
      }

      linkEditPopup.setX(event.getScreenX() - linkEditPopupDragOffsetX);
      linkEditPopup.setY(event.getScreenY() - linkEditPopupDragOffsetY);
    });
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
    button.getStyleClass().addAll("icon-button", "zettel-link-type-toggle");
    button.setGraphic(icon.imageView());
    button.setTooltip(new Tooltip(tooltip));
    button.setFocusTraversable(false);
    button.setToggleGroup(group);
    button.setMinWidth(34);
    button.setPrefWidth(34);
    button.setMinHeight(30);
    button.setPrefHeight(30);
    button.setOnAction(_ -> handleLinkTypeToggle(linkType, button.isSelected()));
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
      setLinkEditStatus("Zettelnummer eingeben", LinkEditStatusTone.WARNING);
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
      setLinkEditStatus("Zettel existiert nicht", LinkEditStatusTone.ERROR);
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
      setLinkEditStatus("Selbstverweis ist nicht erlaubt", LinkEditStatusTone.ERROR);
      selectLinkTypeToggle(null);
      setLinkTypeButtonsDisabled(true);
      if (linkEditReverseButton != null) {
        linkEditReverseButton.setDisable(true);
      }
      return;
    }

    if (hasIncomingLinkFrom(selectedNoteId)) {
      setLinkEditStatus("eingehender Verweis", LinkEditStatusTone.WARNING);
      selectLinkTypeToggle(null);
      setLinkTypeButtonsDisabled(true);
      if (linkEditReverseButton != null) {
        linkEditReverseButton.setDisable(true);
      }
      return;
    }

    clearLinkEditStatus();
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
      setLinkEditStatus("Keine Folgezettel vorhanden", LinkEditStatusTone.WARNING);
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
      setLinkEditStatus("Zettelnummer eingeben", LinkEditStatusTone.WARNING);
      return;
    }

    if (!noteRepository.existsNote(selectedNoteId)) {
      setLinkEditStatus("Zettel existiert nicht", LinkEditStatusTone.ERROR);
      return;
    }

    if (selectedNoteId == currentNote.getId()) {
      setLinkEditStatus("Selbstverweis ist nicht erlaubt", LinkEditStatusTone.ERROR);
      return;
    }

    if (hasIncomingLinkFrom(selectedNoteId)) {
      setLinkEditStatus("eingehender Verweis", LinkEditStatusTone.WARNING);
      return;
    }

    LinkType currentType = findCurrentLinkTypeTo(selectedNoteId);
    if (currentType == null) {
      setLinkEditStatus("Kein Verweis zum Umdrehen vorhanden", LinkEditStatusTone.WARNING);
      return;
    }

    boolean reversed = noteRepository.reverseSuccessorLink(currentNote.getId(), selectedNoteId);
    if (!reversed) {
      setLinkEditStatus("Verweis konnte nicht umgedreht werden", LinkEditStatusTone.ERROR);
      return;
    }

    refreshCurrentSuccessorsFromRepository();
    syncInfoPopup(currentNote);
    refreshLinkEditSelectionState();
    refreshMagnifyLinkWindow();

    setLinkEditStatus("Verweis wurde umgedreht", LinkEditStatusTone.NEUTRAL);
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
      setLinkEditStatus("Selbstverweis ist nicht erlaubt", LinkEditStatusTone.ERROR);
      selectLinkTypeToggle(null);
      setLinkTypeButtonsDisabled(true);
      return;
    }

    if (hasIncomingLinkFrom(targetId)) {
      setLinkEditStatus("eingehender Verweis", LinkEditStatusTone.WARNING);
      selectLinkTypeToggle(null);
      setLinkTypeButtonsDisabled(true);
      if (linkEditReverseButton != null) {
        linkEditReverseButton.setDisable(true);
      }
      return;
    }

    if (!selected) {
      LinkType currentType = findCurrentLinkTypeTo(targetId);
      if (currentType != null && currentType == linkType) {
        noteRepository.removeSuccessorLink(currentNote.getId(), targetId);
        refreshCurrentSuccessorsFromRepository();
        syncInfoPopup(currentNote);
        refreshMagnifyLinkWindow();
      }
      refreshLinkEditSelectionState();
      return;
    }

    noteRepository.addSuccessorLink(currentNote.getId(), targetId, linkType);
    refreshCurrentSuccessorsFromRepository();
    syncInfoPopup(currentNote);
    refreshMagnifyLinkWindow();
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
   * Prüft, ob vom ausgewählten Zettel bereits ein eingehender Verweis
   * auf den aktuell geöffneten Zettel existiert.
   *
   * @param sourceNoteId potenzieller Quellzettel des eingehenden Verweises
   * @return {@code true}, wenn ein eingehender Verweis auf den aktuellen Zettel existiert
   */
  private boolean hasIncomingLinkFrom(int sourceNoteId) {
    if (currentNote == null || currentNote.getId() <= 0 || sourceNoteId <= 0) {
      return false;
    }

    List<NoteReferenceInfo> incomingReferences =
        noteRepository.loadIncomingReferenceInfos(currentNote.getId());

    for (NoteReferenceInfo reference : incomingReferences) {
      if (reference != null && reference.noteId() == sourceNoteId) {
        return true;
      }
    }

    return false;
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

  /**
   * Setzt Text und Farbstil der Statusanzeige im Link-Edit-Popup.
   *
   * @param text anzuzeigender Text
   * @param tone gewünschter Farbton
   */
  private void setLinkEditStatus(String text, LinkEditStatusTone tone) {
    if (linkEditStatusLabel == null) {
      return;
    }

    linkEditStatusLabel.setText(text == null ? "" : text);
    linkEditStatusLabel.getStyleClass().removeAll(
        "zettel-link-edit-status-neutral",
        "zettel-link-edit-status-warning",
        "zettel-link-edit-status-error"
    );

    String toneClass = switch (tone) {
      case WARNING -> "zettel-link-edit-status-warning";
      case ERROR -> "zettel-link-edit-status-error";
      case NEUTRAL -> "zettel-link-edit-status-neutral";
    };

    linkEditStatusLabel.getStyleClass().add(toneClass);
  }

  /**
   * Leert die Statusanzeige im Link-Edit-Popup und setzt sie auf neutral zurück.
   */
  private void clearLinkEditStatus() {
    setLinkEditStatus("", LinkEditStatusTone.NEUTRAL);
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
    renderBibliographyHost(false);
    syncBottomToolbar(currentNote);
    view.getNoteEditorPane().setBibliographyVisible(
        view.getBottomToolbar().bookToggle().isSelected()
    );
    if (view.getActiveServiceAreaMode() == ZettelWindowView.ServiceAreaMode.KEY) {
      refreshKeyWindow();
    }
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
    editor.getFormattingContextMenu().hide();
    saveCurrentIfValid();
  }

  private void refreshKeywordCounts() {
    var counts = noteRepository.loadKeywordCounts();
    view.getKeywordsTablePane().applyCounts(counts);
  }

  /**
   * Aktualisiert beide Schlagwort-Ansichten:
   * die linke Schlagwortliste des aktuellen Zettels (Zählspalte)
   * sowie das KEY-Fenster in der Service-Area.
   */
  private void refreshKeywordViews() {
    refreshKeywordCounts();
    refreshKeyWindow();
  }

  /**
   * Synchronisiert die Schlagwörter des aktuell sichtbaren Zettels nach einer
   * globalen Umbenennung oder Zusammenführung aus dem KEY-Fenster.
   * Dabei werden nur die Keywords im linken Schlagwortbereich angepasst;
   * andere Inhalte des Zettels bleiben unberührt.
   *
   * @param sourceKeyword altes bzw. zu entfernendes Schlagwort
   * @param targetKeyword neues bzw. beizubehaltendes Schlagwort;
   *                      bei {@code null} oder Leerstring wird das alte nur entfernt
   */
  private void syncCurrentNoteKeywordsAfterRepositoryChange(String sourceKeyword, String targetKeyword) {
    String normalizedSource = normalizeKeyword(sourceKeyword);
    String normalizedTarget = normalizeKeyword(targetKeyword);

    if (normalizedSource.isBlank()) {
      return;
    }

    List<String> currentKeywords = new ArrayList<>(view.getKeywordsTablePane().getKeywords());
    List<String> updatedKeywords = new ArrayList<>();
    boolean targetAlreadyPresent = false;

    for (String keyword : currentKeywords) {
      String normalizedKeyword = normalizeKeyword(keyword);

      if (normalizedKeyword.equalsIgnoreCase(normalizedSource)) {
        if (!normalizedTarget.isBlank()) {
          if (!containsKeywordIgnoreCase(updatedKeywords, normalizedTarget)) {
            updatedKeywords.add(normalizedTarget);
            targetAlreadyPresent = true;
          }
        }
        continue;
      }

      updatedKeywords.add(keyword);

      if (!normalizedTarget.isBlank() && normalizedKeyword.equalsIgnoreCase(normalizedTarget)) {
        targetAlreadyPresent = true;
      }
    }

    if (!normalizedTarget.isBlank()
            && containsKeywordIgnoreCase(currentKeywords, normalizedSource)
            && !targetAlreadyPresent
            && !containsKeywordIgnoreCase(updatedKeywords, normalizedTarget)) {
      updatedKeywords.add(normalizedTarget);
    }

    view.getKeywordsTablePane().setKeywords(updatedKeywords);
    currentNote.setKeywords(updatedKeywords);
    refreshKeywordCounts();
  }

  /**
   * Prüft, ob eine Keyword-Liste einen bestimmten Eintrag bereits
   * case-insensitiv enthält.
   *
   * @param keywords zu prüfende Liste
   * @param keyword gesuchtes Schlagwort
   * @return {@code true}, wenn bereits enthalten
   */
  private boolean containsKeywordIgnoreCase(List<String> keywords, String keyword) {
    String normalizedKeyword = normalizeKeyword(keyword);
    if (normalizedKeyword.isBlank()) {
      return false;
    }

    for (String entry : keywords) {
      if (normalizeKeyword(entry).equalsIgnoreCase(normalizedKeyword)) {
        return true;
      }
    }
    return false;
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
    return Math.min(requested, maxNoteId);
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

  /**
   * Oeffnet einen Zettel aus dem LIST-Fenster.
   * Bei aktiver LIST-Sperre wird nur das Zettelfenster gewechselt; die
   * serviceabhaengigen LIST- und Verweisansichten bleiben unveraendert.
   *
   * @param id ID des zu oeffnenden Zettels
   */
  private void openNoteFromList(int id) {
    boolean locked = view.getListLockToggleButton().isSelected();
    if (locked) {
      listRefreshDebounce.stop();
    }
    boolean previous = openingNoteFromLockedList;
    openingNoteFromLockedList = locked;
    try {
      openNoteViaToolbar(id);
    } finally {
      openingNoteFromLockedList = previous;
    }
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
      renderBibliographyHost(false);

      // BottomToolbar befüllen (nur Anzeige, Feld bleibt editierbar)
      setNoteIdField(currentNote.getId());

      // Counts nach dem Laden sofort aktualisieren
      refreshKeywordCounts();

      view.getNoteEditorPane().setOnNavigateToNote(this::openNoteViaToolbar);
      view.getNoteEditorPane().setBibliographyVisible(view.getBottomToolbar().bookToggle().isSelected());
      if (!openingNoteFromLockedList) {
        resetMagnifyBranchCollapseState();
        refreshMagnifyLinkWindow();
        refreshListWindowAfterNoteActivation();
      }
      if (view.getActiveServiceAreaMode() == ZettelWindowView.ServiceAreaMode.KEY) {
        refreshKeyWindow();
      }
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

    int current = currentNote.getId();
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

    int current = currentNote.getId();
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
    String bodyPlain = normalize(editor.getBodyPlainText());

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

    setNoteIdField(currentNote.getId());
    updateHistoryButtons();
  }

  /**
   * Legt einen neuen Folgezettel mit dem angegebenen Linktyp an.
   * Zuerst wird der aktuelle Zettel gespeichert. Anschließend wird am
   * aktuellen Zettel sofort ein Verweis auf die neue Ziel-ID angelegt.
   * Danach wird der neue Zielzettel als leerer Entwurf geöffnet.
   * <p>
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
    refreshMagnifyLinkWindow();
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

    if (noteExists) {
      boolean deletedBecauseLast = noteRepository.deleteNoteIfItIsLast(currentId);
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
            || source.getBibliographyRefId() == null
            || source.getBibliographyRefId() <= 0) {
      target.setBibliographyRefId(null);
      return;
    }

    target.setBibliographyRefId(source.getBibliographyRefId());
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
    String bodyPlain = editor.getBodyPlainText() == null ? "" : editor.getBodyPlainText().trim();

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

    if (openingNoteFromLockedList) {
      return;
    }

    setNoteIdField(savedId);
    refreshKeywordCounts();

    refreshCurrentSuccessorsFromRepository();
    syncInfoPopup(currentNote);
    refreshMagnifyLinkWindow();
    refreshListWindow();
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
    String bodyPlain = editor.getBodyPlainText() == null ? "" : editor.getBodyPlainText().trim();

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
    editor.requestBodyEditorFocus();
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

  /**
   * Erzeugt eine Bibliographie-Shell für den Zettelkontext.
   *
   * @return konfigurierte Bibliographie-Shell
   */
  private BibliographyEditorShell createBibliographyShell() {
    BibliographyEditorShellFactory.ShellBundle bundle = bibliographyShellFactory.create(
        new BibliographyEditorShellFactory.ShellCallbacks() {
          @Override
          public void onSave(BibliographyEditorShell shell) {
            saveAndLinkBibliography(shell);
          }

          @Override
          public void onDelete(BibliographyEditorShell shell) {
            unlinkCurrentBibliography();
          }

          @Override
          public void onNoneSelected(BibliographyEditorShell shell) {
            unlinkCurrentBibliography();
          }

          @Override
          public void onRequestCreateRelated(BibliographyEditorShell shell,
                                             String relatedBibtexName,
                                             String targetMediaTypeBibName,
                                             String displayText) {
            openRelatedEditor(shell, relatedBibtexName, targetMediaTypeBibName, displayText);
          }
        }
    );

    bundle.shell().configureForNoteLinkingContext();
    bundle.shell().setOnCloseEditor(() -> renderBibliographyHost(false));
    return bundle.shell();
  }

  private void showBibliographyShell(BibliographyEditorShell shell) {
    var host = view.getNoteEditorPane().getBibliographyHost();
    activeEmbeddedBibliographyShell = shell;
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

  /**
   * Zeigt einen Hinweis an, dass unter dem Zettel für den gewählten Medientyp
   * kein neuer Eintrag angelegt werden darf.
   *
   * @param typeLabel Anzeigename des Medientyps
   */
  private void showUnderNoteSelectionOnlyWarning(String typeLabel) {
    String effectiveTypeLabel = safeTrim(typeLabel);
    if (effectiveTypeLabel.isBlank()) {
      effectiveTypeLabel = "Dieser Medientyp";
    }

    showBibliographyWarning(
        effectiveTypeLabel,
        effectiveTypeLabel
            + " kann unter dem Zettel nicht als neuer Eintrag angelegt werden.\n\n"
            + "Bitte wähle hier einen bereits vorhandenen Eintrag aus.\n"
            + "Neue Einträge sind in diesem Bereich derzeit nur für Internet- und KI-Texte erlaubt."
    );
  }

  /**
   * Fragt bei einem potenziellen Dublettenfund, wie gespeichert werden soll.
   *
   * @param duplicates gefundene bestehende Einträge
   * @return Nutzerentscheidung
   */
  private DuplicateBibliographyDecision askDuplicateBibliographyDecision(
      List<DynamicBibliographyEntry> duplicates
  ) {
    ButtonType updateExisting = new ButtonType("Bestehenden Eintrag aktualisieren", ButtonBar.ButtonData.YES);
    ButtonType saveAsNew = new ButtonType("Als neuen Eintrag speichern", ButtonBar.ButtonData.OTHER);
    ButtonType cancel = new ButtonType("Nicht speichern", ButtonBar.ButtonData.CANCEL_CLOSE);

    String duplicateText = duplicates.stream()
                               .map(entry -> bibliographyService.loadReference(entry.getId())
                                                 .map(BibliographyReference::displayText)
                                                 .orElse("Eintrag #" + entry.getId()))
                               .filter(text -> !text.isBlank())
                               .distinct()
                               .limit(5)
                               .reduce((a, b) -> a + "\n• " + b)
                               .map(text -> "• " + text)
                               .orElse("• Bestehender Eintrag");

    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
    alert.initOwner(stage);
    alert.setTitle("Bibliographische Angabe");
    alert.setHeaderText("Es existiert bereits ein passender Eintrag");
    alert.setContentText(
        "Es wurden bestehende Einträge mit denselben Identifikationsmerkmalen gefunden:\n\n"
            + duplicateText
            + "\n\nWie soll verfahren werden?"
    );
    alert.getButtonTypes().setAll(updateExisting, saveAsNew, cancel);

    Optional<ButtonType> result = alert.showAndWait();
    if (result.isEmpty() || result.get() == cancel) {
      return DuplicateBibliographyDecision.CANCEL;
    }
    if (result.get() == updateExisting) {
      return DuplicateBibliographyDecision.UPDATE_EXISTING;
    }
    return DuplicateBibliographyDecision.SAVE_AS_NEW;
  }

  /**
   * Fragt nach, wie mit einem bereits mit dem aktuellen Zettel verknüpften
   * Bibliographie-Eintrag verfahren werden soll.
   *
   * @param linkedEntryId ID des aktuell verknüpften Eintrags
   * @return Nutzerentscheidung
   */
  private LinkedBibliographyDecision askLinkedBibliographyDecision(Integer linkedEntryId) {
    ButtonType updateExisting = new ButtonType("Verknüpften Eintrag überschreiben", ButtonBar.ButtonData.YES);
    ButtonType saveAsNew = new ButtonType("Als neuen Eintrag speichern", ButtonBar.ButtonData.OTHER);
    ButtonType cancel = new ButtonType("Nicht speichern", ButtonBar.ButtonData.CANCEL_CLOSE);

    String linkedText = bibliographyService.loadReference(linkedEntryId == null ? 0 : linkedEntryId)
                            .map(BibliographyReference::displayText)
                            .filter(text -> !text.isBlank())
                            .orElse(linkedEntryId == null ? "Bestehender Eintrag" : "Eintrag #" + linkedEntryId);

    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
    alert.initOwner(stage);
    alert.setTitle("Bibliographische Angabe");
    alert.setHeaderText("Der Zettel ist bereits mit einem bibliographischen Eintrag verknüpft");
    alert.setContentText(
        "Der aktuell verknüpfte Eintrag lautet:\n\n• " + linkedText
            + "\n\nSoll dieser bestehende Eintrag überschrieben werden,"
            + "\noder soll aus den Änderungen ein neuer Eintrag entstehen?"
    );
    alert.getButtonTypes().setAll(updateExisting, saveAsNew, cancel);

    Optional<ButtonType> result = alert.showAndWait();
    if (result.isEmpty() || result.get() == cancel) {
      return LinkedBibliographyDecision.CANCEL;
    }
    if (result.get() == updateExisting) {
      return LinkedBibliographyDecision.UPDATE_LINKED_ENTRY;
    }
    return LinkedBibliographyDecision.SAVE_AS_NEW;
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

  /**
   * Erzeugt den roten Bibliographie-Button im Vorschauzustand unter dem Zettel.
   * Der Button erscheint nur als Grafik, mit transparentem Hintergrund und ohne Rand.
   *
   * @return konfigurierte Schaltfläche zum Öffnen des Editors
   */
  private Button createCompactBibliographyOpenButton() {
    Button button = new Button();
    button.setGraphic(BaseIcon.BULLET_RED.imageView());
    button.setTooltip(new Tooltip("Bibliographische Angabe bearbeiten"));
    button.setFocusTraversable(false);
    button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    button.setMinWidth(18);
    button.setPrefWidth(18);
    button.setMaxWidth(18);
    button.setMinHeight(18);
    button.setPrefHeight(18);
    button.setMaxHeight(18);
    button.setStyle("-fx-background-color: transparent; -fx-padding: 0; -fx-border-color: transparent;");
    button.setOnAction(e -> showBibliographyEditor());
    return button;
  }

  private void showCreateBibliographyButton() {
    var host = view.getNoteEditorPane().getBibliographyHost();
    host.getChildren().clear();

    Button btnCreate = createCompactBibliographyOpenButton();

    HBox row = new HBox(btnCreate);
    row.setAlignment(Pos.CENTER_LEFT);
    row.setMaxWidth(Double.MAX_VALUE);
    host.getChildren().add(row);
  }

  private void showUnresolvedBibliographyButton() {
    var host = view.getNoteEditorPane().getBibliographyHost();
    host.getChildren().clear();

    Button btnOpen = createCompactBibliographyOpenButton();

    HBox row = new HBox(btnOpen);
    row.setAlignment(Pos.CENTER_LEFT);
    row.setMaxWidth(Double.MAX_VALUE);
    host.getChildren().add(row);
  }

  /**
   * Rendert eine kompakte Anzeige der aktuell verknüpften bibliographischen
   * Angabe unterhalb des Zettels.
   * Die Vorschau zeigt einen roten Bearbeiten-Button direkt vor dem Text und
   * verwendet im Lesemodus einen dezenten grauen Hintergrund.
   *
   * @param entry geladener bibliographischer Eintrag
   */
  private void showCompactBibliographyPreview(DynamicBibliographyEntry entry) {
    var host = view.getNoteEditorPane().getBibliographyHost();
    host.getChildren().clear();

    if (isWebsiteEntry(entry)) {
      host.getChildren().add(buildWebsitePreviewRow(entry));
      return;
    }

    if (isAiTextEntry(entry)) {
      host.getChildren().add(buildAiTextPreviewRow(entry));
      return;
    }

    HBox row = new HBox(8);
    row.setAlignment(Pos.CENTER_LEFT);
    row.setMaxWidth(Double.MAX_VALUE);
    row.setStyle("-fx-background-color: #D3D3D3; -fx-padding: 6 8 6 8;");

    Button openButton = createCompactBibliographyOpenButton();

    String previewText = buildBibliographyPreviewText(entry);
    Label previewLabel = new Label(previewText);
    previewLabel.getStyleClass().add("biblio-preview-text");
    previewLabel.setWrapText(true);
    previewLabel.setMaxWidth(Double.MAX_VALUE);
    HBox.setHgrow(previewLabel, Priority.ALWAYS);

    row.getChildren().addAll(openButton, previewLabel);
    host.getChildren().add(row);
  }

  /**
   * Prüft, ob es sich beim Eintrag um einen Internet-/Website-Eintrag handelt.
   *
   * @param entry bibliographischer Eintrag
   * @return {@code true}, wenn der Eintrag ein Website-Eintrag ist
   */
  private boolean isWebsiteEntry(DynamicBibliographyEntry entry) {
    if (entry == null) {
      return false;
    }

    String mediaTypeBibName = safeTrim(entry.getMediaTypeBibName()).toLowerCase(Locale.ROOT);
    return "website".equals(mediaTypeBibName);
  }

  /**
   * Prüft, ob es sich beim Eintrag um einen KI-Text-Eintrag handelt.
   *
   * @param entry bibliographischer Eintrag
   * @return {@code true}, wenn der Eintrag ein KI-Text-Eintrag ist
   */
  private boolean isAiTextEntry(DynamicBibliographyEntry entry) {
    if (entry == null) {
      return false;
    }

    String mediaTypeBibName = safeTrim(entry.getMediaTypeBibName()).toLowerCase(Locale.ROOT);
    return "aitext".equals(mediaTypeBibName);
  }

  /**
   * Baut die kompakte Vorschauzeile für einen Website-Eintrag unter dem Zettel.
   * Angezeigt werden roter Bearbeiten-Button, Titel oder URL sowie rechts ein
   * Browser-Button.
   *
   * @param entry Website-Eintrag
   * @return UI-Zeile für die Vorschau
   */
  private HBox buildWebsitePreviewRow(DynamicBibliographyEntry entry) {
    HBox row = new HBox(8);
    row.setAlignment(Pos.CENTER_LEFT);
    row.setMaxWidth(Double.MAX_VALUE);
    row.setStyle("-fx-background-color: #D3D3D3; -fx-padding: 6 8 6 8;");

    Button openButton = createCompactBibliographyOpenButton();

    String title = resolvePreviewTitle(entry);
    String url = resolveWebsiteUrl(entry);
    String displayText = !title.isBlank() ? title : url;

    Label previewLabel = new Label(displayText);
    previewLabel.getStyleClass().add("biblio-preview-text");
    previewLabel.setWrapText(false);
    previewLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
    previewLabel.setMaxWidth(Double.MAX_VALUE);
    HBox.setHgrow(previewLabel, Priority.ALWAYS);

    Button browserButton = BaseIcon.WWW_PAGE.button("Website öffnen");
    browserButton.setFocusTraversable(false);
    browserButton.setDisable(url.isBlank());
    browserButton.setOnAction(e -> openWebsiteInBrowser(url));

    row.getChildren().addAll(openButton, previewLabel, browserButton);
    return row;
  }

  /**
   * Baut die kompakte Vorschauzeile für einen KI-Text-Eintrag unter dem Zettel.
   * Angezeigt werden roter Bearbeiten-Button sowie zwei kurze Zeilen:
   * oben der Titel, unten Anbieter / Modell / Datum.
   *
   * @param entry KI-Text-Eintrag
   * @return UI-Zeile für die Vorschau
   */
  private HBox buildAiTextPreviewRow(DynamicBibliographyEntry entry) {
    HBox row = new HBox(8);
    row.setAlignment(Pos.TOP_LEFT);
    row.setMaxWidth(Double.MAX_VALUE);
    row.setStyle("-fx-background-color: #D3D3D3; -fx-padding: 6 8 6 8;");

    Button openButton = createCompactBibliographyOpenButton();

    VBox textBox = new VBox(2);
    textBox.setAlignment(Pos.CENTER_LEFT);
    textBox.setMaxWidth(Double.MAX_VALUE);
    HBox.setHgrow(textBox, Priority.ALWAYS);

    Label titleLabel = new Label(resolveBibliographyShell().buildAiPreviewTitle(entry));
    titleLabel.getStyleClass().add("biblio-preview-text");
    titleLabel.setWrapText(false);
    titleLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
    titleLabel.setMaxWidth(Double.MAX_VALUE);

    Label metaLabel = new Label(resolveBibliographyShell().buildAiPreviewMeta(entry));
    metaLabel.getStyleClass().add("biblio-preview-text");
    metaLabel.setWrapText(false);
    metaLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
    metaLabel.setMaxWidth(Double.MAX_VALUE);

    textBox.getChildren().addAll(titleLabel, metaLabel);
    row.getChildren().addAll(openButton, textBox);
    return row;
  }

  /**
   * Liefert eine temporäre Bibliographie-Shell als Träger der
   * bibliographischen Vorschau- und Datumslogik.
   *
   * @return konfigurierte Bibliographie-Shell
   */
  private BibliographyEditorShell resolveBibliographyShell() {
    BibliographyEditorShell shell = new BibliographyEditorShell();
    shell.setBibliographyService(bibliographyService);
    return shell;
  }

  /**
   * Ermittelt die URL eines Website-Eintrags.
   *
   * @param entry bibliographischer Eintrag
   * @return URL oder leerer String
   */
  private String resolveWebsiteUrl(DynamicBibliographyEntry entry) {
    if (entry == null) {
      return "";
    }

    return entry.findValue("url")
               .filter(StringBibValue.class::isInstance)
               .map(StringBibValue.class::cast)
               .map(StringBibValue::value)
               .map(this::safeTrim)
               .filter(text -> !text.isBlank())
               .orElse("");
  }

  /**
   * Öffnet eine URL im Systembrowser.
   *
   * @param url zu öffnende URL
   */
  private void openWebsiteInBrowser(String url) {
    String normalizedUrl = safeTrim(url);
    if (normalizedUrl.isBlank()) {
      return;
    }

    try {
      if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
        showBibliographyWarning(
            "Browser nicht verfügbar",
            "Die URL kann auf diesem System nicht direkt im Browser geöffnet werden."
        );
        return;
      }

      Desktop.getDesktop().browse(URI.create(normalizedUrl));
    } catch (Exception ex) {
      showBibliographyError(
          "URL konnte nicht geöffnet werden",
          "Die Website konnte nicht im Browser geöffnet werden:\n\n" + normalizedUrl
      );
    }
  }

  /**
   * Erzeugt den kompakten Vorschautext für die bibliographische Anzeige unter
   * dem Zettel.
   * Bevorzugt wird die erste Person des wichtigsten Personenfeldes sowie der
   * Titel. Fehlt beides oder ist der Datensatz unvollständig, wird defensiv auf
   * den Referenztext zurückgegriffen.
   *
   * @param entry bibliographischer Eintrag
   * @return kompakter Vorschautext
   */
  private String buildBibliographyPreviewText(DynamicBibliographyEntry entry) {
    if (entry == null) {
      return "";
    }

    String personText = resolvePreviewPerson(entry);
    String titleText = resolvePreviewTitle(entry);

    if (!personText.isBlank() && !titleText.isBlank()) {
      return personText + ": " + titleText;
    }
    if (!titleText.isBlank()) {
      return titleText;
    }
    if (!personText.isBlank()) {
      return personText;
    }

    if (entry.getId() != null && entry.getId() > 0) {
      return bibliographyService.loadReference(entry.getId())
                 .map(BibliographyReference::displayText)
                 .map(this::safeTrim)
                 .filter(text -> !text.isBlank())
                 .orElse("Bibliographische Angabe #" + entry.getId());
    }

    return "Bibliographische Angabe";
  }

  /**
   * Ermittelt den Titel für die kompakte Vorschau.
   *
   * @param entry bibliographischer Eintrag
   * @return Titel oder leerer String
   */
  private String resolvePreviewTitle(DynamicBibliographyEntry entry) {
    if (entry == null) {
      return "";
    }

    return entry.findValue("title")
               .filter(StringBibValue.class::isInstance)
               .map(StringBibValue.class::cast)
               .map(StringBibValue::value)
               .map(this::safeTrim)
               .filter(text -> !text.isBlank())
               .orElse("");
  }

  /**
   * Ermittelt die erste Person des wichtigsten Personenfeldes für die kompakte
   * Vorschau unter dem Zettel.
   * Vorrang hat ein Personenfeld mit {@code identify}, danach eines mit
   * {@code necessary}. Bei Gleichstand gewinnt die kleinere BibTeX-Feld-ID.
   *
   * @param entry bibliographischer Eintrag
   * @return formatierte Person oder leerer String
   */
  private String resolvePreviewPerson(DynamicBibliographyEntry entry) {
    if (entry == null || entry.getMediaTypeId() == null || entry.getMediaTypeId() <= 0) {
      return "";
    }

    MediaAttributeDefinition personAttribute = bibliographyService
                                                   .listAttributesForMediaType(entry.getMediaTypeId())
                                                   .stream()
                                                   .filter(attribute -> attribute != null && attribute.fieldDefinition() != null)
                                                   .filter(attribute -> isPersonDatatype(attribute.fieldDefinition().datatype()))
                                                   .filter(attribute -> attribute.isIdentify() || attribute.isNecessary())
                                                   .sorted(
                                                       Comparator
                                                           .comparingInt(this::personPreviewPriority)
                                                           .thenComparingInt(attribute -> attribute.fieldDefinition().id())
                                                   )
                                                   .findFirst()
                                                   .orElse(null);

    if (personAttribute == null) {
      return "";
    }

    String bibtexName = safeTrim(personAttribute.fieldDefinition().bibtexName());
    if (bibtexName.isBlank()) {
      return "";
    }

    return entry.findValue(bibtexName)
               .filter(PersonBibValue.class::isInstance)
               .map(PersonBibValue.class::cast)
               .map(PersonBibValue::value)
               .filter(persons -> persons != null && !persons.isEmpty())
               .map(persons -> persons.getFirst())
               .map(this::formatPreviewPerson)
               .orElse("");
  }

  /**
   * Liefert die Sortierpriorität eines Personenfeldes für die kompakte
   * Bibliographie-Vorschau.
   *
   * @param attribute Attributdefinition
   * @return Sortierpriorität
   */
  private int personPreviewPriority(MediaAttributeDefinition attribute) {
    if (attribute == null) {
      return Integer.MAX_VALUE;
    }
    if (attribute.isIdentify()) {
      return 0;
    }
    if (attribute.isNecessary()) {
      return 1;
    }
    return Integer.MAX_VALUE;
  }

  /**
   * Prüft, ob ein Metadaten-Datentyp als Personenfeld behandelt wird.
   *
   * @param datatype Metadaten-Datentyp
   * @return {@code true}, wenn es sich um ein Personenfeld handelt
   */
  private boolean isPersonDatatype(String datatype) {
    return "person".equalsIgnoreCase(safeTrim(datatype));
  }

  /**
   * Formatiert eine Person für die kompakte Vorschau in der Form
   * "Nachname, Vorname".
   *
   * @param person Personenreferenz
   * @return formatierter Name oder leerer String
   */
  private String formatPreviewPerson(PersonRef person) {
    if (person == null) {
      return "";
    }

    String lastName = safeTrim(person.lastName());
    String firstName = safeTrim(person.firstName());

    if (lastName.isBlank() && firstName.isBlank()) {
      return "";
    }
    if (lastName.isBlank()) {
      return firstName;
    }
    if (firstName.isBlank()) {
      return lastName;
    }

    return lastName + ", " + firstName;
  }

  /**
   * Trimmt einen Text defensiv.
   *
   * @param value Eingabetext
   * @return getrimmter Text oder leerer String
   */
  private String safeTrim(String value) {
    return value == null ? "" : value.trim();
  }

  /**
   * Prüft, ob unter dem Zettel für einen Medientyp überhaupt ein neuer
   * bibliographischer Eintrag angelegt werden darf.
   * Im aktuellen Ausbau ist dies nur für Internet- und KI-Einträge erlaubt.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @return {@code true}, wenn neue Einträge erlaubt sind
   */
  private boolean isCreatableUnderNote(String mediaTypeBibName) {
    String normalized = safeTrim(mediaTypeBibName).toLowerCase(Locale.ROOT);
    return "website".equals(normalized) || "aitext".equals(normalized);
  }

  /**
   * Prüft, ob die Shell aktuell bereits auf einen bestehenden
   * bibliographischen Eintrag zeigt.
   * Dafür werden sowohl die aktuelle Selektion als auch die Ursprungs-ID des
   * geladenen Formulars berücksichtigt.
   *
   * @param shell Bibliographie-Shell
   * @return {@code true}, wenn ein bestehender Eintrag referenziert wird
   */
  private boolean pointsToExistingBibliographyEntry(BibliographyEditorShell shell) {
    if (shell == null) {
      return false;
    }

    Integer selectedEntryId = shell.getCurrentSelectedEntryId();
    if (selectedEntryId != null && selectedEntryId > 0) {
      return true;
    }

    Integer sourceEntryId = shell.getCurrentSourceEntryId();
    return sourceEntryId != null && sourceEntryId > 0;
  }

  private DynamicBibliographyEntry resolveCurrentDynamicBibliographyEntry() {
    if (currentNote == null || currentNote.getBibliographyRefId() == null || currentNote.getBibliographyRefId() <= 0) {
      return null;
    }

    return bibliographyService.loadEntry(currentNote.getBibliographyRefId()).orElse(null);
  }

  /**
   * Rendert den Bibliographiebereich des aktuellen Zettels neu.
   * Unter dem Zettel wird zunächst nur eine kompakte Vorschau angezeigt.
   * Die eigentliche Bearbeitung erfolgt erst nach bewusstem Öffnen des Editors.
   */
  private void renderBibliographyHost() {
    renderBibliographyHost(true);
  }

  /**
   * Rendert den Bibliographiebereich des aktuellen Zettels neu.
   * Ein bereits geöffneter Editor bleibt bei normalen Speicher- und
   * Fokuswechseln erhalten und wird nur bei ausdrücklichem Schließen,
   * Speichern, Zettelwechsel oder Toolbar-Umschaltung ersetzt.
   *
   * @param keepOpenEditor ob ein eingebetteter Editor erhalten bleiben soll
   */
  private void renderBibliographyHost(boolean keepOpenEditor) {
    boolean visible = view.getBottomToolbar().bookToggle().isSelected();
    view.getNoteEditorPane().setBibliographyVisible(visible);

    var host = view.getNoteEditorPane().getBibliographyHost();
    if (keepOpenEditor && shouldKeepEmbeddedBibliographyEditorOpen(host, visible)) {
      return;
    }

    activeEmbeddedBibliographyShell = null;
    host.getChildren().clear();

    if (!visible || currentNote == null) {
      return;
    }

    if (currentNote.getBibliographyRefId() == null || currentNote.getBibliographyRefId() <= 0) {
      showCreateBibliographyButton();
      return;
    }

    DynamicBibliographyEntry entry = resolveCurrentDynamicBibliographyEntry();
    if (entry == null) {
      showUnresolvedBibliographyButton();
      return;
    }

    showCompactBibliographyPreview(entry);
  }

  /**
   * Prüft, ob der aktuell eingebettete Bibliographie-Editor unverändert im
   * Zettelbereich sichtbar bleiben soll.
   *
   * @param host Bibliographie-Container unter dem Zettel
   * @param visible aktueller Sichtbarkeitszustand des Containers
   * @return {@code true}, wenn der offene Editor erhalten bleibt
   */
  private boolean shouldKeepEmbeddedBibliographyEditorOpen(VBox host, boolean visible) {
    return visible
               && activeEmbeddedBibliographyShell != null
               && host != null
               && host.getChildren().contains(activeEmbeddedBibliographyShell.getRoot());
  }

  /**
   * Öffnet den Bibliographie-Editor für den aktuellen Zettel.
   */
  private void showBibliographyEditor() {
    var shell = createBibliographyShell();

    if (currentNote != null
            && currentNote.getBibliographyRefId() != null
            && currentNote.getBibliographyRefId() > 0) {
      DynamicBibliographyEntry entry = resolveCurrentDynamicBibliographyEntry();
      if (entry != null) {
        shell.showDynamicEntry(entry, true);
        showBibliographyShell(shell);
        return;
      }
    }

    shell.showNewEditor("book");
    showBibliographyShell(shell);
  }

  /**
   * Öffnet das Popup zur Medienverwaltung.
   */
  private void openMediaManagementDialog() {
    MediaManagementDialog dialog = new MediaManagementDialog(
        stage,
        bibliographyService,
        bibliographyShellFactory
    );
    dialog.show();
  }

  private boolean validateDynamicEntryForSave(DynamicBibliographyEntry entry) {
    if (entry == null || entry.getMediaTypeId() == null || entry.getMediaTypeId() <= 0) {
      showBibliographyWarning(
          "Bibliographische Angabe",
          "Der Medientyp des Eintrags ist nicht gesetzt."
      );
      return false;
    }

    List<String> missing = new ArrayList<>();
    for (MediaAttributeDefinition attribute : bibliographyService.listAttributesForMediaType(entry.getMediaTypeId())) {
      if (!attribute.isIdentify() && !attribute.isNecessary()) {
        continue;
      }

      String bibtexName = attribute.fieldDefinition().bibtexName();
      if (!hasDynamicValue(entry, bibtexName)) {
        missing.add("• " + attribute.fieldDefinition().displayName());
      }
    }

    if (!missing.isEmpty()) {
      showBibliographyWarning(
          (entry.getMediaTypeName().isBlank() ? "Eintrag" : entry.getMediaTypeName())
              + " kann noch nicht gespeichert werden",
          "Es fehlen Pflichtangaben:\n\n" + String.join("\n", missing)
      );
      return false;
    }

    return true;
  }

  private boolean hasDynamicValue(DynamicBibliographyEntry entry, String bibtexName) {
    return entry.findValue(bibtexName)
               .map(value -> switch (value.valueType()) {
                 case STRING -> value instanceof StringBibValue stringValue
                                    && stringValue.value() != null
                                    && !stringValue.value().trim().isBlank();

                 case INTEGER -> value instanceof IntegerBibValue integerValue
                                     && integerValue.value() != null;

                 case PERSON -> value instanceof PersonBibValue personValue
                                    && personValue.value() != null
                                    && !personValue.value().isEmpty();

                 case RELATED -> value instanceof RelatedBibValue relatedValue
                                     && relatedValue.relatedEntryId() != null
                                     && relatedValue.relatedEntryId() > 0;
               })
               .orElse(false);
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

  /**
   * Öffnet einen generischen Untereditor für ein Related-Zielmedium und
   * übernimmt nach dem Speichern die Verknüpfung zurück in die Ursprungsshell.
   *
   * @param ownerShell Ursprungsshell mit dem Related-Feld
   * @param relatedBibtexName technischer BibTeX-Name des Related-Feldes
   * @param targetMediaTypeBibName technischer Ziel-Medientypname
   * @param displayText aktueller Feldtext der Ursprungsshell
   */
  private void openRelatedEditor(BibliographyEditorShell ownerShell,
                                 String relatedBibtexName,
                                 String targetMediaTypeBibName,
                                 String displayText) {
    if (ownerShell == null
            || relatedBibtexName == null
            || relatedBibtexName.isBlank()
            || targetMediaTypeBibName == null
            || targetMediaTypeBibName.isBlank()) {
      return;
    }

    var relatedShell = createBibliographyShell();

    relatedShell.setOnDelete(() -> showBibliographyShell(ownerShell));
    relatedShell.setOnNoneSelected(() -> showBibliographyShell(ownerShell));
    relatedShell.setOnAdd(() -> saveRelatedEntryForOwner(
        relatedShell,
        ownerShell,
        relatedBibtexName
    ));

    Integer relatedEntryId = ownerShell.getCurrentRelatedEntryId(relatedBibtexName);
    if (relatedEntryId != null && relatedEntryId > 0) {
      DynamicBibliographyEntry loaded = bibliographyService.loadEntry(relatedEntryId).orElse(null);
      if (loaded != null) {
        relatedShell.showDynamicEntry(loaded, true);
        showBibliographyShell(relatedShell);
        return;
      }
    }

    relatedShell.showNewEditor(targetMediaTypeBibName);
    relatedShell.setCurrentFieldValue(
        resolveRelatedDisplayBibtexName(ownerShell, relatedBibtexName),
        displayText
    );
    showBibliographyShell(relatedShell);
  }

  /**
   * Speichert ein im Untereditor bearbeitetes Related-Zielmedium und übernimmt
   * die Verknüpfung zurück in die Ursprungsshell.
   *
   * @param relatedShell Untereditor des Zielmediums
   * @param ownerShell Ursprungsshell mit dem Related-Feld
   * @param relatedBibtexName technischer BibTeX-Name des Related-Feldes
   */
  private void saveRelatedEntryForOwner(BibliographyEditorShell relatedShell,
                                        BibliographyEditorShell ownerShell,
                                        String relatedBibtexName) {
    if (relatedShell == null || ownerShell == null || relatedBibtexName == null) {
      return;
    }

    DynamicBibliographyEntry dynamicEntry = relatedShell.readDynamicEntry();
    if (dynamicEntry == null) {
      showBibliographyError(
          "Related-Eintrag",
          "Der verknüpfte Eintrag konnte nicht aus dem Editor gelesen werden."
      );
      return;
    }

    if (!validateDynamicEntryForSave(dynamicEntry)) {
      return;
    }

    Integer existingId = ownerShell.getCurrentRelatedEntryId(relatedBibtexName);
    if (existingId != null && existingId > 0) {
      dynamicEntry.setId(existingId);
    }

    int entryId = bibliographyService.saveEntry(dynamicEntry);
    if (entryId <= 0) {
      showBibliographyError(
          "Speichern fehlgeschlagen",
          "Der verknüpfte Eintrag konnte nicht gespeichert werden."
      );
      return;
    }

    String displayBibtexName = resolveRelatedDisplayBibtexName(ownerShell, relatedBibtexName);
    String displayValue = relatedShell.getCurrentFieldValue(displayBibtexName);

    if (displayValue == null || displayValue.isBlank()) {
      displayValue = bibliographyService.loadReference(entryId)
                         .map(BibliographyReference::displayText)
                         .orElse("");
    }

    ownerShell.setCurrentRelatedEntry(relatedBibtexName, entryId, displayValue);
    showBibliographyShell(ownerShell);
  }

  /**
   * Ermittelt das bevorzugte Anzeigefeld eines Related-Zielmediums für die
   * Rückübernahme in die Ursprungsshell.
   * Bevorzugt wird die Konfiguration aus {@code related_media_type}.
   * Fehlt diese, wird defensiv auf {@code title} zurückgefallen.
   *
   * @param ownerShell Ursprungsshell mit dem Related-Feld
   * @param relatedBibtexName technischer BibTeX-Name des Related-Feldes
   * @return technischer BibTeX-Name des bevorzugten Anzeigefeldes
   */
  private String resolveRelatedDisplayBibtexName(BibliographyEditorShell ownerShell,
                                                 String relatedBibtexName) {
    if (ownerShell == null || relatedBibtexName == null || relatedBibtexName.isBlank()) {
      return "title";
    }

    String ownerMediaTypeBibName = ownerShell.getSelectedMediaTypeBibName();
    if (ownerMediaTypeBibName == null || ownerMediaTypeBibName.isBlank()) {
      return "title";
    }

    return bibliographyService.loadLookupDefinition(ownerMediaTypeBibName, relatedBibtexName)
               .flatMap(lookupDefinition -> bibliographyService.loadRelatedDefinition(lookupDefinition.bibtexTypeId()))
               .map(RelatedMediaDefinition::displayFieldDefinition)
               .map(BibtexFieldDefinition::bibtexName)
               .map(name -> name == null ? "" : name.trim())
               .filter(name -> !name.isBlank())
               .orElse("title");
  }

  private void unlinkCurrentBibliography() {
    if (currentNote == null) {
      return;
    }

    if (currentNoteExistsInDatabase()) {
      noteRepository.unlinkBibliographyEntry(currentNote.getId());
    }

    currentNote.setBibliographyRefId(null);

    renderBibliographyHost(false);
    if (view.getActiveServiceAreaMode() == ZettelWindowView.ServiceAreaMode.KEY) {
      refreshKeyWindow();
    }
  }

  /**
   * Liest den aktuellen bibliographischen Eintrag direkt aus der Shell,
   * speichert ihn und verknüpft ihn mit dem aktuell geöffneten Zettel.
   *
   * @param shell aktive Bibliographie-Shell
   */
  private void saveAndLinkBibliography(BibliographyEditorShell shell) {
    if (currentNote == null) {
      return;
    }

    String selectedMediaTypeBibName = shell.getSelectedMediaTypeBibName();
    if (selectedMediaTypeBibName == null || selectedMediaTypeBibName.isBlank()) {
      unlinkCurrentBibliography();
      return;
    }

    if (!ensureCurrentNotePersistedForBibliography()) {
      return;
    }

    DynamicBibliographyEntry editedEntry = shell.readDynamicEntry();
    if (editedEntry == null) {
      showBibliographyError(
          "Bibliographische Angabe",
          "Der Eintrag konnte nicht aus dem Editor gelesen werden."
      );
      return;
    }

    boolean inlineMediaTypeSwitchCreation = shell.isCurrentInlineMediaTypeSwitchCreation();

    Integer sourceEntryId = inlineMediaTypeSwitchCreation ? null : shell.getCurrentSourceEntryId();
    if (!inlineMediaTypeSwitchCreation && (sourceEntryId == null || sourceEntryId <= 0)) {
      sourceEntryId = shell.getCurrentSelectedEntryId();
    }

    Integer linkedEntryId = currentNote.getBibliographyRefId();
    boolean hasLinkedBibliography = linkedEntryId != null && linkedEntryId > 0;
    boolean forceSaveAsNew = false;

    Integer baseEntryId = null;
    if (sourceEntryId != null && sourceEntryId > 0) {
      baseEntryId = sourceEntryId;
    } else if (!inlineMediaTypeSwitchCreation && hasLinkedBibliography) {
      baseEntryId = linkedEntryId;
    }

    if (hasLinkedBibliography
            && baseEntryId != null
            && baseEntryId > 0
            && Objects.equals(baseEntryId, linkedEntryId)) {
      LinkedBibliographyDecision linkedDecision = askLinkedBibliographyDecision(linkedEntryId);

      if (linkedDecision == LinkedBibliographyDecision.CANCEL) {
        return;
      }

      if (linkedDecision == LinkedBibliographyDecision.SAVE_AS_NEW) {
        forceSaveAsNew = true;
      }
    }

    DynamicBibliographyEntry dynamicEntry = mergeWithExistingBibliographyEntry(baseEntryId, editedEntry);

    if (forceSaveAsNew) {
      dynamicEntry.setId(null);
    } else if (baseEntryId != null && baseEntryId > 0) {
      dynamicEntry.setId(baseEntryId);
    }

    if (!validateDynamicEntryForSave(dynamicEntry)) {
      return;
    }

    if (dynamicEntry.getId() == null || dynamicEntry.getId() <= 0) {
      List<DynamicBibliographyEntry> duplicates = bibliographyService.findPotentialDuplicates(dynamicEntry);

      if (forceSaveAsNew && hasLinkedBibliography) {
        duplicates = duplicates.stream()
                         .filter(entry -> entry != null
                                              && entry.getId() != null
                                              && !Objects.equals(entry.getId(), linkedEntryId))
                         .toList();
      }

      if (!duplicates.isEmpty()) {
        DuplicateBibliographyDecision decision = askDuplicateBibliographyDecision(duplicates);

        if (decision == DuplicateBibliographyDecision.CANCEL) {
          return;
        }

        if (decision == DuplicateBibliographyDecision.UPDATE_EXISTING) {
          dynamicEntry.setId(duplicates.getFirst().getId());
        }
      }
    }

    String mediaTypeBibName = safeTrim(dynamicEntry.getMediaTypeBibName());
    boolean creatableUnderNote = isCreatableUnderNote(mediaTypeBibName);
    boolean pointsToExistingEntry = pointsToExistingBibliographyEntry(shell);

    if (!creatableUnderNote && !pointsToExistingEntry) {
      showUnderNoteSelectionOnlyWarning(dynamicEntry.getMediaTypeName());
      return;
    }

    int entryId = bibliographyService.saveEntry(dynamicEntry);
    if (entryId <= 0) {
      showBibliographyError(
          "Speichern fehlgeschlagen",
          "Der bibliographische Eintrag konnte nicht gespeichert werden."
      );
      return;
    }

    noteRepository.linkBibliographyEntry(currentNote.getId(), entryId);
    currentNote.setBibliographyRefId(entryId);

    renderBibliographyHost(false);
    if (view.getActiveServiceAreaMode() == ZettelWindowView.ServiceAreaMode.KEY) {
      refreshKeyWindow();
    }
  }

  /**
   * Baut aus einem bestehenden bibliographischen Eintrag und den aktuell aus der
   * Shell gelesenen Änderungen einen vollständigen Speichereintrag.
   * Bereits vorhandene Werte bleiben erhalten, sofern sie in der aktuellen
   * Shell-Auslese nicht erneut gesetzt wurden.
   *
   * @param baseEntryId optionale ID eines bestehenden Basiseintrags
   * @param editedEntry aktuell aus der Shell gelesener Eintrag
   * @return gemergter vollständiger Eintrag
   */
  private DynamicBibliographyEntry mergeWithExistingBibliographyEntry(Integer baseEntryId,
                                                                      DynamicBibliographyEntry editedEntry) {
    if (editedEntry == null) {
      return null;
    }

    if (baseEntryId == null || baseEntryId <= 0) {
      return editedEntry;
    }

    DynamicBibliographyEntry baseEntry = bibliographyService.loadEntry(baseEntryId).orElse(null);
    if (baseEntry == null) {
      return editedEntry;
    }

    DynamicBibliographyEntry merged = new DynamicBibliographyEntry();
    merged.setId(baseEntry.getId());
    merged.setMediaTypeId(
        editedEntry.getMediaTypeId() != null ? editedEntry.getMediaTypeId() : baseEntry.getMediaTypeId()
    );
    merged.setMediaTypeBibName(
        editedEntry.getMediaTypeBibName() != null && !editedEntry.getMediaTypeBibName().isBlank()
            ? editedEntry.getMediaTypeBibName()
            : baseEntry.getMediaTypeBibName()
    );
    merged.setMediaTypeName(
        editedEntry.getMediaTypeName() != null && !editedEntry.getMediaTypeName().isBlank()
            ? editedEntry.getMediaTypeName()
            : baseEntry.getMediaTypeName()
    );
    merged.setCreatedAt(baseEntry.getCreatedAt());
    merged.setUpdatedAt(baseEntry.getUpdatedAt());

    for (BibValue value : baseEntry.getOrderedValues()) {
      merged.putValue(value);
    }

    for (BibValue value : editedEntry.getOrderedValues()) {
      merged.putValue(value);
    }

    return merged;
  }

  /**
   * Klappt den rechten Arbeitsbereich ein oder wieder aus.
   * Beim erneuten Öffnen wird der aktuell gewählte Zettel im nächsten Schritt
   * wieder für den aktiven Modus synchronisiert.
   */
  private void toggleServiceArea() {
    boolean wasCollapsed = view.isServiceAreaCollapsed();
    view.rememberCurrentWorkAreaWidths();
    double serviceAreaWidthDelta = view.getServiceAreaToggleWidthDelta();

    if (wasCollapsed) {
      stage.setWidth(stage.getWidth() + serviceAreaWidthDelta);
      view.setServiceAreaCollapsed(false);
    } else {
      view.setServiceAreaCollapsed(true);
      stage.setWidth(Math.max(stage.getMinWidth(), stage.getWidth() - serviceAreaWidthDelta));
    }
    view.restoreRememberedWorkAreaWidthsAfterLayout();

    if (wasCollapsed && view.getActiveServiceAreaMode() != null) {
      if (view.getActiveServiceAreaMode() == ZettelWindowView.ServiceAreaMode.MAGNIFY_LINK) {
        syncMagnifyLinkSourceNoteFromCurrentNote();
      }
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
      view.getMagnifyLinkDirectedTraversalButton().setSelected(false);
      return;
    }

    view.setActiveServiceAreaMode(mode);

    if (mode == ZettelWindowView.ServiceAreaMode.MAGNIFY_LINK) {
      syncMagnifyLinkSourceNoteFromCurrentNote();
      view.selectAllMagnifyLinkTypeButtons();
    }

    if (mode == ZettelWindowView.ServiceAreaMode.LIST) {
      view.setListMode(ZettelWindowView.ListMode.FILTER);
    }

    refreshServiceAreaForCurrentState();
  }

  /**
   * Aktualisiert den sichtbaren Inhalt des rechten Arbeitsbereichs
   * passend zum aktuell gewählten Modus.
   */
  private void refreshServiceAreaForCurrentState() {
    ZettelWindowView.ServiceAreaMode mode = view.getActiveServiceAreaMode();
    if (mode == null) {
      view.setServiceAreaContent(new Pane());
      return;
    }

    if (mode == ZettelWindowView.ServiceAreaMode.MAGNIFY_LINK) {
      view.setServiceAreaContent(view.getMagnifyLinkPane());
      refreshMagnifyLinkWindow();
      return;
    }

    if (mode == ZettelWindowView.ServiceAreaMode.KEY) {
      view.setServiceAreaContent(view.getKeyPane());
      refreshKeyWindow();
      return;
    }

    if (mode == ZettelWindowView.ServiceAreaMode.BOOK) {
      view.setServiceAreaContent(view.getBookPane());
      scheduleBookRefreshAfterServiceSwitch();
      return;
    }

    if (mode == ZettelWindowView.ServiceAreaMode.LIST) {
      view.setServiceAreaContent(view.getListPane());
      refreshListWindow();
      return;
    }

    view.setServiceAreaContent(view.createModePlaceholder(mode));
  }

  /**
   * Aktualisiert den sichtbaren Zustand des BOOK-Fensters.
   * Schritt 4 ergänzt:
   * - stabile Sortierung der Tabellen
   * - automatische Mehrfach-Autorenfolgezeile
   */
  private void refreshBookWindow() {
    if (view.getActiveServiceAreaMode() != ZettelWindowView.ServiceAreaMode.BOOK) {
      return;
    }

    BookRefreshRequest request = createBookRefreshRequest(++bookServiceRefreshRequestId);
    BookRefreshResult result = loadBookRefreshResult(request);
    applyBookRefreshResult(request, result);
  }

  /**
   * Fordert eine BOOK-Aktualisierung im Hintergrund an.
   * Der JavaFX-Thread erstellt nur einen stabilen Snapshot und wendet das
   * Ergebnis spaeter an; Datenbankarbeit und Zeilenaufbau laufen im Executor.
   */
  private void requestAsyncBookRefresh() {
    if (view.getActiveServiceAreaMode() != ZettelWindowView.ServiceAreaMode.BOOK) {
      return;
    }

    BookRefreshRequest request = createBookRefreshRequest(++bookServiceRefreshRequestId);
    try {
      bookRefreshExecutor.execute(() -> {
        BookRefreshResult result = loadBookRefreshResult(request);
        Platform.runLater(() -> applyBookRefreshResult(request, result));
      });
    } catch (RuntimeException ignored) {
      // Beim Schliessen des Fensters kann der Executor bereits beendet sein.
    }
  }

  /**
   * Erstellt einen stabilen BOOK-Refresh-Snapshot auf dem JavaFX-Thread.
   *
   * @param requestId eindeutige Aktualisierungsnummer
   * @return Snapshot der aktuellen BOOK-Eingaben
   */
  private BookRefreshRequest createBookRefreshRequest(long requestId) {
    String mediaTypeBibName = view.hasBookResolvedMediaTypeSelection()
                                  ? view.getBookResolvedMediaTypeBibName()
                                  : "";

    return new BookRefreshRequest(
        requestId,
        view.getBookMediaTableSortState(),
        view.getBookNoteTableSortState(),
        mediaTypeBibName,
        safeTrim(view.getBookTitleField().getText()),
        view.hasBookResolvedMediaTypeSelection(),
        view.getBookShowNotesToggleButton().isSelected(),
        selectedBookEntryId,
        view.getActiveBookAuthorMode(),
        snapshotBookAuthorFilters()
    );
  }

  /**
   * Laedt und berechnet alle BOOK-Zeilen fuer einen Snapshot.
   *
   * @param request Refresh-Snapshot
   * @return berechnetes Ergebnis
   */
  private BookRefreshResult loadBookRefreshResult(BookRefreshRequest request) {
    try {
      List<BibliographyReference> references = bibliographyService.searchMediaReferences(
          request.mediaTypeBibName(),
          request.titleQuery(),
          MEDIA_REFERENCE_SEARCH_LIMIT
      );

      Map<Integer, Integer> noteCountsByEntryId = loadBookNoteCountsByEntryId();
      List<ZettelWindowView.BookMediaRow> mediaRows = buildBookMediaRows(
          request,
          references == null ? List.of() : references,
          noteCountsByEntryId
      );

      List<ZettelWindowView.BookNoteRow> noteRows = List.of();
      if (request.notesVisible()) {
        List<NoteRepository.BibliographyUsageRow> usageRows = noteRepository.loadBibliographyUsageRows();
        noteRows = buildBookNoteRows(mediaRows, usageRows == null ? List.of() : usageRows, request.selectedEntryId());
      }

      return new BookRefreshResult(mediaRows, noteRows, "");
    } catch (RuntimeException exception) {
      return new BookRefreshResult(List.of(), List.of(), safeTrim(exception.getMessage()));
    }
  }

  /**
   * Laedt die aggregierten Zettelzahlen fuer BOOK-Medien.
   *
   * @return Zuordnung Bibliographie-Eintrags-ID -> Zettelzahl
   */
  private Map<Integer, Integer> loadBookNoteCountsByEntryId() {
    Map<Integer, Integer> noteCountsByEntryId = new LinkedHashMap<>();
    Map<Integer, Integer> loadedNoteCounts = noteRepository.loadBibliographyUsageCountsByEntryId();
    if (loadedNoteCounts != null) {
      loadedNoteCounts.forEach((entryId, noteCount) -> {
        if (entryId != null && entryId > 0 && noteCount != null && noteCount > 0) {
          noteCountsByEntryId.put(entryId, noteCount);
        }
      });
    }
    return noteCountsByEntryId;
  }

  /**
   * Baut Medienzeilen fuer den BOOK-Service aus schlanken Referenzen.
   *
   * @param request Refresh-Snapshot
   * @param references gefundene Bibliographie-Referenzen
   * @param noteCountsByEntryId Zettelzahlen nach Bibliographie-ID
   * @return sichtbare Medienzeilen
   */
  private List<ZettelWindowView.BookMediaRow> buildBookMediaRows(
      BookRefreshRequest request,
      List<BibliographyReference> references,
      Map<Integer, Integer> noteCountsByEntryId
  ) {
    boolean hasAuthorFilters = request.authorMode() != ZettelWindowView.BookAuthorMode.OFF
                                   && countActiveBookAuthorFilters(request.authorFilters()) > 0;
    List<ZettelWindowView.BookMediaRow> mediaRows = new ArrayList<>();
    for (BibliographyReference reference : references) {
      if (reference == null || reference.entryId() <= 0) {
        continue;
      }

      DynamicBibliographyEntry entry = bibliographyService.loadEntry(reference.entryId()).orElse(null);
      if (hasAuthorFilters) {
        if (entry == null || !matchesBookAuthorFilters(entry, request.authorMode(), request.authorFilters())) {
          continue;
        }
      }

      String authorText = entry == null
                              ? resolveBookReferenceAuthor(reference)
                              : resolvePreviewPerson(entry);
      String titleText = entry == null
                             ? resolveBookReferenceTitle(reference, authorText)
                             : resolvePreviewTitle(entry);

      if (titleText.isBlank()) {
        titleText = safeTrim(reference.displayText());
      }

      mediaRows.add(new ZettelWindowView.BookMediaRow(
          reference.entryId(),
          authorText,
          titleText,
          safeTrim(reference.mediaTypeName()),
          noteCountsByEntryId == null ? 0 : noteCountsByEntryId.getOrDefault(reference.entryId(), 0)
      ));
    }

    return mediaRows;
  }

  /**
   * Wendet ein fertiges BOOK-Ergebnis auf dem JavaFX-Thread an.
   *
   * @param request Refresh-Snapshot
   * @param result Ergebnis der Hintergrundarbeit
   */
  private void applyBookRefreshResult(BookRefreshRequest request, BookRefreshResult result) {
    if (request.requestId() != bookServiceRefreshRequestId
            || view.getActiveServiceAreaMode() != ZettelWindowView.ServiceAreaMode.BOOK) {
      return;
    }

    if (result.errorMessage() != null && !result.errorMessage().isBlank()) {
      view.setBookStatus("BookService konnte nicht aktualisiert werden: " + result.errorMessage());
      return;
    }

    List<ZettelWindowView.BookMediaRow> mediaRows = result.mediaRows() == null ? List.of() : result.mediaRows();
    List<ZettelWindowView.BookNoteRow> noteRows = result.noteRows() == null ? List.of() : result.noteRows();

    Integer selectedEntryId = request.selectedEntryId();
    if (selectedEntryId != null) {
      int selectedEntryIdValue = selectedEntryId;
      boolean selectedStillVisible = mediaRows.stream()
                                         .anyMatch(row -> row != null && row.getEntryId() == selectedEntryIdValue);

      if (!selectedStillVisible) {
        selectedEntryId = null;
      }
    }

    selectedBookEntryId = selectedEntryId;
    view.setBookMediaRows(mediaRows);
    view.applyBookMediaTableSortState(request.mediaSortState());
    view.selectBookMediaRow(selectedBookEntryId);

    if (request.notesVisible()) {
      view.setBookNoteRows(noteRows);
      view.applyBookNoteTableSortState(request.noteSortState());
    } else if (!view.getBookNotesTable().getItems().isEmpty()) {
      view.setBookNoteRows(List.of());
    }

    view.setBookResolvedMediaTypeSelection(request.resolvedMediaType(), request.mediaTypeBibName());
    view.setBookNotesVisible(request.notesVisible());

    view.setBookStatus(buildBookStatusText(mediaRows, noteRows));
  }

  /**
   * Plant den BOOK-Refresh nach dem Einsetzen des BOOK-Panels ein.
   * Dadurch kann der Moduswechsel zuerst sichtbar werden; die Tabellenarbeit
   * laeuft im naechsten JavaFX-Puls und nicht mehr im unmittelbaren Klickpfad.
   */
  private void scheduleBookRefreshAfterServiceSwitch() {
    Platform.runLater(() -> {
      if (view.getActiveServiceAreaMode() != ZettelWindowView.ServiceAreaMode.BOOK) {
        return;
      }
      requestAsyncBookRefresh();
    });
  }

  /**
   * Ermittelt aus einer Referenzanzeige einen Autorenwert fuer die BOOK-Tabelle,
   * ohne den kompletten Eintrag erneut laden zu muessen.
   *
   * @param reference bibliographische Referenz
   * @return erkannter Autorenwert oder leerer String
   */
  private String resolveBookReferenceAuthor(BibliographyReference reference) {
    String displayText = safeTrim(reference == null ? "" : reference.displayText());
    if (displayText.isBlank()) {
      return "";
    }

    String firstPart = firstDisplayPart(displayText);
    return looksLikePersonDisplay(firstPart) ? firstPart : "";
  }

  /**
   * Ermittelt aus der Referenzanzeige einen Titelwert fuer die BOOK-Tabelle,
   * wenn kein kompletter Eintrag fuer Autorenfilter geladen werden musste.
   *
   * @param reference bibliographische Referenz
   * @param authorText bereits erkannter Autorenwert
   * @return Titel- bzw. Identifikationstext
   */
  private String resolveBookReferenceTitle(BibliographyReference reference, String authorText) {
    if (reference == null) {
      return "";
    }

    String displayText = safeTrim(reference.displayText());
    if (displayText.isBlank()) {
      return "";
    }

    String normalizedAuthor = safeTrim(authorText);
    if (!normalizedAuthor.isBlank()) {
      String pipePrefix = normalizedAuthor + " | ";
      if (displayText.startsWith(pipePrefix)) {
        return safeTrim(displayText.substring(pipePrefix.length()));
      }

      String colonPrefix = normalizedAuthor + ": ";
      if (displayText.startsWith(colonPrefix)) {
        return safeTrim(displayText.substring(colonPrefix.length()));
      }
    }

    return displayText;
  }

  /**
   * Liefert den ersten durch die Referenzanzeige getrennten Teil.
   *
   * @param displayText kompakter Referenztext
   * @return erster Teil oder leerer String
   */
  private String firstDisplayPart(String displayText) {
    String normalized = safeTrim(displayText);
    int separator = normalized.indexOf(" | ");
    if (separator < 0) {
      separator = normalized.indexOf(": ");
    }
    if (separator < 0) {
      return normalized;
    }

    return safeTrim(normalized.substring(0, separator));
  }

  /**
   * Prueft defensiv, ob ein Anzeigenwert wie eine Personenanzeige aussieht.
   *
   * @param value Anzeigenwert
   * @return {@code true}, wenn der Wert wahrscheinlich eine Person ist
   */
  private boolean looksLikePersonDisplay(String value) {
    String normalized = safeTrim(value);
    return !normalized.isBlank() && normalized.contains(",");
  }

  /**
   * Setzt das Zettelnummernfeld des MAGNIFY_LINK-Fensters auf den aktuell
   * im Editor sichtbaren Zettel. Diese Synchronisation geschieht bewusst nur
   * beim Öffnen des Fensters bzw. beim Umschalten auf MAGNIFY_LINK.
   * Dabei wird der temporäre Klappzustand der Verweisliste zurückgesetzt.
   */
  private void syncMagnifyLinkSourceNoteFromCurrentNote() {
    resetMagnifyBranchCollapseState();

    if (currentNote == null || currentNote.getId() <= 0) {
      view.getMagnifyLinkNoteIdField().clear();
      return;
    }

    view.getMagnifyLinkNoteIdField().setText(Integer.toString(currentNote.getId()));
  }

  /**
   * Aktualisiert den gesamten sichtbaren Inhalt des MAGNIFY_LINK-Fensters.
   * Die Darstellung liest ausschließlich aus der Datenbank.
   */
  private void refreshMagnifyLinkWindow() {
    if (view.getActiveServiceAreaMode() != ZettelWindowView.ServiceAreaMode.MAGNIFY_LINK) {
      return;
    }

    Integer sourceNoteId = parseMagnifyLinkSourceNoteId();
    if (sourceNoteId == null || sourceNoteId <= 0) {
      view.showMagnifyLinkStatus("Zettelnummer eingeben");
      return;
    }

    if (!noteRepository.existsNote(sourceNoteId)) {
      view.showMagnifyLinkStatus("Zettel " + sourceNoteId + " existiert nicht in der Datenbank");
      return;
    }

    if (!Objects.equals(lastMagnifyRenderedSourceNoteId, sourceNoteId)) {
      resetMagnifyBranchCollapseState();
      lastMagnifyRenderedSourceNoteId = sourceNoteId;
    }

    String rootTitle = noteRepository.loadNoteTitle(sourceNoteId).orElse("—");
    List<Node> rows = new ArrayList<>();

    rows.add(createMagnifyRootButton(sourceNoteId, rootTitle));

    int incomingDepth = (int) Math.round(view.getMagnifyLinkIncomingDepthSlider().getValue());
    int outgoingDepth = (int) Math.round(view.getMagnifyLinkOutgoingDepthSlider().getValue());

    if (incomingDepth > 0) {
      appendMagnifyBranchRows(
          rows,
          sourceNoteId,
          true,
          1,
          incomingDepth,
          new LinkedHashSet<>(Set.of(sourceNoteId)),
          null,
          false
      );
    }

    if (outgoingDepth > 0) {
      appendMagnifyBranchRows(
          rows,
          sourceNoteId,
          false,
          1,
          outgoingDepth,
          new LinkedHashSet<>(Set.of(sourceNoteId)),
          null,
          false
      );
    }

    int hitCount = Math.max(0, rows.size() - 1);
    String status = buildMagnifyStatusText(sourceNoteId, hitCount);
    view.setMagnifyLinkRows(status, rows);
  }

  /**
   * Erzeugt den Statustext für das MAGNIFY_LINK-Fenster einschließlich
   * der aktuell sichtbaren Trefferzahl ohne Wurzelzeile.
   *
   * @param sourceNoteId ID des Quellzettels
   * @param hitCount Anzahl der sichtbaren Verweiszeilen
   * @return formatierter Statustext
   */
  private String buildMagnifyStatusText(int sourceNoteId, int hitCount) {
    String hitLabel = hitCount == 1 ? "Treffer" : "Treffer";
    return "Zettel " + sourceNoteId + " · " + hitCount + " " + hitLabel;
  }

  /**
   * Liest die aktuell im MAGNIFY_LINK-Fenster eingetragene Zettelnummer.
   *
   * @return Zettelnummer oder {@code null}, wenn die Eingabe leer oder ungültig ist
   */
  private Integer parseMagnifyLinkSourceNoteId() {
    String text = view.getMagnifyLinkNoteIdField().getText();
    if (text == null || text.isBlank()) {
      return null;
    }

    try {
      return Integer.parseInt(text.trim());
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  /**
   * Baut rekursiv die Verweiszeilen für eine Richtung auf.
   * Im Normalfall bleibt die Rekursion richtungsrein.
   * Ist der Toggle für gerichtete Verweise aktiv, können ab freigegebenen Ebenen
   * sowohl eingehende als auch ausgehende Verweise weiter verfolgt werden.
   *
   * Zusätzlich wird der unmittelbar vorhergehende Zettel nur für die direkt
   * nächste Ebene ausgefiltert, damit kein unmittelbarer Rücksprung als Kind
   * desselben Listeneintrags erscheint.
   *
   * @param rows Ziel-Liste für die UI-Zeilen
   * @param sourceNoteId Ausgangszettel
   * @param incoming {@code true} für übergeordnete, {@code false} für untergeordnete Zettel
   * @param depth aktuelle Tiefe ab 1
   * @param maxDepth maximal darzustellende Tiefe
   * @param branchVisited pro Ast bereits besuchte Zettel zur Zyklusvermeidung
   * @param immediatePreviousNoteId Zettel-ID des direkt vorhergehenden Eintrags;
   *                                dieser wird nur auf der nächsten Ebene ausgefiltert
   * @param allowBidirectionalTraversal {@code true}, wenn ab dieser Ebene beide Richtungen
   *                                    gemeinsam verfolgt werden dürfen
   */
  private void appendMagnifyBranchRows(List<Node> rows,
                                       int sourceNoteId,
                                       boolean incoming,
                                       int depth,
                                       int maxDepth,
                                       Set<Integer> branchVisited,
                                       Integer immediatePreviousNoteId,
                                       boolean allowBidirectionalTraversal) {
    if (depth > maxDepth) {
      return;
    }

    List<MagnifyBranchReference> references =
        loadVisibleMagnifyBranchReferences(sourceNoteId, incoming, allowBidirectionalTraversal);

    for (MagnifyBranchReference branchReference : references) {
      NoteReferenceInfo reference = branchReference.reference();
      if (reference == null || reference.noteId() <= 0) {
        continue;
      }

      if (immediatePreviousNoteId != null && reference.noteId() == immediatePreviousNoteId) {
        continue;
      }

      boolean branchDirection = branchReference.incoming();
      boolean expandable = depth < maxDepth && hasVisibleMagnifyChildren(reference.noteId(), branchDirection);
      boolean collapsed = expandable && isMagnifyBranchCollapsed(reference.noteId(), branchDirection);

      rows.add(createMagnifyReferenceRow(reference, branchDirection, depth, expandable, collapsed));

      if (!expandable || collapsed) {
        continue;
      }

      if (branchVisited.contains(reference.noteId())) {
        continue;
      }

      Set<Integer> nextVisited = new LinkedHashSet<>(branchVisited);
      nextVisited.add(reference.noteId());

      appendMagnifyBranchRows(
          rows,
          reference.noteId(),
          branchDirection,
          depth + 1,
          maxDepth,
          nextVisited,
          sourceNoteId,
          isDirectedMagnifyTraversalEnabled()
      );
    }
  }

  /**
   * Lädt die für die aktuelle Rekursion sichtbaren Referenzen eines Zettels.
   * Ohne freigegebene bidirektionale Traversierung wird nur die aktuelle Richtung verwendet.
   * Mit freigegebener bidirektionaler Traversierung werden beide Richtungen geladen.
   *
   * @param noteId Zettel-ID
   * @param incoming aktuelle Grundrichtung
   * @param allowBidirectionalTraversal {@code true}, wenn beide Richtungen geladen werden dürfen
   * @return sichtbare Referenzen inklusive Richtungsinformation je Eintrag
   */
  private List<MagnifyBranchReference> loadVisibleMagnifyBranchReferences(int noteId,
                                                                          boolean incoming,
                                                                          boolean allowBidirectionalTraversal) {
    List<MagnifyBranchReference> visible = new ArrayList<>();

    appendVisibleMagnifyBranchReferences(visible, noteId, incoming);

    if (allowBidirectionalTraversal) {
      appendVisibleMagnifyBranchReferences(visible, noteId, !incoming);
    }

    return visible;
  }

  /**
   * Ergänzt sichtbare Referenzen einer Richtung in die Ziel-Liste.
   *
   * @param target Zielliste
   * @param noteId Zettel-ID
   * @param incoming Richtung der zu ladenden Referenzen
   */
  private void appendVisibleMagnifyBranchReferences(List<MagnifyBranchReference> target,
                                                    int noteId,
                                                    boolean incoming) {
    List<NoteReferenceInfo> references = loadVisibleMagnifyReferences(noteId, incoming);
    for (NoteReferenceInfo reference : references) {
      if (reference != null && reference.noteId() > 0) {
        target.add(new MagnifyBranchReference(reference, incoming));
      }
    }
  }

  /**
   * Prüft, ob die gerichtete Traversierung im MAGNIFY_LINK-Fenster aktiv ist.
   *
   * @return {@code true}, wenn eingehende und ausgehende Verweise gemeinsam verfolgt werden sollen
   */
  private boolean isDirectedMagnifyTraversalEnabled() {
    return view.getMagnifyLinkDirectedTraversalButton().isSelected();
  }

  /**
   * Lädt die für die aktuelle MAGNIFY_LINK-Ansicht sichtbaren Referenzen
   * eines Zettels in einer Richtung.
   *
   * @param noteId Zettel-ID
   * @param incoming {@code true} für eingehende, {@code false} für ausgehende Referenzen
   * @return gefilterte Referenzen
   */
  private List<NoteReferenceInfo> loadVisibleMagnifyReferences(int noteId, boolean incoming) {
    List<NoteReferenceInfo> references = incoming
                                             ? noteRepository.loadIncomingReferenceInfos(noteId)
                                             : noteRepository.loadOutgoingReferenceInfos(noteId);

    List<NoteReferenceInfo> visible = new ArrayList<>();
    for (NoteReferenceInfo reference : references) {
      if (isMagnifyReferenceVisible(reference)) {
        visible.add(reference);
      }
    }
    return visible;
  }

  /**
   * Prüft, ob ein MAGNIFY_LINK-Eintrag in der aktuellen Traversierungslogik
   * sichtbare direkte Kinder besitzt.
   *
   * @param noteId Zettel-ID des Eintrags
   * @param incoming Grundrichtung des Astes
   * @return {@code true}, wenn direkte sichtbare Kinder existieren
   */
  private boolean hasVisibleMagnifyChildren(int noteId, boolean incoming) {
    return !loadVisibleMagnifyBranchReferences(
        noteId,
        incoming,
        isDirectedMagnifyTraversalEnabled()
    ).isEmpty();
  }

  /**
   * Setzt den temporären Klappzustand der MAGNIFY_LINK-Liste zurück.
   * Dieser Zustand wird bewusst nicht persistiert.
   */
  private void resetMagnifyBranchCollapseState() {
    collapsedMagnifyIncomingBranches.clear();
    collapsedMagnifyOutgoingBranches.clear();
    lastMagnifyRenderedSourceNoteId = null;
  }

  /**
   * Prüft, ob ein bestimmter Listenast aktuell eingeklappt ist.
   *
   * @param noteId Zettel-ID des Astes
   * @param incoming {@code true} für übergeordnete Richtung, {@code false} für untergeordnete
   * @return {@code true}, wenn der Ast eingeklappt ist
   */
  private boolean isMagnifyBranchCollapsed(int noteId, boolean incoming) {
    String key = buildMagnifyBranchKey(noteId);
    return incoming
               ? collapsedMagnifyIncomingBranches.contains(key)
               : collapsedMagnifyOutgoingBranches.contains(key);
  }

  /**
   * Schaltet den temporären Klappzustand eines MAGNIFY_LINK-Astes um.
   *
   * @param noteId Zettel-ID des Astes
   * @param incoming {@code true} für übergeordnete Richtung, {@code false} für untergeordnete
   */
  private void toggleMagnifyBranchCollapsed(int noteId, boolean incoming) {
    String key = buildMagnifyBranchKey(noteId);
    Set<String> target = incoming ? collapsedMagnifyIncomingBranches : collapsedMagnifyOutgoingBranches;

    if (target.contains(key)) {
      target.remove(key);
    } else {
      target.add(key);
    }
  }

  /**
   * Erzeugt den internen Schlüssel für einen MAGNIFY_LINK-Ast.
   *
   * @param noteId Zettel-ID des Astes
   * @return Schlüsseltext
   */
  private String buildMagnifyBranchKey(int noteId) {
    return Integer.toString(noteId);
  }

  /**
   * Prüft, ob eine Referenz unter den aktuell gesetzten Filtern sichtbar sein soll.
   *
   * @param reference zu prüfende Referenz
   * @return {@code true}, wenn die Referenz angezeigt werden soll
   */
  private boolean isMagnifyReferenceVisible(NoteReferenceInfo reference) {
    if (reference == null) {
      return false;
    }

    if (!isMagnifyLinkTypeSelected(reference.linkType())) {
      return false;
    }

    String filter = normalizeMagnifyFilterText();
    if (filter.isEmpty()) {
      return true;
    }

    String title = reference.title() == null ? "" : reference.title();
    return title.toLowerCase(Locale.ROOT).contains(filter);
  }

  /**
   * Prüft, ob der angegebene Linktyp aktuell im Verweisfenster aktiviert ist.
   *
   * @param linkType zu prüfender Linktyp
   * @return {@code true}, wenn der Typ sichtbar ist
   */
  private boolean isMagnifyLinkTypeSelected(LinkType linkType) {
    if (linkType == null) {
      return false;
    }

    return switch (linkType) {
      case SERIE -> view.getMagnifyLinkSerieButton().isSelected();
      case IDEE -> view.getMagnifyLinkIdeaButton().isSelected();
      case FRAGE -> view.getMagnifyLinkQuestionButton().isSelected();
      case KRITIK -> view.getMagnifyLinkCritiqueButton().isSelected();
      case AUFGABE -> view.getMagnifyLinkTaskButton().isSelected();
    };
  }

  /**
   * Liefert den normalisierten Textfilter des Verweisfensters.
   *
   * @return kleingeschriebener Filtertext oder leerer String
   */
  private String normalizeMagnifyFilterText() {
    String filter = view.getMagnifyLinkFilterField().getText();
    return filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
  }

  /**
   * Erzeugt die Wurzelzeile für den aktuell ausgewählten Zettel.
   * Icon und Titel sind getrennt aufgebaut; nur der Titel ist anklickbar.
   *
   * @param noteId Zettel-ID
   * @param title Zettelüberschrift
   * @return Zeile als UI-Knoten
   */
  private Node createMagnifyRootButton(int noteId, String title) {
    String safeTitle = (title == null || title.isBlank()) ? "—" : title;

    Region indentRegion = createMagnifyIndentRegion(0);

    Label iconLabel = createMagnifyStaticIconLabel(BaseIcon.PAGE_LINK);

    Button titleButton = createMagnifyTitleButton(noteId + ": " + safeTitle);
    titleButton.setTooltip(new Tooltip("PAGE_LINK: " + safeTitle));
    titleButton.setOnAction(e -> openNoteViaToolbar(noteId));

    HBox row = new HBox(4, indentRegion, iconLabel, titleButton);
    row.getStyleClass().add("zettel-magnify-link-row");
    row.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(titleButton, Priority.ALWAYS);
    return row;
  }

  /**
   * Erzeugt eine einzelne Verweiszeile für einen über- oder untergeordneten Zettel.
   * Pfeilbutton und Titelbutton sind bewusst getrennt:
   * Nur der Pfeil klappt Äste auf oder zu, nur der Titel öffnet den Zettel.
   *
   * @param reference Referenzdaten
   * @param incoming Richtung der Referenz
   * @param depth Einrückungstiefe ab 1
   * @param expandable {@code true}, wenn der Ast sichtbare Kinder besitzt
   * @param collapsed {@code true}, wenn der Ast aktuell eingeklappt ist
   * @return UI-Zeile
   */
  private Node createMagnifyReferenceRow(NoteReferenceInfo reference,
                                         boolean incoming,
                                         int depth,
                                         boolean expandable,
                                         boolean collapsed) {
    String title = reference.title() == null || reference.title().isBlank() ? "—" : reference.title();
    String text = reference.linkType() + ": " + title;

    Region indentRegion = createMagnifyIndentRegion(depth * 16.0);

    Button arrowButton = createMagnifyBranchToggleButton(reference, incoming, expandable, collapsed);

    Button titleButton = createMagnifyTitleButton(text);
    titleButton.setTooltip(new Tooltip(reference.tooltipText()));
    titleButton.setOnAction(e -> openNoteViaToolbar(reference.noteId()));

    HBox row = new HBox(4, indentRegion, arrowButton, titleButton);
    row.getStyleClass().add("zettel-magnify-link-row");
    row.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(titleButton, Priority.ALWAYS);
    return row;
  }

  /**
   * Erzeugt den Pfeilbutton einer MAGNIFY_LINK-Zeile.
   * Nur dieser Button ist für das Auf- und Zuklappen zuständig.
   *
   * @param reference Referenzdaten
   * @param incoming Richtung der Referenz
   * @param expandable {@code true}, wenn sichtbare Kinder vorhanden sind
   * @param collapsed aktueller Klappzustand
   * @return konfigurierter Pfeilbutton
   */
  private Button createMagnifyBranchToggleButton(NoteReferenceInfo reference,
                                                 boolean incoming,
                                                 boolean expandable,
                                                 boolean collapsed) {
    BaseIcon icon;
    if (incoming) {
      icon = collapsed ? BaseIcon.BULLET_ARROW_UP : BaseIcon.BULLET_ARROW_LEFT;
    } else {
      icon = collapsed ? BaseIcon.BULLET_ARROW_DOWN : BaseIcon.BULLET_ARROW_RIGHT;
    }

    Button button = new Button();
    button.getStyleClass().add("zettel-magnify-link-branch-button");
    if (expandable) {
      button.getStyleClass().add("zettel-magnify-link-branch-button-expandable");
      button.setTooltip(new Tooltip(collapsed ? "Zweig aufklappen" : "Zweig einklappen"));
      button.setOnAction(e -> {
        toggleMagnifyBranchCollapsed(reference.noteId(), incoming);
        refreshMagnifyLinkWindow();
      });
    } else {
      button.getStyleClass().add("zettel-magnify-link-branch-button-static");
      button.setMouseTransparent(true);
    }

    button.setGraphic(icon.imageView());
    button.setFocusTraversable(false);
    button.setMinWidth(18);
    button.setPrefWidth(18);
    button.setMaxWidth(18);
    button.setMinHeight(18);
    button.setPrefHeight(18);
    button.setMaxHeight(18);
    return button;
  }

  /**
   * Erzeugt das Einrückungs-Element für eine MAGNIFY_LINK-Zeile.
   * Pfeil und Titel werden dadurch gemeinsam eingerückt.
   *
   * @param leftIndent linke Einrückung in Pixeln
   * @return Einrückungs-Region
   */
  private Region createMagnifyIndentRegion(double leftIndent) {
    Region indentRegion = new Region();
    indentRegion.getStyleClass().add("zettel-magnify-link-indent-region");
    indentRegion.setMinWidth(leftIndent);
    indentRegion.setPrefWidth(leftIndent);
    indentRegion.setMaxWidth(leftIndent);
    return indentRegion;
  }

  /**
   * Erzeugt ein statisches Icon-Label für nicht klappbare Zeilen,
   * etwa für die Wurzelzeile.
   *
   * @param icon darzustellendes Icon
   * @return Label mit Icon
   */
  private Label createMagnifyStaticIconLabel(BaseIcon icon) {
    Label label = new Label();
    label.getStyleClass().add("zettel-magnify-link-static-icon");
    label.setGraphic(icon.imageView());
    label.setMinWidth(18);
    label.setPrefWidth(18);
    label.setMaxWidth(18);
    return label;
  }

  /**
   * Erzeugt den labelartig aussehenden Titelbutton einer MAGNIFY_LINK-Zeile.
   * Nur dieser Button öffnet den referenzierten Zettel.
   *
   * @param text anzuzeigender Text
   * @return konfigurierter Titelbutton
   */
  private Button createMagnifyTitleButton(String text) {
    Label label = new Label(text == null ? "" : text);
    label.getStyleClass().add("zettel-magnify-link-row-label");
    label.setWrapText(false);
    label.setMaxWidth(Double.MAX_VALUE);
    label.setEllipsisString("…");

    HBox graphic = new HBox(label);
    graphic.getStyleClass().add("zettel-magnify-link-row-graphic");
    graphic.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(label, Priority.ALWAYS);
    graphic.setMaxWidth(Double.MAX_VALUE);

    Button button = new Button();
    button.getStyleClass().add("zettel-magnify-link-row-button");
    button.setGraphic(graphic);
    button.setMaxWidth(Double.MAX_VALUE);
    button.setAlignment(Pos.CENTER_LEFT);
    button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    button.setPadding(new Insets(2, 6, 2, 0));
    button.setFocusTraversable(false);
    return button;
  }

  /**
   * Verdrahtet die Bedienelemente des KEY-Fensters.
   */
  private void wireKeyEvents() {
    view.setKeyKeywordEditCommitHandler(this::handleKeyTableKeywordCommit);

    view.getKeyFilterField().textProperty().addListener((obs, oldValue, newValue) -> {
      refreshKeyWindow();
      view.clearKeyActionStatus();
    });

    view.getKeyMediumFilterButton().setOnAction(e -> {
      if (!view.getKeyMediumFilterButton().isSelected()) {
        keyContextFilterUsesAiContext = false;
        view.getKeyAiContextFilterButton().setSelected(false);
      }
      refreshKeyWindow();
      view.clearKeyActionStatus();
    });

    view.getKeyAiContextFilterButton().setOnAction(e -> {
      if (!view.getKeyMediumFilterButton().isSelected()) {
        keyContextFilterUsesAiContext = false;
        view.getKeyAiContextFilterButton().setSelected(false);
      } else {
        keyContextFilterUsesAiContext = view.getKeyAiContextFilterButton().isSelected();
      }
      refreshKeyWindow();
      view.clearKeyActionStatus();
    });

    view.getKeyShowHeadersToggleButton().setOnAction(e -> {
      boolean visible = view.getKeyShowHeadersToggleButton().isSelected();
      view.setKeyHeaderPaneVisible(visible);
      refreshKeyHeaderTable();
    });

    view.getKeyTable().getSelectionModel().selectedItemProperty().addListener(
        (obs, oldValue, newValue) -> refreshKeyHeaderTable()
    );

    view.getKeyHeaderTable().setRowFactory(table -> {
      TableRow<ZettelWindowView.KeyHeaderRow> row = new TableRow<>();
      row.setOnMouseClicked(event -> {
        if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 1 && !row.isEmpty()) {
          openNoteViaToolbar(row.getItem().getNoteId());
        }
      });
      return row;
    });

    // Kein setOnAction am ComboBox-Feld: Der Ersetzen-Dialog darf ausschließlich über den Button ausgelöst werden.
  }

  /**
   * Lädt das KEY-Fenster aus der Datenbank neu.
   * Ein vorhandener Sortierzustand der KEY-Tabelle wird vor dem Refresh
   * gesichert und danach wiederhergestellt.
   */
  private void refreshKeyWindow() {
    ZettelWindowView.KeyTableSortState sortState = view.snapshotKeyTableSortState();

    String filterText = normalizeKeyword(view.getKeyFilterField().getText());
    DynamicBibliographyEntry currentBibliographyEntry = resolveCurrentDynamicBibliographyEntry();
    syncKeyContextFilterButtons(currentBibliographyEntry);

    List<ZettelWindowView.KeyTableRow> contextRows = loadKeyContextRows(currentBibliographyEntry);
    List<ZettelWindowView.KeyTableRow> rows = loadGlobalKeyRowsExcluding(contextRows);

    List<ZettelWindowView.KeyTableRow> visibleRows = new ArrayList<>();
    visibleRows.addAll(applyKeyFilter(contextRows, filterText));
    visibleRows.addAll(applyKeyFilter(rows, filterText));

    int totalKeywordCount = contextRows.size() + rows.size();

    view.setKeyRows(visibleRows);
    view.restoreKeyTableSortState(sortState);
    view.setKeyInventoryStatus(buildKeyInventoryStatusText(visibleRows.size(), filterText, totalKeywordCount));
    refreshKeyHeaderTable();
  }

  /**
   * Synchronisiert Sichtbarkeit und Aktivierbarkeit der KEY-Kontextbuttons.
   *
   * @param currentBibliographyEntry aktuell verknüpfter bibliographischer Eintrag
   */
  private void syncKeyContextFilterButtons(DynamicBibliographyEntry currentBibliographyEntry) {
    boolean aiText = isAiTextEntry(currentBibliographyEntry);
    boolean mediumFilterActive = view.getKeyMediumFilterButton().isSelected();

    view.getKeyAiContextFilterButton().setVisible(aiText);
    view.getKeyAiContextFilterButton().setManaged(aiText);
    view.getKeyAiContextFilterButton().setDisable(!aiText || !mediumFilterActive);

    if (!aiText || !mediumFilterActive) {
      keyContextFilterUsesAiContext = false;
      view.getKeyAiContextFilterButton().setSelected(false);
    } else {
      view.getKeyAiContextFilterButton().setSelected(keyContextFilterUsesAiContext);
    }
  }

  /**
   * Lädt die hervorgehobene Zusatzliste des KEY-Fensters.
   *
   * @param currentBibliographyEntry aktuell verknüpfter bibliographischer Eintrag
   * @return Zusatzzeilen für Medium oder KI-Kontext
   */
  private List<ZettelWindowView.KeyTableRow> loadKeyContextRows(DynamicBibliographyEntry currentBibliographyEntry) {
    if (!view.getKeyMediumFilterButton().isSelected()
            || currentNote == null
            || currentNote.getBibliographyRefId() == null
            || currentNote.getBibliographyRefId() <= 0) {
      return List.of();
    }

    if (keyContextFilterUsesAiContext && isAiTextEntry(currentBibliographyEntry)) {
      String aiContext = extractStringBibValue(currentBibliographyEntry, "ai_context");
      return toKeyRows(noteRepository.loadKeywordUsageRowsForAiContext(aiContext), true);
    }

    return toKeyRows(
        noteRepository.loadKeywordUsageRowsForBibliographyEntry(currentNote.getBibliographyRefId()),
        true
    );
  }

  /**
   * Lädt die globale KEY-Liste ohne Schlagwörter, die bereits in der Zusatzliste stehen.
   *
   * @param contextRows bereits geladene Zusatzzeilen
   * @return reguläre KEY-Zeilen
   */
  private List<ZettelWindowView.KeyTableRow> loadGlobalKeyRowsExcluding(List<ZettelWindowView.KeyTableRow> contextRows) {
    Set<Integer> contextKeywordIds = new HashSet<>();
    if (contextRows != null) {
      for (ZettelWindowView.KeyTableRow row : contextRows) {
        contextKeywordIds.add(row.getKeywordId());
      }
    }

    List<ZettelWindowView.KeyTableRow> rows = new ArrayList<>();
    for (NoteRepository.KeywordUsageRow row : noteRepository.loadKeywordUsageRows()) {
      if (!contextKeywordIds.contains(row.id())) {
        rows.add(new KeyTableRow(row.id(), row.keyword(), row.count()));
      }
    }
    return rows;
  }

  /**
   * Wandelt Repository-Zeilen in KEY-Tabellenzeilen um.
   *
   * @param rows Repository-Zeilen
   * @param contextRow {@code true}, wenn die Zeilen zur Zusatzliste gehören
   * @return Tabellenzeilen
   */
  private List<ZettelWindowView.KeyTableRow> toKeyRows(List<NoteRepository.KeywordUsageRow> rows, boolean contextRow) {
    if (rows == null || rows.isEmpty()) {
      return List.of();
    }

    List<ZettelWindowView.KeyTableRow> mappedRows = new ArrayList<>();
    for (NoteRepository.KeywordUsageRow row : rows) {
      mappedRows.add(new KeyTableRow(row.id(), row.keyword(), row.count(), contextRow));
    }
    return mappedRows;
  }

  /**
   * Liest einen String-Wert aus einem dynamischen bibliographischen Eintrag.
   *
   * @param entry bibliographischer Eintrag
   * @param bibtexName technischer Feldname
   * @return String-Wert oder leerer String
   */
  private String extractStringBibValue(DynamicBibliographyEntry entry, String bibtexName) {
    if (entry == null || bibtexName == null || bibtexName.isBlank()) {
      return "";
    }

    return entry.findValue(bibtexName)
                .filter(StringBibValue.class::isInstance)
                .map(StringBibValue.class::cast)
                .map(StringBibValue::value)
                .map(this::safeTrim)
                .orElse("");
  }

  /**
   * Filtert und sortiert die KEY-Zeilen gemäß aktuellem Suchtext.
   * Ohne Suchtext werden alle Zeilen unverändert zurückgegeben.
   *
   * @param rows vollständige Zeilenliste
   * @param filterText Suchtext
   * @return gefilterte und ggf. vorsortierte Liste
   */
  private List<ZettelWindowView.KeyTableRow> applyKeyFilter(List<ZettelWindowView.KeyTableRow> rows, String filterText) {
    if (filterText.isBlank()) {
      return rows;
    }

    String lowerFilter = filterText.toLowerCase(Locale.ROOT);

    return rows.stream()
               .filter(row -> row.getKeyword() != null
                                  && row.getKeyword().toLowerCase(Locale.ROOT).contains(lowerFilter))
               .sorted((left, right) -> compareKeyRowsByFilterMatch(left, right, lowerFilter))
               .toList();
  }

  /**
   * Vergleicht zwei KEY-Zeilen anhand der Position des Suchtreffers.
   * Frühere Trefferpositionen kommen zuerst, danach wird alphabetisch sortiert.
   *
   * @param left linke Zeile
   * @param right rechte Zeile
   * @param lowerFilter Suchtext in Kleinschreibung
   * @return Vergleichsergebnis
   */
  private int compareKeyRowsByFilterMatch(
      ZettelWindowView.KeyTableRow left,
      ZettelWindowView.KeyTableRow right,
      String lowerFilter) {

    String leftKeyword = left.getKeyword() == null ? "" : left.getKeyword();
    String rightKeyword = right.getKeyword() == null ? "" : right.getKeyword();

    int leftIndex = leftKeyword.toLowerCase(Locale.ROOT).indexOf(lowerFilter);
    int rightIndex = rightKeyword.toLowerCase(Locale.ROOT).indexOf(lowerFilter);

    int byPosition = Integer.compare(leftIndex, rightIndex);
    if (byPosition != 0) {
      return byPosition;
    }

    return String.CASE_INSENSITIVE_ORDER.compare(leftKeyword, rightKeyword);
  }

  /**
   * Erzeugt den Bestandsstatus für das KEY-Fenster.
   *
   * @param visibleKeywordCount Anzahl aktuell sichtbarer Schlagwörter
   * @param filterText aktueller Suchtext
   * @param totalKeywordCount Gesamtanzahl aller Schlagwörter
   * @return formatierter Status
   */
  private String buildKeyInventoryStatusText(int visibleKeywordCount, String filterText, int totalKeywordCount) {
    if (filterText.isBlank()) {
      if (visibleKeywordCount <= 0) {
        return "Keine Schlagwörter vorhanden";
      }
      if (visibleKeywordCount == 1) {
        return "1 Schlagwort";
      }
      return visibleKeywordCount + " Schlagwörter";
    }

    if (visibleKeywordCount <= 0) {
      return "Keine Treffer für „" + filterText + "“";
    }

    if (visibleKeywordCount == 1) {
      return "1 Treffer für „" + filterText + "“ in " + totalKeywordCount + " Schlagwörtern";
    }

    return visibleKeywordCount + " Treffer für „" + filterText + "“ in " + totalKeywordCount + " Schlagwörtern";
  }

  /**
   * Aktualisiert die Suchvorschläge für das zweite Eingabefeld.
   *
   * @param text aktueller Eingabetext
   */
  private void refreshKeyReplaceSuggestions(String text) {
    String normalized = normalizeKeyword(text);
    if (normalized.length() < 3) {
      view.setKeyReplaceSuggestions(List.of());
      view.hideKeyReplaceSuggestions();
      return;
    }

    List<String> suggestions = noteRepository.searchKeywordsContainingIgnoreCase(normalized, 15);
    view.setKeyReplaceSuggestions(suggestions);

    if (suggestions.isEmpty()) {
      view.hideKeyReplaceSuggestions();
    } else {
      view.showKeyReplaceSuggestions();
    }
  }

  /**
   * Reagiert auf das Abschließen einer Inline-Bearbeitung in der Schlagworttabelle.
   *
   * @param row bearbeitete Tabellenzeile
   * @param newKeyword neu eingegebener Wert
   * @return {@code true}, wenn die Änderung übernommen wurde
   */
  private boolean handleKeyTableKeywordCommit(ZettelWindowView.KeyTableRow row, String newKeyword) {
    if (row == null) {
      return false;
    }

    String normalized = normalizeKeyword(newKeyword);

    if (normalized.isBlank()) {
      noteRepository.deleteKeyword(row.getKeywordId());
      syncCurrentNoteKeywordsAfterRepositoryChange(row.getKeyword(), "");
      refreshKeyWindow();
      view.setKeyKeepKeyword("");
      view.setKeyActionStatus("Schlagwort gelöscht", ZettelWindowView.KeyStatusType.SUCCESS);
      return true;
    }

    Optional<NoteRepository.KeywordUsageRow> existing =
        noteRepository.findKeywordByExactIgnoreCase(normalized);

    if (existing.isPresent() && existing.get().id() != row.getKeywordId()) {
      Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
      alert.initOwner(stage);
      alert.setTitle("Schlagwörter zusammenführen");
      alert.setHeaderText("Schlagwort existiert bereits");
      alert.setContentText(
          "Das Schlagwort „" + normalized + "“ existiert bereits.\n" +
              "Soll „" + row.getKeyword() + "“ mit diesem Schlagwort zusammengeführt werden?"
      );

      Optional<ButtonType> result = alert.showAndWait();
      if (result.isPresent() && result.get() == ButtonType.OK) {
        noteRepository.mergeKeywords(existing.get().id(), row.getKeywordId());
        syncCurrentNoteKeywordsAfterRepositoryChange(row.getKeyword(), existing.get().keyword());
        refreshKeyWindow();
        view.setKeyKeepKeyword(existing.get().keyword());
        view.setKeyActionStatus("Schlagwörter zusammengeführt", ZettelWindowView.KeyStatusType.SUCCESS);
        return true;
      }

      view.setKeyActionStatus("Zusammenführen abgebrochen", ZettelWindowView.KeyStatusType.WARNING);
      return false;
    }

    noteRepository.renameKeyword(row.getKeywordId(), normalized);
    syncCurrentNoteKeywordsAfterRepositoryChange(row.getKeyword(), normalized);
    refreshKeyWindow();
    view.setKeyKeepKeyword(normalized);
    view.setKeyActionStatus("Schlagwort umbenannt", ZettelWindowView.KeyStatusType.SUCCESS);
    return true;
  }

  /**
   * Führt die Ersetzungslogik des KEY-Fensters aus.
   * Der Dialog erscheint ausschließlich über den Button „Ersetzen“.
   * Existiert das Schlagwort aus dem zweiten Feld nicht in der Datenbank,
   * wird keine Änderung vorgenommen.
   */
  private void handleKeyReplaceAction() {
    String keepKeyword = normalizeKeyword(view.getKeyKeepField().getText());
    String replaceKeyword = normalizeKeyword(view.getKeyReplaceField().getEditor().getText());

    if (keepKeyword.isBlank() || replaceKeyword.isBlank()) {
      view.setKeyActionStatus("Beide Eingabefelder müssen gefüllt sein", ZettelWindowView.KeyStatusType.ERROR);
      return;
    }

    if (keepKeyword.equalsIgnoreCase(replaceKeyword)) {
      view.setKeyActionStatus("Die Schlagwörter sind identisch", ZettelWindowView.KeyStatusType.WARNING);
      return;
    }

    Optional<NoteRepository.KeywordUsageRow> replaceRow =
        noteRepository.findKeywordByExactIgnoreCase(replaceKeyword);

    // Existiert das zweite Schlagwort nicht, geschieht fachlich nichts. Also: kein Dialog, keine DB-Änderung.
    if (replaceRow.isEmpty()) {
      view.setKeyActionStatus("Das zu ersetzende Schlagwort existiert nicht", ZettelWindowView.KeyStatusType.WARNING);
      return;
    }

    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
    alert.initOwner(stage);
    alert.setTitle("Schlagwort ersetzen");
    alert.setHeaderText("Ersetzen bestätigen");
    alert.setContentText(
        "Soll das Schlagwort „" + keepKeyword + "“ über das Schlagwort „" + replaceRow.get().keyword() + "“ geschrieben werden?"
    );

    Optional<ButtonType> result = alert.showAndWait();
    if (result.isEmpty() || result.get() != ButtonType.OK) {
      view.setKeyActionStatus("Ersetzen abgebrochen", ZettelWindowView.KeyStatusType.WARNING);
      return;
    }

    Optional<NoteRepository.KeywordUsageRow> keepRow =
        noteRepository.findKeywordByExactIgnoreCase(keepKeyword);

    String effectiveReplaceKeyword = replaceRow.get().keyword();
    String effectiveKeepKeyword;

    if (keepRow.isPresent()) {
      noteRepository.mergeKeywords(keepRow.get().id(), replaceRow.get().id());
      effectiveKeepKeyword = keepRow.get().keyword();
    } else {
      noteRepository.renameKeyword(replaceRow.get().id(), keepKeyword);
      effectiveKeepKeyword = keepKeyword;
    }

    syncCurrentNoteKeywordsAfterRepositoryChange(effectiveReplaceKeyword, effectiveKeepKeyword);
    refreshKeyWindow();
    view.setKeyKeepKeyword(effectiveKeepKeyword);
    view.getKeyReplaceField().getEditor().setText("");
    view.hideKeyReplaceSuggestions();
    view.setKeyActionStatus("Schlagwort erfolgreich ersetzt", ZettelWindowView.KeyStatusType.SUCCESS);
  }

  /**
   * Aktualisiert die untere Zettelkopf-Tabelle des KEY-Fensters.
   * Es werden immer alle aktuell sichtbaren Schlagwörter der KEY-Tabelle verwendet.
   * Die Treffer werden dedupliziert und nach Zettelnummer aufsteigend angezeigt.
   */
  private void refreshKeyHeaderTable() {
    if (!view.getKeyShowHeadersToggleButton().isSelected()) {
      view.setKeyHeaderRows(List.of());
      return;
    }

    List<String> visibleKeywords = view.getKeyTable().getItems().stream()
                                       .map(KeyTableRow::getKeyword)
                                       .filter(Objects::nonNull)
                                       .map(this::normalizeKeyword)
                                       .filter(keyword -> !keyword.isBlank())
                                       .toList();

    if (visibleKeywords.isEmpty()) {
      view.setKeyHeaderRows(List.of());
      return;
    }

    List<ZettelWindowView.KeyHeaderRow> rows = new ArrayList<>();
    for (NoteRepository.NoteHeaderRow row : noteRepository.loadNoteHeadersForVisibleKeywords(visibleKeywords)) {
      rows.add(new ZettelWindowView.KeyHeaderRow(row.noteId(), row.title()));
    }

    view.setKeyHeaderRows(rows);
  }

  /**
   * Lädt Schlagwort-Vorschläge für das Inline-Edit im keywordsTablePane.
   * Die aktuell eingegebene Zeichenkette wird case-insensitiv innerhalb
   * der vorhandenen Schlagwörter gesucht.
   *
   * @param request KeywordSuggestionRequest
   * @return maximal 15 passende Schlagwörter
   */
  private List<String> loadKeywordSuggestionsForInlineEdit(KeywordsTablePane.KeywordSuggestionRequest request) {
    String normalized = normalizeKeyword(request.text());
    if (normalized.isBlank()) {
      return List.of();
    }

    List<String> raw = noteRepository.searchKeywordsUnicodeAware(normalized, request.prefixOnly(), request.allTokens(), 40);
    return preferCaseAwareDuplicates(raw, normalized).stream()
               .filter(keyword -> !keyword.equals(normalized))
               .limit(15)
               .toList();
  }

  /**
   * Filtert case-insensitive Duplikatgruppen so, dass bei mehrfach vorhandenen
   * Schreibweisen die zur Eingabe passende Gross-/Kleinschreibung bevorzugt wird.
   *
   * @param suggestions Vorschlaege
   * @param typedText Eingabe
   * @return bereinigte Vorschlaege
   */
  private List<String> preferCaseAwareDuplicates(List<String> suggestions, String typedText) {
    Map<String, List<String>> groups = new LinkedHashMap<>();
    for (String suggestion : suggestions) {
      groups.computeIfAbsent(lowerKeyword(suggestion), ignored -> new ArrayList<>()).add(suggestion);
    }

    List<String> out = new ArrayList<>();
    for (List<String> group : groups.values()) {
      if (group.size() <= 1) {
        out.addAll(group);
        continue;
      }
      List<String> caseMatches = group.stream()
                                      .filter(value -> value.contains(typedText) || value.startsWith(typedText))
                                      .toList();
      out.addAll(caseMatches.isEmpty() ? group : caseMatches);
    }
    return out;
  }

  /**
   * Fuegt dem aktuellen Zettel ein Schlagwort hinzu und aktualisiert die Ansicht sofort.
   *
   * @param keyword Schlagwort
   */
  private void addKeywordToCurrentNote(String keyword) {
    String normalized = normalizeKeyword(keyword);
    if (currentNote == null || normalized.isBlank()) {
      return;
    }
    if (currentNote.getKeywords().stream().anyMatch(existing -> existing.equals(normalized))) {
      return;
    }
    currentNote.getKeywords().add(normalized);
    view.getKeywordsTablePane().setKeywords(currentNote.getKeywords());
    saveCurrentIfValid();
    refreshKeywordViews();
    refreshListWindow();
  }

  /**
   * Wandelt einen Schlagworttext unicodefaehig in Kleinschreibung.
   *
   * @param value Rohwert
   * @return normalisierter Wert
   */
  private String lowerKeyword(String value) {
    return value == null ? "" : value.toLowerCase(Locale.GERMAN);
  }

  /**
   * Aktualisiert den sichtbaren Inhalt des LIST-Fensters.
   */
  private void refreshListWindow() {
    refreshListWindow(false);
  }

  /**
   * Aktualisiert den sichtbaren Inhalt des LIST-Fensters.
   *
   * @param force {@code true}, wenn auch eine gesperrte Liste neu aufgebaut werden soll
   */
  private void refreshListWindow(boolean force) {
    if (view.getActiveServiceAreaMode() != ZettelWindowView.ServiceAreaMode.LIST) {
      return;
    }
    if (!force && view.getListLockToggleButton().isSelected()) {
      return;
    }

    if (currentNote == null || currentNote.getId() <= 0) {
      view.setListPageKeyRows(List.of(createListStatusLabel("Kein aktiver Zettel")));
      view.setListFilterRows(List.of());
      return;
    }

    Map<Integer, ListNoteData> notesById = loadListNoteData();
    Map<String, SortedSet<Integer>> keywordIndex = buildKeywordIndex(notesById);

    if (view.getActiveListMode() == ZettelWindowView.ListMode.PAGE_KEY) {
      refreshListPageKeyWindow(notesById, keywordIndex);
    } else {
      refreshListFilterWindow(notesById, keywordIndex);
    }
  }

  /**
   * Aktualisiert LIST nach einem Zettelwechsel nur dann, wenn die aktuelle
   * Ansicht vom aktiven Zettel abhaengt. Die Filtertabelle bleibt dadurch
   * schnell anklickbar und wird beim Oeffnen eines Treffers nicht neu berechnet.
   */
  private void refreshListWindowAfterNoteActivation() {
    if (view.getActiveListMode() == ZettelWindowView.ListMode.FILTER) {
      return;
    }
    refreshListWindow(false);
  }

  /**
   * Fordert eine entprellte Aktualisierung des LIST-Fensters an.
   */
  private void requestListRefresh() {
    listRefreshDebounce.playFromStart();
  }

  /**
   * Lädt alle LIST-relevanten Zetteldaten aus der Datenbank und gruppiert sie pro Zettel.
   *
   * @return Zettelindex nach ID
   */
  private Map<Integer, ListNoteData> loadListNoteData() {
    Map<Integer, ListNoteData> out = new LinkedHashMap<>();

    for (NoteRepository.ListBrowseRow row : noteRepository.loadListBrowseRows()) {
      ListNoteData data = out.computeIfAbsent(
          row.noteId(),
          id -> new ListNoteData(id, row.title(), row.bodyBlob(), row.bodyCodec())
      );

      String keyword = row.keyword() == null ? "" : row.keyword().trim();
      if (!keyword.isBlank()) {
        data.keywords.add(keyword);
      }
    }

    return out;
  }

  /**
   * Baut einen Schlagwortindex auf, der pro Schlagwort alle zugehörigen Zettelnummern enthält.
   *
   * @param notesById gruppierte Zetteldaten
   * @return Schlagwortindex
   */
  private Map<String, SortedSet<Integer>> buildKeywordIndex(Map<Integer, ListNoteData> notesById) {
    Map<String, SortedSet<Integer>> out = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    for (ListNoteData note : notesById.values()) {
      for (String keyword : note.keywords) {
        out.computeIfAbsent(keyword, ignored -> new TreeSet<>()).add(note.noteId);
      }
    }

    return out;
  }

  /**
   * Rendert die PAGE_KEY-Ansicht.
   *
   * @param notesById gruppierte Zetteldaten
   * @param keywordIndex Schlagwortindex
   */
  private void refreshListPageKeyWindow(Map<Integer, ListNoteData> notesById,
                                        Map<String, SortedSet<Integer>> keywordIndex) {
    ListNoteData root = notesById.get(currentNote.getId());
    if (root == null) {
      view.setListPageKeyRows(List.of(createListStatusLabel("Aktueller Zettel nicht gefunden")));
      return;
    }

    List<Node> rows = new ArrayList<>();
    rows.add(createListRootTitle(root.title));

    int maxDepth = (int) Math.round(view.getListDepthSlider().getValue());
    appendPageKeyRows(
        rows,
        root,
        notesById,
        keywordIndex,
        1,
        maxDepth,
        new LinkedHashSet<>(),
        new LinkedHashSet<>(Set.of(root.noteId))
    );

    view.setListPageKeyRows(rows);
  }

  /**
   * Baut die PAGE_KEY-Struktur rekursiv auf.
   *
   * @param rows Zielknoten
   * @param note aktueller Zettel
   * @param notesById Zettelindex
   * @param keywordIndex Schlagwortindex
   * @param depth aktuelle Tiefe
   * @param maxDepth maximale Tiefe
   * @param excludedKeywords Schlagwörter, die in dieser und tieferen Ebenen nicht erneut auftauchen sollen
   * @param visitedNoteIds bereits im aktuellen Pfad besuchte Zettelnummern
   */
  private void appendPageKeyRows(List<Node> rows,
                                 ListNoteData note,
                                 Map<Integer, ListNoteData> notesById,
                                 Map<String, SortedSet<Integer>> keywordIndex,
                                 int depth,
                                 int maxDepth,
                                 Set<String> excludedKeywords,
                                 LinkedHashSet<Integer> visitedNoteIds) {
    if (depth > maxDepth) {
      return;
    }

    List<String> visibleKeywords = note.keywords.stream()
                                       .filter(keyword -> !containsIgnoreCase(excludedKeywords, keyword))
                                       .sorted(String.CASE_INSENSITIVE_ORDER)
                                       .toList();

    for (String keyword : visibleKeywords) {
      rows.add(createPageKeyKeywordLabel(keyword, depth));

      SortedSet<Integer> relatedIds = keywordIndex.getOrDefault(keyword, new TreeSet<>());
      for (Integer relatedId : relatedIds) {
        if (relatedId == null || relatedId <= 0 || relatedId == note.noteId || visitedNoteIds.contains(relatedId)) {
          continue;
        }

        ListNoteData related = notesById.get(relatedId);
        if (related == null) {
          continue;
        }

        rows.add(createPageKeyNoteButton(related.noteId, related.title, depth));

        LinkedHashSet<Integer> nextVisited = new LinkedHashSet<>(visitedNoteIds);
        nextVisited.add(related.noteId);

        LinkedHashSet<String> nextExcluded = new LinkedHashSet<>(excludedKeywords);
        nextExcluded.addAll(note.keywords);

        appendPageKeyRows(
            rows,
            related,
            notesById,
            keywordIndex,
            depth + 1,
            maxDepth,
            nextExcluded,
            nextVisited
        );
      }
    }
  }

  /**
   * Rendert die LIST-Filteransicht.
   *
   * @param notesById gruppierte Zetteldaten
   * @param keywordIndex Schlagwortindex
   */
  private void refreshListFilterWindow(Map<Integer, ListNoteData> notesById,
                                       Map<String, SortedSet<Integer>> keywordIndex) {
    String filterText = normalizeFilterText(view.getListFilterField().getText());
    boolean fullText = view.getListFullTextToggleButton().isSelected();
    boolean emptyFilter = filterText.isBlank();

    List<ZettelWindowView.ListFilterRow> rows = new ArrayList<>();

    for (ListNoteData note : notesById.values()) {
      boolean titleMatch = emptyFilter || containsIgnoreCase(note.title, filterText);
      boolean keywordMatch = emptyFilter || note.keywords.stream().anyMatch(keyword -> containsIgnoreCase(keyword, filterText));

      boolean bodyMatch = false;
      if (!emptyFilter && fullText) {
        String plainText = decodePlainText(note);
        bodyMatch = containsIgnoreCase(plainText, filterText);
      }

      boolean include = emptyFilter || titleMatch || keywordMatch || bodyMatch;
      if (!include) {
        continue;
      }

      boolean bodyMatchOnly = !emptyFilter && bodyMatch && !titleMatch && !keywordMatch;
      Integer linkCount = computeLinkCount(note, keywordIndex);

      rows.add(new ZettelWindowView.ListFilterRow(
          note.noteId,
          note.title,
          linkCount,
          bodyMatchOnly
      ));
    }

    view.setListFilterRows(rows);
  }

  /**
   * Berechnet die Anzahl verschiedener anderer Zettel, die über mindestens ein gemeinsames Schlagwort
   * mit dem gegebenen Zettel verbunden sind.
   *
   * @param note Zettel
   * @param keywordIndex Schlagwortindex
   * @return Anzahl verknüpfter Zettel oder {@code null}, wenn der Zettel kein Schlagwort besitzt
   */
  private Integer computeLinkCount(ListNoteData note, Map<String, SortedSet<Integer>> keywordIndex) {
    if (note.keywords.isEmpty()) {
      return null;
    }

    Set<Integer> linked = new LinkedHashSet<>();
    for (String keyword : note.keywords) {
      for (Integer targetId : keywordIndex.getOrDefault(keyword, new TreeSet<>())) {
        if (targetId != null && targetId != note.noteId) {
          linked.add(targetId);
        }
      }
    }
    return linked.size();
  }

  /**
   * Dekodiert den gespeicherten RichText-Inhalt eines Zettels in Plaintext.
   *
   * @param note Zetteldaten
   * @return durchsuchbarer Text
   */
  private String decodePlainText(ListNoteData note) {
    if (note.bodyBlob == null || note.bodyBlob.length == 0) {
      return "";
    }

    try {
      if (HtmlBodyCodec.CODEC_NAME.equals(note.bodyCodec)) {
        return htmlToPlainText(new String(note.bodyBlob, StandardCharsets.UTF_8));
      }
      if (InlineCssRtfxBlobCodec.CODEC_NAME.equals(note.bodyCodec)) {
        return InlineCssRtfxBlobCodec.decodeFromGzipToPlainText(note.bodyBlob);
      }
      return "";
    } catch (Exception ex) {
      return "";
    }
  }

  private String htmlToPlainText(String html) {
    if (html == null || html.isBlank()) {
      return "";
    }
    return html.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
               .replaceAll("(?i)<br\\s*/?>", "\n")
               .replaceAll("(?i)</(p|div|li|tr|h[1-6])>", "\n")
               .replaceAll("(?s)<[^>]+>", " ")
               .replace("&nbsp;", " ")
               .replace("&lt;", "<")
               .replace("&gt;", ">")
               .replace("&amp;", "&")
               .trim();
  }

  /**
   * Normalisiert einen Filtertext für case-insensitive Enthält-Suchen.
   *
   * @param text Eingabetext
   * @return normalisierter Text
   */
  private String normalizeFilterText(String text) {
    return text == null ? "" : text.trim().toLowerCase(Locale.GERMAN);
  }

  /**
   * Prüft case-insensitiv, ob ein Text einen Suchtext enthält.
   *
   * @param text Quelltext
   * @param needle Suchtext
   * @return {@code true}, wenn Treffer vorliegt
   */
  private boolean containsIgnoreCase(String text, String needle) {
    String source = text == null ? "" : text.toLowerCase(Locale.GERMAN);
    String search = needle == null ? "" : needle.toLowerCase(Locale.GERMAN);
    return source.contains(search);
  }

  /**
   * Prüft case-insensitiv, ob eine Sammlung einen bestimmten Text enthält.
   *
   * @param values Quellwerte
   * @param needle Suchtext
   * @return {@code true}, wenn mindestens ein exakt gleicher Wert vorliegt
   */
  private boolean containsIgnoreCase(Collection<String> values, String needle) {
    if (values == null || values.isEmpty()) {
      return false;
    }
    for (String value : values) {
      if (value != null && value.equalsIgnoreCase(needle)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Erzeugt die Wurzelüberschrift der PAGE_KEY-Ansicht.
   *
   * @param title Überschrift des aktuellen Zettels
   * @return UI-Knoten
   */
  private Node createListRootTitle(String title) {
    Label label = new Label(title == null || title.isBlank() ? "—" : title);
    label.getStyleClass().add("zettel-list-root-title");
    label.setWrapText(false);

    VBox box = new VBox(label);
    box.getStyleClass().add("zettel-list-root-title-box");
    return box;
  }

  /**
   * Erzeugt ein Schlagwortlabel für die PAGE_KEY-Ansicht.
   *
   * @param keyword Schlagwort
   * @param depth aktuelle Ebene
   * @return UI-Knoten
   */
  private Node createPageKeyKeywordLabel(String keyword, int depth) {
    Label label = new Label(keyword == null ? "" : keyword);
    label.getStyleClass().add("zettel-list-keyword-label");
    label.setPadding(new Insets(depth == 1 ? 10 : 6, 0, 0, depth * 16.0));
    return label;
  }

  /**
   * Erzeugt einen anklickbaren Zettelkopf für die PAGE_KEY-Ansicht.
   *
   * @param noteId Zettelnummer
   * @param title Überschrift
   * @param depth aktuelle Ebene
   * @return UI-Knoten
   */
  private Node createPageKeyNoteButton(int noteId, String title, int depth) {
    Button button = new Button(noteId + " " + (title == null ? "" : title));
    button.getStyleClass().add("zettel-list-note-button");
    button.setMaxWidth(Double.MAX_VALUE);
    button.setAlignment(Pos.CENTER_LEFT);
    button.setWrapText(false);
    button.setPadding(new Insets(2, 0, 2, depth * 16.0));
    button.setOnAction(e -> openNoteFromList(noteId));
    return button;
  }

  /**
   * Erzeugt ein einfaches Statuslabel für LIST.
   *
   * @param text Meldungstext
   * @return UI-Knoten
   */
  private Node createListStatusLabel(String text) {
    Label label = new Label(text == null ? "" : text);
    label.getStyleClass().add("zettel-list-status-label");
    return label;
  }

  /**
   * Normalisiert einen Schlagworttext.
   *
   * @param value Rohtext
   * @return getrimmter Text oder Leerstring
   */
  private String normalizeKeyword(String value) {
    return value == null ? "" : value.trim();
  }
}


