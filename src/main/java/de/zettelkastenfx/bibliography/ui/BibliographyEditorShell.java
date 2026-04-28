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
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import lombok.Setter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

import static de.zettelkastenfx.bibliography.util.BibtexDatatypeUtil.*;

/**
 * Orchestriert den Editor für bibliographische Angaben.
 * Die Klasse verwaltet Typauswahl, Formulare, Lookup-Interaktionen und den Transfer
 * zwischen UI-Komponenten und den Domänenobjekten der Bibliographie.
 */
public class BibliographyEditorShell {

  private final VBox root = new VBox(8);
  private final ToggleButton toggleEdit = new ToggleButton();
  private final VBox content = new VBox(8);
  private final TextField typeField = new TextField();
  private final ContextMenu typeSuggestionMenu = new ContextMenu();

  private final ScrollPane formHost = new ScrollPane();
  private final VBox formPane = new VBox(8);
  private final VBox formFieldsBox = new VBox(8);
  private final HBox dynamicAddRow = new HBox();
  private final Button btnDynamicAdd = BaseIcon.TAG_BLUE_ADD.button("Eigenschaft hinzufügen");
  private final VBox dynamicAddHost = new VBox(6);
  private final ContextMenu dynamicBibtexTypeSuggestionMenu = new ContextMenu();

  @Setter private BibliographyService bibliographyService = null;
  private final Map<String, VBox> dynamicFormsByMediaType = new LinkedHashMap<>();
  private final Map<String, Map<String, Object>> dynamicFieldRegistryByMediaType = new LinkedHashMap<>();


  private final Map<String, MediaTypeOption> mediaTypeOptionsByBibName = new LinkedHashMap<>();
  private final Button btnDelete = BaseIcon.DELETE.button("Angabe löschen");
  private final Button btnAdd = BaseIcon.ADD.button("speichern");

  private final Map<String, Integer> selectedEntryIdsByMediaType = new LinkedHashMap<>();
  private final Map<String, Integer> sourceEntryIdsByMediaType = new LinkedHashMap<>();
  private final Map<String, Boolean> autoFillLockedByMediaType = new LinkedHashMap<>();
  private final Map<String, Boolean> autoFilledTitleByMediaType = new LinkedHashMap<>();
  private final Map<String, Integer> relatedEntryIdsByFieldKey = new LinkedHashMap<>();
  private boolean suppressDynamicRelatedCallbacks;
  private boolean suppressTypeFieldCallbacks;
  private List<MediaTypeOption> allMediaTypeOptions = List.of();
  private String activeMediaTypeBibName = "";

  private final Map<String, Map<String, Node>> dynamicFieldNodesByMediaType = new LinkedHashMap<>();
  private final Map<String, Map<String, BibtexFieldDefinition>> additionalFieldDefinitionsByMediaType =
      new LinkedHashMap<>();

  private EditorUsageContext editorUsageContext = EditorUsageContext.STANDARD;

  private Runnable onCloseEditor = () -> {};
  private Runnable onDynamicAddField = () -> {};
  private boolean noteLinkingContext;
  private boolean dirty;
  private String baselineSignature = "";

  private static final DateTimeFormatter GERMAN_PREVIEW_DATE_FORMAT =
      DateTimeFormatter.ofPattern("d MMM yyyy", Locale.GERMAN);
  private static final double FIELD_LABEL_AREA_WIDTH = 170;
  private static final double FIELD_DELETE_BUTTON_WIDTH = 22;
  private static final double FIELD_DELETE_LABEL_GAP = 6;

  /**
   * Beschreibt den Verwendungszusammenhang der Shell.
   */
  public enum EditorUsageContext {
    STANDARD,
    NOTE_LINKING
  }

  /**
   * Beschreibt das Setzen von farblichen Markierungen im Label des Media-Editors
   */
  private boolean mediaManagementPopupContext;

  /**
   * Beschreibt einen lokal aus dem sichtbaren Editor gelesenen Zustands-Snapshot
   * für die Pflichtfeldvalidierung.
   *
   * @param fieldValues einfache Feldwerte nach technischem BibTeX-Namen
   * @param personValues belegte Personenfelder
   * @param relatedFieldNames belegte Related-Felder
   */
  private record EditorValidationSnapshot(Map<String, String> fieldValues,
                                          Map<String, List<Author>> personValues,
                                          Set<String> relatedFieldNames) {
  }

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
    setOnDynamicAddField(this::showDynamicBibtexTypeSelector);
  }

  /**
   * Legt die Standardstruktur der Shell an.
   * Der Inhaltsbereich arbeitet aktiv nur noch mit einem abgegrenzten
   * Feld-Pane, in dem Formularfelder und der dynamische Add-Bereich
   * getrennt vom Footer sitzen.
   */
  private void initializeStructure() {
    root.getStyleClass().add("biblio-editor-shell");

    toggleEdit.getStyleClass().add("biblio-edit-toggle");
    toggleEdit.setFocusTraversable(false);

    typeField.getStyleClass().add("biblio-type-combo");
    typeField.setPromptText("Medientyp");
    typeField.setFocusTraversable(true);

    typeSuggestionMenu.setAutoHide(true);

    content.getStyleClass().add("biblio-editor-content");

    formHost.getStyleClass().add("biblio-form-host");
    formHost.setFitToWidth(true);
    formHost.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    formHost.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

    VBox.setVgrow(formHost, Priority.ALWAYS);

    formPane.getStyleClass().add("biblio-form-pane");
    formFieldsBox.getStyleClass().add("biblio-form-fields");

    dynamicAddHost.getStyleClass().add("biblio-dynamic-add-host");

    dynamicAddRow.getStyleClass().add("biblio-dynamic-add-row");
    dynamicAddRow.setAlignment(Pos.CENTER_LEFT);
    dynamicAddRow.getChildren().add(btnDynamicAdd);

    dynamicBibtexTypeSuggestionMenu.setAutoHide(true);

    dynamicAddHost.getChildren().setAll(dynamicAddRow);

    formPane.getChildren().setAll(formFieldsBox, dynamicAddHost);

    formHost.setContent(formPane);
    content.getChildren().setAll(formHost);
    VBox.setVgrow(content, Priority.ALWAYS);

    btnDynamicAdd.visibleProperty().bind(toggleEdit.selectedProperty());
    btnDynamicAdd.managedProperty().bind(toggleEdit.selectedProperty());

    setEditMode(true);
    updateDynamicAddAreaVisibility();
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
   * Initialisiert die Medientyp-Auswahl als echtes Eingabefeld mit Vorschlagsdropdown.
   * Während des Tippens wird nur gefiltert; ein Formularwechsel erfolgt erst nach
   * expliziter Auswahl oder bei eindeutigem Commit.
   */
  private void initializeTypeSelection() {
    typeField.textProperty().addListener((obs, oldValue, newValue) -> {
      if (suppressTypeFieldCallbacks) {
        return;
      }

      showTypeSuggestions(newValue);
    });

    typeField.focusedProperty().addListener((obs, oldValue, focused) -> {
      if (focused) {
        showTypeSuggestions(typeField.getText());
      } else {
        commitTypedMediaTypeSelection();
        typeSuggestionMenu.hide();
      }
    });

    typeField.setOnMouseClicked(event -> {
      if (!typeField.isFocused()) {
        typeField.requestFocus();
      }
      showTypeSuggestions(typeField.getText());
    });

    typeField.setOnKeyPressed(event -> {
      switch (event.getCode()) {
        case DOWN, ENTER -> {
          showTypeSuggestions(typeField.getText());
          if (event.getCode() == KeyCode.ENTER) {
            commitTypedMediaTypeSelection();
          }
        }
        case ESCAPE -> typeSuggestionMenu.hide();
        default -> {
        }
      }
    });
  }

  /**
   * Zeigt Vorschläge für den Medientyp anhand des aktuellen Eingabetexts.
   *
   * @param query aktueller Suchtext
   */
  private void showTypeSuggestions(String query) {
    List<MediaTypeOption> matches = findMatchingMediaTypeOptions(query);

    if (matches.isEmpty()) {
      typeSuggestionMenu.hide();
      return;
    }

    List<CustomMenuItem> items = new ArrayList<>();
    for (MediaTypeOption option : matches) {
      if (option == null) {
        continue;
      }

      Label label = new Label(option.displayName());
      CustomMenuItem item = new CustomMenuItem(label, true);
      item.setOnAction(event -> applyMediaTypeSelection(option));
      items.add(item);
    }

    if (items.isEmpty()) {
      typeSuggestionMenu.hide();
      return;
    }

    typeSuggestionMenu.getItems().setAll(items);
    showContextMenuSafely(typeSuggestionMenu, typeField);
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
      javafx.application.Platform.runLater(() -> {
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
   * Liefert die zum aktuellen Suchtext passenden Medientyp-Optionen.
   * Berücksichtigt werden Anzeigename und technischer Medientypname.
   *
   * @param query aktueller Suchtext
   * @return passende Optionen
   */
  private List<MediaTypeOption> findMatchingMediaTypeOptions(String query) {
    String normalizedQuery = normalizeSearchText(query);

    if (normalizedQuery.isBlank()) {
      return allMediaTypeOptions;
    }

    return allMediaTypeOptions.stream()
               .filter(option -> option != null)
               .filter(option -> matchesMediaTypeOption(option, normalizedQuery))
               .toList();
  }

  /**
   * Übernimmt eine Medientyp-Auswahl aus dem Vorschlagsdropdown.
   *
   * @param option ausgewählte Medientyp-Option
   */
  private void applyMediaTypeSelection(MediaTypeOption option) {
    activeMediaTypeBibName = option == null ? "" : normalizeMediaTypeBibName(option.mediaTypeBibName());

    suppressTypeFieldCallbacks = true;
    try {
      typeField.setText(option == null ? "" : option.toString());
    } finally {
      suppressTypeFieldCallbacks = false;
    }

    typeSuggestionMenu.hide();
    showNewEditorAndKeepTypeVisible(option == null ? "" : option.mediaTypeBibName());
  }

  /**
   * Führt die Eingabe im Medientyp-Feld zu Ende.
   * Bei exakt passender Eingabe wird der Medientyp übernommen,
   * andernfalls wird der aktuelle Formularzustand nicht zerstört.
   */
  private void commitTypedMediaTypeSelection() {
    MediaTypeOption resolved = resolveMediaTypeOption(typeField.getText());
    if (resolved != null) {
      applyMediaTypeSelection(resolved);
    }
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

    toggleEdit.selectedProperty().addListener((obs, old, isOn) -> {
      setEditMode(isOn);

      if (noteLinkingContext && old && !isOn) {
        onCloseEditor.run();
      }
    });
    typeField.visibleProperty().bind(toggleEdit.selectedProperty());
    typeField.managedProperty().bind(toggleEdit.selectedProperty());
    btnDelete.visibleProperty().bind(toggleEdit.selectedProperty());
    btnDelete.managedProperty().bind(toggleEdit.selectedProperty());

    header.getChildren().addAll(toggleEdit, typeField, spacer, btnDelete);
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

    MediaTypeOption previousSelection = resolveMediaTypeOption(typeField.getText());

    allMediaTypeOptions = List.copyOf(safeOptions);

    if (safeOptions.isEmpty()) {
      typeField.clear();
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
      activeMediaTypeBibName = "";
      suppressTypeFieldCallbacks = true;
      try {
        typeField.clear();
      } finally {
        suppressTypeFieldCallbacks = false;
      }
      formFieldsBox.getChildren().clear();
      return;
    }

    activeMediaTypeBibName = normalizeMediaTypeBibName(nextSelection.mediaTypeBibName());
    suppressTypeFieldCallbacks = true;
    try {
      typeField.setText(nextSelection.toString());
    } finally {
      suppressTypeFieldCallbacks = false;
    }
    switchForm(nextSelection);
  }

  /**
   * Löst einen Medientyp aus einem Eingabetext auf.
   * Es werden sowohl Anzeigename als auch technischer Medientypname unterstützt.
   *
   * @param text Eingabetext
   * @return passende Option oder {@code null}
   */
  private MediaTypeOption resolveMediaTypeOption(String text) {
    String normalizedText = normalizeSearchText(text);
    if (normalizedText.isBlank()) {
      return null;
    }

    for (MediaTypeOption option : allMediaTypeOptions) {
      if (option == null) {
        continue;
      }

      if (normalizeSearchText(option.displayName()).equals(normalizedText)
              || normalizeSearchText(option.mediaTypeBibName()).equals(normalizedText)) {
        return option;
      }
    }

    return null;
  }

  /**
   * Prüft, ob eine Medientyp-Option zum aktuellen Suchtext passt.
   *
   * @param option Medientyp-Option
   * @param normalizedQuery normalisierter Suchtext
   * @return {@code true}, wenn die Option passt
   */
  private boolean matchesMediaTypeOption(MediaTypeOption option, String normalizedQuery) {
    if (option == null || normalizedQuery.isBlank()) {
      return false;
    }

    return normalizeSearchText(option.displayName()).contains(normalizedQuery)
               || normalizeSearchText(option.mediaTypeBibName()).contains(normalizedQuery);
  }

  /**
   * Normalisiert einen Suchtext für Vergleich und Filterung.
   *
   * @param text Eingabetext
   * @return normalisierter Suchtext
   */
  private String normalizeSearchText(String text) {
    return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
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
    updateDynamicAddAreaVisibility();
  }

  /**
   * Synchronisiert die Sichtbarkeit des Zusatzfeld-Bereichs mit dem aktuellen
   * Nutzungskontext der Shell.
   * Im Zettelkontext wird der Bereich vollständig ausgeblendet, damit dort
   * keine neuen Felder ergänzt werden können.
   */
  private void updateDynamicAddAreaVisibility() {
    boolean visible = toggleEdit.isSelected()
                          && editorUsageContext != EditorUsageContext.NOTE_LINKING;

    dynamicAddHost.setVisible(visible);
    dynamicAddHost.setManaged(visible);

    if (!visible) {
      removeDynamicBibtexTypeSelector();
    }
  }

  /**
   * Schaltet die Shell in einen Popup-Modus für die Medienverwaltung.
   * Dabei werden die internen Header-Elemente für Typauswahl und
   * Bearbeitungstoggle ausgeblendet, während der Löschen-Button als
   * BOOK_DELETE-Aktion sichtbar bleibt.
   */
  public void configureForMediaManagementPopup() {
    mediaManagementPopupContext = true;
    setEditMode(true);

    toggleEdit.setVisible(false);
    toggleEdit.setManaged(false);

    typeField.visibleProperty().unbind();
    typeField.managedProperty().unbind();
    typeField.setVisible(false);
    typeField.setManaged(false);
    typeSuggestionMenu.hide();

    btnDelete.visibleProperty().unbind();
    btnDelete.managedProperty().unbind();
    btnDelete.setVisible(true);
    btnDelete.setManaged(true);
    btnDelete.setGraphic(BaseIcon.BOOK_DELETE.imageView());
    btnDelete.setTooltip(new Tooltip("Medium löschen"));
    updateDynamicAddAreaVisibility();
  }

  /**
   * Schaltet die Shell in den Zettelkontext um.
   * In diesem Modus bleibt der Bearbeitungstoggle sichtbar.
   * Der grüne Toggle schließt zurück in die kompakte
   * Vorschau unter dem Zettel.
   */
  public void configureForNoteLinkingContext() {
    noteLinkingContext = true;
    editorUsageContext = EditorUsageContext.NOTE_LINKING;
    setEditMode(true);

    toggleEdit.setVisible(true);
    toggleEdit.setManaged(true);

    typeField.visibleProperty().unbind();
    typeField.managedProperty().unbind();
    typeField.setVisible(true);
    typeField.setManaged(true);

    btnDelete.visibleProperty().unbind();
    btnDelete.managedProperty().unbind();
    btnDelete.setVisible(true);
    btnDelete.setManaged(true);
    btnDelete.setGraphic(BaseIcon.DELETE.imageView());
    btnDelete.setTooltip(new Tooltip("Verknüpfung lösen"));

    applyCurrentContextVisibility();
    updateSaveButtonState();
    updateDynamicAddAreaVisibility();
  }

  /**
   * Wendet die Sichtbarkeitsregeln des aktuellen Shell-Kontexts auf das gerade
   * aktive Formular an.
   */
  private void applyCurrentContextVisibility() {
    String mediaTypeBibName = getSelectedMediaTypeBibName();
    if (!mediaTypeBibName.isBlank()) {
      applyContextVisibility(mediaTypeBibName);
      applyFieldRowPresentationForMediaType(mediaTypeBibName);
    }
  }

  /**
   * Aktualisiert die Darstellung aller aktuell registrierten Feldzeilen des
   * sichtbaren Formulars entsprechend des aktuellen Shell-Kontexts.
   *
   * @param mediaTypeBibName technischer Medientypname
   */
  private void applyFieldRowPresentationForMediaType(String mediaTypeBibName) {
    String normalizedMediaTypeBibName = normalizeMediaTypeBibName(mediaTypeBibName);
    Map<String, Node> nodeRegistry = dynamicFieldNodesByMediaType.get(normalizedMediaTypeBibName);
    if (nodeRegistry == null || nodeRegistry.isEmpty()) {
      return;
    }

    for (Map.Entry<String, Node> entry : nodeRegistry.entrySet()) {
      String bibtexName = normalizeBibtexName(entry.getKey());
      Node node = entry.getValue();

      if (!(node instanceof HBox row)) {
        continue;
      }

      if (row.getChildren().size() < 2) {
        continue;
      }

      Node first = row.getChildren().get(0);
      Node second = row.getChildren().get(1);

      if (!(first instanceof HBox labelArea)) {
        continue;
      }

      boolean personField = false;
      Map<String, Object> registry = dynamicFieldRegistryByMediaType.get(normalizedMediaTypeBibName);
      if (registry != null) {
        Object component = registry.get(bibtexName);
        personField = component instanceof BibliographyAuthorsView;
      }

      applyFieldRowPresentation(row, second, labelArea, personField);
    }
  }

  /**
   * Wendet die Sichtbarkeitsregeln des aktuellen Shell-Kontexts auf ein
   * konkretes dynamisches Formular an.
   *
   * @param mediaTypeBibName technischer Medientypname
   */
  private void applyContextVisibility(String mediaTypeBibName) {
    String normalizedMediaTypeBibName = normalizeMediaTypeBibName(mediaTypeBibName);
    Map<String, Node> nodeRegistry = dynamicFieldNodesByMediaType.get(normalizedMediaTypeBibName);
    List<MediaAttributeDefinition> attributes = getEditorAttributesForMediaType(normalizedMediaTypeBibName);

    if (nodeRegistry == null || attributes.isEmpty()) {
      return;
    }

    boolean allowNecessaryFields = isCreatableInNoteContext(normalizedMediaTypeBibName);

    for (MediaAttributeDefinition attribute : attributes) {
      if (attribute == null || attribute.fieldDefinition() == null) {
        continue;
      }

      String bibtexName = normalizeBibtexName(attribute.fieldDefinition().bibtexName());
      Node node = nodeRegistry.get(bibtexName);
      if (node == null) {
        continue;
      }

      boolean forceWebsiteTitle = "website".equals(normalizedMediaTypeBibName)
        && "title".equals(normalizeBibtexName(attribute.fieldDefinition().bibtexName()));

      boolean forceAiField = "aitext".equals(normalizedMediaTypeBibName)
                                 && (
          "title".equals(normalizeBibtexName(attribute.fieldDefinition().bibtexName()))
              || "ai_provider".equals(normalizeBibtexName(attribute.fieldDefinition().bibtexName()))
              || "ai_model".equals(normalizeBibtexName(attribute.fieldDefinition().bibtexName()))
              || "ai_used_at".equals(normalizeBibtexName(attribute.fieldDefinition().bibtexName()))
      );

      boolean personField = isPersonDatatype(attribute.fieldDefinition().datatype());

      boolean visible = switch (editorUsageContext) {
        case STANDARD -> true;
        case NOTE_LINKING -> forceWebsiteTitle
                                 || forceAiField
                                 || personField
                                 || attribute.isIdentify()
                                 || (allowNecessaryFields && attribute.isNecessary());
      };

      node.setVisible(visible);
      node.setManaged(visible);
    }
  }

  /**
   * Prüft, ob ein Medientyp im Zettelkontext als echter Neuerfassungsfall
   * behandelt wird.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @return {@code true}, wenn identify- und necessary-Felder sichtbar bleiben
   */
  private boolean isCreatableInNoteContext(String mediaTypeBibName) {
    String normalized = normalizeMediaTypeBibName(mediaTypeBibName);
    return "website".equals(normalized) || "aitext".equals(normalized);
  }

  /**
   * Ermittelt das aktuelle KI-Datumsfeld.
   * Nach Abschluss der Migration wird ausschließlich {@code ai_used_at}
   * als fachliches Datumsfeld für KI-Einträge verwendet.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @return {@code ai_used_at} oder leerer String
   */
  private String resolveAiDateFieldName(String mediaTypeBibName) {
    String normalizedMediaTypeBibName = normalizeMediaTypeBibName(mediaTypeBibName);
    if (!"aitext".equals(normalizedMediaTypeBibName)) {
      return "";
    }

    Map<String, Object> registry = dynamicFieldRegistryByMediaType.get(normalizedMediaTypeBibName);
    if (registry == null || registry.isEmpty()) {
      return "";
    }

    return registry.containsKey("ai_used_at") ? "ai_used_at" : "";
  }

  /**
   * Liefert die im aktuellen Shell-Kontext sichtbare Zeilenzahl für Personenfelder.
   *
   * @return sichtbare Zeilenzahl der Autorenansicht
   */
  private int getVisibleAuthorRowCount() {
    return editorUsageContext == EditorUsageContext.NOTE_LINKING ? 2 : 4;
  }

  /**
   * Leert die aktuell sichtbare Editoransicht vollständig.
   */
  public void clearEditorView() {
    activeMediaTypeBibName = "";
    typeField.clear();
    typeSuggestionMenu.hide();
    dynamicBibtexTypeSuggestionMenu.hide();
    formFieldsBox.getChildren().clear();
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

    List<MediaAttributeDefinition> attributes =
        bibliographyService.listEditorAttributesForMediaType(mediaType.bibName());
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

    Map<String, BibtexFieldDefinition> additionalFields =
        additionalFieldDefinitionsByMediaType.get(normalizeMediaTypeBibName(mediaType.bibName()));

    if (additionalFields != null && !additionalFields.isEmpty()) {
      for (BibtexFieldDefinition fieldDefinition : additionalFields.values()) {
        if (fieldDefinition == null) {
          continue;
        }

        String bibtexName = normalizeBibtexName(fieldDefinition.bibtexName());
        String datatype = normalizeBibtexName(fieldDefinition.datatype());

        if (bibtexName.isBlank()) {
          continue;
        }

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

        String rawValue = getCurrentFieldValue(mediaType.bibName(), bibtexName);

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

    showExistingEditor(mediaTypeBibName);
    setEditMode(editMode);

    setSelectedEntryId(mediaTypeBibName, entry.getId());
    setSourceEntryId(mediaTypeBibName, entry.getId());
    setAutoFillLocked(mediaTypeBibName, false);
    setAutoFilled(mediaTypeBibName, false);

    ensureAdditionalFieldsForEntry(mediaTypeBibName, entry);

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
    applyAiDateValue(mediaTypeBibName, entry);
    captureBaseline();
    applyCurrentContextVisibility();
  }

  /**
   * Überträgt das KI-Datum in das vorhandene DatePicker-Feld.
   * Verwendet ausschließlich das kanonische Feld {@code ai_used_at}.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @param entry geladener Eintrag
   */
  private void applyAiDateValue(String mediaTypeBibName, DynamicBibliographyEntry entry) {
    if (entry == null) {
      return;
    }

    String normalizedMediaTypeBibName = normalizeMediaTypeBibName(mediaTypeBibName);
    if (!"aitext".equals(normalizedMediaTypeBibName)) {
      return;
    }

    String targetDateField = resolveAiDateFieldName(normalizedMediaTypeBibName);
    if (targetDateField.isBlank()) {
      return;
    }

    String dateValue = extractAiRawDateValue(entry);
    if (dateValue.isBlank()) {
      return;
    }

    setCurrentFieldValue(targetDateField, dateValue);
  }

  /**
   * Liest das KI-Nutzungsdatum aus dem kanonischen Feld {@code ai_used_at}.
   *
   * @param entry bibliographischer Eintrag
   * @return roher Datumswert oder leerer String
   */
  private String extractAiRawDateValue(DynamicBibliographyEntry entry) {
    return extractStringValue(entry, "ai_used_at");
  }

  /**
   * Liest einen String-Wert defensiv aus einem dynamischen Eintrag.
   *
   * @param entry bibliographischer Eintrag
   * @param bibtexName technischer Feldname
   * @return String-Wert oder leerer String
   */
  private String extractStringValue(DynamicBibliographyEntry entry, String bibtexName) {
    if (entry == null || bibtexName == null || bibtexName.isBlank()) {
      return "";
    }

    return entry.findValue(bibtexName)
               .filter(StringBibValue.class::isInstance)
               .map(StringBibValue.class::cast)
               .map(StringBibValue::value)
               .map(String::trim)
               .orElse("");
  }

  /**
   * Ermittelt die erste Zeile der passiven KI-Vorschau.
   * Diese Zeile ist ausschließlich für den Titel reserviert.
   *
   * @param entry bibliographischer Eintrag
   * @return Titel oder leerer String
   */
  public String buildAiPreviewTitle(DynamicBibliographyEntry entry) {
    if (entry == null) {
      return "";
    }

    return extractStringValue(entry, "title");
  }

  /**
   * Ermittelt die zweite Zeile der passiven KI-Vorschau.
   * Angezeigt werden Anbieter, Modell und Datum in kompakter Form.
   *
   * @param entry bibliographischer Eintrag
   * @return zweite Vorschauzeile oder leerer String
   */
  public String buildAiPreviewMeta(DynamicBibliographyEntry entry) {
    if (entry == null) {
      return "";
    }

    String provider = extractFirstStringValue(entry, "ai_provider", "provider");
    String model = extractFirstStringValue(entry, "ai_model", "model");
    String usedAt = formatPreviewDate(extractAiRawDateValue(entry));

    List<String> parts = new ArrayList<>();
    if (!provider.isBlank()) {
      parts.add(provider);
    }
    if (!model.isBlank()) {
      parts.add(model);
    }
    if (!usedAt.isBlank()) {
      parts.add(usedAt);
    }

    return String.join(" / ", parts);
  }

  /**
   * Liest den ersten nichtleeren String-Wert aus mehreren möglichen Feldnamen.
   *
   * @param entry bibliographischer Eintrag
   * @param bibtexNames mögliche technische Feldnamen
   * @return erster nichtleer gefundener Wert oder leerer String
   */
  private String extractFirstStringValue(DynamicBibliographyEntry entry, String... bibtexNames) {
    if (entry == null || bibtexNames == null) {
      return "";
    }

    for (String bibtexName : bibtexNames) {
      String value = extractStringValue(entry, bibtexName);
      if (!value.isBlank()) {
        return value;
      }
    }

    return "";
  }

  /**
   * Formatiert einen Datumswert für die passive Bibliographie-Vorschau.
   * Unterstützt ISO-Date sowie ISO-DateTime und reduziert diese auf
   * "Tag Mon Jahr".
   *
   * @param rawValue roher Datumswert
   * @return formatiertes Datum oder leerer String
   */
  private String formatPreviewDate(String rawValue) {
    String text = rawValue == null ? "" : rawValue.trim();
    if (text.isBlank()) {
      return "";
    }

    try {
      return LocalDate.parse(text).format(GERMAN_PREVIEW_DATE_FORMAT);
    } catch (DateTimeParseException ignored) {
    }

    try {
      return java.time.LocalDateTime.parse(text).toLocalDate().format(GERMAN_PREVIEW_DATE_FORMAT);
    } catch (DateTimeParseException ignored) {
    }

    int tIndex = text.indexOf('T');
    if (tIndex > 0) {
      String datePart = text.substring(0, tIndex).trim();
      try {
        return LocalDate.parse(datePart).format(GERMAN_PREVIEW_DATE_FORMAT);
      } catch (DateTimeParseException ignored) {
      }
    }

    return text;
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

    return bibliographyService.loadMediaTypeByBibName(mediaTypeBibName).orElse(null);
  }

  /**
   * Liefert die im Editor sichtbaren Standardattribute eines Medientyps.
   * Die fachliche Auswahl und Sortierung liegt im Service; die Shell greift
   * nur noch lesend darauf zu.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @return sichtbare Editor-Attribute
   */
  private List<MediaAttributeDefinition> getEditorAttributesForMediaType(String mediaTypeBibName) {
    String normalizedMediaTypeBibName = normalizeMediaTypeBibName(mediaTypeBibName);
    if (bibliographyService == null || normalizedMediaTypeBibName.isBlank()) {
      return List.of();
    }

    return bibliographyService.listEditorAttributesForMediaType(normalizedMediaTypeBibName);
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
   * Begrenzt ein Textfeld auf Integer-Eingaben.
   * Erlaubt werden ausschließlich Ziffern sowie ein leerer Feldinhalt.
   * Damit bleiben auch Jahr- und Monatsfelder konsistent rein numerisch.
   *
   * @param fieldView Feldansicht mit zugrunde liegendem TextField
   */
  private void enforceIntegerInput(BibliographyFieldView fieldView) {
    if (fieldView == null || fieldView.getEditor() == null) {
      return;
    }

    fieldView.getEditor().setTextFormatter(new TextFormatter<String>(change -> {
      String nextText = change.getControlNewText();
      if (nextText.isEmpty() || nextText.matches("\\d+")) {
        return change;
      }
      return null;
    }));
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
        getEditorAttributesForMediaType(mediaType.bibName());

    VBox form = new VBox(8);
    form.getStyleClass().add("biblio-dynamic-form");

    Map<String, Object> fieldRegistry = new LinkedHashMap<>();
    Map<String, Node> fieldNodeRegistry = new LinkedHashMap<>();

    for (MediaAttributeDefinition attribute : attributes) {
      createDynamicField(form, fieldRegistry, fieldNodeRegistry, mediaTypeBibName, attribute);
    }

    dynamicFormsByMediaType.put(mediaTypeBibName, form);
    dynamicFieldRegistryByMediaType.put(mediaTypeBibName, fieldRegistry);
    dynamicFieldNodesByMediaType.put(mediaTypeBibName, fieldNodeRegistry);
    applyContextVisibility(mediaTypeBibName);
  }

  /**
   * Liefert die zusätzlichen, nicht fest zum Medientyp gehörenden Felddefinitionen
   * eines Formulars.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @return Map technischer Feldnamen auf Felddefinitionen
   */
  private Map<String, BibtexFieldDefinition> getAdditionalFieldDefinitions(String mediaTypeBibName) {
    String normalizedMediaTypeBibName = normalizeMediaTypeBibName(mediaTypeBibName);
    return additionalFieldDefinitionsByMediaType.computeIfAbsent(
        normalizedMediaTypeBibName,
        key -> new LinkedHashMap<>()
    );
  }

  /**
   * Liefert alle technischen Feldnamen, die in einem Formular bereits vorhanden sind.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @return vorhandene technische Feldnamen
   */
  private Set<String> collectExistingBibtexNames(String mediaTypeBibName) {
    String normalizedMediaTypeBibName = normalizeMediaTypeBibName(mediaTypeBibName);
    Set<String> result = new LinkedHashSet<>();

    Map<String, Object> registry = dynamicFieldRegistryByMediaType.get(normalizedMediaTypeBibName);
    if (registry != null) {
      result.addAll(registry.keySet());
    }

    return result;
  }

  /**
   * Löst eine Medientypdefinition über ihren technischen Namen auf.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @return Medientypdefinition oder {@code null}
   */
  private MediaTypeDefinition resolveMediaTypeDefinition(String mediaTypeBibName) {
    if (bibliographyService == null) {
      return null;
    }

    String normalizedMediaTypeBibName = normalizeMediaTypeBibName(mediaTypeBibName);
    if (normalizedMediaTypeBibName.isBlank()) {
      return null;
    }

    return bibliographyService.loadMediaTypeByBibName(normalizedMediaTypeBibName).orElse(null);
  }

  /**
   * Filtert alle zusätzlich auswählbaren BibTeX-Felder eines Medientyps.
   * Die eigentliche Metadatenlogik liegt im Service; die Shell liefert nur
   * den aktuellen Formularzustand und den Suchtext.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @param query aktueller Suchtext
   * @return auswählbare Felddefinitionen
   */
  private List<BibtexFieldDefinition> findAvailableAdditionalBibtexTypes(String mediaTypeBibName, String query) {
    if (bibliographyService == null) {
      return List.of();
    }

    return bibliographyService.findAvailableAdditionalBibtexTypes(
        mediaTypeBibName,
        collectExistingBibtexNames(mediaTypeBibName),
        query
    );
  }

  /**
   * Öffnet das temporäre Auswahlfeld für ein zusätzliches BibTeX-Feld.
   */
  private void showDynamicBibtexTypeSelector() {
    String mediaTypeBibName = getSelectedMediaTypeBibName();
    if (mediaTypeBibName.isBlank() || bibliographyService == null) {
      return;
    }

    if (dynamicAddHost.getChildren().size() > 1) {
      Node existingSelector = dynamicAddHost.getChildren().getFirst();
      if (existingSelector instanceof HBox selectorRow) {
        Node candidate = selectorRow.getChildren().isEmpty() ? null : selectorRow.getChildren().getFirst();
        if (candidate instanceof TextField textField) {
          textField.requestFocus();
          textField.selectAll();
          showDynamicBibtexTypeSuggestions(textField);
        }
      }
      return;
    }

    TextField selectorField = new TextField();
    selectorField.getStyleClass().add("biblio-dynamic-type-field");
    selectorField.setPromptText("BibTeX-Feld hinzufügen");

    Button closeButton = BaseIcon.CROSS.button("Auswahl schließen");
    closeButton.setOnAction(event -> removeDynamicBibtexTypeSelector());

    HBox selectorRow = new HBox(6, selectorField, closeButton);
    selectorRow.getStyleClass().add("biblio-dynamic-type-selector-row");
    HBox.setHgrow(selectorField, Priority.ALWAYS);

    selectorField.textProperty().addListener((obs, oldValue, newValue) -> {
      showDynamicBibtexTypeSuggestions(selectorField);
    });

    selectorField.focusedProperty().addListener((obs, oldValue, focused) -> {
      if (!focused) {
        commitDynamicBibtexTypeSelection(selectorField);
      }
    });

    selectorField.setOnKeyPressed(event -> {
      switch (event.getCode()) {
        case ENTER, TAB -> commitDynamicBibtexTypeSelection(selectorField);
        case DOWN -> showDynamicBibtexTypeSuggestions(selectorField);
        case ESCAPE -> {
          dynamicBibtexTypeSuggestionMenu.hide();
          removeDynamicBibtexTypeSelector();
        }
        default -> {
        }
      }
    });

    dynamicAddHost.getChildren().addFirst(selectorRow);
    selectorField.requestFocus();
    showDynamicBibtexTypeSuggestions(selectorField);
  }

  /**
   * Entfernt das temporäre Auswahlfeld für zusätzliche BibTeX-Felder.
   */
  private void removeDynamicBibtexTypeSelector() {
    dynamicBibtexTypeSuggestionMenu.hide();

    if (dynamicAddHost.getChildren().size() > 1) {
      Node first = dynamicAddHost.getChildren().getFirst();
      if (first instanceof HBox) {
        dynamicAddHost.getChildren().removeFirst();
      }
    }
  }

  /**
   * Zeigt Vorschläge für das temporäre BibTeX-Auswahlfeld.
   * Vorschläge werden erst ab dem zweiten eingegebenen Zeichen angeboten.
   * Die Anzeige bleibt dabei in einem fest begrenzten Scrollbereich innerhalb
   * des Add-Hosts, so dass der Add-Button nicht nach unten verdrängt wird.
   *
   * @param selectorField Auswahlfeld
   */
  private void showDynamicBibtexTypeSuggestions(TextField selectorField) {
    if (selectorField == null) {
      dynamicBibtexTypeSuggestionMenu.hide();
      return;
    }

    String query = selectorField.getText() == null ? "" : selectorField.getText().trim();
    if (query.length() < 2) {
      dynamicBibtexTypeSuggestionMenu.hide();
      return;
    }

    List<BibtexFieldDefinition> matches =
        findAvailableAdditionalBibtexTypes(getSelectedMediaTypeBibName(), query);

    if (matches.isEmpty()) {
      dynamicBibtexTypeSuggestionMenu.hide();
      return;
    }

    List<CustomMenuItem> items = new ArrayList<>();
    for (BibtexFieldDefinition field : matches) {
      if (field == null) {
        continue;
      }

      Label label = new Label(field.displayName());
      CustomMenuItem item = new CustomMenuItem(label, true);
      item.setOnAction(event -> {
        addAdditionalFieldAndDependencies(getSelectedMediaTypeBibName(), field);
        removeDynamicBibtexTypeSelector();
      });
      items.add(item);
    }

    if (items.isEmpty()) {
      dynamicBibtexTypeSuggestionMenu.hide();
      return;
    }

    dynamicBibtexTypeSuggestionMenu.getItems().setAll(items);

    if (dynamicBibtexTypeSuggestionMenu.isShowing()) {
      dynamicBibtexTypeSuggestionMenu.hide();
    }

    showContextMenuSafely(dynamicBibtexTypeSuggestionMenu, selectorField);
  }

  /**
   * Führt die manuelle Auswahl eines zusätzlichen BibTeX-Feldes zu Ende.
   *
   * @param selectorField Auswahlfeld
   */
  private void commitDynamicBibtexTypeSelection(TextField selectorField) {
    if (selectorField == null || bibliographyService == null) {
      removeDynamicBibtexTypeSelector();
      return;
    }

    String rawText = selectorField.getText() == null ? "" : selectorField.getText().trim();
    if (rawText.isBlank()) {
      removeDynamicBibtexTypeSelector();
      return;
    }

    Optional<BibtexFieldDefinition> resolved =
        bibliographyService.resolveAvailableAdditionalBibtexType(
            getSelectedMediaTypeBibName(),
            collectExistingBibtexNames(getSelectedMediaTypeBibName()),
            rawText
        );

    resolved.ifPresent(fieldDefinition ->
                           addAdditionalFieldAndDependencies(getSelectedMediaTypeBibName(), fieldDefinition)
    );

    dynamicBibtexTypeSuggestionMenu.hide();
    removeDynamicBibtexTypeSelector();
  }

  /**
   * Fügt ein zusätzliches Feld hinzu und setzt abhängige Felder automatisch mit.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @param fieldDefinition Felddefinition
   */
  private void addAdditionalFieldAndDependencies(String mediaTypeBibName,
                                                 BibtexFieldDefinition fieldDefinition) {
    if (fieldDefinition == null) {
      return;
    }

    addAdditionalField(mediaTypeBibName, fieldDefinition);

    if (bibliographyService != null) {
      bibliographyService.loadRequiredBibtexType(fieldDefinition)
          .ifPresent(requiredField -> addAdditionalField(mediaTypeBibName, requiredField));
    }
  }

  /**
   * Fügt ein zusätzliches Feld in das aktive Formular ein, sofern es noch nicht existiert.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @param fieldDefinition Felddefinition
   */
  private void addAdditionalField(String mediaTypeBibName, BibtexFieldDefinition fieldDefinition) {
    String normalizedMediaTypeBibName = normalizeMediaTypeBibName(mediaTypeBibName);
    String bibtexName = normalizeBibtexName(fieldDefinition.bibtexName());

    if (normalizedMediaTypeBibName.isBlank() || bibtexName.isBlank()) {
      return;
    }

    Map<String, Object> registry = dynamicFieldRegistryByMediaType.get(normalizedMediaTypeBibName);
    Map<String, Node> nodeRegistry = dynamicFieldNodesByMediaType.get(normalizedMediaTypeBibName);
    VBox form = dynamicFormsByMediaType.get(normalizedMediaTypeBibName);

    if (registry == null || nodeRegistry == null || form == null) {
      return;
    }

    if (registry.containsKey(bibtexName)) {
      return;
    }

    getAdditionalFieldDefinitions(normalizedMediaTypeBibName).put(bibtexName, fieldDefinition);

    Node node = createAdditionalFieldNode(normalizedMediaTypeBibName, fieldDefinition);
    if (node == null) {
      return;
    }

    form.getChildren().add(node);
    nodeRegistry.put(bibtexName, node);
    refreshDirtyState();
  }

  /**
   * Entfernt ein zusätzliches Feld aus dem aktiven Formular.
   * Abhängige Zusatzfelder werden rekursiv mit entfernt, und umgekehrt wird
   * auch ein vom gelöschten Feld vorausgesetztes Zusatzfeld mit entfernt.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @param bibtexName technischer Feldname
   */
  private void removeAdditionalField(String mediaTypeBibName, String bibtexName) {
    String normalizedMediaTypeBibName = normalizeMediaTypeBibName(mediaTypeBibName);
    String normalizedBibtexName = normalizeBibtexName(bibtexName);

    Map<String, Object> registry = dynamicFieldRegistryByMediaType.get(normalizedMediaTypeBibName);
    Map<String, Node> nodeRegistry = dynamicFieldNodesByMediaType.get(normalizedMediaTypeBibName);
    Map<String, BibtexFieldDefinition> additionalDefinitions =
        additionalFieldDefinitionsByMediaType.get(normalizedMediaTypeBibName);
    VBox form = dynamicFormsByMediaType.get(normalizedMediaTypeBibName);

    if (registry == null || nodeRegistry == null || form == null || additionalDefinitions == null) {
      return;
    }

    Set<String> fieldsToRemove = collectAdditionalRemovalClosure(
        normalizedMediaTypeBibName,
        normalizedBibtexName
    );

    for (String fieldToRemove : fieldsToRemove) {
      Node node = nodeRegistry.remove(fieldToRemove);
      if (node != null) {
        form.getChildren().remove(node);
      }

      registry.remove(fieldToRemove);
      additionalDefinitions.remove(fieldToRemove);
      relatedEntryIdsByFieldKey.remove(
          relatedFieldKey(normalizedMediaTypeBibName, fieldToRemove)
      );
    }

    refreshDirtyState();
  }

  /**
   * Ermittelt die vollständige Löschmenge eines Zusatzfeldes einschließlich
   * aller abhängigen Zusatzfelder.
   * Berücksichtigt werden beide Richtungen:
   * <ul>
   *   <li>Felder, die vom Startfeld abhängen</li>
   *   <li>Felder, von denen das Startfeld selbst abhängig ist</li>
   * </ul>
   *
   * @param mediaTypeBibName technischer Medientypname
   * @param startBibtexName technischer Startfeldname
   * @return vollständig zu entfernende Zusatzfelder
   */
  private Set<String> collectAdditionalRemovalClosure(String mediaTypeBibName, String startBibtexName) {
    String normalizedMediaTypeBibName = normalizeMediaTypeBibName(mediaTypeBibName);
    String normalizedStartBibtexName = normalizeBibtexName(startBibtexName);

    Map<String, BibtexFieldDefinition> additionalDefinitions =
        additionalFieldDefinitionsByMediaType.get(normalizedMediaTypeBibName);

    if (additionalDefinitions == null || additionalDefinitions.isEmpty() || normalizedStartBibtexName.isBlank()) {
      return Set.of();
    }

    LinkedHashSet<String> result = new LinkedHashSet<>();
    ArrayDeque<String> queue = new ArrayDeque<>();
    queue.add(normalizedStartBibtexName);

    while (!queue.isEmpty()) {
      String current = normalizeBibtexName(queue.removeFirst());
      if (current.isBlank() || !result.add(current)) {
        continue;
      }

      BibtexFieldDefinition currentDefinition = additionalDefinitions.get(current);
      if (currentDefinition != null) {
        String required = normalizeBibtexName(currentDefinition.requires());
        if (!required.isBlank() && additionalDefinitions.containsKey(required) && !result.contains(required)) {
          queue.add(required);
        }
      }

      for (Map.Entry<String, BibtexFieldDefinition> entry : additionalDefinitions.entrySet()) {
        String candidateBibtexName = normalizeBibtexName(entry.getKey());
        BibtexFieldDefinition candidateDefinition = entry.getValue();
        if (candidateDefinition == null) {
          continue;
        }

        String candidateRequires = normalizeBibtexName(candidateDefinition.requires());
        if (candidateRequires.equals(current) && !result.contains(candidateBibtexName)) {
          queue.add(candidateBibtexName);
        }
      }
    }

    return result;
  }

  /**
   * Erzeugt die UI eines zusätzlich eingefügten Feldes mit Delete-Button.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @param fieldDefinition Felddefinition
   * @return erzeugter UI-Knoten
   */
  private Node createAdditionalFieldNode(String mediaTypeBibName, BibtexFieldDefinition fieldDefinition) {
    String normalizedMediaTypeBibName = normalizeMediaTypeBibName(mediaTypeBibName);
    String bibtexName = normalizeBibtexName(fieldDefinition.bibtexName());
    String datatype = normalizeBibtexName(fieldDefinition.datatype());
    String labelText = fieldDefinition.displayName();

    Button deleteButton = BaseIcon.TAG_BLUE_DELETE.button("Eigenschaft entfernen");
    deleteButton.setOnAction(event -> removeAdditionalField(normalizedMediaTypeBibName, bibtexName));

    Map<String, Object> registry = dynamicFieldRegistryByMediaType.get(normalizedMediaTypeBibName);
    if (registry == null) {
      return null;
    }

    if (isPersonDatatype(datatype)) {
      Label label = new Label(labelText);

      HBox header = new HBox(6, deleteButton, label);
      header.setAlignment(Pos.CENTER_LEFT);

      BibliographyAuthorsView authorsView = new BibliographyAuthorsView(
          normalizedMediaTypeBibName,
          editProperty(),
          row -> findDynamicAuthorSuggestions(
              normalizedMediaTypeBibName,
              bibtexName,
              row.getLastName(),
              getSelectedEntryId(normalizedMediaTypeBibName)
          ),
          this::handleAuthorsEdited,
          () -> {},
          this::isLookupSuppressed,
          this::setSelectedEntryId,
          getVisibleAuthorRowCount()
      );

      registry.put(bibtexName, authorsView);

      VBox wrapper = new VBox(4, header, authorsView);
      wrapper.getStyleClass().add("biblio-dynamic-added-field");
      return wrapper;
    }

    if (isDatePickerDatatype(datatype, bibtexName)) {
      DatePicker datePicker = new DatePicker();
      datePicker.valueProperty().addListener((obs, oldValue, newValue) -> refreshDirtyState());
      registry.put(bibtexName, datePicker);

      Node node = labeledNode(labelText, datePicker, true, deleteButton);
      if (node instanceof Region region) {
        region.getStyleClass().add("biblio-dynamic-added-field");
      }
      return node;
    }

    BibliographyFieldView fieldView = new BibliographyFieldView(labelText, editProperty());

    if (isIntegerDatatype(datatype)) {
      enforceIntegerInput(fieldView);
    }

    fieldView.getEditor().textProperty().addListener((obs, oldValue, newValue) -> refreshDirtyState());

    if (bibliographyService != null) {
      bibliographyService.createSyntheticAdditionalAttribute(normalizedMediaTypeBibName, fieldDefinition)
          .ifPresent(syntheticAttribute ->
                         bindDynamicLookupIfAvailable(fieldView, normalizedMediaTypeBibName, syntheticAttribute)
          );
    }

    registry.put(bibtexName, fieldView);

    Node node = labeledNode(labelText, fieldView, true, deleteButton);
    if (node instanceof Region region) {
      region.getStyleClass().add("biblio-dynamic-added-field");
    }
    return node;
  }

  /**
   * Stellt sicher, dass beim Laden eines Eintrags alle zusätzlich belegten Felder
   * vor dem Zurückschreiben in die UI als echte Feldkomponenten vorhanden sind.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @param entry geladener Eintrag
   */
  private void ensureAdditionalFieldsForEntry(String mediaTypeBibName, DynamicBibliographyEntry entry) {
    if (entry == null || bibliographyService == null) {
      return;
    }

    for (BibValue value : entry.getOrderedValues()) {
      if (value == null) {
        continue;
      }

      String bibtexName = normalizeBibtexName(value.bibtexName());
      if (bibtexName.isBlank()) {
        continue;
      }

      if (collectExistingBibtexNames(mediaTypeBibName).contains(bibtexName)) {
        continue;
      }

      bibliographyService.loadBibtexTypeByBibName(bibtexName)
          .ifPresent(fieldDefinition -> addAdditionalField(mediaTypeBibName, fieldDefinition));
    }
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
                                  Map<String, Node> fieldNodeRegistry,
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
          this::setSelectedEntryId,
          getVisibleAuthorRowCount()
      );

      Node node = labeledNode(labelText, authorsView, false, null, true);
      applyImportanceStyle(node, attribute);

      form.getChildren().add(node);
      fieldRegistry.put(bibtexName, authorsView);
      fieldNodeRegistry.put(bibtexName, node);
      return;
    }

    if (isRelatedDatatype(datatype)) {
      BibliographyFieldView fieldView = new BibliographyFieldView(labelText, editProperty());

      bindDynamicLookupIfAvailable(
          fieldView,
          normalizedOwnerMediaTypeBibName,
          attribute
      );

      fieldView.getEditor().textProperty().addListener((obs, oldValue, newValue) -> refreshDirtyState());

      Node node = labeledNode(labelText, fieldView);
      applyImportanceStyle(node, attribute);

      form.getChildren().add(node);
      fieldRegistry.put(bibtexName, fieldView);
      fieldNodeRegistry.put(bibtexName, node);
      return;
    }

    if (isDatePickerDatatype(datatype, bibtexName)) {
      DatePicker datePicker = new DatePicker();
      datePicker.valueProperty().addListener((obs, oldValue, newValue) -> refreshDirtyState());
      Node node = labeledNode(labelText, datePicker);
      applyImportanceStyle(node, attribute);

      form.getChildren().add(node);
      fieldRegistry.put(bibtexName, datePicker);
      fieldNodeRegistry.put(bibtexName, node);
      return;
    }

    if (isAiChoiceField(normalizedOwnerMediaTypeBibName, bibtexName)) {
      BibliographyFieldView fieldView = new BibliographyFieldView(labelText, editProperty());

      bindAiChoiceField(fieldView, normalizedOwnerMediaTypeBibName, bibtexName);

      fieldView.getEditor().textProperty().addListener((obs, oldValue, newValue) -> refreshDirtyState());

      Node node = labeledNode(labelText, fieldView);
      applyImportanceStyle(node, attribute);

      form.getChildren().add(node);
      fieldRegistry.put(bibtexName, fieldView);
      fieldNodeRegistry.put(bibtexName, node);
      return;
    }

    BibliographyFieldView fieldView = new BibliographyFieldView(labelText, editProperty());

    if (isIntegerDatatype(datatype)) {
      enforceIntegerInput(fieldView);
    }

    bindDynamicLookupIfAvailable(
        fieldView,
        normalizedOwnerMediaTypeBibName,
        attribute
    );

    fieldView.getEditor().textProperty().addListener((obs, oldValue, newValue) -> refreshDirtyState());

    Node node = labeledNode(labelText, fieldView);
    applyImportanceStyle(node, attribute);

    form.getChildren().add(node);
    fieldRegistry.put(bibtexName, fieldView);
    fieldNodeRegistry.put(bibtexName, node);
  }

  /**
   * Markiert Identify- und Necessary-Feldzeilen nur im Medienverwaltungs-Popup visuell.
   *
   * @param node Feldzeile
   * @param attribute Attributdefinition
   */
  private void applyImportanceStyle(Node node, MediaAttributeDefinition attribute) {
    if (node == null || attribute == null) {
      return;
    }

    node.getStyleClass().removeAll("biblio-identify-field", "biblio-necessary-field");

    if (!mediaManagementPopupContext) {
      return;
    }

    if (attribute.isIdentify()) {
      node.getStyleClass().add("biblio-identify-field");
    } else if (attribute.isNecessary()) {
      node.getStyleClass().add("biblio-necessary-field");
    }
  }

  /**
   * Prüft, ob ein Feld als KI-Auswahlfeld mit Vorschlagsdropdown behandelt
   * werden soll.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @param bibtexName technischer Feldname
   * @return {@code true}, wenn es sich um ai_provider oder ai_model handelt
   */
  private boolean isAiChoiceField(String mediaTypeBibName, String bibtexName) {
    String normalizedMediaType = normalizeMediaTypeBibName(mediaTypeBibName);
    String normalizedBibtexName = normalizeBibtexName(bibtexName);

    if (!"aitext".equals(normalizedMediaType)) {
      return false;
    }

    return "ai_provider".equals(normalizedBibtexName)
               || "ai_model".equals(normalizedBibtexName);
  }

  /**
   * Verdrahtet ein KI-Auswahlfeld mit Vorschlagsdropdown.
   * Unterstützt Anbieter- und Modellvorschläge sowie wechselseitige Filterung.
   *
   * @param fieldView Feldansicht
   * @param mediaTypeBibName technischer Medientypname
   * @param bibtexName technischer Feldname
   */
  private void bindAiChoiceField(BibliographyFieldView fieldView,
                                 String mediaTypeBibName,
                                 String bibtexName) {
    if (fieldView == null || fieldView.getEditor() == null) {
      return;
    }

    fieldView.getEditor().textProperty().addListener((obs, oldValue, newValue) -> {
      if (suppressAiChoiceRefresh || isLookupSuppressed()) {
        return;
      }
      showAiChoiceSuggestions(fieldView, mediaTypeBibName, bibtexName, newValue, false);
    });

    fieldView.getEditor().focusedProperty().addListener((obs, oldValue, focused) -> {
      if (focused) {
        showAiChoiceSuggestions(
            fieldView,
            mediaTypeBibName,
            bibtexName,
            fieldView.getText(),
            true
        );
      } else {
        fieldView.hideSuggestions();
      }
    });

    fieldView.getEditor().setOnKeyPressed(event -> {
      switch (event.getCode()) {
        case DOWN, ENTER -> showAiChoiceSuggestions(
            fieldView,
            mediaTypeBibName,
            bibtexName,
            fieldView.getText(),
            true
        );
        case ESCAPE -> fieldView.hideSuggestions();
        default -> {
        }
      }
    });
  }

  /**
   * Zeigt Vorschläge für Anbieter- oder Modellfelder eines KI-Eintrags.
   *
   * @param fieldView Feldansicht
   * @param mediaTypeBibName technischer Medientypname
   * @param bibtexName technischer Feldname
   * @param query aktueller Suchtext
   * @param allowRecent {@code true}, wenn bei leerem Suchtext auch zuletzt
   *                    verwendete Werte angeboten werden sollen
   */
  private void showAiChoiceSuggestions(BibliographyFieldView fieldView,
                                       String mediaTypeBibName,
                                       String bibtexName,
                                       String query,
                                       boolean allowRecent) {
    if (fieldView == null || fieldView.getEditor() == null) {
      return;
    }

    String normalizedMediaType = normalizeMediaTypeBibName(mediaTypeBibName);
    String normalizedBibtexName = normalizeBibtexName(bibtexName);
    String normalizedQuery = query == null ? "" : query.trim();

    List<String> suggestions = loadAiChoiceSuggestions(
        normalizedMediaType,
        normalizedBibtexName,
        normalizedQuery,
        allowRecent
    );

    if (suggestions.isEmpty()) {
      fieldView.hideSuggestions();
      return;
    }

    List<CustomMenuItem> items = new ArrayList<>();
    for (String suggestion : suggestions) {
      if (suggestion == null || suggestion.isBlank()) {
        continue;
      }

      Label option = new Label(suggestion);
      CustomMenuItem item = new CustomMenuItem(option, true);
      item.setOnAction(event -> applyAiChoiceSuggestion(fieldView, suggestion));
      items.add(item);
    }

    if (items.isEmpty()) {
      fieldView.hideSuggestions();
      return;
    }

    fieldView.showSuggestions(items);
  }

  /**
   * Lädt Vorschläge für Anbieter- oder Modellfelder eines KI-Eintrags.
   * Bevorzugt wird der konfigurierte {@link AiChoiceProvider}; fehlt dieser,
   * wird defensiv direkt auf den Service zurückgegriffen.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @param bibtexName technischer Feldname
   * @param query aktueller Suchtext
   * @param allowRecent {@code true}, wenn bei leerem Suchtext auch zuletzt
   *                    verwendete Werte geladen werden sollen
   * @return Vorschlagswerte
   */
  private List<String> loadAiChoiceSuggestions(String mediaTypeBibName,
                                               String bibtexName,
                                               String query,
                                               boolean allowRecent) {
    if (!"aitext".equals(normalizeMediaTypeBibName(mediaTypeBibName))) {
      return List.of();
    }

    String providerFilter = getCurrentFieldValue(mediaTypeBibName, "ai_provider");
    String modelFilter = getCurrentFieldValue(mediaTypeBibName, "ai_model");
    String normalizedBibtexName = normalizeBibtexName(bibtexName);
    String normalizedQuery = query == null ? "" : query.trim();

    if ("ai_provider".equals(normalizedBibtexName)) {
      if (!normalizedQuery.isBlank()) {
        List<String> hits = aiChoiceProvider.findProviders(normalizedQuery, modelFilter, 20);
        if (!hits.isEmpty()) {
          return distinctNonBlank(hits);
        }
        if (bibliographyService != null) {
          return distinctNonBlank(
              bibliographyService.findAiProviders(normalizedQuery, modelFilter, 20)
          );
        }
        return List.of();
      }

      if (allowRecent) {
        List<String> recent = aiChoiceProvider.loadRecentProviders(20);
        if (!recent.isEmpty()) {
          return distinctNonBlank(recent);
        }
        if (bibliographyService != null) {
          return distinctNonBlank(
              bibliographyService.loadRecentAiProviders(20)
          );
        }
      }
      return List.of();
    }

    if ("ai_model".equals(normalizedBibtexName)) {
      if (!normalizedQuery.isBlank()) {
        List<String> hits = aiChoiceProvider.findModels(normalizedQuery, providerFilter, 20);
        if (!hits.isEmpty()) {
          return distinctNonBlank(hits);
        }
        if (bibliographyService != null) {
          return distinctNonBlank(
              bibliographyService.findAiModels(normalizedQuery, providerFilter, 20)
          );
        }
        return List.of();
      }

      if (allowRecent) {
        List<String> recent = aiChoiceProvider.loadRecentModels(20);
        if (!recent.isEmpty()) {
          return distinctNonBlank(recent);
        }
        if (bibliographyService != null) {
          return distinctNonBlank(
              bibliographyService.loadRecentAiModels(20)
          );
        }
      }
    }

    return List.of();
  }

  /**
   * Übernimmt einen KI-Vorschlagswert in das Eingabefeld.
   *
   * @param fieldView Feldansicht
   * @param value ausgewählter Vorschlagswert
   */
  private void applyAiChoiceSuggestion(BibliographyFieldView fieldView, String value) {
    if (fieldView == null || fieldView.getEditor() == null) {
      return;
    }

    suppressAiChoiceRefresh = true;
    try {
      fieldView.getEditor().setText(value == null ? "" : value);
      fieldView.getEditor().positionCaret(fieldView.getEditor().getText().length());
    } finally {
      suppressAiChoiceRefresh = false;
    }

    fieldView.hideSuggestions();
    refreshDirtyState();
  }

  /**
   * Entfernt leere und doppelte Vorschlagswerte bei Erhalt der Reihenfolge.
   *
   * @param values Rohwerte
   * @return bereinigte Vorschlagswerte
   */
  private List<String> distinctNonBlank(List<String> values) {
    if (values == null || values.isEmpty()) {
      return List.of();
    }

    LinkedHashSet<String> result = new LinkedHashSet<>();
    for (String value : values) {
      if (value == null) {
        continue;
      }

      String trimmed = value.trim();
      if (!trimmed.isBlank()) {
        result.add(trimmed);
      }
    }

    return List.copyOf(result);
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
   * Verpackt einen Knoten mit Beschriftung.
   *
   * @param labelText Beschriftung
   * @param node Zielknoten
   * @return Container mit Label und Knoten
   */
  private Node labeledNode(String labelText, Node node) {
    return labeledNode(labelText, node, false, null, false);
  }

  /**
   * Verpackt einen Knoten mit Beschriftung und optionalem Delete-Button
   * in einer einheitlich ausgerichteten Feldzeile.
   *
   * @param labelText Beschriftung
   * @param node Zielknoten
   * @param withDeleteButton {@code true}, wenn die Zeile Platz für einen
   *                         Delete-Button berücksichtigen soll
   * @return Container mit Feldzeile
   */
  private Node labeledNode(String labelText, Node node, boolean withDeleteButton) {
    return labeledNode(labelText, node, withDeleteButton, null, false);
  }

  /**
   * Verpackt einen Knoten mit Beschriftung und optionalem Delete-Button
   * in einer einheitlich ausgerichteten Feldzeile.
   *
   * @param labelText Beschriftung
   * @param node Zielknoten
   * @param withDeleteButton {@code true}, wenn die Zeile Platz für einen
   *                         Delete-Button berücksichtigen soll
   * @param deleteButton optionaler Delete-Button
   * @return Container mit Feldzeile
   */
  private Node labeledNode(String labelText, Node node, boolean withDeleteButton, Button deleteButton) {
    return labeledNode(labelText, node, withDeleteButton, deleteButton, false);
  }

  /**
   * Verpackt einen Knoten mit Beschriftung und optionalem Delete-Button
   * in einer einheitlich ausgerichteten Feldzeile.
   *
   * @param labelText Beschriftung
   * @param node Zielknoten
   * @param withDeleteButton {@code true}, wenn die Zeile Platz für einen
   *                         Delete-Button berücksichtigen soll
   * @param deleteButton optionaler Delete-Button
   * @param personField {@code true}, wenn es sich um ein Personenfeld handelt
   * @return Container mit Feldzeile
   */
  private Node labeledNode(String labelText,
                           Node node,
                           boolean withDeleteButton,
                           Button deleteButton,
                           boolean personField) {
    HBox row = new HBox(8);
    row.getStyleClass().add("biblio-field-row");
    row.setAlignment(Pos.CENTER_LEFT);

    HBox labelArea = new HBox(FIELD_DELETE_LABEL_GAP);
    labelArea.getStyleClass().add("biblio-field-label-area");
    labelArea.setAlignment(Pos.CENTER_LEFT);
    labelArea.setMinWidth(FIELD_LABEL_AREA_WIDTH);
    labelArea.setPrefWidth(FIELD_LABEL_AREA_WIDTH);
    labelArea.setMaxWidth(FIELD_LABEL_AREA_WIDTH);

    if (withDeleteButton && deleteButton != null) {
      deleteButton.setMinWidth(FIELD_DELETE_BUTTON_WIDTH);
      deleteButton.setPrefWidth(FIELD_DELETE_BUTTON_WIDTH);
      deleteButton.setMaxWidth(FIELD_DELETE_BUTTON_WIDTH);
      labelArea.getChildren().add(deleteButton);
    }

    Label label = createFixedFieldLabel(
        labelText,
        withDeleteButton
            ? FIELD_LABEL_AREA_WIDTH - FIELD_DELETE_BUTTON_WIDTH - FIELD_DELETE_LABEL_GAP
            : FIELD_LABEL_AREA_WIDTH
    );
    labelArea.getChildren().add(label);

    applyPromptText(node, labelText);
    applyFieldRowPresentation(row, node, labelArea, personField);

    row.getChildren().addAll(labelArea, node);
    return row;
  }

  /**
   * Erzeugt ein fest breites Feldlabel mit Tooltip auf den vollständigen Text.
   * Ist der Text länger als die verfügbare Breite, wird er visuell gekürzt.
   *
   * @param labelText anzuzeigender Text
   * @param width feste Breite des Labels
   * @return konfiguriertes Label
   */
  private Label createFixedFieldLabel(String labelText, double width) {
    String text = labelText == null ? "" : labelText.trim();

    Label label = new Label(text);
    label.getStyleClass().add("biblio-field-label");
    label.setMinWidth(width);
    label.setPrefWidth(width);
    label.setMaxWidth(width);
    label.setTextOverrun(OverrunStyle.ELLIPSIS);
    label.setWrapText(false);

    if (!text.isBlank()) {
      label.setTooltip(new Tooltip(text));
    }

    return label;
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
   * Überträgt einen Labeltext als interne Beschriftung in die eigentliche
   * Eingabekomponente.
   *
   * @param node Zielkomponente
   * @param labelText anzuzeigender Hinweistext
   */
  private void applyPromptText(Node node, String labelText) {
    String prompt = labelText == null ? "" : labelText.trim();
    if (prompt.isBlank() || node == null) {
      return;
    }

    if (node instanceof BibliographyFieldView fieldView && fieldView.getEditor() != null) {
      fieldView.getEditor().setPromptText(prompt);
      return;
    }

    if (node instanceof DatePicker datePicker) {
      datePicker.setPromptText(prompt);
    }
  }

  /**
   * Passt die Darstellung einer Feldzeile an den aktuellen Shell-Kontext an.
   * Im Zettelkontext werden äußere Labels für Nicht-Personenfelder ausgeblendet,
   * so dass nur die Eingabefelder mit interner Beschriftung sichtbar bleiben.
   *
   * @param row Feldzeile
   * @param fieldNode eigentliche Eingabekomponente
   * @param labelArea linker Labelbereich
   * @param personField {@code true}, wenn es sich um ein Personenfeld handelt
   */
  private void applyFieldRowPresentation(HBox row,
                                         Node fieldNode,
                                         HBox labelArea,
                                         boolean personField) {
    if (row == null || fieldNode == null || labelArea == null) {
      return;
    }

    boolean hideExternalLabel =
        editorUsageContext == EditorUsageContext.NOTE_LINKING;

    labelArea.setVisible(!hideExternalLabel);
    labelArea.setManaged(!hideExternalLabel);

    if (hideExternalLabel) {
      labelArea.setMinWidth(0);
      labelArea.setPrefWidth(0);
      labelArea.setMaxWidth(0);
    } else {
      labelArea.setMinWidth(FIELD_LABEL_AREA_WIDTH);
      labelArea.setPrefWidth(FIELD_LABEL_AREA_WIDTH);
      labelArea.setMaxWidth(FIELD_LABEL_AREA_WIDTH);
    }

    if (fieldNode instanceof Region region) {
      HBox.setHgrow(region, Priority.ALWAYS);
      region.setMaxWidth(Double.MAX_VALUE);
    }
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
    formFieldsBox.getChildren().clear();
    applyCurrentContextVisibility();

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
   * Registriert einen Callback für das Hinzufügen eines neuen dynamischen Feldes.
   *
   * @param action auszuführende Aktion; {@code null} deaktiviert die Reaktion
   */
  public void setOnDynamicAddField(Runnable action) {
    this.onDynamicAddField = action == null ? () -> {} : action;
    btnDynamicAdd.setOnAction(e -> this.onDynamicAddField.run());
  }

  /**
   * Liefert den aktuell ausgewählten technischen Medientypnamen.
   *
   * @return technischer Medientypname oder leerer String
   */
  public String getSelectedMediaTypeBibName() {
    if (activeMediaTypeBibName != null && !activeMediaTypeBibName.isBlank()) {
      return normalizeMediaTypeBibName(activeMediaTypeBibName);
    }

    MediaTypeOption option = resolveMediaTypeOption(typeField.getText());
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
   * Initialisiert die Shell für einen konkreten Medientyp und bereitet das
   * zugehörige dynamische Formular als neue oder bestehende Editoransicht vor.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @param populateTypeField {@code true}, wenn der Medientyp sichtbar im
   *                          Eingabefeld stehen bleiben soll
   */
  private void initializeEditorForMediaType(String mediaTypeBibName, boolean populateTypeField) {
    String normalizedMediaTypeBibName = normalizeMediaTypeBibName(mediaTypeBibName);
    if (normalizedMediaTypeBibName.isBlank() || bibliographyService == null) {
      clearEditorView();
      return;
    }

    MediaTypeDefinition mediaType =
        bibliographyService.loadMediaTypeByBibName(normalizedMediaTypeBibName).orElse(null);

    if (mediaType == null) {
      clearEditorView();
      return;
    }

    activeMediaTypeBibName = normalizedMediaTypeBibName;

    suppressTypeFieldCallbacks = true;
    try {
      if (populateTypeField) {
        MediaTypeOption option = mediaTypeOptionsByBibName.get(normalizedMediaTypeBibName);
        typeField.setText(option == null ? mediaType.name() : option.toString());
      } else {
        typeField.clear();
      }
    } finally {
      suppressTypeFieldCallbacks = false;
    }

    typeSuggestionMenu.hide();

    ensureDynamicForm(mediaType);

    VBox form = dynamicFormsByMediaType.get(normalizedMediaTypeBibName);
    formFieldsBox.getChildren().clear();
    if (form != null) {
      formFieldsBox.getChildren().add(form);
    }

    clearDynamicFormValues(normalizedMediaTypeBibName);
    setSelectedEntryId(normalizedMediaTypeBibName, null);
    setSourceEntryId(normalizedMediaTypeBibName, null);
    setAutoFillLocked(normalizedMediaTypeBibName, false);
    setAutoFilled(normalizedMediaTypeBibName, false);

    setEditMode(true);
    applyCurrentContextVisibility();
    captureBaseline();
  }

  /**
   * Öffnet einen neuen Editor für einen Medientyp.
   *
   * @param mediaTypeBibName technischer Medientypname
   */
  public void showNewEditor(String mediaTypeBibName) {
    initializeEditorForMediaType(mediaTypeBibName, false);
  }

  /**
   * Öffnet einen Editor für einen bereits vorhandenen Eintrag, wobei der
   * Medientyp sichtbar im Eingabefeld stehen bleibt.
   *
   * @param mediaTypeBibName technischer Medientypname
   */
  private void showExistingEditor(String mediaTypeBibName) {
    initializeEditorForMediaType(mediaTypeBibName, true);
  }

  /**
   * Öffnet einen neuen Editor für einen Medientyp und zeigt den gewählten
   * Medientyp dabei auch sichtbar im Eingabefeld an.
   *
   * @param mediaTypeBibName technischer Medientypname
   */
  public void showNewEditorAndKeepTypeVisible(String mediaTypeBibName) {
    initializeEditorForMediaType(mediaTypeBibName, true);
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

    Map<String, BibtexFieldDefinition> additionalFields =
        additionalFieldDefinitionsByMediaType.get(normalizedMediaTypeBibName);

    if (additionalFields != null && !additionalFields.isEmpty()) {
      List<String> additionalKeys = new ArrayList<>(additionalFields.keySet());
      for (String additionalKey : additionalKeys) {
        removeAdditionalField(normalizedMediaTypeBibName, additionalKey);
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
      boolean previousSuppressed = isLookupSuppressed();
      setLookupSuppressed(true);
      try {
        fieldView.setText(displayText == null ? "" : displayText);
      } finally {
        setLookupSuppressed(previousSuppressed);
      }
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
    this.onRequestCreateRelated =
      action == null
        ? (relatedBibtexName, targetMediaTypeBibName, displayText) -> {}
        : action;
  }

  /**
   * Registriert einen Callback zum Schließen des Editors im Zettelkontext.
   *
   * @param action auszuführender Callback; {@code null} wird als No-Op behandelt
   */
  public void setOnCloseEditor(Runnable action) {
    this.onCloseEditor = action == null ? () -> {} : action;
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
    refreshDirtyState();
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
   * Normalisiert einen technischen BibTeX-Namen defensiv.
   *
   * @param value Eingabewert
   * @return bereinigter Feldname
   */
  private String normalizeBibtexName(String value) {
    return value == null ? "" : value.trim();
  }

  /**
   * Normalisiert einen Metadaten-Datentyp für technische Vergleiche.
   *
   * @param datatype Datentyp aus den Metadaten
   * @return normalisierte Datentypbezeichnung
   */
  private String normalizeDatatype(String datatype) {
    return datatype == null ? "" : datatype.trim().toLowerCase(Locale.ROOT);
  }

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
   * Markiert den Editorzustand als geändert oder unverändert und synchronisiert
   * den Speichern-Button.
   *
   * @param value neuer Dirty-Zustand
   */
  private void setDirty(boolean value) {
    this.dirty = value;
    updateSaveButtonState();
  }

  /**
   * Aktualisiert den Aktivierungszustand des Speichern-Buttons.
   * Auch im normalen Medien-Popup darf nur gespeichert werden, wenn das
   * Formular lokal gültig ist. Im Zettelkontext bleibt zusätzlich der Fall
   * erlaubt, dass bereits ein bestehender Eintrag selektiert ist.
   */
  private void updateSaveButtonState() {
    boolean locallyValid = isCurrentEntryLocallyValidForSave();

    if (!noteLinkingContext) {
      btnAdd.setDisable(!(dirty && locallyValid));
      return;
    }

    boolean hasExistingSelection = hasExistingSelectionInCurrentForm();
    boolean validNewEntry = dirty && locallyValid;

    btnAdd.setDisable(!(hasExistingSelection || validNewEntry));
  }

  /**
   * Merkt den aktuellen Formularzustand als unveränderte Ausgangsbasis.
   */
  private void captureBaseline() {
    baselineSignature = buildCurrentStateSignature();
    setDirty(false);
  }

  /**
   * Prüft den aktuellen Formularzustand gegen die gemerkte Ausgangsbasis.
   */
  private void refreshDirtyState() {
    setDirty(!Objects.equals(baselineSignature, buildCurrentStateSignature()));
  }

  /**
   * Prüft, ob im aktuell sichtbaren Formular bereits ein bestehender
   * bibliographischer Eintrag referenziert wird.
   *
   * @return {@code true}, wenn eine bestehende Eintrags-ID vorliegt
   */
  private boolean hasExistingSelectionInCurrentForm() {
    String mediaTypeBibName = getSelectedMediaTypeBibName();
    if (mediaTypeBibName.isBlank()) {
      return false;
    }

    Integer selectedEntryId = getSelectedEntryId(mediaTypeBibName);
    if (selectedEntryId != null && selectedEntryId > 0) {
      return true;
    }

    Integer sourceEntryId = getSourceEntryId(mediaTypeBibName);
    return sourceEntryId != null && sourceEntryId > 0;
  }

  /**
   * Prüft lokal, ob das aktuell sichtbare Formular die fachlichen Pflichtfelder
   * für einen Speichervorgang erfüllt.
   * Die Shell liefert dafür nur noch ihren aktuellen Zustand; die eigentliche
   * Pflichtfeldentscheidung trifft der Service.
   *
   * @return {@code true}, wenn alle Pflichtfelder befüllt sind
   */
  private boolean isCurrentEntryLocallyValidForSave() {
    MediaTypeDefinition mediaType = resolveSelectedMediaTypeDefinition();
    if (mediaType == null || bibliographyService == null) {
      return false;
    }

    EditorValidationSnapshot snapshot =
        collectCurrentValidationSnapshot(mediaType.bibName());

    return bibliographyService.isLocallyValidForSave(
        mediaType.bibName(),
        snapshot.fieldValues(),
        snapshot.personValues(),
        snapshot.relatedFieldNames()
    );
  }

  /**
   * Sammelt den aktuell sichtbaren Formularzustand in eine kompakte Struktur
   * für die lokale Pflichtfeldvalidierung.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @return Snapshot des aktuellen Editorzustands
   */
  private EditorValidationSnapshot collectCurrentValidationSnapshot(String mediaTypeBibName) {
    String normalizedMediaTypeBibName = normalizeMediaTypeBibName(mediaTypeBibName);

    Map<String, String> fieldValues = new LinkedHashMap<>();
    Map<String, List<Author>> personValues = new LinkedHashMap<>();
    Set<String> relatedFieldNames = new LinkedHashSet<>();

    if (bibliographyService == null || normalizedMediaTypeBibName.isBlank()) {
      return new EditorValidationSnapshot(fieldValues, personValues, relatedFieldNames);
    }

    for (MediaAttributeDefinition attribute : bibliographyService.listAttributesForMediaType(normalizedMediaTypeBibName)) {
      if (attribute == null || attribute.fieldDefinition() == null) {
        continue;
      }

      collectValidationStateForField(
          normalizedMediaTypeBibName,
          normalizeBibtexName(attribute.fieldDefinition().bibtexName()),
          normalizeBibtexName(attribute.fieldDefinition().datatype()),
          fieldValues,
          personValues,
          relatedFieldNames
      );
    }

    Map<String, BibtexFieldDefinition> additionalFields =
        additionalFieldDefinitionsByMediaType.get(normalizedMediaTypeBibName);

    if (additionalFields != null && !additionalFields.isEmpty()) {
      for (BibtexFieldDefinition fieldDefinition : additionalFields.values()) {
        if (fieldDefinition == null) {
          continue;
        }

        collectValidationStateForField(
            normalizedMediaTypeBibName,
            normalizeBibtexName(fieldDefinition.bibtexName()),
            normalizeBibtexName(fieldDefinition.datatype()),
            fieldValues,
            personValues,
            relatedFieldNames
        );
      }
    }

    return new EditorValidationSnapshot(fieldValues, personValues, relatedFieldNames);
  }

  /**
   * Überträgt den lokalen Zustand eines einzelnen Feldes in die Snapshot-Struktur
   * der Pflichtfeldvalidierung.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @param bibtexName technischer Feldname
   * @param datatype technischer Datentyp
   * @param fieldValues einfache Feldwerte
   * @param personValues belegte Personenfelder
   * @param relatedFieldNames belegte Related-Felder
   */
  private void collectValidationStateForField(String mediaTypeBibName,
                                              String bibtexName,
                                              String datatype,
                                              Map<String, String> fieldValues,
                                              Map<String, List<Author>> personValues,
                                              Set<String> relatedFieldNames) {
    if (bibtexName == null || bibtexName.isBlank()) {
      return;
    }

    if (isPersonDatatype(datatype)) {
      List<Author> authors = getCurrentAuthorsForField(mediaTypeBibName, bibtexName);
      if (authors != null && !authors.isEmpty()) {
        personValues.put(bibtexName, authors);
      }
      return;
    }

    if (isRelatedDatatype(datatype)) {
      Integer relatedEntryId = getCurrentRelatedEntryId(mediaTypeBibName, bibtexName);
      if (relatedEntryId != null && relatedEntryId > 0) {
        relatedFieldNames.add(bibtexName);
      }
      return;
    }

    fieldValues.put(bibtexName, getCurrentFieldValue(mediaTypeBibName, bibtexName));
  }

  /**
   * Baut eine stabile Signatur des aktuell sichtbaren Formularzustands.
   *
   * @return Zustands-Signatur des aktuellen Editors
   */
  private String buildCurrentStateSignature() {
    String mediaTypeBibName = getSelectedMediaTypeBibName();
    if (mediaTypeBibName.isBlank() || bibliographyService == null) {
      return "";
    }

    StringBuilder builder = new StringBuilder();
    builder.append(mediaTypeBibName).append('|');
    builder.append(getCurrentSourceEntryId()).append('|');
    builder.append(getCurrentSelectedEntryId()).append('|');

    MediaTypeDefinition mediaType = resolveSelectedMediaTypeDefinition();
    if (mediaType == null) {
      return builder.toString();
    }

    appendRegularFieldStateSignature(builder, mediaTypeBibName, mediaType.bibName());
    appendAdditionalFieldStateSignature(builder, mediaTypeBibName);

    return builder.toString();
  }

  /**
   * Hängt die Zustands-Signatur aller regulären Formularfelder eines Medientyps
   * an den übergebenen Builder an.
   *
   * @param builder Ziel-Builder
   * @param activeMediaTypeBibName technischer Medientypname des aktiven Formulars
   * @param signatureMediaTypeBibName technischer Medientypname für die Attributauflösung
   */
  private void appendRegularFieldStateSignature(StringBuilder builder,
                                                String activeMediaTypeBibName,
                                                String signatureMediaTypeBibName) {
    for (MediaAttributeDefinition attribute : bibliographyService.listAttributesForMediaType(signatureMediaTypeBibName)) {
      if (attribute == null || attribute.fieldDefinition() == null) {
        continue;
      }

      appendFieldStateSignature(
          builder,
          activeMediaTypeBibName,
          attribute.fieldDefinition().bibtexName(),
          attribute.fieldDefinition().datatype(),
          false
      );
    }
  }

  /**
   * Hängt die Zustands-Signatur aller zusätzlich eingefügten Formularfelder
   * eines Medientyps an den übergebenen Builder an.
   *
   * @param builder Ziel-Builder
   * @param mediaTypeBibName technischer Medientypname
   */
  private void appendAdditionalFieldStateSignature(StringBuilder builder, String mediaTypeBibName) {
    Map<String, BibtexFieldDefinition> additionalFields =
        additionalFieldDefinitionsByMediaType.get(normalizeMediaTypeBibName(mediaTypeBibName));

    if (additionalFields == null || additionalFields.isEmpty()) {
      return;
    }

    for (BibtexFieldDefinition fieldDefinition : additionalFields.values()) {
      if (fieldDefinition == null) {
        continue;
      }

      appendFieldStateSignature(
          builder,
          mediaTypeBibName,
          fieldDefinition.bibtexName(),
          fieldDefinition.datatype(),
          true
      );
    }
  }

  /**
   * Hängt die Zustands-Signatur eines einzelnen Feldes an den Builder an.
   *
   * @param builder Ziel-Builder
   * @param mediaTypeBibName technischer Medientypname
   * @param bibtexName technischer Feldname
   * @param datatype technischer Datentyp
   * @param additionalField {@code true}, wenn es sich um ein Zusatzfeld handelt
   */
  private void appendFieldStateSignature(StringBuilder builder,
                                         String mediaTypeBibName,
                                         String bibtexName,
                                         String datatype,
                                         boolean additionalField) {
    if (builder == null) {
      return;
    }

    String normalizedBibtexName = normalizeBibtexName(bibtexName);
    if (normalizedBibtexName.isBlank()) {
      return;
    }

    if (additionalField) {
      builder.append("extra:");
    }

    builder.append(normalizedBibtexName).append('=');

    if (isPersonDatatype(datatype)) {
      appendPersonFieldStateSignature(builder, mediaTypeBibName, normalizedBibtexName);
    } else if (isRelatedDatatype(datatype)) {
      appendRelatedFieldStateSignature(builder, mediaTypeBibName, normalizedBibtexName);
    } else {
      builder.append(getCurrentFieldValue(mediaTypeBibName, normalizedBibtexName));
    }

    builder.append('|');
  }

  /**
   * Hängt die Signatur eines Personenfeldes an den Builder an.
   *
   * @param builder Ziel-Builder
   * @param mediaTypeBibName technischer Medientypname
   * @param bibtexName technischer Feldname
   */
  private void appendPersonFieldStateSignature(StringBuilder builder,
                                               String mediaTypeBibName,
                                               String bibtexName) {
    List<Author> authors = getCurrentAuthorsForField(mediaTypeBibName, bibtexName);
    for (Author author : authors) {
      if (author == null) {
        continue;
      }

      builder.append(author.getId()).append(':')
          .append(author.getLastName()).append(':')
          .append(author.getFirstName()).append(':')
          .append(author.getPosition()).append(';');
    }
  }

  /**
   * Hängt die Signatur eines Related-Feldes an den Builder an.
   *
   * @param builder Ziel-Builder
   * @param mediaTypeBibName technischer Medientypname
   * @param bibtexName technischer Feldname
   */
  private void appendRelatedFieldStateSignature(StringBuilder builder,
                                                String mediaTypeBibName,
                                                String bibtexName) {
    builder.append(getCurrentRelatedEntryId(mediaTypeBibName, bibtexName)).append(':')
        .append(getCurrentFieldValue(mediaTypeBibName, bibtexName));
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


