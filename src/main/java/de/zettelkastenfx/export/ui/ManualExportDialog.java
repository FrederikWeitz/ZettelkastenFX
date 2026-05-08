package de.zettelkastenfx.export.ui;

import de.zettelkastenfx.base.BaseIcon;
import de.zettelkastenfx.bibliography.api.BibliographyService;
import de.zettelkastenfx.bibliography.model.Author;
import de.zettelkastenfx.bibliography.ui.lookup.AuthorSuggestion;
import de.zettelkastenfx.bibliography.ui.lookup.TitleSuggestion;
import de.zettelkastenfx.bibliography.ui.support.BibliographyAuthorsView;
import de.zettelkastenfx.export.*;
import javafx.application.Platform;
import de.zettelkastenfx.persistence.NoteRepository;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Eigenes Fenster fuer den manuellen Zettelexport.
 */
public class ManualExportDialog {

  private final Stage owner;
  private final NoteRepository noteRepository;
  private final BibliographyService bibliographyService;
  private final NoteExportService exportService;
  private final MediaExportNoteSelector mediaSelector;
  private final NoteNumberSelectionParser numberParser = new NoteNumberSelectionParser();

  private final Stage stage = new Stage();
  private final Label countLabel = new Label();
  private final ComboBox<FormatOption> formatBox = new ComboBox<>();
  private final CheckBox allNotesBox = new CheckBox("alle Zettel");
  private final CheckBox mediaSelectionBox = new CheckBox("Medienauswahl");
  private final TextField notesPerFileField = new TextField();
  private final TextArea noteNumbersArea = new TextArea();
  private final VBox numberSelectionPane = new VBox(6);
  private final VBox mediaSelectionPane = new VBox(6);
  private final BooleanProperty mediaAuthorsEditOn = new SimpleBooleanProperty(true);
  private final BibliographyAuthorsView mediaAuthorsView;
  private final TextField mediaTitleField = new TextField();
  private final ContextMenu mediaTitleMenu = new ContextMenu();
  private final CheckBox includeNumberBox = new CheckBox("mit Zettelnummer");
  private final CheckBox includeTitleBox = new CheckBox("mit Überschrift");
  private final CheckBox includeKeywordsBox = new CheckBox("mit Stichwörtern");
  private final CheckBox includeBibliographyBox = new CheckBox("mit bibliografischer Angabe");
  private final RadioButton bibliographyIdentifyButton = new RadioButton("Kernattribute");
  private final RadioButton bibliographyNecessaryButton = new RadioButton("notwendige Attribute");
  private final RadioButton bibliographyAllButton = new RadioButton("alle Attribute");
  private final VBox bibliographyOptionsBox = new VBox(8);
  private final RadioButton bibliographyAfterEachButton = new RadioButton("hinter jedem Zettel");
  private final RadioButton bibliographyAtEndButton = new RadioButton("am Ende");
  private final RadioButton bibliographyAtLastEndButton = new RadioButton("nur letzte Datei");
  private final RadioButton bibliographySeparateFileButton = new RadioButton("Extradatei");
  private final TextField directoryField = new TextField();
  private final TextField fileNameField = new TextField("Zettelkasten-Export");
  private final Label statusLabel = new Label();
  private final StackPane selectionHost = new StackPane();

  private List<Integer> selectedNoteIds = List.of();
  private List<Integer> committedMediaNoteIds = List.of();
  private Integer selectedMediaEntryId;
  private boolean suppressMediaTitleCallbacks;

  /**
   * Erzeugt das Exportfenster.
   *
   * @param owner besitzendes Fenster
   * @param noteRepository Zettel-Repository
   * @param bibliographyService Bibliographie-Fassade
   */
  public ManualExportDialog(Stage owner,
                            NoteRepository noteRepository,
                            BibliographyService bibliographyService) {
    this.owner = owner;
    this.noteRepository = noteRepository;
    this.bibliographyService = bibliographyService;
    this.exportService = new DefaultNoteExportService(noteRepository, bibliographyService);
    this.mediaSelector = new MediaExportNoteSelector(noteRepository, bibliographyService);
    this.mediaAuthorsView = new BibliographyAuthorsView(
        "",
        mediaAuthorsEditOn,
        this::findMediaAuthorSuggestions,
        mediaType -> {
          selectedMediaEntryId = null;
          commitMediaSelection();
        },
        this::rebuildSelection,
        () -> false,
        (mediaType, entryId) -> selectedMediaEntryId = entryId,
        3
    );
    initializeStage();
    initializeDefaults();
    wireEvents();
    rebuildSelection();
  }

  /**
   * Zeigt das Exportfenster an.
   */
  public void show() {
    stage.show();
    stage.toFront();
  }

  /**
   * Initialisiert die Fensterstruktur.
   */
  private void initializeStage() {
    stage.setTitle("Export");
    stage.initOwner(owner);
    stage.initModality(Modality.NONE);

    VBox root = new VBox(12);
    root.setPadding(new Insets(14));
    root.setMinWidth(720);

    root.getChildren().addAll(
        countLabel,
        buildFormatAndSelectionRow(),
        selectionHost,
        buildExportConditionsPane(),
        buildTargetPane(),
        buildFooter()
    );

    stage.setScene(new Scene(root));
  }

  /**
   * Setzt die Startwerte des Fensters.
   */
  private void initializeDefaults() {
    formatBox.setItems(FXCollections.observableArrayList(
        new FormatOption("Word Dokument", ExportDocumentType.DOCX),
        new FormatOption("RTF", ExportDocumentType.RTF),
        new FormatOption("PDF", ExportDocumentType.PDF),
        new FormatOption("HTML", ExportDocumentType.HTML)
    ));
    formatBox.getSelectionModel().selectFirst();

    allNotesBox.setSelected(true);
    includeNumberBox.setSelected(true);
    includeTitleBox.setSelected(true);
    includeKeywordsBox.setSelected(false);
    includeBibliographyBox.setSelected(false);

    int count = Math.max(1, noteRepository.countNotes());
    notesPerFileField.setText(Integer.toString(count));
    notesPerFileField.setPrefColumnCount(6);

    directoryField.setText(defaultDownloadDirectory().toString());
    noteNumbersArea.setPromptText("1; 3-7; 12");
    noteNumbersArea.setPrefRowCount(4);
    mediaTitleField.setPromptText("Titel");
    mediaTitleMenu.setAutoHide(true);

    ToggleGroup bibliographyLevelGroup = new ToggleGroup();
    bibliographyIdentifyButton.setToggleGroup(bibliographyLevelGroup);
    bibliographyNecessaryButton.setToggleGroup(bibliographyLevelGroup);
    bibliographyAllButton.setToggleGroup(bibliographyLevelGroup);
    bibliographyIdentifyButton.setSelected(true);

    ToggleGroup bibliographyPlacementGroup = new ToggleGroup();
    bibliographyAfterEachButton.setToggleGroup(bibliographyPlacementGroup);
    bibliographyAtEndButton.setToggleGroup(bibliographyPlacementGroup);
    bibliographyAtLastEndButton.setToggleGroup(bibliographyPlacementGroup);
    bibliographySeparateFileButton.setToggleGroup(bibliographyPlacementGroup);
    bibliographyAfterEachButton.setSelected(true);
  }

  /**
   * Verdrahtet alle Interaktionen.
   */
  private void wireEvents() {
    ChangeListener<Object> rebuildListener = (obs, oldValue, newValue) -> rebuildSelection();
    allNotesBox.selectedProperty().addListener(rebuildListener);
    mediaSelectionBox.selectedProperty().addListener(rebuildListener);
    noteNumbersArea.textProperty().addListener(rebuildListener);
    notesPerFileField.textProperty().addListener(rebuildListener);
    includeBibliographyBox.selectedProperty().addListener(rebuildListener);
    formatBox.valueProperty().addListener(rebuildListener);

    noteNumbersArea.focusedProperty().addListener((obs, wasFocused, focused) -> {
      if (wasFocused && !focused) {
        validateNumberInput();
      }
    });
    mediaTitleField.textProperty().addListener((obs, oldValue, newValue) -> {
      if (!suppressMediaTitleCallbacks) {
        selectedMediaEntryId = null;
      }
      showTitleSuggestions();
    });
    mediaTitleField.setOnAction(event -> commitMediaSelection());
    mediaTitleField.setOnKeyPressed(event -> {
      if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.TAB) {
        commitMediaSelection();
      } else if (event.getCode() == KeyCode.ESCAPE) {
        mediaTitleMenu.hide();
      }
    });
    mediaTitleField.focusedProperty().addListener((obs, wasFocused, focused) -> {
      if (wasFocused && !focused) {
        mediaTitleMenu.hide();
        commitMediaSelection();
      }
    });
  }

  /**
   * Baut die zweite Zeile mit Format, Auswahlmodus und Split-Wert.
   *
   * @return Zeilenknoten
   */
  private HBox buildFormatAndSelectionRow() {
    HBox row = new HBox(12);
    row.setAlignment(Pos.CENTER_LEFT);
    Label splitLabel = new Label("Zettel pro Datei");
    row.getChildren().addAll(
        new Label("Dateiformat"),
        formatBox,
        allNotesBox,
        mediaSelectionBox,
        splitLabel,
        notesPerFileField
    );
    return row;
  }

  /**
   * Baut den Bereich fuer Exportbedingungen.
   *
   * @return Bedingungsbereich
   */
  private VBox buildExportConditionsPane() {
    HBox core = new HBox(12, includeNumberBox, includeTitleBox, includeKeywordsBox, includeBibliographyBox);
    core.setAlignment(Pos.CENTER_LEFT);

    bibliographyOptionsBox.getChildren().addAll(
        new HBox(12, bibliographyIdentifyButton, bibliographyNecessaryButton, bibliographyAllButton),
        new HBox(12, bibliographyAfterEachButton, bibliographyAtEndButton, bibliographyAtLastEndButton, bibliographySeparateFileButton)
    );

    VBox box = new VBox(8, core, bibliographyOptionsBox);
    bibliographyOptionsBox.visibleProperty().bind(includeBibliographyBox.selectedProperty());
    bibliographyOptionsBox.managedProperty().bind(includeBibliographyBox.selectedProperty());
    return box;
  }

  /**
   * Baut den Zielpfadbereich.
   *
   * @return Zielpfadbereich
   */
  private GridPane buildTargetPane() {
    GridPane grid = new GridPane();
    grid.setHgap(8);
    grid.setVgap(8);

    Button chooseDirectoryButton = new Button("...");
    chooseDirectoryButton.setOnAction(event -> chooseDirectory());
    HBox directoryRow = new HBox(6, directoryField, chooseDirectoryButton);
    HBox.setHgrow(directoryField, Priority.ALWAYS);

    grid.add(new Label("Ordner"), 0, 0);
    grid.add(directoryRow, 1, 0);
    grid.add(new Label("Dateiname"), 0, 1);
    grid.add(fileNameField, 1, 1);
    ColumnConstraints labelColumn = new ColumnConstraints();
    ColumnConstraints valueColumn = new ColumnConstraints();
    valueColumn.setHgrow(Priority.ALWAYS);
    grid.getColumnConstraints().addAll(labelColumn, valueColumn);
    return grid;
  }

  /**
   * Baut die untere Buttonzeile.
   *
   * @return Footer-Knoten
   */
  private HBox buildFooter() {
    Button exportButton = BaseIcon.DISKETTE.button("Exportieren");
    exportButton.setText("Exportieren");
    exportButton.setContentDisplay(ContentDisplay.LEFT);
    exportButton.setOnAction(event -> export());

    HBox footer = new HBox(12, exportButton, statusLabel);
    footer.setAlignment(Pos.CENTER_LEFT);
    return footer;
  }

  /**
   * Baut die sichtbare Auswahlzeile anhand des Modus neu auf.
   */
  private void rebuildSelection() {
    mediaSelectionBox.setDisable(allNotesBox.isSelected());

    numberSelectionPane.getChildren().setAll(new Label("Zettelnummern"), noteNumbersArea);
    mediaSelectionPane.getChildren().setAll(
        new Label("Autoren"),
        mediaAuthorsView,
        new Label("Titel"),
        mediaTitleField
    );

    selectionHost.getChildren().clear();
    if (!allNotesBox.isSelected()) {
      selectionHost.getChildren().add(mediaSelectionBox.isSelected() ? mediaSelectionPane : numberSelectionPane);
    }

    selectedNoteIds = computeSelectedNoteIds();
    int split = parseSplitCount();
    bibliographyAtLastEndButton.setVisible(selectedNoteIds.size() > split && split > 0);
    bibliographyAtLastEndButton.setManaged(bibliographyAtLastEndButton.isVisible());
    if (!bibliographyAtLastEndButton.isVisible() && bibliographyAtLastEndButton.isSelected()) {
      bibliographyAtEndButton.setSelected(true);
    }
    countLabel.setText("Zu exportierende Zettel: " + selectedNoteIds.size());
    resizeToContent();
  }

  /**
   * Ermittelt die aktuelle Zettelauswahl.
   *
   * @return Zettelnummern
   */
  private List<Integer> computeSelectedNoteIds() {
    statusLabel.setText("");
    if (allNotesBox.isSelected()) {
      return noteRepository.loadAllNoteIdsForExport();
    }
    if (mediaSelectionBox.isSelected()) {
      if (!hasMediaFilter()) {
        return noteRepository.loadAllNoteIdsForExport();
      }
      return committedMediaNoteIds;
    }
    try {
      return numberParser.parse(noteNumbersArea.getText());
    } catch (IllegalArgumentException ex) {
      statusLabel.setText(ex.getMessage());
      return List.of();
    }
  }

  /**
   * Prueft das Nummernfeld explizit beim Verlassen.
   */
  private void validateNumberInput() {
    try {
      numberParser.parse(noteNumbersArea.getText());
      statusLabel.setText("");
    } catch (IllegalArgumentException ex) {
      statusLabel.setText(ex.getMessage());
    }
  }

  /**
   * Parst die Autorenauswahl des Medienmodus.
   *
   * @return Autorentexte
   */
  private List<Author> parseAuthorQueries() {
    List<Author> authors = new ArrayList<>();
    for (Author author : currentMediaAuthors()) {
      String lastName = author.getLastName() == null ? "" : author.getLastName().trim();
      String firstName = author.getFirstName() == null ? "" : author.getFirstName().trim();
      if (!lastName.isBlank() || !firstName.isBlank()) {
        Author copy = new Author();
        copy.setLastName(lastName);
        copy.setFirstName(firstName);
        authors.add(copy);
      }
    }
    return authors;
  }

  /**
   * Commitet die Medienauswahl nach vollstaendigem Feldwert.
   */
  private void commitMediaSelection() {
    resolveSelectedMediaEntry();
    committedMediaNoteIds = mediaSelector.selectNoteIds(
        parseAuthorQueries(),
        mediaTitleField.getText(),
        selectedMediaEntryId
    );
    rebuildSelection();
  }

  /**
   * Prueft, ob im Medienfilter ein Autor oder Titel eingetragen ist.
   *
   * @return {@code true}, wenn ein Filterwert vorhanden ist
   */
  private boolean hasMediaFilter() {
    return !currentMediaAuthors().isEmpty()
        || (mediaTitleField.getText() != null && !mediaTitleField.getText().trim().isBlank());
  }

  /**
   * Liest die aktuell eingetragenen Medienautoren aus.
   *
   * @return Autoren ohne leere Zeilen
   */
  private List<Author> currentMediaAuthors() {
    return mediaAuthorsView.toAuthors();
  }

  /**
   * Ermittelt eine eindeutige Medien-Entry-ID aus den aktuellen Eingabefeldern.
   */
  private void resolveSelectedMediaEntry() {
    if (selectedMediaEntryId != null && selectedMediaEntryId > 0) {
      return;
    }

    List<Author> authors = currentMediaAuthors();
    String title = mediaTitleField.getText() == null ? "" : mediaTitleField.getText().trim();
    if (title.isBlank()) {
      selectedMediaEntryId = null;
      return;
    }

    if (!title.isBlank()) {
      TitleSuggestion exact = bibliographyService.resolveExactTitle("", title, authors);
      selectedMediaEntryId = exact == null ? null : exact.entryId();
      return;
    }
  }

  /**
   * Zeigt Titelvorschlaege im Medienmodus.
   */
  private void showTitleSuggestions() {
    if (!mediaSelectionBox.isSelected() || allNotesBox.isSelected()) {
      mediaTitleMenu.hide();
      return;
    }
    String query = mediaTitleField.getText() == null ? "" : mediaTitleField.getText().trim();
    if (query.isBlank()) {
      mediaTitleMenu.hide();
      return;
    }
    List<CustomMenuItem> items = bibliographyService.findTitleSuggestions("", query, currentMediaAuthors()).stream()
                                     .map(this::buildTitleSuggestionItem)
                                     .toList();
    if (items.isEmpty()) {
      mediaTitleMenu.hide();
      return;
    }
    mediaTitleMenu.getItems().setAll(items);
    if (mediaTitleField.getScene() != null && mediaTitleField.getScene().getWindow() != null) {
      mediaTitleMenu.show(mediaTitleField, Side.BOTTOM, 0, 0);
    }
  }

  /**
   * Baut einen Menueintrag fuer einen Titelvorschlag.
   *
   * @param suggestion Titelvorschlag
   * @return Menueintrag
   */
  private CustomMenuItem buildTitleSuggestionItem(TitleSuggestion suggestion) {
    Label label = new Label(suggestion.displayText());
    CustomMenuItem item = new CustomMenuItem(label, true);
    item.setOnAction(event -> {
      selectedMediaEntryId = suggestion.entryId();
      suppressMediaTitleCallbacks = true;
      try {
        mediaTitleField.setText(suggestion.displayText());
      } finally {
        suppressMediaTitleCallbacks = false;
      }
      if (currentMediaAuthors().isEmpty() && suggestion.authors() != null && !suggestion.authors().isEmpty()) {
        mediaAuthorsView.fromAuthors(suggestion.authors());
      }
      commitMediaSelection();
    });
    return item;
  }

  /**
   * Fuehrt den Export nach Validierung und Ueberschreibabfrage aus.
   */
  private void export() {
    rebuildSelection();
    if (selectedNoteIds.isEmpty()) {
      statusLabel.setText("Keine Zettel ausgewählt.");
      return;
    }

    Path target = targetPath();
    List<Path> plannedFiles = plannedOutputFiles(target, selectedNoteIds.size(), parseSplitCount(), selectedType());
    if (!confirmOverwrite(plannedFiles)) {
      return;
    }

    try {
      ExportResult result = exportService.export(new ExportRequest(
          selectedNoteIds,
          selectedType(),
          buildSelection(),
          target,
          parseSplitCount()
      ));
      statusLabel.setText("Export erfolgreich: " + result.writtenFiles().size() + " Datei(en).");
    } catch (Exception ex) {
      statusLabel.setText("Export fehlgeschlagen: " + ex.getMessage());
    }
  }

  /**
   * Baut die Exportauswahl aus den UI-Werten.
   *
   * @return Exportauswahl
   */
  private ExportSelection buildSelection() {
    BibliographyExportLevel level = BibliographyExportLevel.NONE;
    if (includeBibliographyBox.isSelected()) {
      if (bibliographyIdentifyButton.isSelected()) {
        level = BibliographyExportLevel.IDENTIFY;
      } else if (bibliographyNecessaryButton.isSelected()) {
        level = BibliographyExportLevel.IDENTIFY_AND_NECESSARY;
      } else if (bibliographyAllButton.isSelected()) {
        level = BibliographyExportLevel.ALL;
      } else {
        level = BibliographyExportLevel.IDENTIFY;
      }
    }

    return new ExportSelection(
        true,
        includeTitleBox.isSelected(),
        includeNumberBox.isSelected(),
        includeKeywordsBox.isSelected(),
        level,
        selectedBibliographyPlacement(),
        true
    );
  }

  /**
   * Ermittelt die gewaehlte Bibliographieposition.
   *
   * @return Bibliographieposition
   */
  private BibliographyPlacement selectedBibliographyPlacement() {
    if (bibliographyAtEndButton.isSelected()) {
      return BibliographyPlacement.END_OF_DOCUMENT;
    }
    if (bibliographyAtLastEndButton.isSelected()) {
      return BibliographyPlacement.END_OF_LAST_DOCUMENT;
    }
    if (bibliographySeparateFileButton.isSelected()) {
      return BibliographyPlacement.SEPARATE_FILE;
    }
    return BibliographyPlacement.AFTER_EACH_NOTE;
  }

  /**
   * Oeffnet die Ordnerauswahl.
   */
  private void chooseDirectory() {
    DirectoryChooser chooser = new DirectoryChooser();
    chooser.setTitle("Exportordner auswählen");
    Path current = Path.of(directoryField.getText());
    if (Files.isDirectory(current)) {
      chooser.setInitialDirectory(current.toFile());
    }
    java.io.File selected = chooser.showDialog(stage);
    if (selected != null) {
      directoryField.setText(selected.toPath().toString());
    }
  }

  /**
   * Fragt das Ueberschreiben bestehender Dateien ab.
   *
   * @param plannedFiles geplante Dateien
   * @return {@code true}, wenn fortgesetzt werden soll
   */
  private boolean confirmOverwrite(List<Path> plannedFiles) {
    List<Path> existing = plannedFiles.stream().filter(Files::exists).toList();
    if (existing.isEmpty()) {
      return true;
    }

    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
    alert.initOwner(stage);
    alert.setTitle("Dateien überschreiben");
    alert.setHeaderText("Es gibt bereits Exportdateien.");
    alert.setContentText("Sollen " + existing.size() + " vorhandene Datei(en) überschrieben werden?");
    return alert.showAndWait().filter(button -> button == ButtonType.OK).isPresent();
  }

  /**
   * Berechnet die voraussichtlich erzeugten Dateien.
   *
   * @param target Basispfad
   * @param noteCount Anzahl der Zettel
   * @param split Zettel pro Datei
   * @param type Dokumenttyp
   * @return geplante Dateien
   */
  private List<Path> plannedOutputFiles(Path target, int noteCount, int split, ExportDocumentType type) {
    Path normalized = ensureExtension(target, type.extension());
    int chunks = split <= 0 ? 1 : Math.max(1, (int) Math.ceil(noteCount / (double) split));
    LinkedHashSet<Path> files = new LinkedHashSet<>();
    for (int i = 1; i <= chunks; i++) {
      files.add(chunks == 1 ? normalized : numberedPath(normalized, i, type.extension()));
    }
    if (includeBibliographyBox.isSelected() && selectedBibliographyPlacement() == BibliographyPlacement.SEPARATE_FILE) {
      files.add(suffixedPath(normalized, "_Bibliographie", type.extension()));
    }
    return new ArrayList<>(files);
  }

  /**
   * Liefert den nummerierten Pfad fuer mehrteilige Exporte.
   *
   * @param path Basispfad
   * @param index laufende Nummer
   * @param extension Dateiendung
   * @return nummerierter Pfad
   */
  private Path numberedPath(Path path, int index, String extension) {
    return suffixedPath(path, "_" + index, extension);
  }

  /**
   * Liefert einen Pfad mit Suffix vor der Dateiendung.
   *
   * @param path Basispfad
   * @param suffix Suffix
   * @param extension Dateiendung
   * @return Pfad mit Suffix
   */
  private Path suffixedPath(Path path, String suffix, String extension) {
    String fileName = path.getFileName().toString();
    String base = fileName.substring(0, fileName.length() - extension.length());
    Path renamed = Path.of(base + suffix + extension);
    return path.getParent() == null ? renamed : path.getParent().resolve(renamed);
  }

  /**
   * Baut den Zielpfad aus Ordner, Dateiname und Format.
   *
   * @return Zielpfad
   */
  private Path targetPath() {
    String fileName = fileNameField.getText() == null || fileNameField.getText().isBlank()
                          ? "Zettelkasten-Export"
                          : fileNameField.getText().trim();
    fileName = fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
    return Path.of(directoryField.getText()).resolve(fileName);
  }

  /**
   * Stellt die Dateiendung sicher.
   *
   * @param path Pfad
   * @param extension Dateiendung
   * @return Pfad mit Endung
   */
  private Path ensureExtension(Path path, String extension) {
    String fileName = path.getFileName().toString();
    if (fileName.toLowerCase(Locale.ROOT).endsWith(extension)) {
      return path;
    }
    return path.getParent() == null
               ? Path.of(fileName + extension)
               : path.getParent().resolve(fileName + extension);
  }

  /**
   * Parst die Anzahl der Zettel pro Datei.
   *
   * @return Split-Anzahl
   */
  private int parseSplitCount() {
    try {
      return Math.max(1, Integer.parseInt(notesPerFileField.getText().trim()));
    } catch (Exception ex) {
      return Math.max(1, noteRepository.countNotes());
    }
  }

  /**
   * Liefert den gewaehlten Dokumenttyp.
   *
   * @return Dokumenttyp
   */
  private ExportDocumentType selectedType() {
    FormatOption option = formatBox.getValue();
    return option == null ? ExportDocumentType.DOCX : option.type();
  }

  /**
   * Ermittelt den Download-Ordner.
   *
   * @return Download-Ordner oder Benutzerordner
   */
  private Path defaultDownloadDirectory() {
    Path downloads = Path.of(System.getProperty("user.home"), "Downloads");
    return Files.isDirectory(downloads) ? downloads : Path.of(System.getProperty("user.home"));
  }

  /**
   * Liefert Autorvorschlaege fuer die Autorenkomponente des Bibliographie-Popups.
   *
   * @param row bearbeitete Autorenzeile
   * @return passende Autorvorschlaege
   */
  private List<AuthorSuggestion> findMediaAuthorSuggestions(BibliographyAuthorsView.AuthorRow row) {
    if (row == null || row.getLastName().isBlank()) {
      return List.of();
    }
    return bibliographyService.findAuthorSuggestions("", row.getLastName(), selectedMediaEntryId);
  }

  /**
   * Passt die Fensterhoehe an neue sichtbare Inhalte an.
   */
  private void resizeToContent() {
    Platform.runLater(stage::sizeToScene);
  }

  /**
   * Anzeigeoption fuer Dokumentformate.
   *
   * @param label Anzeigename
   * @param type Dokumenttyp
   */
  private record FormatOption(String label, ExportDocumentType type) {
    @Override
    public String toString() {
      return label;
    }
  }

}
