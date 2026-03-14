package de.zettelkastenfx.bibliography.ui;

import de.zettelkastenfx.base.BaseIcon;
import de.zettelkastenfx.base.util.BrowserConnection;
import de.zettelkastenfx.bibliography.model.*;
import de.zettelkastenfx.bibliography.ui.binding.*;
import de.zettelkastenfx.bibliography.ui.lookup.*;
import de.zettelkastenfx.bibliography.ui.support.BibliographyAuthorsView;
import de.zettelkastenfx.bibliography.ui.support.BibliographyFieldView;
import javafx.beans.property.BooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestriert den Editor für bibliographische Angaben.
 * Die Klasse verwaltet Typauswahl, Formulare, Lookup-Interaktionen und den Transfer
 * zwischen UI-Komponenten und den Domänenobjekten der Bibliographie.
 */
public class BibliographyEditorShell {

  private final VBox root = new VBox(8);
  private final ToggleButton toggleEdit = new ToggleButton();
  private final VBox content = new VBox(8);
  private final ComboBox<TypeChoice> typeCombo = new ComboBox<>();
  private final StackPane formHost = new StackPane();
  private final Map<TypeChoice, Node> forms = new EnumMap<>(TypeChoice.class);
  private final Button btnDelete = BaseIcon.DELETE.button("Angabe löschen");
  private final Button btnAdd = BaseIcon.ADD.button("Speichern & verknüpfen");
  private final Map<TypeChoice, Integer> selectedEntryIds = new EnumMap<>(TypeChoice.class);
  private final Map<TypeChoice, Integer> sourceEntryIds = new EnumMap<>(TypeChoice.class);
  private final EnumMap<TypeChoice, Boolean> autoFillLocked = new EnumMap<>(TypeChoice.class);
  private final EnumMap<TypeChoice, Boolean> autoFilledTitle = new EnumMap<>(TypeChoice.class);

  private Runnable onRequestCreateCollection = () -> {};
  private Runnable onNoneSelected = () -> {};
  private Runnable onRequestCreateJournal = () -> {};

  private BibliographyLookupProvider lookupProvider = BibliographyLookupProvider.NONE;
  private BibliographyLookupCoordinator lookupCoordinator;
  private BookBibliographyBinder bookBinder;
  private CollectionBibliographyBinder collectionBinder;
  private ArticleInCollectionBibliographyBinder articleInCollectionBinder;

  // Thesis
  private ComboBox<ThesisType> thesisTypeCombo;
  private ThesisBibliographyBinder thesisBinder;

  // Internet
  private InternetBibliographyBinder internetBinder;

  // Edition
  private EditionBibliographyBinder editionBinder;

  // Buch
  private BibliographyAuthorsView bookAuthorsView;
  private BibliographyFieldView bookTitleField;
  private BibliographyFieldView bookSubtitleField;
  private BibliographyFieldView bookPublisherField;
  private BibliographyFieldView bookPlaceField;
  private BibliographyFieldView bookYearField;
  private BibliographyFieldView bookIsbnField;
  private BibliographyFieldView bookEditionField;
  private BibliographyFieldView bookSeriesField;
  private BibliographyFieldView bookSeriesNumberField;

  // Collection
  private BibliographyAuthorsView collectionAuthorsView;
  private BibliographyFieldView collectionTitleField;
  private BibliographyFieldView collectionSubtitleField;
  private BibliographyFieldView collectionPublisherField;
  private BibliographyFieldView collectionPlaceField;
  private BibliographyFieldView collectionYearField;
  private BibliographyFieldView collectionSeriesField;

  // ArticleInCollection
  private BibliographyAuthorsView articleAuthorsView;
  private BibliographyFieldView articleTitleField;
  private BibliographyFieldView articleCollectionTitleField;
  private BibliographyFieldView articlePagesField;

  // Journal
  private BibliographyFieldView journalTitleField;
  private BibliographyFieldView journalSubtitleField;
  private BibliographyFieldView journalIssnField;
  private BibliographyFieldView journalPublisherField;
  private BibliographyFieldView journalPlaceField;
  private BibliographyFieldView journalStartYearField;
  private BibliographyFieldView journalEndYearField;
  private BibliographyFieldView journalNoteField;
  private JournalBibliographyBinder journalBinder;

  // JournalArticle
  private BibliographyAuthorsView journalArticleAuthorsView;
  private BibliographyFieldView journalArticleTitleField;
  private BibliographyFieldView journalArticleSubtitleField;
  private BibliographyFieldView journalArticlePublisherField;
  private BibliographyFieldView journalArticleJournalTitleField;
  private BibliographyFieldView journalArticleVolumeField;
  private BibliographyFieldView journalArticleIssueField;
  private BibliographyFieldView journalArticleYearField;
  private BibliographyFieldView journalArticlePagesField;
  private BibliographyFieldView journalArticleDoiField;
  private JournalArticleBibliographyBinder journalArticleBinder;

  // Thesis
  private BibliographyAuthorsView thesisAuthorsView;
  private BibliographyFieldView thesisTitleField;
  private BibliographyFieldView thesisSubtitleField;
  private BibliographyFieldView thesisUniversityField;
  private BibliographyFieldView thesisPlaceField;
  private BibliographyFieldView thesisYearField;
  private BibliographyFieldView thesisAdvisorField;
  private BibliographyFieldView thesisSeriesField;
  private BibliographyFieldView thesisNoteField;

  // Internet
  private BibliographyAuthorsView internetAuthorsView;
  private BibliographyFieldView internetTitleField;
  private BibliographyFieldView internetHostNameField;
  private BibliographyFieldView internetUrlField;
  private DatePicker internetPublishedPicker;
  private Label internetAccessedAtLabel;
  private LocalDateTime internetAccessedAtValue;

  // Edition
  private BibliographyAuthorsView editionAuthorsView;
  private BibliographyFieldView editionTitleField;
  private BibliographyFieldView editionPublisherField;
  private BibliographyFieldView editionPlaceField;
  private BibliographyFieldView editionYearField;
  private BibliographyFieldView editionIsbnField;
  private BibliographyFieldView editionRunField;
  private BibliographyFieldView editionSeriesField;
  private BibliographyFieldView editionVolumeField;

  // AI
  private BibliographyFieldView aiTitleField;
  private ComboBox<String> aiProviderCombo;
  private ComboBox<String> aiModelCombo;
  private Label aiProviderLabel;
  private Label aiModelLabel;
  private BibliographyFieldView aiContextField;
  private BibliographyFieldView aiPromptTitleField;
  private DatePicker aiUsedAtPicker;
  private Label aiUsedAtLabel;
  private AiBibliographyBinder aiBinder;

  private AiChoiceProvider aiChoiceProvider = AiChoiceProvider.NONE;
  private boolean suppressAiChoiceRefresh;

  /**
   * Erzeugt die Shell und initialisiert Aufbau, Formulare und Lookup-Verhalten.
   */
  public BibliographyEditorShell() {
    initializeStructure();
    initializeForms();
    initializeHeaderAndFooter();
    initializeTypeSelection();
    initializeLookupCoordinator();
    lookupCoordinator.wireLookupInteractions();
  }

  /**
   * Legt die Standardstruktur der Shell an.
   */
  private void initializeStructure() {
    root.getStyleClass().add("biblio-editor-shell");
    toggleEdit.getStyleClass().add("biblio-edit-toggle");
    toggleEdit.setFocusTraversable(false);
    typeCombo.getStyleClass().add("biblio-type-combo");
    formHost.getStyleClass().add("biblio-form-host");
    content.getStyleClass().add("biblio-editor-content");
    setEditMode(true);
  }

  /**
   * Baut die Formulare für die unterstützten Bibliographietypen auf.
   */
  private void initializeForms() {
    forms.put(TypeChoice.BOOK, buildBookForm());
    forms.put(TypeChoice.COLLECTION, buildCollectionForm());
    forms.put(TypeChoice.ARTICLE_IN_COLLECTION, buildArticleInCollectionForm());
    forms.put(TypeChoice.JOURNAL, buildJournalForm());
    forms.put(TypeChoice.JOURNAL_ARTICLE, buildJournalArticleForm());
    forms.put(TypeChoice.THESIS, buildThesisForm());
    forms.put(TypeChoice.INTERNET, buildInternetForm());
    forms.put(TypeChoice.EDITION, buildEditionForm());
    forms.put(TypeChoice.AI, buildAiForm());
    content.getChildren().setAll(formHost);
  }

  /**
   * Baut Kopf- und Fußbereich der Shell auf.
   */
  private void initializeHeaderAndFooter() {
    HBox header = buildHeader();
    HBox footer = buildFooter();
    root.getChildren().setAll(header, content, footer);
  }

  /**
   * Initialisiert Typauswahl und Formularumschaltung.
   */
  private void initializeTypeSelection() {
    setTypeChoices(
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
    typeCombo.setValue(TypeChoice.BOOK);
    switchForm(typeCombo.getValue());
    typeCombo.valueProperty().addListener((obs, old, value) -> switchForm(value));
  }

  private void initializeLookupCoordinator() {
    LookupStateAccess stateAccess = new LookupStateAccess(
        this::currentAuthors,
        this::setSelectedEntryId,
        this::setSourceEntryId,
        this::isAutoFillLocked,
        this::setAutoFillLocked,
        this::wasAutoFilled,
        this::setAutoFilled
    );

    LookupFieldAccess fieldAccess = new LookupFieldAccess(
        bookTitleField,
        collectionTitleField,
        articleTitleField,
        journalTitleField,
        journalArticleTitleField,
        internetTitleField,
        editionTitleField,
        bookSeriesField,
        collectionSeriesField,
        articleCollectionTitleField,
        journalArticleJournalTitleField,
        this::getCollectionTitle,
        this::setCollectionTitle,
        this::getJournalTitle,
        this::setJournalTitle
    );

    LookupEntryAccess entryAccess = new LookupEntryAccess(
        this::readFromBookEntry,
        this::readFromCollectionEntry,
        this::readFromArticleInCollectionEntry,
        this::readFromJournalEntry,
        this::readFromJournalArticleEntry,
        this::readFromInternetEntry,
        this::readFromEditionEntry,
        this::getCollectionEntryId,
        this::setCollectionEntryId,
        this::getJournalEntryId,
        this::setJournalEntryId,
        () -> onRequestCreateCollection.run(),
        () -> onRequestCreateJournal.run()
    );

    lookupCoordinator = new BibliographyLookupCoordinator(
        () -> lookupProvider,
        stateAccess,
        fieldAccess,
        entryAccess
    );
  }

  /**
   * Erzeugt den Kopfbereich mit Bearbeitungstoggle, Typauswahl und Löschen-Button.
   *
   * @return konfigurierte Header-Box.
   */
  private HBox buildHeader() {
    HBox header = new HBox(6);
    header.getStyleClass().add("biblio-editor-header");

    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);

    toggleEdit.selectedProperty().addListener((obs, old, isOn) -> setEditMode(isOn));
    typeCombo.visibleProperty().bind(toggleEdit.selectedProperty());
    typeCombo.managedProperty().bind(toggleEdit.selectedProperty());
    btnDelete.visibleProperty().bind(toggleEdit.selectedProperty());
    btnDelete.managedProperty().bind(toggleEdit.selectedProperty());

    header.getChildren().addAll(toggleEdit, typeCombo, spacer, btnDelete);
    return header;
  }

  /**
   * Erzeugt den Fußbereich mit Speichern-Button.
   *
   * @return konfigurierte Footer-Box.
   */
  private HBox buildFooter() {
    HBox footer = new HBox();
    footer.getStyleClass().add("biblio-editor-footer");
    footer.setAlignment(Pos.BOTTOM_RIGHT);
    footer.getChildren().add(btnAdd);
    footer.visibleProperty().bind(toggleEdit.selectedProperty());
    footer.managedProperty().bind(toggleEdit.selectedProperty());
    return footer;
  }

  /**
   * Konfiguriert die verfügbaren Bibliographietypen in der Typauswahl.
   *
   * @param types auswählbare Typen; bei leerer Übergabe werden Standardtypen verwendet.
   */
  public void setTypeChoices(TypeChoice... types) {
    if (types == null || types.length == 0) {
      typeCombo.getItems().setAll(TypeChoice.NONE, TypeChoice.BOOK);
    } else {
      typeCombo.getItems().setAll(types);
    }

    if (!typeCombo.getItems().contains(typeCombo.getValue()) && !typeCombo.getItems().isEmpty()) {
      typeCombo.setValue(typeCombo.getItems().get(0));
    }
  }

  /**
   * Liefert die Wurzelkomponente der Shell.
   *
   * @return Root-Node der Shell.
   */
  public Node getRoot() {
    return root;
  }

  /**
   * Prüft, ob die Shell im Bearbeitungsmodus ist.
   *
   * @return {@code true}, wenn Bearbeitung aktiv ist.
   */
  public boolean isEditMode() {
    return toggleEdit.isSelected();
  }

  /**
   * Aktiviert oder deaktiviert den Bearbeitungsmodus.
   *
   * @param on {@code true} aktiviert Bearbeitung, {@code false} den Lesemodus.
   */
  public void setEditMode(boolean on) {
    if (toggleEdit.isSelected() != on) {
      toggleEdit.setSelected(on);
    }
    toggleEdit.setGraphic(on ? BaseIcon.BULLET_GREEN.imageView() : BaseIcon.BULLET_RED.imageView());
    content.setOpacity(1.0);
  }

  /**
   * Setzt den Lookup-Provider für Vorschläge und Auflösungen.
   *
   * @param lookupProvider Provider für Lookup-Operationen; {@code null} deaktiviert Lookups.
   */
  public void setLookupProvider(BibliographyLookupProvider lookupProvider) {
    this.lookupProvider = lookupProvider == null ? BibliographyLookupProvider.NONE : lookupProvider;
  }

  /**
   * Setzt den Provider für Anbieter-/Modellvorschläge der KI-Quelle.
   *
   * @param provider Vorschlagsprovider; {@code null} deaktiviert Vorschläge
   */
  public void setAiChoiceProvider(AiChoiceProvider provider) {
    this.aiChoiceProvider = provider == null ? AiChoiceProvider.NONE : provider;
    refreshAiChoiceItems();
  }

  /**
   * Wechselt das sichtbare Formular entsprechend dem gewählten Typ.
   *
   * @param type gewünschter Bibliographietyp.
   */
  private void switchForm(TypeChoice type) {
    formHost.getChildren().clear();
    if (type == TypeChoice.NONE) {
      onNoneSelected.run();
      return;
    }

    Node form = forms.get(type);
    if (form != null) {
      formHost.getChildren().add(form);
    }
  }

  /**
   * Registriert einen Callback für das Löschen einer Angabe.
   *
   * @param action auszuführende Aktion; {@code null} deaktiviert die Reaktion.
   */
  public void setOnDelete(Runnable action) {
    btnDelete.setOnAction(e -> {
      if (action != null) {
        action.run();
      }
    });
  }

  /**
   * Registriert einen Callback für das Speichern und Verknüpfen.
   *
   * @param action auszuführende Aktion; {@code null} deaktiviert die Reaktion.
   */
  public void setOnAdd(Runnable action) {
    btnAdd.setOnAction(e -> {
      if (action != null) {
        action.run();
      }
    });
  }

  /**
   * Liefert den aktuell gewählten Bibliographietyp.
   *
   * @return selektierter Typ.
   */
  public TypeChoice getTypeChoice() {
    return typeCombo.getValue();
  }

  /**
   * Liefert den Titel des aktuell gewählten Formulars.
   *
   * @return Titelfeld des aktiven Typs oder leerer String.
   */
  public String getTitleText() {
    return switch (getTypeChoice()) {
      case BOOK -> fieldText(bookTitleField);
      case COLLECTION -> fieldText(collectionTitleField);
      case ARTICLE_IN_COLLECTION -> fieldText(articleTitleField);
      case JOURNAL -> fieldText(journalTitleField);
      case JOURNAL_ARTICLE -> fieldText(journalArticleTitleField);
      case THESIS -> fieldText(thesisTitleField);
      case INTERNET -> fieldText(internetTitleField);
      case EDITION -> fieldText(editionTitleField);
      case AI -> fieldText(aiTitleField);
      case NONE -> "";
    };
  }

  /**
   * Liefert die Autoren des aktuell gewählten Formulars.
   *
   * @return Autorenliste des aktiven Typs.
   */
  public List<Author> getAuthors() {
    return switch (getTypeChoice()) {
      case BOOK -> authorsOf(bookAuthorsView);
      case COLLECTION -> authorsOf(collectionAuthorsView);
      case ARTICLE_IN_COLLECTION -> authorsOf(articleAuthorsView);
      case JOURNAL -> List.of();
      case JOURNAL_ARTICLE -> authorsOf(journalArticleAuthorsView);
      case THESIS -> authorsOf(thesisAuthorsView);
      case INTERNET -> authorsOf(internetAuthorsView);
      case EDITION -> authorsOf(editionAuthorsView);
      case AI, NONE -> List.of();
    };
  }

  /**
   * Registriert einen Callback, der bei Auswahl von {@link TypeChoice#NONE} ausgeführt wird.
   *
   * @param action auszuführender Callback; {@code null} wird als No-Op behandelt.
   */
  public void setOnNoneSelected(Runnable action) {
    this.onNoneSelected = action == null ? () -> {
    } : action;
  }

  /**
   * Registriert einen Callback zum Anlegen eines Sammelbandes aus dem Artikel-Formular.
   *
   * @param action auszuführender Callback; {@code null} wird als No-Op behandelt.
   */
  public void setOnRequestCreateCollection(Runnable action) {
    this.onRequestCreateCollection = action == null ? () -> {
    } : action;
  }

  /**
   * Registriert einen Callback zum Anlegen einer Zeitschrift aus dem JournalArticle-Formular.
   *
   * @param action auszuführender Callback; {@code null} wird als No-Op behandelt.
   */
  public void setOnRequestCreateJournal(Runnable action) {
    this.onRequestCreateJournal = action == null ? () -> {
    } : action;
  }

  /**
   * Liefert die aktuell ausgewählte Sammelband-ID.
   *
   * @return ID des Sammelbandes oder {@code null}.
   */
  public Integer getCollectionEntryId() {
    return articleInCollectionBinder == null ? null : articleInCollectionBinder.getCollectionEntryId();
  }

  /**
   * Setzt die aktuell ausgewählte Sammelband-ID im Artikel-Binder.
   *
   * @param entryId ID des Sammelbandes oder {@code null}.
   */
  public void setCollectionEntryId(Integer entryId) {
    if (articleInCollectionBinder != null) {
      articleInCollectionBinder.setCollectionEntryId(entryId);
    }
  }

  /**
   * Liefert den aktuell eingetragenen Sammelbandtitel.
   *
   * @return Titel des Sammelbandfeldes oder leerer String.
   */
  public String getCollectionTitle() {
    return fieldText(articleCollectionTitleField);
  }

  /**
   * Setzt den Titel des Sammelbandfeldes im Artikel-Formular.
   *
   * @param title neuer Sammelbandtitel.
   */
  public void setCollectionTitle(String title) {
    if (articleCollectionTitleField != null) {
      articleCollectionTitleField.setText(title);
    }
  }

  /**
   * Reagiert auf Änderungen in den Autorenfeldern.
   *
   * @param type Typ des betroffenen Formulars.
   */
  private void handleAuthorsEdited(TypeChoice type) {
    if (lookupCoordinator != null) {
      lookupCoordinator.handleAuthorsEdited(type);
    }
  }

  private boolean isLookupSuppressed() {
    return lookupCoordinator != null && lookupCoordinator.isSuppressLookupCallbacks();
  }

  /**
   * Liefert die Autorenliste eines Typs aus dem UI-Zustand.
   *
   * @param type gewünschter Bibliographietyp.
   * @return aktuelle Autorenliste.
   */
  private List<Author> currentAuthors(TypeChoice type) {
    return switch (type) {
      case BOOK -> authorsOf(bookAuthorsView);
      case COLLECTION -> authorsOf(collectionAuthorsView);
      case ARTICLE_IN_COLLECTION -> authorsOf(articleAuthorsView);
      case JOURNAL -> List.of();
      case JOURNAL_ARTICLE -> authorsOf(journalArticleAuthorsView);
      case THESIS -> authorsOf(thesisAuthorsView);
      case INTERNET -> authorsOf(internetAuthorsView);
      case EDITION -> authorsOf(editionAuthorsView);
      case AI, NONE -> List.of();
    };
  }

  /**
   * Liefert die selektierte Eintrags-ID eines Typs.
   *
   * @param type gewünschter Bibliographietyp.
   * @return selektierte Eintrags-ID oder {@code null}.
   */
  private Integer getSelectedEntryId(TypeChoice type) {
    return selectedEntryIds.get(type);
  }

  /**
   * Setzt oder entfernt die selektierte Eintrags-ID eines Typs.
   *
   * @param type    gewünschter Bibliographietyp.
   * @param entryId zu speichernde ID; ungültige Werte entfernen den Eintrag.
   */
  private void setSelectedEntryId(TypeChoice type, Integer entryId) {
    updateEntryIdMap(selectedEntryIds, type, entryId);
  }

  /**
   * Baut das Formular für Bücher.
   *
   * @return Node des Buchformulars.
   */
  private Node buildBookForm() {
    VBox box = createFormContainer("Buch");

    bookAuthorsView = createAuthorsView(TypeChoice.BOOK);
    bookTitleField = createField("Buchtitel (Pflicht)");
    bookSubtitleField = createField("Untertitel");
    bookPublisherField = createField("Verlag");
    bookPlaceField = createField("Erscheinungsort");
    bookYearField = createField("Erscheinungsjahr");
    bookIsbnField = createField("ISBN");
    bookEditionField = createField("Auflage");
    bookSeriesField = createField("Reihentitel");
    bookSeriesNumberField = createField("Reihennummer");

    bookBinder = new BookBibliographyBinder(
        bookTitleField,
        bookSubtitleField,
        bookPublisherField,
        bookPlaceField,
        bookYearField,
        bookIsbnField,
        bookEditionField,
        bookSeriesField,
        bookSeriesNumberField,
        bookAuthorsView,
        id -> setSelectedEntryId(TypeChoice.BOOK, id),
        id -> setSourceEntryId(TypeChoice.BOOK, id),
        value -> setAutoFilled(TypeChoice.BOOK, value),
        value -> setAutoFillLocked(TypeChoice.BOOK, value)
    );

    box.getChildren().addAll(
        bookAuthorsView,
        bookTitleField,
        bookSubtitleField,
        bookPublisherField,
        bookPlaceField,
        bookYearField,
        bookIsbnField,
        bookEditionField,
        bookSeriesField,
        bookSeriesNumberField
    );
    return box;
  }

  /**
   * Baut das Formular für Zeitschriften.
   *
   * @return Node des Zeitschriftenformulars.
   */
  private Node buildJournalForm() {
    VBox box = createFormContainer("Zeitschrift");

    journalTitleField = createField("Titel der Zeitschrift (Pflicht)");
    journalSubtitleField = createField("Untertitel");
    journalIssnField = createField("ISSN");
    journalPublisherField = createField("Verlag / Institution");
    journalPlaceField = createField("Erscheinungsort");
    journalStartYearField = createField("Startjahr");
    journalEndYearField = createField("Endjahr");
    journalNoteField = createField("Notiz");

    journalBinder = new JournalBibliographyBinder(
        journalTitleField,
        journalSubtitleField,
        journalIssnField,
        journalPublisherField,
        journalPlaceField,
        journalStartYearField,
        journalEndYearField,
        journalNoteField,
        id -> setSelectedEntryId(TypeChoice.JOURNAL, id),
        id -> setSourceEntryId(TypeChoice.JOURNAL, id),
        value -> setAutoFilled(TypeChoice.JOURNAL, value),
        value -> setAutoFillLocked(TypeChoice.JOURNAL, value)
    );

    box.getChildren().addAll(
        journalTitleField,
        journalSubtitleField,
        journalIssnField,
        journalPublisherField,
        journalPlaceField,
        journalStartYearField,
        journalEndYearField,
        journalNoteField
    );
    return box;
  }

  /**
   * Baut das Formular für Zeitschriftenartikel.
   *
   * @return Node des Zeitschriftenartikelformulars.
   */
  private Node buildJournalArticleForm() {
    VBox box = createFormContainer("Zeitschriftenartikel");

    journalArticleAuthorsView = createAuthorsView(TypeChoice.JOURNAL_ARTICLE);
    journalArticleTitleField = createField("Artikeltitel (Pflicht)");
    journalArticleSubtitleField = createField("Untertitel");
    journalArticlePublisherField = createField("Verlag / Institution");
    journalArticleJournalTitleField = createField("Zeitschrift");
    journalArticleVolumeField = createField("Band");
    journalArticleIssueField = createField("Heft");
    journalArticleYearField = createField("Jahr");
    journalArticlePagesField = createField("Seiten");
    journalArticleDoiField = createField("DOI");

    journalArticleBinder = new JournalArticleBibliographyBinder(
        journalArticleAuthorsView,
        journalArticleTitleField,
        journalArticleSubtitleField,
        journalArticlePublisherField,
        journalArticleJournalTitleField,
        journalArticleVolumeField,
        journalArticleIssueField,
        journalArticleYearField,
        journalArticlePagesField,
        journalArticleDoiField,
        id -> setSelectedEntryId(TypeChoice.JOURNAL_ARTICLE, id),
        id -> setSourceEntryId(TypeChoice.JOURNAL_ARTICLE, id),
        value -> setAutoFilled(TypeChoice.JOURNAL_ARTICLE, value),
        value -> setAutoFillLocked(TypeChoice.JOURNAL_ARTICLE, value)
    );

    Button btnAddJournal = BaseIcon.BOOK_ADD.button("Zeitschrift anlegen");
    btnAddJournal.getStyleClass().add("biblio-journal-add");
    btnAddJournal.visibleProperty().bind(toggleEdit.selectedProperty());
    btnAddJournal.managedProperty().bind(toggleEdit.selectedProperty());
    btnAddJournal.setOnAction(e -> onRequestCreateJournal.run());

    HBox.setHgrow(journalArticleJournalTitleField, Priority.ALWAYS);
    HBox journalRow = new HBox(8, journalArticleJournalTitleField, btnAddJournal);
    journalRow.setAlignment(Pos.CENTER_LEFT);
    journalRow.getStyleClass().add("biblio-journal-row");

    box.getChildren().addAll(
        journalArticleAuthorsView,
        journalArticleTitleField,
        journalArticleSubtitleField,
        journalArticlePublisherField,
        journalRow,
        journalArticleVolumeField,
        journalArticleIssueField,
        journalArticleYearField,
        journalArticlePagesField,
        journalArticleDoiField
    );
    return box;
  }

  /**
   * Baut das Formular für Hochschulschriften.
   *
   * @return Node des Thesis-Formulars.
   */
  private Node buildThesisForm() {
    VBox box = createFormContainer("Thesis");

    thesisAuthorsView = createAuthorsView(TypeChoice.THESIS);
    thesisTitleField = createField("Titel der Arbeit (Pflicht)");
    thesisSubtitleField = createField("Untertitel");

    thesisTypeCombo = new ComboBox<>();
    thesisTypeCombo.getItems().setAll(ThesisType.values());
    thesisTypeCombo.setValue(ThesisType.DISSERTATION);
    thesisTypeCombo.setMaxWidth(Double.MAX_VALUE);

    Label thesisTypeLabel = new Label("Typ der Hochschulschrift");
    HBox thesisTypeBox = new HBox(8, thesisTypeLabel, thesisTypeCombo);
    thesisTypeBox.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(thesisTypeCombo, Priority.ALWAYS);

    thesisUniversityField = createField("Hochschule / Universität");
    thesisPlaceField = createField("Ort");
    thesisYearField = createField("Jahr");
    thesisAdvisorField = createField("Betreuer");
    thesisSeriesField = createField("Schriftenreihe");
    thesisNoteField = createField("Notiz");

    thesisBinder = new ThesisBibliographyBinder(
        thesisAuthorsView,
        thesisTitleField,
        thesisSubtitleField,
        thesisTypeCombo::getValue,
        thesisTypeCombo::setValue,
        thesisUniversityField,
        thesisPlaceField,
        thesisYearField,
        thesisAdvisorField,
        thesisSeriesField,
        thesisNoteField,
        id -> setSelectedEntryId(TypeChoice.THESIS, id),
        id -> setSourceEntryId(TypeChoice.THESIS, id),
        value -> setAutoFilled(TypeChoice.THESIS, value),
        value -> setAutoFillLocked(TypeChoice.THESIS, value)
    );

    box.getChildren().addAll(
        thesisAuthorsView,
        thesisTitleField,
        thesisSubtitleField,
        thesisTypeBox,
        thesisUniversityField,
        thesisPlaceField,
        thesisYearField,
        thesisAdvisorField,
        thesisSeriesField,
        thesisNoteField
    );
    return box;
  }

  /**
   * Baut das Formular für Internetquellen.
   *
   * @return Node des Internet-Formulars.
   */
  private Node buildInternetForm() {
    VBox box = createFormContainer("Internetquelle");

    internetAuthorsView = createAuthorsView(TypeChoice.INTERNET);
    internetTitleField = createField("Titel (Pflicht)");
    internetHostNameField = createField("Hostname / Projekt");
    internetUrlField = createField("URL");

    Button btnOpenUrl = BaseIcon.WWW_PAGE.button();
    btnOpenUrl.visibleProperty().bind(toggleEdit.selectedProperty());
    btnOpenUrl.managedProperty().bind(toggleEdit.selectedProperty());
    btnOpenUrl.setOnAction(e -> openInternetUrlInBrowser());

    HBox.setHgrow(internetUrlField, Priority.ALWAYS);
    HBox urlRow = new HBox(8, internetUrlField, btnOpenUrl);
    urlRow.setAlignment(Pos.CENTER_LEFT);
    urlRow.getStyleClass().add("biblio-internet-url-row");

    configureInternetUrlReadOnlyLink();

    internetPublishedPicker = new DatePicker();
    internetPublishedPicker.setMaxWidth(Double.MAX_VALUE);

    Label publishedLabel = new Label("Veröffentlicht am ");
    publishedLabel.visibleProperty().bind(toggleEdit.selectedProperty().not());
    publishedLabel.managedProperty().bind(toggleEdit.selectedProperty().not());

    internetPublishedPicker.visibleProperty().bind(toggleEdit.selectedProperty());
    internetPublishedPicker.managedProperty().bind(toggleEdit.selectedProperty());

    updateInternetPublishedLabel(publishedLabel);

    internetPublishedPicker.valueProperty().addListener((obs, old, value) ->
                                                            updateInternetPublishedLabel(publishedLabel)
    );

    HBox.setHgrow(internetPublishedPicker, Priority.ALWAYS);
    HBox publishedRow = new HBox(8, publishedLabel, internetPublishedPicker);
    publishedRow.setAlignment(Pos.CENTER_LEFT);
    publishedRow.getStyleClass().add("biblio-published-row");

    internetAccessedAtLabel = new Label("Letzter Zugriff");
    internetAccessedAtLabel.getStyleClass().add("biblio-accessed-at-label");

    Region accessedSpacer = new Region();
    HBox.setHgrow(accessedSpacer, Priority.ALWAYS);

    Button btnRefreshAccessedAt = BaseIcon.ARROW_REFRESH.button();
    btnRefreshAccessedAt.visibleProperty().bind(toggleEdit.selectedProperty());
    btnRefreshAccessedAt.managedProperty().bind(toggleEdit.selectedProperty());
    btnRefreshAccessedAt.setOnAction(e -> setInternetAccessedAt(LocalDateTime.now()));

    HBox accessedAtRow = new HBox(8, internetAccessedAtLabel, accessedSpacer, btnRefreshAccessedAt);
    accessedAtRow.setAlignment(Pos.CENTER_LEFT);
    accessedAtRow.getStyleClass().add("biblio-accessed-at-row");

    internetBinder = new InternetBibliographyBinder(
        internetAuthorsView,
        internetTitleField,
        internetHostNameField,
        internetUrlField,
        this::getInternetPublished,
        this::setInternetPublished,
        this::parseInternetAccessedAt,
        this::setInternetAccessedAt,
        id -> setSelectedEntryId(TypeChoice.INTERNET, id),
        id -> setSourceEntryId(TypeChoice.INTERNET, id),
        value -> setAutoFilled(TypeChoice.INTERNET, value),
        value -> setAutoFillLocked(TypeChoice.INTERNET, value)
    );

    setInternetPublished(null);
    setInternetAccessedAt(null);

    box.getChildren().addAll(
        internetAuthorsView,
        internetTitleField,
        internetHostNameField,
        urlRow,
        publishedRow,
        accessedAtRow
    );
    return box;
  }

  /**
   * Baut das Formular für Editionen.
   *
   * @return Node des Editionsformulars.
   */
  private Node buildEditionForm() {
    VBox box = createFormContainer("Edition");

    editionAuthorsView = createAuthorsView(TypeChoice.EDITION);
    editionTitleField = createField("Titel der Edition (Pflicht)");
    editionPublisherField = createField("Verlag / Institution");
    editionPlaceField = createField("Ort");
    editionYearField = createField("Jahr");
    editionIsbnField = createField("ISBN");
    editionRunField = createField("Auflage");
    editionSeriesField = createField("Reihe");
    editionVolumeField = createField("Band");

    editionBinder = new EditionBibliographyBinder(
        editionAuthorsView,
        editionTitleField,
        editionPublisherField,
        editionPlaceField,
        editionYearField,
        editionIsbnField,
        editionRunField,
        editionSeriesField,
        editionVolumeField,
        id -> setSelectedEntryId(TypeChoice.EDITION, id),
        id -> setSourceEntryId(TypeChoice.EDITION, id),
        value -> setAutoFilled(TypeChoice.EDITION, value),
        value -> setAutoFillLocked(TypeChoice.EDITION, value)
    );

    box.getChildren().addAll(
        editionAuthorsView,
        editionTitleField,
        editionPublisherField,
        editionPlaceField,
        editionYearField,
        editionIsbnField,
        editionRunField,
        editionSeriesField,
        editionVolumeField
    );
    return box;
  }

  /**
   * Baut das Formular für KI-Quellen.
   *
   * @return Node des KI-Formulars.
   */
  private Node buildAiForm() {
    VBox box = createFormContainer("KI-Quelle");

    aiTitleField = createField("Titel (Pflicht)");

    aiProviderCombo = createEditableChoiceCombo();
    aiModelCombo = createEditableChoiceCombo();

    configureAiChoiceCombo(aiProviderCombo, true);
    configureAiChoiceCombo(aiModelCombo, false);

    aiProviderLabel = new Label();
    aiProviderLabel.visibleProperty().bind(toggleEdit.selectedProperty().not());
    aiProviderLabel.managedProperty().bind(toggleEdit.selectedProperty().not());

    aiModelLabel = new Label();
    aiModelLabel.visibleProperty().bind(toggleEdit.selectedProperty().not());
    aiModelLabel.managedProperty().bind(toggleEdit.selectedProperty().not());

    aiProviderCombo.visibleProperty().bind(toggleEdit.selectedProperty());
    aiProviderCombo.managedProperty().bind(toggleEdit.selectedProperty());

    aiModelCombo.visibleProperty().bind(toggleEdit.selectedProperty());
    aiModelCombo.managedProperty().bind(toggleEdit.selectedProperty());

    HBox.setHgrow(aiProviderCombo, Priority.ALWAYS);
    HBox providerRow = new HBox(8, new Label("Anbieter:"), aiProviderLabel, aiProviderCombo);
    providerRow.setAlignment(Pos.CENTER_LEFT);
    providerRow.getStyleClass().add("biblio-ai-provider-row");

    HBox.setHgrow(aiModelCombo, Priority.ALWAYS);
    HBox modelRow = new HBox(8, new Label("Modell:"), aiModelLabel, aiModelCombo);
    modelRow.setAlignment(Pos.CENTER_LEFT);
    modelRow.getStyleClass().add("biblio-ai-model-row");

    aiContextField = createField("Kontext");
    aiPromptTitleField = createField("Prompttitel");

    aiUsedAtPicker = new DatePicker();
    aiUsedAtPicker.setMaxWidth(Double.MAX_VALUE);

    Label usedAtLabel = new Label();
    usedAtLabel.visibleProperty().bind(toggleEdit.selectedProperty().not());
    usedAtLabel.managedProperty().bind(toggleEdit.selectedProperty().not());

    aiUsedAtPicker.visibleProperty().bind(toggleEdit.selectedProperty());
    aiUsedAtPicker.managedProperty().bind(toggleEdit.selectedProperty());

    aiUsedAtLabel = usedAtLabel;
    updateAiUsedAtLabel();

    aiUsedAtPicker.valueProperty().addListener((obs, old, value) -> updateAiUsedAtLabel());

    HBox.setHgrow(aiUsedAtPicker, Priority.ALWAYS);
    HBox usedAtRow = new HBox(8, usedAtLabel, aiUsedAtPicker);
    usedAtRow.setAlignment(Pos.CENTER_LEFT);
    usedAtRow.getStyleClass().add("biblio-ai-used-at-row");

    aiBinder = new AiBibliographyBinder(
        aiTitleField,
        this::getAiProvider,
        this::setAiProvider,
        this::getAiModel,
        this::setAiModel,
        aiContextField,
        aiPromptTitleField,
        this::parseAiUsedAt,
        this::setAiUsedAt,
        id -> setSelectedEntryId(TypeChoice.AI, id),
        id -> setSourceEntryId(TypeChoice.AI, id),
        value -> setAutoFilled(TypeChoice.AI, value),
        value -> setAutoFillLocked(TypeChoice.AI, value)
    );

    setAiProvider("");
    setAiModel("");
    setAiUsedAt(null);
    refreshAiChoiceItems();

    box.getChildren().addAll(
        aiTitleField,
        providerRow,
        modelRow,
        aiContextField,
        aiPromptTitleField,
        usedAtRow
    );
    return box;
  }

  /**
   * Baut das Formular für Sammelbände.
   *
   * @return Node des Sammelbandformulars.
   */
  private Node buildCollectionForm() {
    VBox box = createFormContainer("Sammelband");

    collectionAuthorsView = createAuthorsView(TypeChoice.COLLECTION);
    collectionTitleField = createField("Titel des Sammelbands (Pflicht)");
    collectionSubtitleField = createField("Untertitel");
    collectionPublisherField = createField("Verlag");
    collectionPlaceField = createField("Erscheinungsort");
    collectionYearField = createField("Erscheinungsjahr");
    collectionSeriesField = createField("Reihentitel");

    collectionBinder = new CollectionBibliographyBinder(
        collectionTitleField,
        collectionSubtitleField,
        collectionPublisherField,
        collectionPlaceField,
        collectionYearField,
        collectionSeriesField,
        collectionAuthorsView,
        id -> setSelectedEntryId(TypeChoice.COLLECTION, id),
        id -> setSourceEntryId(TypeChoice.COLLECTION, id),
        value -> setAutoFilled(TypeChoice.COLLECTION, value),
        value -> setAutoFillLocked(TypeChoice.COLLECTION, value)
    );

    box.getChildren().addAll(
        collectionAuthorsView,
        collectionTitleField,
        collectionSubtitleField,
        collectionPublisherField,
        collectionPlaceField,
        collectionYearField,
        collectionSeriesField
    );
    return box;
  }

  /**
   * Baut das Formular für Artikel in Sammelbänden.
   *
   * @return Node des Artikelformulars.
   */
  private Node buildArticleInCollectionForm() {
    VBox box = createFormContainer("Artikel in Sammelband");

    articleAuthorsView = createAuthorsView(TypeChoice.ARTICLE_IN_COLLECTION);
    articleTitleField = createField("Artikeltitel (Pflicht)");
    articleCollectionTitleField = createField("Sammelband (Titel)");
    articlePagesField = createField("Seiten");

    // articleCollectionTitleField.editor.textProperty().addListener((obs, old, value) -> collectionEntryId = null);

    articleInCollectionBinder = new ArticleInCollectionBibliographyBinder(
        articleTitleField,
        articleCollectionTitleField,
        articlePagesField,
        articleAuthorsView,
        id -> setSelectedEntryId(TypeChoice.ARTICLE_IN_COLLECTION, id),
        id -> setSourceEntryId(TypeChoice.ARTICLE_IN_COLLECTION, id),
        value -> setAutoFilled(TypeChoice.ARTICLE_IN_COLLECTION, value),
        value -> setAutoFillLocked(TypeChoice.ARTICLE_IN_COLLECTION, value)
    );

    Button btnAddCollection = BaseIcon.BOOK_ADD.button("Sammelband anlegen");
    btnAddCollection.getStyleClass().add("biblio-collection-add");
    btnAddCollection.visibleProperty().bind(toggleEdit.selectedProperty());
    btnAddCollection.managedProperty().bind(toggleEdit.selectedProperty());
    btnAddCollection.setOnAction(e -> onRequestCreateCollection.run());

    HBox.setHgrow(articleCollectionTitleField, Priority.ALWAYS);
    HBox row = new HBox(8, articleCollectionTitleField, btnAddCollection);
    row.setAlignment(Pos.CENTER_LEFT);
    row.getStyleClass().add("biblio-collection-row");

    box.getChildren().addAll(articleAuthorsView, articleTitleField, row, articlePagesField);
    return box;
  }

  /**
   * Zeigt einen vorhandenen Bucheintrag an.
   *
   * @param entry    anzuzeigender Bucheintrag.
   * @param editMode gewünschter Bearbeitungsmodus.
   */
  public void showBook(BookEntry entry, boolean editMode) {
    typeCombo.setValue(TypeChoice.BOOK);
    readFromBookEntry(entry);
    setEditMode(editMode);
  }

  /**
   * Öffnet einen leeren Editor für neue Bücher.
   */
  public void showNewBookEditor() {
    typeCombo.setValue(TypeChoice.BOOK);
    clearBookForm();
    setEditMode(true);
  }

  /**
   * Überträgt die aktuellen Buchformularwerte in einen Bucheintrag.
   *
   * @param entry Zielobjekt für die Übernahme.
   */
  public void writeIntoBookEntry(BookEntry entry) {
    if (bookBinder != null) {
      bookBinder.writeInto(entry);
    }
  }

  /**
   * Befüllt das Buchformular aus einem vorhandenen Bucheintrag.
   *
   * @param entry Quellobjekt; {@code null} leert das Formular.
   */
  public void readFromBookEntry(BookEntry entry) {
    if (bookBinder != null) {
      bookBinder.readFrom(entry);
    }
  }

  /**
   * Leert das Buchformular.
   */
  public void clearBookForm() {
    if (bookBinder != null) {
      bookBinder.clear();
    }
  }

  /**
   * Zeigt einen vorhandenen Sammelbandeintrag an.
   *
   * @param entry    anzuzeigender Sammelbandeintrag.
   * @param editMode gewünschter Bearbeitungsmodus.
   */
  public void showCollection(CollectionEntry entry, boolean editMode) {
    typeCombo.setValue(TypeChoice.COLLECTION);
    readFromCollectionEntry(entry);
    setEditMode(editMode);
  }

  /**
   * Öffnet einen leeren Editor für neue Sammelbände.
   */
  public void showNewCollectionEditor() {
    typeCombo.setValue(TypeChoice.COLLECTION);
    clearCollectionForm();
    setEditMode(true);
  }

  /**
   * Überträgt die aktuellen Formularwerte des Sammelbands in einen Eintrag.
   *
   * @param entry Zielobjekt für die Übernahme.
   */
  public void writeIntoCollectionEntry(CollectionEntry entry) {
    if (collectionBinder != null) {
      collectionBinder.writeInto(entry);
    }
  }

  /**
   * Befüllt das Sammelbandformular aus einem vorhandenen Eintrag.
   *
   * @param entry Quellobjekt; {@code null} leert das Formular.
   */
  public void readFromCollectionEntry(CollectionEntry entry) {
    if (collectionBinder != null) {
      collectionBinder.readFrom(entry);
    }
  }

  /**
   * Leert das Sammelbandformular.
   */
  public void clearCollectionForm() {
    if (collectionBinder != null) {
      collectionBinder.clear();
    }
  }

  /**
   * Zeigt einen vorhandenen Artikeleintrag an.
   *
   * @param entry    anzuzeigender Artikeleintrag.
   * @param editMode gewünschter Bearbeitungsmodus.
   */
  public void showArticleInCollection(ArticleInCollectionEntry entry, boolean editMode) {
    typeCombo.setValue(TypeChoice.ARTICLE_IN_COLLECTION);
    readFromArticleInCollectionEntry(entry);
    setEditMode(editMode);
  }

  /**
   * Öffnet einen leeren Editor für neue Artikel in Sammelbänden.
   */
  public void showNewArticleInCollectionEditor() {
    typeCombo.setValue(TypeChoice.ARTICLE_IN_COLLECTION);
    clearArticleInCollectionForm();
    setEditMode(true);
  }

  /**
   * Überträgt die aktuellen Formularwerte des Artikels in einen Eintrag.
   *
   * @param entry Zielobjekt für die Übernahme.
   */
  public void writeIntoArticleInCollectionEntry(ArticleInCollectionEntry entry) {
    if (articleInCollectionBinder != null) {
      articleInCollectionBinder.writeInto(entry);
    }
  }

  /**
   * Befüllt das Artikelformular aus einem vorhandenen Eintrag.
   *
   * @param entry Quellobjekt; {@code null} leert das Formular.
   */
  public void readFromArticleInCollectionEntry(ArticleInCollectionEntry entry) {
    if (articleInCollectionBinder != null) {
      articleInCollectionBinder.readFrom(entry);
    }
  }

  /**
   * Leert das Artikelformular.
   */
  public void clearArticleInCollectionForm() {
    if (articleInCollectionBinder != null) {
      articleInCollectionBinder.clear();
    }
  }

  /**
   * Zeigt einen vorhandenen Zeitschrifteneintrag an.
   *
   * @param entry anzuzeigender Zeitschrifteneintrag.
   * @param editMode gewünschter Bearbeitungsmodus.
   */
  public void showJournal(JournalEntry entry, boolean editMode) {
    typeCombo.setValue(TypeChoice.JOURNAL);
    readFromJournalEntry(entry);
    setEditMode(editMode);
  }

  /**
   * Öffnet einen leeren Editor für neue Zeitschriften.
   */
  public void showNewJournalEditor() {
    typeCombo.setValue(TypeChoice.JOURNAL);
    clearJournalForm();
    setEditMode(true);
  }

  /**
   * Überträgt die aktuellen Formularwerte der Zeitschrift in einen Eintrag.
   *
   * @param entry Zielobjekt für die Übernahme.
   */
  public void writeIntoJournalEntry(JournalEntry entry) {
    if (journalBinder != null) {
      journalBinder.writeInto(entry);
    }
  }

  /**
   * Befüllt das Zeitschriftenformular aus einem vorhandenen Eintrag.
   *
   * @param entry Quellobjekt; {@code null} leert das Formular.
   */
  public void readFromJournalEntry(JournalEntry entry) {
    if (journalBinder != null) {
      journalBinder.readFrom(entry);
    }
  }

  /**
   * Leert das Zeitschriftenformular.
   */
  public void clearJournalForm() {
    if (journalBinder != null) {
      journalBinder.clear();
    }
  }

  /**
   * Zeigt einen vorhandenen Thesis-Eintrag an.
   *
   * @param entry anzuzeigender Thesis-Eintrag.
   * @param editMode gewünschter Bearbeitungsmodus.
   */
  public void showThesis(ThesisEntry entry, boolean editMode) {
    typeCombo.setValue(TypeChoice.THESIS);
    readFromThesisEntry(entry);
    setEditMode(editMode);
  }

  /**
   * Öffnet einen leeren Editor für neue Hochschulschriften.
   */
  public void showNewThesisEditor() {
    typeCombo.setValue(TypeChoice.THESIS);
    clearThesisForm();
    setEditMode(true);
  }

  /**
   * Überträgt die aktuellen Formularwerte der Thesis in einen Eintrag.
   *
   * @param entry Zielobjekt für die Übernahme.
   */
  public void writeIntoThesisEntry(ThesisEntry entry) {
    if (thesisBinder != null) {
      thesisBinder.writeInto(entry);
    }
  }

  /**
   * Befüllt das Thesis-Formular aus einem vorhandenen Eintrag.
   *
   * @param entry Quellobjekt; {@code null} leert das Formular.
   */
  public void readFromThesisEntry(ThesisEntry entry) {
    if (thesisBinder != null) {
      thesisBinder.readFrom(entry);
    }
  }

  /**
   * Leert das Thesis-Formular.
   */
  public void clearThesisForm() {
    if (thesisBinder != null) {
      thesisBinder.clear();
    }
  }

  /**
   * Zeigt einen vorhandenen Internet-Eintrag an.
   *
   * @param entry anzuzeigender Internet-Eintrag.
   * @param editMode gewünschter Bearbeitungsmodus.
   */
  public void showInternet(InternetEntry entry, boolean editMode) {
    typeCombo.setValue(TypeChoice.INTERNET);
    readFromInternetEntry(entry);
    setEditMode(editMode);
  }

  /**
   * Öffnet einen leeren Editor für neue Internetquellen.
   */
  public void showNewInternetEditor() {
    typeCombo.setValue(TypeChoice.INTERNET);
    clearInternetForm();
    setEditMode(true);
  }

  /**
   * Überträgt die aktuellen Formularwerte der Internetquelle in einen Eintrag.
   *
   * @param entry Zielobjekt für die Übernahme.
   */
  public void writeIntoInternetEntry(InternetEntry entry) {
    if (internetBinder != null) {
      internetBinder.writeInto(entry);
    }
  }

  /**
   * Befüllt das Internet-Formular aus einem vorhandenen Eintrag.
   *
   * @param entry Quellobjekt; {@code null} leert das Formular.
   */
  public void readFromInternetEntry(InternetEntry entry) {
    if (internetBinder != null) {
      internetBinder.readFrom(entry);
    }
  }

  /**
   * Leert das Internet-Formular.
   */
  public void clearInternetForm() {
    if (internetBinder != null) {
      internetBinder.clear();
    }
  }

  /**
   * Zeigt einen vorhandenen Editions-Eintrag an.
   *
   * @param entry anzuzeigender Editions-Eintrag.
   * @param editMode gewünschter Bearbeitungsmodus.
   */
  public void showEdition(EditionEntry entry, boolean editMode) {
    typeCombo.setValue(TypeChoice.EDITION);
    readFromEditionEntry(entry);
    setEditMode(editMode);
  }

  /**
   * Öffnet einen leeren Editor für neue Editionen.
   */
  public void showNewEditionEditor() {
    typeCombo.setValue(TypeChoice.EDITION);
    clearEditionForm();
    setEditMode(true);
  }

  /**
   * Überträgt die aktuellen Formularwerte der Edition in einen Eintrag.
   *
   * @param entry Zielobjekt für die Übernahme.
   */
  public void writeIntoEditionEntry(EditionEntry entry) {
    if (editionBinder != null) {
      editionBinder.writeInto(entry);
    }
  }

  /**
   * Befüllt das Editionsformular aus einem vorhandenen Eintrag.
   *
   * @param entry Quellobjekt; {@code null} leert das Formular.
   */
  public void readFromEditionEntry(EditionEntry entry) {
    if (editionBinder != null) {
      editionBinder.readFrom(entry);
    }
  }

  /**
   * Leert das Editionsformular.
   */
  public void clearEditionForm() {
    if (editionBinder != null) {
      editionBinder.clear();
    }
  }

  /**
   * Zeigt einen vorhandenen KI-Eintrag an.
   *
   * @param entry anzuzeigender KI-Eintrag.
   * @param editMode gewünschter Bearbeitungsmodus.
   */
  public void showAi(AiEntry entry, boolean editMode) {
    typeCombo.setValue(TypeChoice.AI);
    readFromAiEntry(entry);
    setEditMode(editMode);
  }

  /**
   * Öffnet einen leeren Editor für neue KI-Quellen.
   */
  public void showNewAiEditor() {
    typeCombo.setValue(TypeChoice.AI);
    clearAiForm();
    setEditMode(true);
  }

  /**
   * Überträgt die aktuellen Formularwerte der KI-Quelle in einen Eintrag.
   *
   * @param entry Zielobjekt für die Übernahme.
   */
  public void writeIntoAiEntry(AiEntry entry) {
    if (aiBinder != null) {
      aiBinder.writeInto(entry);
    }
  }

  /**
   * Befüllt das KI-Formular aus einem vorhandenen Eintrag.
   *
   * @param entry Quellobjekt; {@code null} leert das Formular.
   */
  public void readFromAiEntry(AiEntry entry) {
    if (aiBinder != null) {
      aiBinder.readFrom(entry);
    }
  }

  /**
   * Leert das KI-Formular.
   */
  public void clearAiForm() {
    if (aiBinder != null) {
      aiBinder.clear();
    }
  }

  /**
   * Zeigt einen vorhandenen Zeitschriftenartikel an.
   *
   * @param entry anzuzeigender Zeitschriftenartikel.
   * @param editMode gewünschter Bearbeitungsmodus.
   */
  public void showJournalArticle(JournalArticleEntry entry, boolean editMode) {
    typeCombo.setValue(TypeChoice.JOURNAL_ARTICLE);
    readFromJournalArticleEntry(entry);
    setEditMode(editMode);
  }

  /**
   * Öffnet einen leeren Editor für neue Zeitschriftenartikel.
   */
  public void showNewJournalArticleEditor() {
    typeCombo.setValue(TypeChoice.JOURNAL_ARTICLE);
    clearJournalArticleForm();
    setEditMode(true);
  }

  /**
   * Überträgt die aktuellen Formularwerte des Zeitschriftenartikels in einen Eintrag.
   *
   * @param entry Zielobjekt für die Übernahme.
   */
  public void writeIntoJournalArticleEntry(JournalArticleEntry entry) {
    if (journalArticleBinder != null) {
      journalArticleBinder.writeInto(entry);
    }
  }

  /**
   * Befüllt das Zeitschriftenartikelformular aus einem vorhandenen Eintrag.
   *
   * @param entry Quellobjekt; {@code null} leert das Formular.
   */
  public void readFromJournalArticleEntry(JournalArticleEntry entry) {
    if (journalArticleBinder != null) {
      journalArticleBinder.readFrom(entry);
    }
  }

  /**
   * Leert das Zeitschriftenartikelformular.
   */
  public void clearJournalArticleForm() {
    if (journalArticleBinder != null) {
      journalArticleBinder.clear();
    }
  }

  /**
   * Baut ein generisches Platzhalterformular.
   *
   * @param title Titel des Formulars.
   * @return Platzhalter-Node.
   */
  private Node placeholderForm(String title) {
    VBox box = createFormContainer(title);
    BibliographyFieldView fieldA = createField("Feld A");
    BibliographyFieldView fieldB = createField("Feld B");
    BibliographyFieldView fieldC = createField("Feld C");
    box.getChildren().addAll(fieldA, fieldB, fieldC);
    return box;
  }

  /**
   * Erzeugt einen Form-Container mit Überschrift.
   *
   * @param title Formulartitel.
   * @return vorbereitete Formular-Box.
   */
  private VBox createFormContainer(String title) {
    VBox box = new VBox(6);
    box.getStyleClass().add("biblio-form");
    Label head = new Label(title);
    head.getStyleClass().add("biblio-form-title");
    box.getChildren().add(head);
    return box;
  }

  /**
   * Erzeugt ein Bibliographiefeld im aktuellen Bearbeitungsmodus.
   *
   * @param prompt Prompttext des Feldes.
   * @return neu erzeugtes Feld.
   */
  private BibliographyFieldView createField(String prompt) {
    return new BibliographyFieldView(prompt, editProperty());
  }

  /**
   * Erzeugt eine Autorenansicht für einen Bibliographietyp.
   *
   * @param ownerType Bibliographietyp des Formulars.
   * @return neu erzeugte Autorenansicht.
   */
  private BibliographyAuthorsView createAuthorsView(TypeChoice ownerType) {
    return new BibliographyAuthorsView(
        ownerType,
        editProperty(),
        row -> lookupProvider.findAuthors(ownerType, row.getLastName(), getSelectedEntryId(ownerType)),
        this::handleAuthorsEdited,
        () -> {
        },
        this::isLookupSuppressed,
        this::setSelectedEntryId
    );
  }

  /**
   * Liefert die Property des Bearbeitungsmodus.
   *
   * @return Selected-Property des Bearbeitungstoggles.
   */
  private BooleanProperty editProperty() {
    return toggleEdit.selectedProperty();
  }

  /**
   * Liest den Text eines Feldes sicher aus.
   *
   * @param field auszulesendes Feld.
   * @return Feldinhalt oder leerer String.
   */
  private String fieldText(BibliographyFieldView field) {
    return field == null ? "" : field.getText();
  }

  /**
   * Liest die Autoren einer Autorenansicht sicher aus.
   *
   * @param view auszulesende Autorenansicht.
   * @return Autorenliste oder leere Liste.
   */
  private List<Author> authorsOf(BibliographyAuthorsView view) {
    return view == null ? List.of() : view.toAuthors();
  }

  /**
   * Aktualisiert eine Map mit optionaler Eintrags-ID.
   *
   * @param map     Ziel-Map.
   * @param type    Bibliographietyp als Schlüssel.
   * @param entryId zu setzende ID; ungültige Werte entfernen den Schlüssel.
   */
  private void updateEntryIdMap(Map<TypeChoice, Integer> map, TypeChoice type, Integer entryId) {
    if (entryId == null || entryId <= 0) {
      map.remove(type);
    } else {
      map.put(type, entryId);
    }
  }

  /**
   * Prüft, ob der automatische Titelfill für einen Typ gesperrt ist.
   *
   * @param type gewünschter Bibliographietyp.
   * @return {@code true}, wenn kein weiterer Autofill erfolgen soll.
   */
  private boolean isAutoFillLocked(TypeChoice type) {
    return Boolean.TRUE.equals(autoFillLocked.get(type));
  }

  /**
   * Setzt die Sperre für automatisches Titelfill eines Typs.
   *
   * @param type   gewünschter Bibliographietyp.
   * @param locked neue Sperreinstellung.
   */
  private void setAutoFillLocked(TypeChoice type, boolean locked) {
    autoFillLocked.put(type, locked);
  }

  /**
   * Prüft, ob der Titel eines Typs zuvor automatisch gefüllt wurde.
   *
   * @param type gewünschter Bibliographietyp.
   * @return {@code true}, wenn Autofill zuvor stattgefunden hat.
   */
  private boolean wasAutoFilled(TypeChoice type) {
    return Boolean.TRUE.equals(autoFilledTitle.get(type));
  }

  /**
   * Merkt, ob ein Titel automatisch gefüllt wurde.
   *
   * @param type  gewünschter Bibliographietyp.
   * @param value neuer Merkerzustand.
   */
  private void setAutoFilled(TypeChoice type, boolean value) {
    autoFilledTitle.put(type, value);
  }

  /**
   * Liefert die Ursprungs-ID eines geladenen Eintrags.
   *
   * @param type gewünschter Bibliographietyp.
   * @return Ursprungs-ID oder {@code null}.
   */
  public Integer getSourceEntryId(TypeChoice type) {
    return sourceEntryIds.get(type);
  }

  /**
   * Setzt oder entfernt die Ursprungs-ID eines geladenen Eintrags.
   *
   * @param type    gewünschter Bibliographietyp.
   * @param entryId zu speichernde Ursprungs-ID.
   */
  private void setSourceEntryId(TypeChoice type, Integer entryId) {
    updateEntryIdMap(sourceEntryIds, type, entryId);
  }

  /**
   * Liefert die aktuell ausgewählte Zeitschriften-ID.
   *
   * @return ID der Zeitschrift oder {@code null}.
   */
  public Integer getJournalEntryId() {
    return journalArticleBinder == null ? null : journalArticleBinder.getJournalEntryId();
  }

  /**
   * Setzt die aktuell ausgewählte Zeitschriften-ID im JournalArticle-Binder.
   *
   * @param entryId ID der Zeitschrift oder {@code null}.
   */
  public void setJournalEntryId(Integer entryId) {
    if (journalArticleBinder != null) {
      journalArticleBinder.setJournalEntryId(entryId);
    }
  }

  /**
   * Liefert den aktuell eingetragenen Zeitschriftentitel.
   *
   * @return Titel des Zeitschriftenfeldes oder leerer String.
   */
  public String getJournalTitle() {
    return fieldText(journalArticleJournalTitleField);
  }

  /**
   * Setzt den Titel des Zeitschriftenfeldes im JournalArticle-Formular.
   *
   * @param title neuer Zeitschriftentitel.
   */
  public void setJournalTitle(String title) {
    if (journalArticleJournalTitleField != null) {
      journalArticleJournalTitleField.setText(title);
    }
  }

  /**
   * Liest den Zugriffszeitpunkt der Internetquelle aus dem Formular.
   *
   * @return geparster Zeitpunkt oder {@code null}, wenn das Feld leer oder ungültig ist
   */
  private LocalDateTime parseInternetAccessedAt() {
    return internetAccessedAtValue;
  }

  /**
   * Setzt den Zugriffszeitpunkt der Internetquelle im Formular.
   *
   * @param value neuer Zeitpunkt oder {@code null}
   */
  private void setInternetAccessedAt(LocalDateTime value) {
    internetAccessedAtValue = value;

    if (internetAccessedAtLabel != null) {
      if (value == null) {
        internetAccessedAtLabel.setText("Letzter Zugriff: noch nicht gespeichert");
      } else {
        internetAccessedAtLabel.setText("Letzter Zugriff: " + value.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
      }
    }
  }

  /**
   * Liest den Nutzungszeitpunkt der KI-Quelle aus dem Formular.
   *
   * @return Zeitpunkt mit Tagesbeginn oder {@code null}
   */
  private LocalDateTime parseAiUsedAt() {
    if (aiUsedAtPicker == null || aiUsedAtPicker.getValue() == null) {
      return null;
    }
    return aiUsedAtPicker.getValue().atStartOfDay();
  }

  /**
   * Setzt den Nutzungszeitpunkt der KI-Quelle im Formular.
   *
   * @param value neuer Zeitpunkt oder {@code null}
   */
  private void setAiUsedAt(LocalDateTime value) {
    if (aiUsedAtPicker != null) {
      aiUsedAtPicker.setValue(value == null ? null : value.toLocalDate());
    }
    updateAiUsedAtLabel();
  }

  /**
   * Aktualisiert das Label für den KI-Nutzungszeitpunkt.
   */
  private void updateAiUsedAtLabel() {
    if (aiUsedAtLabel == null) {
      return;
    }

    LocalDate value = aiUsedAtPicker == null ? null : aiUsedAtPicker.getValue();
    if (value == null) {
      aiUsedAtLabel.setText("Verwendet am");
    } else {
      aiUsedAtLabel.setText("Verwendet am " + value.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
    }
  }

  /**
   * Erzeugt eine editierbare ComboBox für KI-Auswahlen.
   *
   * @return editierbare ComboBox
   */
  private ComboBox<String> createEditableChoiceCombo() {
    ComboBox<String> combo = new ComboBox<>();
    combo.setEditable(true);
    combo.setMaxWidth(Double.MAX_VALUE);
    return combo;
  }

  /**
   * Verdrahtet eine ComboBox für Anbieter oder Modell.
   *
   * @param combo Ziel-ComboBox
   * @param providerCombo {@code true} für Anbieter, {@code false} für Modell
   */
  private void configureAiChoiceCombo(ComboBox<String> combo, boolean providerCombo) {
    combo.getEditor().textProperty().addListener((obs, old, value) -> {
      if (suppressAiChoiceRefresh) {
        return;
      }

      updateAiChoiceLabels();

      if (value != null && value.trim().length() >= 2) {
        if (providerCombo) {
          refreshAiProviderItems();
        } else {
          refreshAiModelItems();
        }
        combo.show();
      }
    });

    combo.setOnShowing(e -> {
      if (providerCombo) {
        refreshAiProviderItems();
      } else {
        refreshAiModelItems();
      }
    });

    combo.valueProperty().addListener((obs, old, value) -> {
      if (suppressAiChoiceRefresh) {
        return;
      }

      updateAiChoiceLabels();

      if (providerCombo) {
        refreshAiModelItems();
      } else {
        refreshAiProviderItems();
      }
    });

    combo.getEditor().focusedProperty().addListener((obs, old, focused) -> {
      if (!focused) {
        updateAiChoiceLabels();

        if (providerCombo) {
          refreshAiModelItems();
        } else {
          refreshAiProviderItems();
        }
      }
    });
  }

  /**
   * Aktualisiert beide KI-Auswahlboxen.
   */
  private void refreshAiChoiceItems() {
    refreshAiProviderItems();
    refreshAiModelItems();
  }

  /**
   * Aktualisiert die Anbieterliste unter Berücksichtigung von Recents und Modellfilter.
   */
  private void refreshAiProviderItems() {
    if (aiProviderCombo == null) {
      return;
    }

    String prefix = aiProviderCombo.getEditor().getText() == null ? "" : aiProviderCombo.getEditor().getText().trim();
    String modelFilter = getAiModel();

    List<String> recents = aiChoiceProvider.loadRecentProviders(3);
    List<String> hits = prefix.length() >= 2
                            ? aiChoiceProvider.findProviders(prefix, modelFilter, 50)
                            : List.of();

    aiProviderCombo.getItems().setAll(mergeRecentAndHits(recents, hits));
  }

  /**
   * Aktualisiert die Modellliste unter Berücksichtigung von Recents und Anbieterfilter.
   */
  private void refreshAiModelItems() {
    if (aiModelCombo == null) {
      return;
    }

    String prefix = aiModelCombo.getEditor().getText() == null ? "" : aiModelCombo.getEditor().getText().trim();
    String providerFilter = getAiProvider();

    List<String> recents = aiChoiceProvider.loadRecentModels(3);
    List<String> hits = prefix.length() >= 2
                            ? aiChoiceProvider.findModels(prefix, providerFilter, 50)
                            : List.of();

    aiModelCombo.getItems().setAll(mergeRecentAndHits(recents, hits));
  }

  /**
   * Führt Recents und alphabetische Treffer zusammen, ohne Duplikate.
   *
   * @param recents zuletzt verwendete Werte
   * @param hits reguläre Treffer
   * @return kombinierte Liste
   */
  private List<String> mergeRecentAndHits(List<String> recents, List<String> hits) {
    java.util.LinkedHashSet<String> values = new java.util.LinkedHashSet<>();

    if (recents != null) {
      for (String value : recents) {
        if (value != null && !value.isBlank()) {
          values.add(value.trim());
        }
      }
    }

    if (hits != null) {
      for (String value : hits) {
        if (value != null && !value.isBlank()) {
          values.add(value.trim());
        }
      }
    }

    return List.copyOf(values);
  }

  /**
   * Liefert den aktuell eingetragenen Anbieter.
   *
   * @return Anbieter oder leerer String
   */
  private String getAiProvider() {
    if (aiProviderCombo == null) {
      return "";
    }
    String value = aiProviderCombo.getEditor().getText();
    return value == null ? "" : value.trim();
  }

  /**
   * Setzt den Anbieter in der ComboBox.
   *
   * @param value neuer Anbieter
   */
  private void setAiProvider(String value) {
    if (aiProviderCombo == null) {
      return;
    }

    suppressAiChoiceRefresh = true;
    try {
      aiProviderCombo.getSelectionModel().clearSelection();
      aiProviderCombo.getEditor().setText(value == null ? "" : value.trim());
    } finally {
      suppressAiChoiceRefresh = false;
    }

    updateAiChoiceLabels();
  }

  /**
   * Liefert das aktuell eingetragene Modell.
   *
   * @return Modell oder leerer String
   */
  private String getAiModel() {
    if (aiModelCombo == null) {
      return "";
    }
    String value = aiModelCombo.getEditor().getText();
    return value == null ? "" : value.trim();
  }

  /**
   * Setzt das Modell in der ComboBox.
   *
   * @param value neues Modell
   */
  private void setAiModel(String value) {
    if (aiModelCombo == null) {
      return;
    }

    suppressAiChoiceRefresh = true;
    try {
      aiModelCombo.getSelectionModel().clearSelection();
      aiModelCombo.getEditor().setText(value == null ? "" : value.trim());
    } finally {
      suppressAiChoiceRefresh = false;
    }

    updateAiChoiceLabels();
  }

  /**
   * Aktualisiert die Labels für Anbieter und Modell im Lesemodus.
   */
  private void updateAiChoiceLabels() {
    if (aiProviderLabel != null) {
      String provider = getAiProvider();
      aiProviderLabel.setText(provider == null || provider.isBlank() ? "" : provider);
    }

    if (aiModelLabel != null) {
      String model = getAiModel();
      aiModelLabel.setText(model == null || model.isBlank() ? "" : model);
    }
  }

  /**
   * Öffnet die im Internetformular eingetragene URL im Standardbrowser des Systems.
   */
  private void openInternetUrlInBrowser() {
    String raw = fieldText(internetUrlField).trim();
    BrowserConnection.openBrowser(raw);
  }

  /**
   * Konfiguriert die URL-Anzeige im Lesemodus als klickbaren Link.
   */
  private void configureInternetUrlReadOnlyLink() {
    if (internetUrlField == null || internetUrlField.getLabel() == null) {
      return;
    }

    internetUrlField.getLabel().setUnderline(true);
    internetUrlField.getLabel().setOnMouseClicked(e -> {
      if (!isEditMode()) {
        openInternetUrlInBrowser();
      }
    });
  }

  /**
   * Liefert das Veröffentlichungsdatum der Internetquelle als Text.
   *
   * @return ISO-Datumsstring oder leerer String
   */
  private String getInternetPublished() {
    LocalDate value = internetPublishedPicker == null ? null : internetPublishedPicker.getValue();
    return value == null ? "" : value.toString();
  }

  /**
   * Setzt das Veröffentlichungsdatum der Internetquelle.
   *
   * @param value ISO-Datumsstring oder leer
   */
  private void setInternetPublished(String value) {
    if (internetPublishedPicker == null) {
      return;
    }

    if (value == null || value.isBlank()) {
      internetPublishedPicker.setValue(null);
      return;
    }

    try {
      internetPublishedPicker.setValue(LocalDate.parse(value.trim()));
    } catch (Exception ex) {
      internetPublishedPicker.setValue(null);
    }
  }

  /**
   * Aktualisiert das Veröffentlichungslabel der Internetquelle abhängig vom DatePicker-Wert.
   *
   * @param label zu aktualisierendes Label
   */
  private void updateInternetPublishedLabel(Label label) {
    if (label == null) {
      return;
    }

    LocalDate value = internetPublishedPicker == null ? null : internetPublishedPicker.getValue();
    if (value == null) {
      label.setText("Veröffentlicht am");
    } else {
      label.setText("Veröffentlicht am " + value.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
    }
  }
}
