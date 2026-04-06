package de.zettelkastenfx.bibliography.ui;

import de.zettelkastenfx.base.BaseIcon;
import de.zettelkastenfx.bibliography.api.BibliographyService;
import de.zettelkastenfx.bibliography.model.*;
import de.zettelkastenfx.bibliography.ui.lookup.AiChoiceProvider;
import de.zettelkastenfx.bibliography.ui.lookup.AuthorSuggestion;
import de.zettelkastenfx.bibliography.ui.lookup.DynamicLookupCoordinator;
import de.zettelkastenfx.bibliography.ui.support.BibliographyAuthorsView;
import de.zettelkastenfx.bibliography.ui.support.BibliographyFieldView;
import javafx.beans.property.BooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import lombok.Setter;

import java.time.LocalDate;
import java.util.*;

/**
 * Orchestriert den Editor für bibliographische Angaben.
 * Die Klasse verwaltet Typauswahl, Formulare, Lookup-Interaktionen und den Transfer
 * zwischen UI-Komponenten und den Domänenobjekten der Bibliographie.
 */
public class BibliographyEditorShell {

  private final VBox root = new VBox(8);
  private final ToggleButton toggleEdit = new ToggleButton();
  private final VBox content = new VBox(8);
  private final ComboBox<MediaTypeOption> typeCombo = new ComboBox<>();
  private final StackPane formHost = new StackPane();

  @Setter private BibliographyService bibliographyService = null;
  private final Map<String, VBox> dynamicFormsByMediaType = new LinkedHashMap<>();
  private final Map<String, Map<String, Object>> dynamicFieldRegistryByMediaType = new LinkedHashMap<>();
  private final Map<String, List<MediaAttributeDefinition>> attributesByMediaType = new LinkedHashMap<>();

  private final Map<String, MediaTypeOption> mediaTypeOptionsByBibName = new LinkedHashMap<>();
  private final Button btnDelete = BaseIcon.DELETE.button("Angabe löschen");
  private final Button btnAdd = BaseIcon.ADD.button("Speichern & verknüpfen");

  private final Map<String, Integer> selectedEntryIdsByMediaType = new LinkedHashMap<>();
  private final Map<String, Integer> sourceEntryIdsByMediaType = new LinkedHashMap<>();
  private final Map<String, Boolean> autoFillLockedByMediaType = new LinkedHashMap<>();
  private final Map<String, Boolean> autoFilledTitleByMediaType = new LinkedHashMap<>();
  private final Map<String, Integer> relatedEntryIdsByFieldKey = new LinkedHashMap<>();
  private boolean suppressDynamicRelatedCallbacks;

  @FunctionalInterface
  public interface RelatedCreationHandler {
    /**
     * Fordert das Anlegen eines Related-Zielmediums an.
     *
     * @param relatedBibtexName technischer BibTeX-Name des Related-Feldes
     * @param targetMediaTypeBibName technischer Ziel-Medientypname
     * @param displayText aktueller Feldtext
     */
    void requestCreateRelated(String relatedBibtexName,
                              String targetMediaTypeBibName,
                              String displayText);
  }

  private RelatedCreationHandler onRequestCreateRelated = (relatedBibtexName,
                                                           targetMediaTypeBibName,
                                                           displayText) -> {
  };
  private Runnable onNoneSelected = () -> {};

  /**
   * -- SETTER --
   *  Setzt den zentralen dynamischen Lookup-Koordinator.
   *
   * @param dynamicLookupCoordinator Koordinator; {@code null} deaktiviert die
   *                                 aus der Shell ausgelagerte Lookup-Logik
   */
  @Setter
  private DynamicLookupCoordinator dynamicLookupCoordinator = null;

  private AiChoiceProvider aiChoiceProvider = AiChoiceProvider.NONE;
  private boolean suppressAiChoiceRefresh;

  /**
   * Beschreibt eine auswählbare Medientyp-Option der Shell.
   * Die Auswahl arbeitet nur noch mit technischem Medientypnamen und
   * Anzeigenamen.
   */
  public static final class MediaTypeOption {
    private final String mediaTypeBibName;
    private final String displayName;

    /**
     * Erzeugt eine auswählbare Medientyp-Option.
     *
     * @param mediaTypeBibName technischer Medientypname
     * @param displayName Anzeigename für die ComboBox
     */
    public MediaTypeOption(String mediaTypeBibName, String displayName) {
      this.mediaTypeBibName = mediaTypeBibName == null ? "" : mediaTypeBibName.trim();
      this.displayName = displayName == null ? "" : displayName.trim();
    }

    /**
     * Liefert den technischen Medientypnamen.
     *
     * @return technischer Medientypname
     */
    public String mediaTypeBibName() {
      return mediaTypeBibName;
    }

    /**
     * Liefert den Anzeigenamen.
     *
     * @return Anzeigename
     */
    public String displayName() {
      return displayName;
    }

    @Override
    public String toString() {
      return displayName.isBlank() ? mediaTypeBibName : displayName;
    }
  }

  /**
   * Erzeugt die Shell und initialisiert nur noch die aktive dynamische
   * Grundstruktur. Die alten festen Formulare werden beim Erzeugen der Shell
   * nicht mehr aufgebaut.
   */
  public BibliographyEditorShell() {
    initializeStructure();
    initializeHeaderAndFooter();
    initializeTypeSelection();
  }

  /**
   * Legt die Standardstruktur der Shell an.
   * Der Inhaltsbereich arbeitet aktiv nur noch mit dem dynamischen
   * Formular-Host.
   */
  private void initializeStructure() {
    root.getStyleClass().add("biblio-editor-shell");
    toggleEdit.getStyleClass().add("biblio-edit-toggle");
    toggleEdit.setFocusTraversable(false);
    typeCombo.getStyleClass().add("biblio-type-combo");
    formHost.getStyleClass().add("biblio-form-host");
    content.getStyleClass().add("biblio-editor-content");
    content.getChildren().setAll(formHost);
    setEditMode(true);
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
   * Initialisiert die Formularumschaltung der Typauswahl.
   * Die eigentlichen auswählbaren Medientypen werden von außen über
   * {@link #setMediaTypeOptions(List)} gesetzt.
   */
  private void initializeTypeSelection() {
    typeCombo.valueProperty().addListener((obs, old, value) -> switchForm(value));
    switchForm(typeCombo.getValue());
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
   * Konfiguriert die verfügbaren Medientyp-Optionen der Shell.
   *
   * @param options auswählbare Optionen; leere oder {@code null}-Werte werden defensiv behandelt
   */
  public void setMediaTypeOptions(List<MediaTypeOption> options) {
    mediaTypeOptionsByBibName.clear();

    List<MediaTypeOption> safeOptions = new ArrayList<>();
    if (options != null) {
      for (MediaTypeOption option : options) {
        if (option == null) {
          continue;
        }

        String key = normalizeMediaTypeBibName(option.mediaTypeBibName());
        if (key.isBlank()) {
          continue;
        }

        MediaTypeOption normalizedOption = new MediaTypeOption(
            key,
            option.displayName()
        );

        safeOptions.add(normalizedOption);
        mediaTypeOptionsByBibName.put(key, normalizedOption);
      }
    }

    MediaTypeOption previousSelection = typeCombo.getValue();

    typeCombo.getItems().setAll(safeOptions);

    if (safeOptions.isEmpty()) {
      typeCombo.setValue(null);
      switchForm(null);
      return;
    }

    MediaTypeOption nextSelection = null;
    if (previousSelection != null) {
      nextSelection = mediaTypeOptionsByBibName.get(
          normalizeMediaTypeBibName(previousSelection.mediaTypeBibName())
      );
    }

    if (nextSelection == null) {
      nextSelection = safeOptions.getFirst();
    }

    typeCombo.setValue(nextSelection);
    switchForm(nextSelection);
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
   * Schaltet die Shell in einen Popup-Modus für die Medienverwaltung.
   * Dabei werden die internen Header-Elemente für Typauswahl und
   * Bearbeitungstoggle ausgeblendet, während der Löschen-Button als
   * BOOK_DELETE-Aktion sichtbar bleibt.
   */
  public void configureForMediaManagementPopup() {
    setEditMode(true);

    toggleEdit.setVisible(false);
    toggleEdit.setManaged(false);

    typeCombo.visibleProperty().unbind();
    typeCombo.managedProperty().unbind();
    typeCombo.setVisible(false);
    typeCombo.setManaged(false);

    btnDelete.visibleProperty().unbind();
    btnDelete.managedProperty().unbind();
    btnDelete.setVisible(true);
    btnDelete.setManaged(true);
    btnDelete.setGraphic(BaseIcon.BOOK_DELETE.imageView());
    btnDelete.setTooltip(new Tooltip("Medium löschen"));
  }

  /**
   * Leert die aktuell sichtbare Editoransicht vollständig.
   */
  public void clearEditorView() {
    typeCombo.setValue(null);
    formHost.getChildren().clear();
  }

  /**
   * Liest den aktuellen Zustand des sichtbaren dynamischen Editors in ein
   * dynamisches Bibliographie-Objekt aus.
   *
   * @return dynamischer Bibliographie-Eintrag aus dem aktuellen UI-Zustand
   */
  public DynamicBibliographyEntry readDynamicEntry() {
    MediaTypeDefinition mediaType = resolveSelectedMediaTypeDefinition();
    if (mediaType == null) {
      throw new IllegalStateException("Kein Medientyp in der Shell ausgewählt.");
    }

    DynamicBibliographyEntry entry = new DynamicBibliographyEntry();
    entry.setMediaTypeId(mediaType.id());
    entry.setMediaTypeBibName(mediaType.bibName());
    entry.setMediaTypeName(mediaType.name());

    Integer sourceEntryId = getCurrentSourceEntryId();
    if (sourceEntryId == null || sourceEntryId <= 0) {
      sourceEntryId = getCurrentSelectedEntryId();
    }
    if (sourceEntryId != null && sourceEntryId > 0) {
      entry.setId(sourceEntryId);
    }

    List<MediaAttributeDefinition> attributes = bibliographyService.listAttributesForMediaType(mediaType.id());
    for (MediaAttributeDefinition attribute : attributes) {
      if (attribute == null || attribute.fieldDefinition() == null) {
        continue;
      }

      String bibtexName = attribute.fieldDefinition().bibtexName();
      String datatype = attribute.fieldDefinition().datatype();

      if (isPersonDatatype(datatype)) {
        List<PersonRef> persons = mapAuthors(
            getCurrentAuthorsForField(mediaType.bibName(), bibtexName)
        );
        if (!persons.isEmpty()) {
          entry.putValue(new PersonBibValue(bibtexName, persons));
        }
        continue;
      }

      if (isRelatedDatatype(datatype)) {
        Integer relatedEntryId = getCurrentRelatedEntryId(mediaType.bibName(), bibtexName);
        if (relatedEntryId != null && relatedEntryId > 0) {
          String displayText = getCurrentFieldValue(mediaType.bibName(), bibtexName);
          entry.putValue(new RelatedBibValue(
              bibtexName,
              relatedEntryId,
              null,
              displayText
          ));
        }
        continue;
      }

      String rawValue = getCurrentFieldValue(bibtexName);

      if (isIntegerDatatype(datatype)) {
        Integer parsed = parseIntegerValue(rawValue);
        if (parsed != null || !rawValue.isBlank()) {
          entry.putValue(new IntegerBibValue(bibtexName, parsed));
        }
      } else {
        if (!rawValue.isBlank()) {
          entry.putValue(new StringBibValue(bibtexName, rawValue));
        }
      }
    }

    return entry;
  }

  /**
   * Schreibt einen dynamischen Bibliographie-Eintrag direkt in die Shell zurück.
   *
   * @param entry dynamischer Eintrag
   * @param editMode gewünschter Bearbeitungsmodus
   */
  public void showDynamicEntry(DynamicBibliographyEntry entry, boolean editMode) {
    if (entry == null) {
      throw new IllegalArgumentException("entry darf nicht null sein.");
    }

    String mediaTypeBibName = normalizeMediaTypeBibName(entry.getMediaTypeBibName());
    if (mediaTypeBibName.isBlank()) {
      mediaTypeBibName = getSelectedMediaTypeBibName();
    }

    showNewEditor(mediaTypeBibName);
    setEditMode(editMode);

    setSelectedEntryId(mediaTypeBibName, entry.getId());
    setSourceEntryId(mediaTypeBibName, entry.getId());
    setAutoFillLocked(mediaTypeBibName, false);
    setAutoFilled(mediaTypeBibName, false);

    for (BibValue value : entry.getOrderedValues()) {
      switch (value) {
        case StringBibValue stringBibValue ->
            setCurrentFieldValue(stringBibValue.bibtexName(), stringBibValue.value());

        case IntegerBibValue integerBibValue ->
            setCurrentFieldValue(
                integerBibValue.bibtexName(),
                integerBibValue.value() == null ? "" : String.valueOf(integerBibValue.value())
            );

        case RelatedBibValue relatedBibValue ->
            setCurrentRelatedEntry(
                relatedBibValue.bibtexName(),
                relatedBibValue.relatedEntryId(),
                relatedBibValue.displayValue()
            );

        case PersonBibValue personBibValue ->
            setCurrentAuthorsForField(
                mediaTypeBibName,
                personBibValue.bibtexName(),
                mapPersons(personBibValue.value())
            );

        default -> {
        }
      }
    }
  }

  /**
   * Ermittelt den aktuell selektierten Medientyp der Shell.
   *
   * @return Medientypdefinition oder {@code null}
   */
  private MediaTypeDefinition resolveSelectedMediaTypeDefinition() {
    if (bibliographyService == null) {
      return null;
    }

    String mediaTypeBibName = getSelectedMediaTypeBibName();
    if (mediaTypeBibName.isBlank()) {
      return null;
    }

    return bibliographyService.listMediaTypes().stream()
               .filter(type -> normalizeMediaTypeBibName(type.bibName()).equals(mediaTypeBibName))
               .findFirst()
               .orElse(null);
  }

  /**
   * Prüft, ob ein Metadaten-Datentyp als Integer behandelt werden soll.
   *
   * @param datatype Metadaten-Datentyp
   * @return {@code true}, wenn Integer-Semantik vorliegt
   */
  private boolean isIntegerDatatype(String datatype) {
    String normalized = normalizeMediaTypeBibName(datatype);
    return normalized.equalsIgnoreCase("integer")
               || normalized.equalsIgnoreCase("year")
               || normalized.equalsIgnoreCase("month");
  }

  /**
   * Prüft, ob ein Metadaten-Datentyp als Related-Feld behandelt werden soll.
   *
   * @param datatype Metadaten-Datentyp
   * @return {@code true}, wenn Related-Semantik vorliegt
   */
  private boolean isRelatedDatatype(String datatype) {
    return "anderer eintrag/integer".equalsIgnoreCase(normalizeMediaTypeBibName(datatype));
  }

  /**
   * Parst einen Integer-Wert tolerant aus Text.
   *
   * @param raw roher Eingabetext
   * @return geparster Integer oder {@code null}
   */
  private Integer parseIntegerValue(String raw) {
    String text = raw == null ? "" : raw.trim();
    if (text.isBlank()) {
      return null;
    }

    try {
      return Integer.parseInt(text);
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  /**
   * Wandelt Autoren aus der Shell in Personenreferenzen um.
   *
   * @param authors Autorenliste aus der UI
   * @return Personenreferenzen für das dynamische Modell
   */
  private List<PersonRef> mapAuthors(List<Author> authors) {
    if (authors == null || authors.isEmpty()) {
      return List.of();
    }

    List<PersonRef> result = new ArrayList<>();
    int pos = 1;

    for (Author author : authors) {
      if (author == null) {
        continue;
      }

      String firstName = author.getFirstName() == null ? "" : author.getFirstName().trim();
      String lastName = author.getLastName() == null ? "" : author.getLastName().trim();

      if (firstName.isBlank() && lastName.isBlank()) {
        continue;
      }

      result.add(new PersonRef(
          author.getId(),
          firstName,
          lastName,
          author.getPosition() > 0 ? author.getPosition() : pos
      ));
      pos++;
    }

    return result;
  }

  /**
   * Wandelt Personenreferenzen des dynamischen Modells in Autoren der UI um.
   *
   * @param persons Personenreferenzen
   * @return Autorenliste für die Shell
   */
  private List<Author> mapPersons(List<PersonRef> persons) {
    if (persons == null || persons.isEmpty()) {
      return List.of();
    }

    List<Author> result = new ArrayList<>();
    int pos = 1;

    for (PersonRef person : persons) {
      if (person == null) {
        continue;
      }

      Author author = new Author();
      author.setId(person.id());
      author.setFirstName(person.firstName());
      author.setLastName(person.lastName());
      author.setPosition(person.position() != null && person.position() > 0 ? person.position() : pos++);
      result.add(author);
    }

    return result;
  }

  /**
   * Baut für einen Medientyp bei Bedarf ein metadatengesteuertes Formular auf.
   *
   * @param mediaType Medientypdefinition
   */
  private void ensureDynamicForm(MediaTypeDefinition mediaType) {
    if (mediaType == null) {
      return;
    }

    String mediaTypeBibName = normalizeMediaTypeBibName(mediaType.bibName());
    if (mediaTypeBibName.isBlank() || bibliographyService == null) {
      return;
    }

    if (dynamicFormsByMediaType.containsKey(mediaTypeBibName)) {
      return;
    }

    List<MediaAttributeDefinition> attributes =
        bibliographyService.listAttributesForMediaType(mediaType.id()).stream()
            .filter(attribute -> attribute.isIdentify()
                                     || attribute.isNecessary()
                                     || attribute.isDesired())
            .toList();

    VBox form = new VBox(8);
    form.getStyleClass().add("biblio-dynamic-form");

    Map<String, Object> fieldRegistry = new LinkedHashMap<>();

    for (MediaAttributeDefinition attribute : attributes) {
      createDynamicField(form, fieldRegistry, mediaTypeBibName, attribute);
    }

    dynamicFormsByMediaType.put(mediaTypeBibName, form);
    dynamicFieldRegistryByMediaType.put(mediaTypeBibName, fieldRegistry);
    attributesByMediaType.put(mediaTypeBibName, attributes);
  }

  /**
   * Erzeugt eine UI-Komponente für ein einzelnes Attribut des dynamischen
   * Formulars und registriert diese unter dem technischen BibTeX-Namen.
   *
   * @param form Zielcontainer des dynamischen Formulars
   * @param fieldRegistry Registry der Feldkomponenten des Formulars
   * @param ownerMediaTypeBibName technischer Medientypname des Formulars
   * @param attribute Attributdefinition des zu erzeugenden Feldes
   */
  private void createDynamicField(VBox form,
                                  Map<String, Object> fieldRegistry,
                                  String ownerMediaTypeBibName,
                                  MediaAttributeDefinition attribute) {
    if (form == null
            || fieldRegistry == null
            || ownerMediaTypeBibName == null
            || attribute == null
            || attribute.fieldDefinition() == null) {
      return;
    }

    String normalizedOwnerMediaTypeBibName = normalizeMediaTypeBibName(ownerMediaTypeBibName);
    String bibtexName = normalizeBibtexName(attribute.fieldDefinition().bibtexName());
    String datatype = normalizeBibtexName(attribute.fieldDefinition().datatype());
    String labelText = attribute.fieldDefinition().displayName();

    if (bibtexName.isBlank()) {
      return;
    }

    if (isPersonDatatype(datatype)) {
      BibliographyAuthorsView authorsView = new BibliographyAuthorsView(
          normalizedOwnerMediaTypeBibName,
          editProperty(),
          row -> findDynamicAuthorSuggestions(
              normalizedOwnerMediaTypeBibName,
              bibtexName,
              row.getLastName(),
              getSelectedEntryId(normalizedOwnerMediaTypeBibName)
          ),
          this::handleAuthorsEdited,
          () -> {
          },
          this::isLookupSuppressed,
          this::setSelectedEntryId
      );

      form.getChildren().add(labeledNode(labelText, authorsView));
      fieldRegistry.put(bibtexName, authorsView);
      return;
    }

    if (isRelatedDatatype(datatype)) {
      BibliographyFieldView fieldView = new BibliographyFieldView(labelText, editProperty());

      bindDynamicLookupIfAvailable(
          fieldView,
          normalizedOwnerMediaTypeBibName,
          attribute
      );

      form.getChildren().add(fieldView);
      fieldRegistry.put(bibtexName, fieldView);
      return;
    }

    if (isDatePickerDatatype(datatype, bibtexName)) {
      DatePicker datePicker = new DatePicker();
      form.getChildren().add(labeledNode(labelText, datePicker));
      fieldRegistry.put(bibtexName, datePicker);
      return;
    }

    BibliographyFieldView fieldView = new BibliographyFieldView(labelText, editProperty());

    bindDynamicLookupIfAvailable(
        fieldView,
        normalizedOwnerMediaTypeBibName,
        attribute
    );

    form.getChildren().add(fieldView);
    fieldRegistry.put(bibtexName, fieldView);
  }

  /**
   * Verdrahtet ein Feld bei vorhandenem dynamischen Lookup-Koordinator mit
   * metadatengetriebener Lookup-Logik.
   *
   * @param fieldView Feldansicht
   * @param ownerMediaTypeBibName technischer Medientypname des Formulars
   * @param attribute Attributdefinition des Feldes
   */
  private void bindDynamicLookupIfAvailable(BibliographyFieldView fieldView,
                                            String ownerMediaTypeBibName,
                                            MediaAttributeDefinition attribute) {
    if (dynamicLookupCoordinator == null || fieldView == null || attribute == null) {
      return;
    }

    dynamicLookupCoordinator.bindField(
        fieldView,
        ownerMediaTypeBibName,
        attribute,
        createLookupShellAccess()
    );
  }

  /**
   * Liefert Autorenvorschläge für ein Personenfeld des dynamischen Editors.
   * Bevorzugt wird der {@link DynamicLookupCoordinator}; fehlt dieser noch,
   * wird defensiv direkt auf den Service zurückgefallen.
   *
   * @param ownerMediaTypeBibName technischer Medientypname des Formulars
   * @param personBibtexName technischer Feldname des Personenfeldes
   * @param lastNamePrefix aktuelles Präfix des Nachnamens
   * @param selectedEntryId aktuell selektierter Eintrag oder {@code null}
   * @return passende Autorenvorschläge
   */
  private List<AuthorSuggestion> findDynamicAuthorSuggestions(String ownerMediaTypeBibName,
                                                              String personBibtexName,
                                                              String lastNamePrefix,
                                                              Integer selectedEntryId) {
    if (dynamicLookupCoordinator != null) {
      return dynamicLookupCoordinator.findPersonSuggestions(
          ownerMediaTypeBibName,
          personBibtexName,
          lastNamePrefix,
          selectedEntryId
      );
    }

    if (bibliographyService == null) {
      return List.of();
    }

    return bibliographyService.findAuthorSuggestions(
        ownerMediaTypeBibName,
        lastNamePrefix,
        selectedEntryId
    );
  }

  /**
   * Erzeugt die schmale Shell-Zugriffsfläche für den
   * {@link DynamicLookupCoordinator}.
   *
   * @return Zugriff auf den notwendigen Laufzeitzustand der Shell
   */
  private DynamicLookupCoordinator.ShellAccess createLookupShellAccess() {
    return new DynamicLookupCoordinator.ShellAccess() {
      @Override
      public boolean isLookupSuppressed() {
        return BibliographyEditorShell.this.isLookupSuppressed();
      }

      @Override
      public void setLookupSuppressed(boolean suppressed) {
        BibliographyEditorShell.this.setLookupSuppressed(suppressed);
      }

      @Override
      public String getCurrentFieldValue(String mediaTypeBibName, String bibtexName) {
        return BibliographyEditorShell.this.getCurrentFieldValue(mediaTypeBibName, bibtexName);
      }

      @Override
      public Integer getCurrentRelatedEntryId(String mediaTypeBibName, String bibtexName) {
        return BibliographyEditorShell.this.getCurrentRelatedEntryId(mediaTypeBibName, bibtexName);
      }

      @Override
      public void setCurrentRelatedEntry(String mediaTypeBibName,
                                         String bibtexName,
                                         Integer entryId,
                                         String displayText) {
        BibliographyEditorShell.this.setCurrentRelatedEntry(
            mediaTypeBibName,
            bibtexName,
            entryId,
            displayText
        );
      }

      @Override
      public Integer getSelectedEntryId(String mediaTypeBibName) {
        return BibliographyEditorShell.this.getSelectedEntryId(mediaTypeBibName);
      }

      @Override
      public void setSelectedEntryId(String mediaTypeBibName, Integer entryId) {
        BibliographyEditorShell.this.setSelectedEntryId(mediaTypeBibName, entryId);
      }

      @Override
      public boolean wasAutoFilled(String mediaTypeBibName) {
        return BibliographyEditorShell.this.wasAutoFilled(mediaTypeBibName);
      }

      @Override
      public void setAutoFilled(String mediaTypeBibName, boolean autoFilled) {
        BibliographyEditorShell.this.setAutoFilled(mediaTypeBibName, autoFilled);
      }

      @Override
      public void setAutoFillLocked(String mediaTypeBibName, boolean locked) {
        BibliographyEditorShell.this.setAutoFillLocked(mediaTypeBibName, locked);
      }

      @Override
      public List<Author> getCurrentAuthors(String mediaTypeBibName) {
        return BibliographyEditorShell.this.getCurrentAuthorsForField(
            mediaTypeBibName,
            "author"
        );
      }

      @Override
      public List<Author> getCurrentPersons(String mediaTypeBibName, String bibtexName) {
        return BibliographyEditorShell.this.getCurrentAuthorsForField(
            mediaTypeBibName,
            bibtexName
        );
      }

      @Override
      public void applyResolvedEntry(String mediaTypeBibName, Integer entryId, boolean markAsAutoFill) {
        if (bibliographyService == null || entryId == null || entryId <= 0) {
          return;
        }

        bibliographyService.loadEntry(entryId).ifPresent(entry -> {
          BibliographyEditorShell.this.showDynamicEntry(entry, true);
          BibliographyEditorShell.this.setSelectedEntryId(mediaTypeBibName, entryId);
          BibliographyEditorShell.this.setSourceEntryId(mediaTypeBibName, entryId);
          BibliographyEditorShell.this.setAutoFillLocked(mediaTypeBibName, false);
          BibliographyEditorShell.this.setAutoFilled(mediaTypeBibName, markAsAutoFill);
        });
      }
    };
  }

  /**
   * Liest die Autoren eines konkreten dynamischen Personenfeldes aus.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @param bibtexName technischer Feldname
   * @return Autorenliste des Feldes oder eine leere Liste
   */
  private List<Author> getCurrentAuthorsForField(String mediaTypeBibName, String bibtexName) {
    Map<String, Object> registry = dynamicFieldRegistryByMediaType.get(normalizeMediaTypeBibName(mediaTypeBibName));
    if (registry == null) {
      return List.of();
    }

    Object component = registry.get(normalizeBibtexName(bibtexName));
    if (component instanceof BibliographyAuthorsView authorsView) {
      return authorsView.toAuthors();
    }

    return List.of();
  }

  /**
   * Schreibt Autoren in ein konkretes dynamisches Personenfeld zurück.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @param bibtexName technischer Feldname
   * @param authors zu setzende Autorenliste
   */
  private void setCurrentAuthorsForField(String mediaTypeBibName,
                                         String bibtexName,
                                         List<Author> authors) {
    Map<String, Object> registry = dynamicFieldRegistryByMediaType.get(normalizeMediaTypeBibName(mediaTypeBibName));
    if (registry == null) {
      return;
    }

    Object component = registry.get(normalizeBibtexName(bibtexName));
    if (component instanceof BibliographyAuthorsView authorsView) {
      applyAuthors(authorsView, authors);
    }
  }

  /**
   * Leert den Formularbereich, wenn für einen Medientyp kein dynamisches
   * Formular aufgebaut werden kann.
   * Ein Rücksprung in die alte Enum-gesteuerte Formularlogik findet hier
   * nicht mehr statt.
   *
   * @param option Medientypoption
   */
  /**
   * Verpackt einen Knoten mit Beschriftung.
   *
   * @param labelText Beschriftung
   * @param node Zielknoten
   * @return Container mit Label und Knoten
   */
  private Node labeledNode(String labelText, Node node) {
    VBox box = new VBox(4);
    Label label = new Label(labelText == null ? "" : labelText);
    box.getChildren().addAll(label, node);
    return box;
  }

  private boolean isPersonDatatype(String datatype) {
    return "person".equalsIgnoreCase(normalizeBibtexName(datatype));
  }

  private boolean isDatePickerDatatype(String datatype, String bibtexName) {
    String normalizedDatatype = normalizeBibtexName(datatype);
    String normalizedBibtexName = normalizeBibtexName(bibtexName);

    return "datum".equalsIgnoreCase(normalizedDatatype)
               || "date".equalsIgnoreCase(normalizedDatatype)
               || "date".equalsIgnoreCase(normalizedBibtexName)
               || "urldate".equalsIgnoreCase(normalizedBibtexName)
               || "ai_used_at".equalsIgnoreCase(normalizedBibtexName);
  }

  /**
   * Setzt den Provider für Anbieter-/Modellvorschläge der KI-Quelle.
   *
   * @param provider Vorschlagsprovider; {@code null} deaktiviert Vorschläge
   */
  public void setAiChoiceProvider(AiChoiceProvider provider) {
    this.aiChoiceProvider = provider == null ? AiChoiceProvider.NONE : provider;
  }

  /**
   * Wechselt das sichtbare Formular entsprechend der gewählten Medientyp-Option.
   *
   * @param option gewünschte Medientyp-Option
   */
  private void switchForm(MediaTypeOption option) {
    formHost.getChildren().clear();

    if (option == null) {
      onNoneSelected.run();
      return;
    }

    showNewEditor(option.mediaTypeBibName());
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
   * Liefert den aktuell ausgewählten technischen Medientypnamen.
   *
   * @return technischer Medientypname oder leerer String
   */
  public String getSelectedMediaTypeBibName() {
    MediaTypeOption option = typeCombo.getValue();
    return option == null ? "" : normalizeMediaTypeBibName(option.mediaTypeBibName());
  }

  /**
   * Prüft, ob für einen technischen Medientyp aktuell eine auswählbare
   * Option in der Shell vorhanden ist.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @return {@code true}, wenn eine passende Option existiert
   */
  public boolean supportsMediaTypeBibName(String mediaTypeBibName) {
    return mediaTypeOptionsByBibName.containsKey(normalizeMediaTypeBibName(mediaTypeBibName));
  }

  /**
   * Öffnet einen leeren Editor für einen technischen Medientypnamen.
   * Wenn der dynamische Formularpfad noch nicht verfügbar ist, wird
   * automatisch auf das vorhandene Legacy-Formular zurückgefallen.
   *
   * @param mediaTypeBibName technischer Medientypname
   */
  public void showNewEditor(String mediaTypeBibName) {
    String normalizedBibName = normalizeMediaTypeBibName(mediaTypeBibName);
    MediaTypeOption option = mediaTypeOptionsByBibName.get(normalizedBibName);

    if (option != null) {
      typeCombo.setValue(option);
    }

    if (normalizedBibName.isBlank()) {
      formHost.getChildren().clear();
      setEditMode(true);
      return;
    }

    if (bibliographyService == null) {
      formHost.getChildren().clear();
      setEditMode(true);
      return;
    }

    MediaTypeDefinition mediaType = bibliographyService.listMediaTypes().stream()
                                        .filter(type -> normalizeMediaTypeBibName(type.bibName()).equals(normalizedBibName))
                                        .findFirst()
                                        .orElse(null);

    if (mediaType == null) {
      formHost.getChildren().clear();
      setEditMode(true);
      return;
    }

    ensureDynamicForm(mediaType);

    VBox form = dynamicFormsByMediaType.get(normalizedBibName);
    if (form == null || form.getChildren().isEmpty()) {
      formHost.getChildren().clear();
      setEditMode(true);
      return;
    }

    formHost.getChildren().setAll(form);
    clearDynamicFormValues(normalizedBibName);
    setEditMode(true);
  }

  /**
   * Leert die Werte des dynamischen Formulars eines Medientyps einschließlich
   * der gemerkten Related-Ziel-IDs seiner Felder.
   *
   * @param mediaTypeBibName technischer Medientypname
   */
  private void clearDynamicFormValues(String mediaTypeBibName) {
    String normalizedMediaTypeBibName = normalizeMediaTypeBibName(mediaTypeBibName);
    Map<String, Object> registry = dynamicFieldRegistryByMediaType.get(normalizedMediaTypeBibName);
    if (registry == null) {
      return;
    }

    for (Map.Entry<String, Object> entry : registry.entrySet()) {
      String bibtexName = entry.getKey();
      Object component = entry.getValue();

      relatedEntryIdsByFieldKey.remove(relatedFieldKey(normalizedMediaTypeBibName, bibtexName));

      if (component instanceof BibliographyFieldView fieldView) {
        fieldView.setText("");
        fieldView.hideSuggestions();
      } else if (component instanceof BibliographyAuthorsView authorsView) {
        applyAuthors(authorsView, List.of());
      } else if (component instanceof DatePicker datePicker) {
        datePicker.setValue(null);
      }
    }

    setSelectedEntryId(normalizedMediaTypeBibName, null);
    setSourceEntryId(normalizedMediaTypeBibName, null);
    setAutoFillLocked(normalizedMediaTypeBibName, false);
    setAutoFilled(normalizedMediaTypeBibName, false);
  }

  /**
   * Liest einen Feldwert des aktuell sichtbaren dynamischen Formulars über
   * seinen technischen Feldnamen aus.
   *
   * @param bibtexName technischer Feldname
   * @return Feldinhalt oder leerer String
   */
  public String getCurrentFieldValue(String bibtexName) {
    String key = normalizeBibtexName(bibtexName);
    String mediaTypeBibName = getSelectedMediaTypeBibName();

    Map<String, Object> registry = dynamicFieldRegistryByMediaType.get(mediaTypeBibName);
    if (registry == null) {
      return "";
    }

    Object component = registry.get(key);
    if (component instanceof BibliographyFieldView fieldView) {
      return fieldView.getText();
    }
    if (component instanceof DatePicker datePicker) {
      return datePicker.getValue() == null ? "" : datePicker.getValue().toString();
    }
    if (component instanceof BibliographyAuthorsView) {
      return "";
    }

    return "";
  }

  /**
   * Setzt einen Feldwert des aktuell sichtbaren dynamischen Formulars über
   * seinen technischen Feldnamen.
   *
   * @param bibtexName technischer Feldname
   * @param value zu setzender Wert
   */
  public void setCurrentFieldValue(String bibtexName, String value) {
    String key = normalizeBibtexName(bibtexName);
    String text = value == null ? "" : value.trim();
    String mediaTypeBibName = getSelectedMediaTypeBibName();

    Map<String, Object> registry = dynamicFieldRegistryByMediaType.get(mediaTypeBibName);
    if (registry == null) {
      return;
    }

    Object component = registry.get(key);
    if (component instanceof BibliographyFieldView fieldView) {
      fieldView.setText(text);
      return;
    }
    if (component instanceof DatePicker datePicker) {
      datePicker.setValue(parseLocalDate(text));
    }
  }

  /**
   * Liefert die aktuell ausgewählten Autoren des sichtbaren Formulars.
   *
   * @return aktuelle Autorenliste
   */
  public List<Author> getCurrentAuthors() {
    Map<String, Object> registry = dynamicFieldRegistryByMediaType.get(getSelectedMediaTypeBibName());
    if (registry != null) {
      for (Object component : registry.values()) {
        if (component instanceof BibliographyAuthorsView authorsView) {
          return authorsView.toAuthors();
        }
      }
    }
    return getAuthors();
  }

  /**
   * Setzt die Autoren des aktuell sichtbaren dynamischen Formulars.
   *
   * @param authors neue Autorenliste
   */
  public void setCurrentAuthors(List<Author> authors) {
    Map<String, Object> registry = dynamicFieldRegistryByMediaType.get(getSelectedMediaTypeBibName());
    if (registry == null) {
      return;
    }

    for (Object component : registry.values()) {
      if (component instanceof BibliographyAuthorsView authorsView) {
        applyAuthors(authorsView, authors);
        return;
      }
    }
  }

  /**
   * Liefert die aktuell für ein Related-Feld gemerkte Ziel-ID aus dem
   * dynamischen Formularzustand.
   *
   * @param bibtexName technischer Related-Feldname
   * @return Ziel-ID oder {@code null}
   */
  public Integer getCurrentRelatedEntryId(String bibtexName) {
    String key = normalizeBibtexName(bibtexName);
    String mediaTypeBibName = getSelectedMediaTypeBibName();
    return relatedEntryIdsByFieldKey.get(relatedFieldKey(mediaTypeBibName, key));
  }

  /**
   * Liefert die Ursprungs-ID des aktuell im sichtbaren Formular geladenen
   * bibliographischen Eintrags.
   *
   * @return Ursprungs-ID oder {@code null}
   */
  public Integer getCurrentSourceEntryId() {
    return getSourceEntryId(getSelectedMediaTypeBibName());
  }

  /**
   * Liefert die aktuell selektierte Eintrags-ID des sichtbaren Formulars.
   *
   * @return selektierte Eintrags-ID oder {@code null}
   */
  public Integer getCurrentSelectedEntryId() {
    return getSelectedEntryId(getSelectedMediaTypeBibName());
  }

  /**
   * Setzt die aktuell selektierte Related-ID für das sichtbare dynamische
   * Formular.
   *
   * @param bibtexName technischer Related-Feldname
   * @param entryId Ziel-ID
   * @param displayText Anzeigetext des Zielmediums
   */
  public void setCurrentRelatedEntry(String bibtexName, Integer entryId, String displayText) {
    setCurrentRelatedEntry(
        getSelectedMediaTypeBibName(),
        bibtexName,
        entryId,
        displayText
    );
  }

  /**
   * Setzt die aktuell selektierte Related-ID für das sichtbare Formular und
   * belässt den aktuellen Anzeigetext des Feldes unverändert.
   *
   * @param bibtexName technischer Related-Feldname
   * @param entryId Ziel-ID oder {@code null}
   */
  public void setCurrentRelatedEntryId(String bibtexName, Integer entryId) {
    String displayText = getCurrentFieldValue(getSelectedMediaTypeBibName(), bibtexName);
    setCurrentRelatedEntry(
        getSelectedMediaTypeBibName(),
        bibtexName,
        entryId,
        displayText
    );
  }

  /**
   * Fordert das Anlegen eines Related-Zielmediums anhand des technischen
   * Feldnamens an.
   *
   * @param bibtexName technischer Related-Feldname
   * @param displayText aktueller Feldtext
   */
  public void requestCreateRelatedEntry(String bibtexName, String displayText) {
    if (dynamicLookupCoordinator == null) {
      return;
    }

    String ownerMediaTypeBibName = getSelectedMediaTypeBibName();
    String targetMediaTypeBibName =
        dynamicLookupCoordinator.resolveRelatedTargetMediaTypeBibName(
            ownerMediaTypeBibName,
            bibtexName
        );

    if (targetMediaTypeBibName.isBlank()) {
      return;
    }

    onRequestCreateRelated.requestCreateRelated(
        normalizeBibtexName(bibtexName),
        targetMediaTypeBibName,
        displayText == null ? "" : displayText.trim()
    );
  }

  /**
   * Liefert den Feldtext eines technischen Feldes innerhalb eines bestimmten
   * Formulars.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @param bibtexName technischer Feldname
   * @return Feldtext oder leerer String
   */
  public String getCurrentFieldValue(String mediaTypeBibName, String bibtexName) {
    Map<String, Object> registry =
        dynamicFieldRegistryByMediaType.get(normalizeMediaTypeBibName(mediaTypeBibName));
    if (registry == null) {
      return "";
    }

    Object component = registry.get(normalizeBibtexName(bibtexName));

    if (component instanceof BibliographyFieldView fieldView) {
      return fieldView.getText();
    }

    if (component instanceof DatePicker datePicker) {
      LocalDate value = datePicker.getValue();
      return value == null ? "" : value.toString();
    }

    return "";
  }

  /**
   * Liefert die aktuell gesetzte Related-ID eines technischen Feldes innerhalb
   * eines bestimmten Formulars.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @param bibtexName technischer Feldname
   * @return Related-ID oder {@code null}
   */
  public Integer getCurrentRelatedEntryId(String mediaTypeBibName, String bibtexName) {
    return relatedEntryIdsByFieldKey.get(
        relatedFieldKey(
            normalizeMediaTypeBibName(mediaTypeBibName),
            normalizeBibtexName(bibtexName)
        )
    );
  }

  /**
   * Setzt die Related-ID samt Anzeige­text eines technischen Feldes innerhalb
   * eines bestimmten Formulars.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @param bibtexName technischer Feldname
   * @param entryId Ziel-ID oder {@code null}
   * @param displayText Anzeige­text
   */
  public void setCurrentRelatedEntry(String mediaTypeBibName,
                                     String bibtexName,
                                     Integer entryId,
                                     String displayText) {
    String normalizedMediaTypeBibName = normalizeMediaTypeBibName(mediaTypeBibName);
    String normalizedBibtexName = normalizeBibtexName(bibtexName);
    String fieldKey = relatedFieldKey(normalizedMediaTypeBibName, normalizedBibtexName);

    if (entryId == null || entryId <= 0) {
      relatedEntryIdsByFieldKey.remove(fieldKey);
    } else {
      relatedEntryIdsByFieldKey.put(fieldKey, entryId);
    }

    Map<String, Object> registry = dynamicFieldRegistryByMediaType.get(normalizedMediaTypeBibName);
    if (registry == null) {
      return;
    }

    Object component = registry.get(normalizedBibtexName);
    if (component instanceof BibliographyFieldView fieldView) {
      fieldView.setText(displayText == null ? "" : displayText);
    }
  }

  /**
   * Prüft, ob Lookup-Reaktionen momentan unterdrückt werden.
   *
   * @return {@code true}, wenn die Shell gerade programmgesteuert aktualisiert wird
   */
  public boolean isLookupSuppressed() {
    return suppressDynamicRelatedCallbacks;
  }

  /**
   * Setzt die temporäre Unterdrückung von Lookup-Reaktionen.
   *
   * @param suppressed neuer Unterdrückungszustand
   */
  public void setLookupSuppressed(boolean suppressed) {
    this.suppressDynamicRelatedCallbacks = suppressed;
  }

  /**
   * Liefert den Titel des aktuell sichtbaren dynamischen Formulars.
   *
   * @return Titelfeld des aktiven Formulars oder leerer String
   */
  public String getTitleText() {
    return getCurrentFieldValue(getSelectedMediaTypeBibName(), "title");
  }

  /**
   * Liefert die Autoren des aktuell sichtbaren dynamischen Formulars.
   *
   * @return Autorenliste des aktiven Formulars
   */
  public List<Author> getAuthors() {
    String mediaTypeBibName = getSelectedMediaTypeBibName();
    Map<String, Object> registry = dynamicFieldRegistryByMediaType.get(mediaTypeBibName);
    if (registry == null || registry.isEmpty()) {
      return List.of();
    }

    for (Object component : registry.values()) {
      if (component instanceof BibliographyAuthorsView authorsView) {
        return authorsView.toAuthors();
      }
    }

    return List.of();
  }

  /**
   * Registriert einen Callback, der bei leerer Auswahl ausgef�hrt wird.
   *
   * @param action auszuführender Callback; {@code null} wird als No-Op behandelt.
   */
  public void setOnNoneSelected(Runnable action) {
    this.onNoneSelected = action == null ? () -> {
    } : action;
  }

  /**
   * Registriert einen generischen Callback zum Anlegen eines Related-Zielmediums.
   *
   * @param action Callback; {@code null} wird als No-Op behandelt
   */
  public void setOnRequestCreateRelated(RelatedCreationHandler action) {
    this.onRequestCreateRelated = action == null
                                      ? (relatedBibtexName, targetMediaTypeBibName, displayText) -> {
    }
                                      : action;
  }

  /**
   * Reagiert auf Änderungen in Autorenfeldern eines technischen Medientyps.
   * Im dynamischen Editor bedeutet eine Autorenänderung zunächst nur, dass eine
   * eventuell vorher aufgelöste Titelselektion und Autofill-Sperre dieses
   * Medientyps zurückgesetzt wird.
   *
   * @param mediaTypeBibName technischer Medientypname des betroffenen Formulars
   */
  private void handleAuthorsEdited(String mediaTypeBibName) {
    String normalizedMediaTypeBibName = normalizeMediaTypeBibName(mediaTypeBibName);
    if (normalizedMediaTypeBibName.isBlank()) {
      return;
    }
    setAutoFillLocked(normalizedMediaTypeBibName, false);
    setAutoFilled(normalizedMediaTypeBibName, false);
  }

  /**
   * Liefert die Autorenliste eines technischen Medientyps aus dem aktuellen
   * dynamischen Formularzustand.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @return aktuelle Autorenliste
   */
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
  /**
   * Liest die Autoren einer Autorenansicht sicher aus.
   *
   * @param view auszulesende Autorenansicht.
   * @return Autorenliste oder leere Liste.
   */
  /**
   * Aktualisiert eine Map mit optionaler Eintrags-ID.
   *
   * @param map Ziel-Map
   * @param key Schlüssel der Ziel-Map
   * @param entryId zu setzende ID; ungültige Werte entfernen den Schlüssel
   * @param <K> Schlüsseltyp der Ziel-Map
   */
  private <K> void updateEntryIdMap(Map<K, Integer> map, K key, Integer entryId) {
    if (map == null || key == null) {
      return;
    }

    if (entryId == null || entryId <= 0) {
      map.remove(key);
    } else {
      map.put(key, entryId);
    }
  }

  /**
   * Setzt den Text eines Bibliographiefeldes sicher.
   *
   * @param field Ziel-Feld
   * @param value neuer Text
   */
  /**
   * Normalisiert einen technischen BibTeX-Namen defensiv.
   *
   * @param value Eingabewert
   * @return bereinigter Feldname
   */
  private String normalizeBibtexName(String value) {
    return value == null ? "" : value.trim();
  }

  /**
   * Liest einen dynamischen Bibliographie-Eintrag direkt in die Shell ein.
   *
   * @param entry dynamischer Bibliographie-Eintrag
   */
  /**
   * Parst ein Datum tolerant aus ISO-Text.
   *
   * @param value Eingabetext
   * @return geparstes Datum oder {@code null}
   */
  private LocalDate parseLocalDate(String value) {
    String text = value == null ? "" : value.trim();
    if (text.isBlank()) {
      return null;
    }

    try {
      return LocalDate.parse(text);
    } catch (Exception ex) {
      return null;
    }
  }

  /**
   * Schreibt Autoren direkt in die zugrunde liegende Autorenansicht.
   *
   * @param view Zielansicht
   * @param authors neue Autorenliste
   */
  private void applyAuthors(BibliographyAuthorsView view, List<Author> authors) {
    if (view == null) {
      return;
    }

    List<Author> safeAuthors = authors == null ? List.of() : new ArrayList<>(authors);
    view.fromAuthors(safeAuthors);
  }

  /**
   * Normalisiert einen technischen Medientypnamen.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @return normalisierter Medientypname oder leerer String
   */
  private String normalizeMediaTypeBibName(String mediaTypeBibName) {
    return mediaTypeBibName == null ? "" : mediaTypeBibName.trim().toLowerCase();
  }

  /**
   * Baut einen stabilen Schlüssel für ein Related-Feld innerhalb eines
   * bestimmten Medientyps auf.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @param bibtexName technischer Feldname
   * @return zusammengesetzter Feldschlüssel
   */
  private String relatedFieldKey(String mediaTypeBibName, String bibtexName) {
    return normalizeMediaTypeBibName(mediaTypeBibName) + "::" + normalizeBibtexName(bibtexName);
  }

  /**
   * Liefert die selektierte Eintrags-ID eines technischen Medientyps.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @return selektierte Eintrags-ID oder {@code null}
   */
  private Integer getSelectedEntryId(String mediaTypeBibName) {
    return selectedEntryIdsByMediaType.get(normalizeMediaTypeBibName(mediaTypeBibName));
  }

  /**
   * Setzt oder entfernt die selektierte Eintrags-ID eines technischen Medientyps.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @param entryId zu speichernde ID; ungültige Werte entfernen den Eintrag
   */
  private void setSelectedEntryId(String mediaTypeBibName, Integer entryId) {
    updateEntryIdMap(selectedEntryIdsByMediaType, normalizeMediaTypeBibName(mediaTypeBibName), entryId);
  }

  /**
   * Prüft, ob der automatische Titelfill für einen technischen Medientyp gesperrt ist.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @return {@code true}, wenn kein weiterer Autofill erfolgen soll
   */
  private boolean isAutoFillLocked(String mediaTypeBibName) {
    return Boolean.TRUE.equals(autoFillLockedByMediaType.get(normalizeMediaTypeBibName(mediaTypeBibName)));
  }

  /**
   * Setzt die Sperre für automatisches Titelfill eines technischen Medientyps.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @param locked neue Sperreinstellung
   */
  private void setAutoFillLocked(String mediaTypeBibName, boolean locked) {
    autoFillLockedByMediaType.put(normalizeMediaTypeBibName(mediaTypeBibName), locked);
  }

  /**
   * Prüft, ob der Titel eines technischen Medientyps zuvor automatisch gefüllt wurde.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @return {@code true}, wenn Autofill zuvor stattgefunden hat
   */
  private boolean wasAutoFilled(String mediaTypeBibName) {
    return Boolean.TRUE.equals(
        autoFilledTitleByMediaType.get(normalizeMediaTypeBibName(mediaTypeBibName))
    );
  }

  /**
   * Merkt, ob ein Titel eines technischen Medientyps automatisch gefüllt wurde.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @param value neuer Merkerzustand
   */
  private void setAutoFilled(String mediaTypeBibName, boolean value) {
    autoFilledTitleByMediaType.put(normalizeMediaTypeBibName(mediaTypeBibName), value);
  }

  /**
   * Liefert die Ursprungs-ID eines geladenen Eintrags für einen technischen Medientyp.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @return Ursprungs-ID oder {@code null}
   */
  private Integer getSourceEntryId(String mediaTypeBibName) {
    return sourceEntryIdsByMediaType.get(normalizeMediaTypeBibName(mediaTypeBibName));
  }

  /**
   * Setzt oder entfernt die Ursprungs-ID eines geladenen Eintrags für einen technischen Medientyp.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @param entryId zu speichernde Ursprungs-ID
   */
  private void setSourceEntryId(String mediaTypeBibName, Integer entryId) {
    updateEntryIdMap(sourceEntryIdsByMediaType, normalizeMediaTypeBibName(mediaTypeBibName), entryId);
  }
}


