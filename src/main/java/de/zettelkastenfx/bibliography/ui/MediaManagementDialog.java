package de.zettelkastenfx.bibliography.ui;

import de.zettelkastenfx.base.BaseIcon;
import de.zettelkastenfx.bibliography.api.BibliographyService;
import de.zettelkastenfx.bibliography.model.*;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Modal angezeigtes Popup zur Verwaltung bibliographischer Medien.
 * <p>
 * Phase B:
 * - vorhandene Medien durchsuchen
 * - neue Medien anlegen
 * - bestehende Medien bewusst bearbeiten
 * - Medien aus dem Speicher löschen
 */
public class MediaManagementDialog {

  private enum Mode {
    BROWSE,
    CREATE,
    EDIT
  }

  private final Stage dialogStage;
  private final BibliographyService bibliographyService;

  private final BorderPane root = new BorderPane();
  private final ToolBar modeToolbar = new ToolBar();
  private final ToggleButton btnAdd = createModeToggleButton(BaseIcon.BOOK_ADD, "zufügen");
  private final ToggleButton btnEdit = createModeToggleButton(BaseIcon.BOOK_EDIT, "ändern");
  private final Button btnDelete = BaseIcon.BOOK_DELETE.button("Medium löschen");
  private final ToggleGroup modeToggleGroup = new ToggleGroup();

  private final TextField leftMediaTypeField = new TextField();
  private final TextField rightMediaTypeField = new TextField();
  private final ContextMenu leftMediaTypeMenu = new ContextMenu();
  private final ContextMenu rightMediaTypeMenu = new ContextMenu();

  private final TextField searchField = new TextField();
  private final CheckBox showPersonCheckBox = new CheckBox("mit Person");
  private final ListView<BibliographyReference> resultList = new ListView<>();

  private final BorderPane shellHost = new BorderPane();
  private final Label editorStateLabel = new Label("Kein Medium ausgewählt");
  private final Button btnClose = new Button("Schließen");

  private final BibliographyEditorShellFactory.ShellBundle shellBundle;
  private final ObjectProperty<Mode> mode = new SimpleObjectProperty<>(Mode.BROWSE);

  private List<MediaTypeDefinition> allMediaTypes = List.of();
  private MediaTypeDefinition leftSelectedMediaType;
  private MediaTypeDefinition rightSelectedMediaType;

  private Integer currentEntryId;
  private String currentDisplayText;

  /**
   * Erzeugt das Medien-Popup.
   *
   * @param owner Fensterbesitzer
   * @param bibliographyService Bibliographie-Fassade
   * @param shellFactory Factory für eingebettete Bibliographie-Shells
   */
  public MediaManagementDialog(Stage owner,
                               BibliographyService bibliographyService,
                               BibliographyEditorShellFactory shellFactory) {
    this.bibliographyService = Objects.requireNonNull(bibliographyService, "bibliographyService");

    this.dialogStage = new Stage();
    dialogStage.initOwner(owner);
    dialogStage.initModality(Modality.WINDOW_MODAL);
    dialogStage.setTitle("Medienverwaltung");

    this.shellBundle = Objects.requireNonNull(shellFactory, "shellFactory").create(
        new BibliographyEditorShellFactory.ShellCallbacks() {
          @Override
          public void onSave(BibliographyEditorShell shell) {
            saveCurrentEntry(shell);
          }

          @Override
          public void onDelete(BibliographyEditorShell shell) {
            deleteCurrentEntry();
          }

          @Override
          public void onNoneSelected(BibliographyEditorShell shell) {
            // Im Popup kein automatisches Entkoppeln vom Zettel.
          }
        }
    );

    initializeView();
    shellBundle.shell().configureForMediaManagementPopup();
    shellBundle.shell().clearEditorView();
    initializeBehavior();

    dialogStage.setScene(new Scene(root, 1100, 760));
  }

  /**
   * Zeigt den Dialog modal an.
   */
  public void show() {
    runSearch();
    dialogStage.showAndWait();
  }

  /**
   * Baut die Oberfläche des Dialogs auf.
   */
  private void initializeView() {
    modeToolbar.getItems().addAll(
        btnAdd,
        btnEdit
    );

    btnAdd.setToggleGroup(modeToggleGroup);
    btnEdit.setToggleGroup(modeToggleGroup);

    allMediaTypes = bibliographyService.listMediaTypes();

    leftMediaTypeField.setPromptText("Alle Medien");
    rightMediaTypeField.setPromptText("Medientyp für Editor wählen");
    searchField.setPromptText("Titel oder Suchbegriff eingeben");

    editorStateLabel.getStyleClass().add("media-management-editor-state");

    resultList.setCellFactory(list -> new ListCell<>() {
      @Override
      protected void updateItem(BibliographyReference item, boolean empty) {
        super.updateItem(item, empty);
        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);

        if (empty || item == null) {
          setText(null);
          setGraphic(null);
          return;
        }

        setText(null);
        setGraphic(buildReferenceCellGraphic(item));
      }
    });

    HBox searchRow = new HBox(8, searchField, showPersonCheckBox);
    HBox.setHgrow(searchField, Priority.ALWAYS);
    showPersonCheckBox.setFocusTraversable(false);
    showPersonCheckBox.setSelected(true);

    VBox leftPane = new VBox(8,
        new Label("Medientyp"),
        leftMediaTypeField,
        new Label("Suche"),
        searchRow,
        resultList
    );
    leftPane.setPadding(new Insets(10));
    leftPane.setPrefWidth(330);
    VBox.setVgrow(resultList, Priority.ALWAYS);

    shellHost.setPadding(new Insets(10));
    shellHost.setCenter(null);

    VBox rightPane = new VBox(8,
        editorStateLabel,
        new Label("Editor-Medientyp"),
        rightMediaTypeField,
        shellHost
    );
    rightPane.setPadding(new Insets(10));
    VBox.setVgrow(shellHost, Priority.ALWAYS);

    SplitPane splitPane = new SplitPane(leftPane, rightPane);
    splitPane.setDividerPositions(0.30);

    HBox bottomBar = new HBox(8, btnClose);
    bottomBar.setPadding(new Insets(10));
    bottomBar.setStyle("-fx-alignment: center-right;");

    root.setTop(modeToolbar);
    root.setCenter(splitPane);
    root.setBottom(bottomBar);

    btnAdd.setDisable(false);
    btnEdit.setDisable(true);
    btnAdd.setSelected(false);
    btnEdit.setSelected(false);

    switchMode(Mode.BROWSE);
    updateEditorStateLabel();
  }

  /**
   * Verdrahtet alle Interaktionen des Dialogs.
   */
  private void initializeBehavior() {
    btnAdd.setOnAction(e -> {
      if (!btnAdd.isSelected()) {
        btnAdd.setSelected(true);
      }

      clearLoadedEntryState();

      if (rightSelectedMediaType == null) {
        shellHost.setCenter(null);
        btnEdit.setDisable(true);
        updateEditorStateLabel();
        return;
      }

      shellHost.setCenter(shellBundle.shell().getRoot());
      shellBundle.shell().showNewEditor(rightSelectedMediaType.bibName());
      shellBundle.shell().setEditMode(true);
      switchMode(Mode.CREATE);
    });

    btnEdit.setOnAction(e -> {
      if (currentEntryId == null || currentEntryId <= 0) {
        btnEdit.setSelected(false);
        return;
      }

      btnEdit.setSelected(true);
      switchMode(Mode.EDIT);
    });

    btnDelete.setOnAction(e -> deleteCurrentEntry());

    btnClose.setOnAction(e -> dialogStage.close());

    searchField.textProperty().addListener((obs, oldValue, newValue) -> runSearch());

    showPersonCheckBox.selectedProperty().addListener((obs, oldValue, newValue) -> {
      resultList.refresh();
    });

    leftMediaTypeField.textProperty().addListener((obs, oldValue, newValue) -> {
      MediaTypeDefinition resolvedType = resolveMediaType(newValue);
      if (resolvedType != null) {
        leftSelectedMediaType = resolvedType;
        leftMediaTypeMenu.hide();
        runSearch();
        return;
      }

      if (newValue == null || newValue.isBlank()) {
        leftSelectedMediaType = null;
        leftMediaTypeMenu.hide();
        runSearch();
        return;
      }

      leftSelectedMediaType = null;
      showMediaTypeSuggestions(
          leftMediaTypeField,
          leftMediaTypeMenu,
          newValue,
          this::applyLeftMediaTypeSelection
      );
      runSearch();
    });

    rightMediaTypeField.textProperty().addListener((obs, oldValue, newValue) -> {
      MediaTypeDefinition resolvedType = resolveMediaType(newValue);
      if (resolvedType != null) {
        applyRightMediaTypeSelection(resolvedType);
        return;
      }

      if (newValue == null || newValue.isBlank()) {
        rightSelectedMediaType = null;
        rightMediaTypeMenu.hide();
        shellHost.setCenter(null);
        updateEditorStateLabel();
        return;
      }

      rightSelectedMediaType = null;
      showMediaTypeSuggestions(
          rightMediaTypeField,
          rightMediaTypeMenu,
          newValue,
          this::applyRightMediaTypeSelection
      );
      updateEditorStateLabel();
    });

    leftMediaTypeField.focusedProperty().addListener((obs, oldValue, focused) -> {
      if (!focused) {
        leftMediaTypeMenu.hide();
        applyResolvedLeftMediaTypeFromText();
      }
    });

    rightMediaTypeField.focusedProperty().addListener((obs, oldValue, focused) -> {
      if (!focused) {
        rightMediaTypeMenu.hide();
        applyResolvedRightMediaTypeFromText();
      }
    });

    resultList.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, value) -> {
      if (value == null) {
        return;
      }

      if (value.mediaTypeBibName() != null && !value.mediaTypeBibName().isBlank()) {
        MediaTypeDefinition selectedType = resolveMediaType(value.mediaTypeBibName());
        if (selectedType != null) {
          applyRightMediaTypeSelection(selectedType);
        }
      }

      loadExistingEntry(value.entryId());
    });
  }

  /**
   * Erzeugt einen Toggle-Button für die Modusleiste des Medien-Popups.
   *
   * @param icon anzuzeigendes Icon
   * @param text sichtbarer Buttontext
   * @return konfigurierter Toggle-Button
   */
  private ToggleButton createModeToggleButton(BaseIcon icon, String text) {
    ToggleButton button = icon.toggleButton(text);
    button.setFocusTraversable(false);
    button.setContentDisplay(ContentDisplay.LEFT);
    button.getStyleClass().add("toggle-button");
    button.setMinHeight(28);
    return button;
  }

  /**
   * Schaltet den Dialogmodus um.
   *
   * @param newMode neuer Modus
   */
  private void switchMode(Mode newMode) {
    mode.set(newMode);

    switch (newMode) {
      case BROWSE -> {
        btnAdd.setSelected(false);
        btnEdit.setSelected(false);
      }
      case CREATE -> {
        btnAdd.setSelected(true);
        btnEdit.setSelected(false);
      }
      case EDIT -> {
        btnAdd.setSelected(false);
        btnEdit.setSelected(true);
      }
    }

    switch (newMode) {
      case BROWSE -> {
        searchField.setDisable(false);
        resultList.setDisable(false);
      }
      case CREATE -> {
        if (rightSelectedMediaType != null) {
          shellHost.setCenter(shellBundle.shell().getRoot());
          shellBundle.shell().showNewEditor(rightSelectedMediaType.bibName());
          shellBundle.shell().setEditMode(true);
        } else {
          shellHost.setCenter(null);
        }
        clearLoadedEntryState();
      }
      case EDIT -> {
        if (currentEntryId == null || currentEntryId <= 0) {
          mode.set(Mode.BROWSE);
          updateEditorStateLabel();
          return;
        }
        shellHost.setCenter(shellBundle.shell().getRoot());
        shellBundle.shell().setEditMode(true);
      }
    }

    btnAdd.setDisable(rightSelectedMediaType == null);
    btnEdit.setDisable(currentEntryId == null || currentEntryId <= 0);
    updateEditorStateLabel();
  }

  /**
   * Führt die Mediensuche anhand des linken Medientypfelds und des Suchtexts aus.
   */
  private void runSearch() {
    String mediaTypeBibName = leftSelectedMediaType == null ? "" : leftSelectedMediaType.bibName();
    String query = searchField.getText() == null ? "" : searchField.getText().trim();

    List<BibliographyReference> hits = bibliographyService.searchMediaReferences(
        mediaTypeBibName,
        query,
        100
    );

    resultList.getItems().setAll(hits);
  }

  /**
   * Baut die grafische Darstellung eines Listentreffers im linken Fenster.
   * Die Zeile besteht aus bis zu zwei Labels in einer vertikalen Box:
   * oben optional die Person, unten der Titel in kursiver Schrift.
   *
   * @param reference anzuzeigende Referenz
   * @return grafische Darstellung für die Trefferliste
   */
  private TextFlow buildReferenceCellGraphic(BibliographyReference reference) {
    String personText = showPersonCheckBox.isSelected()
                            ? safeLoadPrimaryPersonDisplay(reference)
                            : "";

    String titleText = resolveListTitle(reference, personText);

    TextFlow flow = new TextFlow();
    flow.setMaxWidth(Double.MAX_VALUE);

    if (!personText.isBlank()) {
      Text person = new Text(personText + ": ");
      flow.getChildren().add(person);
    }

    Text title = new Text(titleText);
    title.setFont(Font.font(title.getFont().getFamily(), FontPosture.ITALIC, title.getFont().getSize()));

    flow.getChildren().add(title);

    return flow;
  }

  /**
   * Ermittelt den anzuzeigenden Titel für die Trefferliste.
   * Vorrang hat das echte Titelfeld des geladenen Eintrags. Erst wenn dieses
   * fehlt, wird auf den kompakten Referenztext zurückgegriffen.
   * Ist die Personenanzeige aktiv und beginnt der Fallback-Text bereits mit
   * derselben Person, wird dieses Präfix entfernt.
   *
   * @param reference Trefferreferenz
   * @param personText bereits aufgelöste Person oder leerer String
   * @return anzuzeigender Titeltext
   */
  private String resolveListTitle(BibliographyReference reference, String personText) {
    if (reference == null) {
      return "";
    }

    String entryTitle = safeLoadEntryTitle(reference.entryId());
    if (!entryTitle.isBlank()) {
      return entryTitle;
    }

    String fallbackText = safeTrim(reference.displayText());
    if (fallbackText.isBlank()) {
      return "";
    }

    if (!personText.isBlank()) {
      String prefix = personText + ": ";
      if (fallbackText.startsWith(prefix)) {
        String stripped = safeTrim(fallbackText.substring(prefix.length()));
        if (!stripped.isBlank()) {
          return stripped;
        }
      }
    }

    return fallbackText;
  }

  /**
   * Baut einen kompakten Anzeigetext für einen bereits geladenen Treffer.
   * Die Darstellung folgt derselben Logik wie die Trefferliste:
   * optional Person, danach Titel.
   *
   * @param reference Trefferreferenz
   * @return kompakter Anzeigetext
   */
  private String buildReferenceDisplayText(BibliographyReference reference) {
    if (reference == null) {
      return "";
    }

    String personText = safeLoadPrimaryPersonDisplay(reference);
    String titleText = resolveListTitle(reference, personText);

    if (!personText.isBlank() && !titleText.isBlank()) {
      return personText + ": " + titleText;
    }
    if (!titleText.isBlank()) {
      return titleText;
    }
    if (!personText.isBlank()) {
      return personText;
    }

    return safeTrim(reference.displayText());
  }

  /**
   * Lädt das echte Titelfeld eines bibliographischen Eintrags fehlertolerant.
   *
   * @param entryId Datenbank-ID des Eintrags
   * @return Titel oder leerer String
   */
  private String safeLoadEntryTitle(int entryId) {
    if (entryId <= 0) {
      return "";
    }

    try {
      return bibliographyService.loadEntry(entryId)
        .flatMap(entry -> entry.findValue("title"))
        .filter(StringBibValue.class::isInstance)
        .map(StringBibValue.class::cast)
        .map(StringBibValue::value)
        .map(this::safeTrim)
        .orElse("");
    } catch (Exception ex) {
      return "";
    }
  }

  /**
   * Lädt die Personenanzeige fehlertolerant. Probleme in Altbeständen oder
   * inkonsistente Metadaten dürfen nicht dazu führen, dass eine Trefferzeile
   * unsichtbar wird.
   *
   * @param reference Trefferreferenz
   * @return formatierte Person oder leerer String
   */
  private String safeLoadPrimaryPersonDisplay(BibliographyReference reference) {
    if (reference == null || reference.entryId() <= 0) {
      return "";
    }

    try {
      return safeTrim(loadPrimaryPersonDisplay(reference.entryId(), reference.mediaTypeId()));
    } catch (Exception ex) {
      return "";
    }
  }

  /**
   * Lädt die erste Person des wichtigsten Personenfeldes eines Eintrags.
   * Bevorzugt werden Personenfelder mit Modus {@code identify}, danach
   * {@code necessary}. Bei Gleichstand gewinnt das Feld mit der kleineren
   * BibTeX-Feld-ID.
   *
   * @param entryId Datenbank-ID des Eintrags
   * @param fallbackMediaTypeId Medientyp-ID aus der Referenz als Fallback
   * @return formatierte Person oder leerer String
   */
  private String loadPrimaryPersonDisplay(int entryId, Integer fallbackMediaTypeId) {
    Optional<DynamicBibliographyEntry> loadedEntry = bibliographyService.loadEntry(entryId);
    if (loadedEntry.isEmpty()) {
      return "";
    }

    DynamicBibliographyEntry entry = loadedEntry.get();
    Integer mediaTypeId = entry.getMediaTypeId() != null ? entry.getMediaTypeId() : fallbackMediaTypeId;

    if (mediaTypeId == null || mediaTypeId <= 0) {
      return "";
    }

    return extractPrimaryPersonDisplay(entry, mediaTypeId);
  }

  /**
   * Ermittelt für einen dynamischen Eintrag das wichtigste Personenfeld und
   * liefert daraus die erste Person in Listenform.
   *
   * @param entry geladener Eintrag
   * @param mediaTypeId aufgelöste Medientyp-ID
   * @return formatierte Person oder leerer String
   */
  private String extractPrimaryPersonDisplay(DynamicBibliographyEntry entry, int mediaTypeId) {
    if (entry == null || mediaTypeId <= 0) {
      return "";
    }

    List<MediaAttributeDefinition> attributes = bibliographyService.listAttributesForMediaType(mediaTypeId);
    if (attributes == null || attributes.isEmpty()) {
      return "";
    }

    MediaAttributeDefinition primaryPersonAttribute = attributes.stream()
      .filter(attribute -> attribute != null && attribute.fieldDefinition() != null)
      .filter(attribute -> isPersonDatatype(attribute.fieldDefinition().datatype()))
      .filter(attribute -> attribute.isIdentify() || attribute.isNecessary())
      .sorted(
        Comparator
          .comparingInt(this::personFieldPriority)
          .thenComparingInt(attribute -> attribute.fieldDefinition().id())
        )
      .findFirst()
      .orElse(null);

    if (primaryPersonAttribute == null) {
      return "";
    }

    String bibtexName = safeTrim(primaryPersonAttribute.fieldDefinition().bibtexName());
    if (bibtexName.isBlank()) {
      return "";
    }

    return entry.findValue(bibtexName)
               .filter(PersonBibValue.class::isInstance)
               .map(PersonBibValue.class::cast)
               .map(PersonBibValue::value)
               .filter(persons -> persons != null && !persons.isEmpty())
               .map(persons -> persons.getFirst())
               .map(this::formatPersonRef)
               .orElse("");
  }

  /**
   * Liefert die Priorität eines Personenfeldes für die Listenanzeige.
   * {@code identify} ist stärker als {@code necessary}.
   *
   * @param attribute Attributdefinition
   * @return Sortierpriorität
   */
  private int personFieldPriority(MediaAttributeDefinition attribute) {
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
   * Prüft, ob ein Metadaten-Datentyp als Personenfeld behandelt werden soll.
   *
   * @param datatype Datentyp aus den Metadaten
   * @return {@code true}, wenn es sich um ein Personenfeld handelt
   */
  private boolean isPersonDatatype(String datatype) {
    return "person".equalsIgnoreCase(safeTrim(datatype));
  }

  /**
   * Formatiert eine Personenreferenz in der Form "Nachname, Vorname".
   *
   * @param person Personenreferenz
   * @return formatierter Personenname oder leerer String
   */
  private String formatPersonRef(PersonRef person) {
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
   * Lädt einen vorhandenen Eintrag in die Shell.
   *
   * @param entryId Datenbank-ID des Eintrags
   */
  private void loadExistingEntry(Integer entryId) {
    if (entryId == null || entryId <= 0) {
      return;
    }

    DynamicBibliographyEntry loaded = bibliographyService.loadEntry(entryId).orElse(null);
    if (loaded == null) {
      return;
    }

    shellHost.setCenter(shellBundle.shell().getRoot());
    shellBundle.shell().showDynamicEntry(loaded, true);

    if (loaded.getMediaTypeBibName() != null && !loaded.getMediaTypeBibName().isBlank()) {
      MediaTypeDefinition selectedType = resolveMediaType(loaded.getMediaTypeBibName());
      if (selectedType != null) {
        rightSelectedMediaType = selectedType;
        rightMediaTypeField.setText(selectedType.name());
      }
    }

    currentEntryId = entryId;
    currentDisplayText = bibliographyService.loadReference(entryId)
                             .map(this::buildReferenceDisplayText)
                             .orElse(null);

    btnEdit.setDisable(false);
    switchMode(Mode.EDIT);
  }

  /**
   * Speichert den aktuell sichtbaren Eintrag direkt aus der Shell.
   *
   * @param shell aktive Editor-Shell
   */
  private void saveCurrentEntry(BibliographyEditorShell shell) {
    DynamicBibliographyEntry dynamicEntry = shell.readDynamicEntry();
    if (dynamicEntry == null) {
      showError("Medium", "Das Medium konnte nicht aus dem Editor gelesen werden.");
      return;
    }

    Integer sourceEntryId = shell.getCurrentSourceEntryId();
    if ((sourceEntryId == null || sourceEntryId <= 0)
            && mode.get() == Mode.EDIT
            && currentEntryId != null
            && currentEntryId > 0) {
      sourceEntryId = currentEntryId;
    }
    if (sourceEntryId == null || sourceEntryId <= 0) {
      sourceEntryId = shell.getCurrentSelectedEntryId();
    }
    if (sourceEntryId != null && sourceEntryId > 0) {
      dynamicEntry.setId(sourceEntryId);
    }

    int entryId = bibliographyService.saveEntry(dynamicEntry);
    if (entryId <= 0) {
      showError("Speichern fehlgeschlagen", "Das Medium konnte nicht gespeichert werden.");
      return;
    }

    currentEntryId = entryId;
    currentDisplayText = bibliographyService.loadReference(entryId)
                             .map(this::buildReferenceDisplayText)
                             .filter(text -> text != null && !text.isBlank())
                             .orElse("Medium #" + entryId);

    DynamicBibliographyEntry reloaded = bibliographyService.loadEntry(entryId).orElse(null);
    if (reloaded != null) {
      shellHost.setCenter(shellBundle.shell().getRoot());
      shellBundle.shell().showDynamicEntry(reloaded, true);

      MediaTypeDefinition selectedType = resolveMediaType(reloaded.getMediaTypeBibName());
      if (selectedType != null) {
        rightSelectedMediaType = selectedType;
        rightMediaTypeField.setText(selectedType.name());
      }
    }

    btnEdit.setDisable(false);
    switchMode(Mode.EDIT);
    runSearch();
  }

  /**
   * Leert den Zustand des aktuell geladenen Eintrags.
   */
  private void clearLoadedEntryState() {
    currentEntryId = null;
    currentDisplayText = null;
    btnEdit.setDisable(true);
    updateEditorStateLabel();
  }

  /**
   * Löscht den aktuell geladenen Eintrag nach Bestätigung.
   */
  private void deleteCurrentEntry() {
    if (currentEntryId == null || currentEntryId <= 0) {
      return;
    }

    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
    alert.initOwner(dialogStage);
    alert.setTitle("Medienverwaltung");
    alert.setHeaderText("Medium wirklich löschen?");
    alert.setContentText("Das ausgewählte Medium wird aus dem Speicher entfernt.");
    Optional<ButtonType> result = alert.showAndWait();

    if (result.isEmpty() || result.get() != ButtonType.OK) {
      return;
    }

    try {
      bibliographyService.deleteEntry(currentEntryId);
      clearLoadedEntryState();
      resultList.getSelectionModel().clearSelection();
      shellBundle.shell().clearEditorView();
      shellHost.setCenter(null);

      btnAdd.setSelected(false);
      btnEdit.setSelected(false);
      btnAdd.setDisable(true);
      btnEdit.setDisable(true);

      switchMode(Mode.BROWSE);
      runSearch();
      updateEditorStateLabel();
    } catch (Exception ex) {
      showError(
          "Löschen fehlgeschlagen",
          ex.getMessage() == null ? "Das Medium konnte nicht gelöscht werden." : ex.getMessage()
      );
    }
  }

  /**
   * Löst einen Medientyp anhand von Anzeigename oder bib_name auf.
   *
   * @param value Eingabetext
   * @return passender Medientyp oder {@code null}
   */
  private MediaTypeDefinition resolveMediaType(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }

    String normalized = value.trim().toLowerCase();
    return allMediaTypes.stream()
               .filter(type -> type.name().trim().toLowerCase().equals(normalized)
                                   || type.bibName().trim().toLowerCase().equals(normalized))
               .findFirst()
               .orElse(null);
  }

  /**
   * Übernimmt links einen manuell vollständig eingegebenen Medientyptext
   * als echten Filterzustand. Leerer oder nicht auflösbarer Text entfernt
   * den Typfilter wieder.
   */
  private void applyResolvedLeftMediaTypeFromText() {
    String text = leftMediaTypeField.getText();

    if (text == null || text.isBlank()) {
      leftSelectedMediaType = null;
      runSearch();
      return;
    }

    MediaTypeDefinition resolvedType = resolveMediaType(text);
    if (resolvedType != null) {
      leftSelectedMediaType = resolvedType;
      leftMediaTypeField.setText(resolvedType.name());
    } else {
      leftSelectedMediaType = null;
    }

    runSearch();
  }

  /**
   * Übernimmt rechts einen manuell vollständig eingegebenen Medientyptext
   * als echten Editorzustand. Leerer oder nicht auflösbarer Text leert den
   * rechten Editorbereich.
   */
  private void applyResolvedRightMediaTypeFromText() {
    String text = rightMediaTypeField.getText();

    if (text == null || text.isBlank()) {
      rightSelectedMediaType = null;
      shellHost.setCenter(null);
      updateEditorStateLabel();
      return;
    }

    MediaTypeDefinition resolvedType = resolveMediaType(text);
    if (resolvedType != null) {
      applyRightMediaTypeSelection(resolvedType);
    } else {
      rightSelectedMediaType = null;
      shellHost.setCenter(null);
      updateEditorStateLabel();
    }
  }

  /**
   * Aktualisiert die Zustandsbezeichnung des rechten Editorbereichs.
   * So wird klar sichtbar, ob gerade kein Medium ausgewählt ist, ein
   * neues Medium angelegt wird oder ein vorhandener Eintrag bearbeitet wird.
   */
  private void updateEditorStateLabel() {
    if (currentEntryId != null && currentEntryId > 0) {
      String display = currentDisplayText == null ? "" : currentDisplayText.trim();
      editorStateLabel.setText(
          display.isBlank()
              ? "Vorhandenes Medium bearbeiten"
              : "Vorhandenes Medium bearbeiten: " + display
      );
      return;
    }

    if (rightSelectedMediaType != null) {
      editorStateLabel.setText("Neues Medium anlegen: " + rightSelectedMediaType.name());
      return;
    }

    editorStateLabel.setText("Kein Medium ausgewählt");
  }

  /**
   * Übernimmt links einen ausgewählten Medientyp für die Suche.
   *
   * @param type ausgewählter Medientyp
   */
  private void applyLeftMediaTypeSelection(MediaTypeDefinition type) {
    leftSelectedMediaType = type;
    leftMediaTypeField.setText(type == null ? "" : type.name());
    leftMediaTypeMenu.hide();
    runSearch();
  }

  /**
   * Übernimmt rechts einen ausgewählten Medientyp für den Editor.
   *
   * @param type ausgewählter Medientyp
   */
  private void applyRightMediaTypeSelection(MediaTypeDefinition type) {
    rightSelectedMediaType = type;
    rightMediaTypeField.setText(type == null ? "" : type.name());
    rightMediaTypeMenu.hide();

    if (type == null) {
      shellHost.setCenter(null);
      updateEditorStateLabel();
      return;
    }

    shellHost.setCenter(shellBundle.shell().getRoot());

    btnAdd.setDisable(false);
    if (currentEntryId == null || currentEntryId <= 0) {
      btnEdit.setDisable(true);
      shellBundle.shell().showNewEditor(type.bibName());
      shellBundle.shell().setEditMode(true);
    }

    updateEditorStateLabel();
  }

  /**
   * Zeigt Vorschläge für Medientypen unterhalb eines Textfelds an.
   *
   * @param field Zielfeld
   * @param menu zugehöriges Menü
   * @param query aktueller Suchtext
   * @param consumer Callback bei Auswahl eines Vorschlags
   */
  private void showMediaTypeSuggestions(TextField field,
                                        ContextMenu menu,
                                        String query,
                                        Consumer<MediaTypeDefinition> consumer) {
    String normalized = query == null ? "" : query.trim().toLowerCase();

    List<MediaTypeDefinition> hits = allMediaTypes.stream()
                                         .filter(type -> normalized.isBlank()
                                                             || type.name().toLowerCase().contains(normalized)
                                                             || type.bibName().toLowerCase().contains(normalized))
                                         .toList();

    if (hits.isEmpty()) {
      menu.hide();
      return;
    }

    List<CustomMenuItem> items = hits.stream()
                                     .map(type -> {
                                       Label label = new Label(type.name());
                                       CustomMenuItem item = new CustomMenuItem(label, true);
                                       item.setOnAction(e -> consumer.accept(type));
                                       return item;
                                     })
                                     .toList();

    menu.getItems().setAll(items);

    if (!menu.isShowing()) {
      menu.show(field, Side.BOTTOM, 0, 0);
    }
  }

  /**
   * Prüft, ob der aktuell eingegebene Text bereits exakt dem selektierten
   * Medientyp entspricht.
   *
   * @param text aktueller Feldtext
   * @param selectedType aktuell selektierter Typ
   * @return {@code true}, wenn Text und Typ zusammenpassen
   */
  private boolean isResolvedMediaTypeText(String text, MediaTypeDefinition selectedType) {
    if (selectedType == null || text == null) {
      return false;
    }
    return text.trim().equalsIgnoreCase(selectedType.name())
               || text.trim().equalsIgnoreCase(selectedType.bibName());
  }

  /**
   * Zeigt einen Fehlerdialog an.
   *
   * @param header Überschrift
   * @param content Inhalt
   */
  private void showError(String header, String content) {
    Alert alert = new Alert(Alert.AlertType.ERROR);
    alert.initOwner(dialogStage);
    alert.setTitle("Medienverwaltung");
    alert.setHeaderText(header);
    alert.setContentText(content);
    alert.showAndWait();
  }
}