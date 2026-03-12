package de.zettelkastenfx.notes.controller;

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
import de.zettelkastenfx.notes.model.Note;
import de.zettelkastenfx.persistence.NoteRepository;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.fxmisc.richtext.model.StyledDocument;

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

  // für BottomToolbar
  private final Deque<Integer> backHistory = new ArrayDeque<>();
  private final Deque<Integer> forwardHistory = new ArrayDeque<>();
  private boolean historyNavigation;

  // Toolbar/Notizen: einfacher Session-Store (damit du mehrere Zettel testen kannst)
  private final NoteMemoryStore store = new NoteMemoryStore();

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

    // Dropdown für Folgezettel
    view.getMiFollowing().setOnAction(e -> System.out.println("Folgezettel (kommt gleich)"));
    view.getMiIdea().setOnAction(e -> System.out.println("Idee (kommt gleich)"));
    view.getMiQuestion().setOnAction(e -> System.out.println("Frage (kommt gleich)"));
    view.getMiCritique().setOnAction(e -> System.out.println("Kritik (kommt gleich)"));
    view.getMiTask().setOnAction(e -> System.out.println("Aufgabe (kommt gleich)"));

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

    stage.setOnCloseRequest(e -> saveCurrentIfValid());
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
    renderBibliographyHost();
    syncBottomToolbar(currentNote);
    view.getNoteEditorPane().setBibliographyVisible(
        view.getBottomToolbar().bookToggle().isSelected()
    );
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


  /**
   * Öffnet den angegebenen Zettel
   *
   * @param id
   */
  private void openNote(int id) {
    loading = true;
    try {
      var opt = noteRepository.load(id);
      if (opt.isEmpty()) return;

      currentNote = opt.get();
      currentNote.applyTo(view.getNoteEditorPane(), view.getKeywordsTablePane());
    } finally {
      loading = false;
    }
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

  private void openNoteViaToolbar(int id) {
    if (!historyNavigation) {
      if (currentNote != null && currentNote.getId() > 0 && currentNote.getId() != id) {
        backHistory.push(currentNote.getId());
        forwardHistory.clear();
      }
    }

    // HIER: deine echte Lade-/Wechsel-Logik aufrufen
    // (Sobald wir Persistenz/Load drin haben, wird das eine Zeile: openNote(id);)
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
      renderBibliographyHost();

      // BottomToolbar befüllen (nur Anzeige, Feld bleibt editierbar)
      setNoteIdField(currentNote.getId());

      // Counts nach dem Laden sofort aktualisieren
      refreshKeywordCounts();

      view.getNoteEditorPane().setOnNavigateToNote(this::openNoteViaToolbar); // klick im Popup lädt Zettel
      view.getNoteEditorPane().setBibliographyVisible(view.getBottomToolbar().bookToggle().isSelected());
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
    if (current != null && current > 0) {
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
    if (current != null && current > 0) {
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

  private static final class NoteMemoryStore {
    private final Map<Integer, NoteSnapshot> notes = new HashMap<>();

    int count() { return notes.size(); }

    java.util.Optional<NoteSnapshot> get(int id) {
      return Optional.ofNullable(notes.get(id));
    }

    void put(NoteSnapshot snap) {
      notes.put(snap.id(), snap);
    }
  }

  private record NoteSnapshot(
      int id,
      String title,
      StyledDocument<String, String, String> bodyDoc,
      List<String> keywords
  ) {}

  /**
   * erstellt einen neuen Zettel mit maxId+1
   */
  private void createNewEmptyNote() {
    saveCurrentIfValid(); // aktuellen ggf. sichern

    currentNote = new Note();
    currentNote.setId(allocateNextNoteId());

    // UI leeren (über Note, damit nichts “verstreut” bleibt)
    applyCurrentNoteToUi();
    InlineCssStyleUtil.clearHeadingForSelection(view.getNoteEditorPane().getBodyArea());

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
    saveCurrentIfValid();

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

  private void createCopyBibliography() {
    // Bibliografie-Übernahme kommt in 3.3; aktuell legen wir den neuen Zettel schon an (leer),
    // damit dein Workflow “Klick -> neuer Zettel” schon funktioniert.
    createNewEmptyNote();
  }

  private void saveCurrentIfValid() {
    if (loading) return;

    var editor = view.getNoteEditorPane();

    String title = editor.getTitleEditorArea().getText() == null ? "" : editor.getTitleEditorArea().getText().trim();
    String bodyPlain = editor.getBodyArea().getText() == null ? "" : editor.getBodyArea().getText().trim();

    // speichern erst wenn Überschrift und Inhalt je >= 1 Zeichen
    if (title.isEmpty() || bodyPlain.isEmpty()) return;

    if (currentNote.getId() <= 0) {
      currentNote.setId(allocateNextNoteId());
    }

    // UI -> Note
    currentNote.captureFrom(view.getNoteEditorPane(), view.getKeywordsTablePane());

    int savedId = noteRepository.save(currentNote);
    currentNote.setId(savedId);
    maxNoteId = Math.max(maxNoteId, savedId);

    setNoteIdField(savedId);
    refreshKeywordCounts();
    renderBibliographyHost();
    syncBottomToolbar(currentNote);
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

  private void openPrevNoteWrap() {
    int max = noteRepository.maxNoteId();
    if (max <= 0) return;

    saveCurrentIfValid();

    int cur = (currentNote != null) ? currentNote.getId() : 0;
    if (cur <= 0) cur = 1;

    int target = (cur <= 1) ? max : (cur - 1);
    openNoteViaToolbar(target);
  }

  private void openNextNoteWrap() {
    int max = noteRepository.maxNoteId();
    if (max <= 0) return;

    saveCurrentIfValid();

    int cur = (currentNote != null) ? currentNote.getId() : 0;
    if (cur <= 0) cur = 1;

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
}


