package de.zettelkastenfx.notes.controller;

import de.zettelkastenfx.base.BaseIcon;
import de.zettelkastenfx.notes.bottom.BottomToolbar;
import de.zettelkastenfx.notes.editor.NoteEditorPane;
import de.zettelkastenfx.notes.keywords.KeywordsTablePane;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.util.StringConverter;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

public class ZettelWindowView {

  @Getter private final BorderPane root;

  @Getter private final VBox topArea;
  @Getter private final MenuBar menuBar;
  @Getter private final Menu menuFile;
  @Getter private final Menu menuEdit;
  @Getter private final Menu menuView;

  @Getter private final ToolBar topToolBar;
  @Getter private final Button toolButtonPageAdd;
  @Getter private final Button toolButtonPageCopy;
  @Getter private final Button toolButtonPageWithBibliography;
  @Getter private final Button toolButtonLinkEdit;

  // Button zum Aufklappen der Service-Area
  @Getter private final Button toolButtonToggleServiceArea;

  @Getter private final MenuButton toolMenuFollowingPages;
  @Getter private final MenuItem miFollowing;
  @Getter private final MenuItem miIdea;
  @Getter private final MenuItem miQuestion;
  @Getter private final MenuItem miCritique;
  @Getter private final MenuItem miTask;

  @Getter private final BottomToolbar bottomToolbar;

  @Getter private final SplitPane workAreaSplitPane;

  @Getter private final ToolBar serviceAreaToolBar;
  @Getter private final StackPane serviceAreaContentHost;

  // Toolbar der Service-Area
  @Getter private final ToggleButton serviceAreaMagnifyLinkButton;
  @Getter private final ToggleButton serviceAreaKeyButton;
  @Getter private final ToggleButton serviceAreaBookButton;
  @Getter private final ToggleButton serviceAreaListButton;
  @Getter private final ToggleButton serviceAreaClusterButton;

  // MAGNIFY_LINK-Komponente
  @Getter private final BorderPane magnifyLinkPane;
  @Getter private final ToolBar magnifyLinkToolBar;
  @Getter private final TextField magnifyLinkNoteIdField;
  @Getter private final Slider magnifyLinkIncomingDepthSlider;
  @Getter private final Slider magnifyLinkOutgoingDepthSlider;
  @Getter private final ToggleButton magnifyLinkDirectedTraversalButton;

  @Getter private final ToggleButton magnifyLinkSerieButton;
  @Getter private final ToggleButton magnifyLinkIdeaButton;
  @Getter private final ToggleButton magnifyLinkQuestionButton;
  @Getter private final ToggleButton magnifyLinkCritiqueButton;
  @Getter private final ToggleButton magnifyLinkTaskButton;

  @Getter private final TextField magnifyLinkFilterField;
  @Getter private final Label magnifyLinkStatusLabel;
  @Getter private final VBox magnifyLinkListBox;

  // KEY-Komponente
  @Getter private final BorderPane keyPane;
  @Getter private final ToolBar keyToolBar;
  @Getter private final TextField keyFilterField;
  @Getter private final TextField keyKeepField;
  @Getter private final ComboBox<String> keyReplaceField;
  @Getter private final Button keyReplaceButton;
  @Getter private final Label keyStatusLabel;
  @Getter private final TableView<KeyTableRow> keyTable;
  @Getter private final TableColumn<KeyTableRow, String> keyKeywordColumn;
  @Getter private final TableColumn<KeyTableRow, Number> keyCountColumn;
  @Getter private final ToggleButton keyShowHeadersToggleButton;
  @Getter private final SplitPane keySplitPane;
  @Getter private final BorderPane keyHeaderPane;
  @Getter private final TableView<KeyHeaderRow> keyHeaderTable;
  @Getter private final TableColumn<KeyHeaderRow, Number> keyHeaderIdColumn;
  @Getter private final TableColumn<KeyHeaderRow, String> keyHeaderTitleColumn;

  private Integer pendingKeyEditRowId;
  private String pendingKeyEditText;
  private double keyHeaderDividerPosition = 0.72;

  public enum KeyStatusType {
    INFO,
    SUCCESS,
    WARNING,
    ERROR
  }

  private String keyInventoryStatusText = "";
  private String keyActionStatusText = "";
  private KeyStatusType keyActionStatusType = KeyStatusType.INFO;

  // LIST-Komponente
  @Getter private final BorderPane listPane;
  @Getter private final ToolBar listToolBar;
  @Getter private final Button listPageKeyButton;
  @Getter private final Slider listDepthSlider;
  @Getter private final TextField listFilterField;
  @Getter private final ToggleButton listFullTextToggleButton;
  @Getter private final StackPane listContentHost;
  @Getter private final ScrollPane listPageKeyScrollPane;
  @Getter private final VBox listPageKeyBox;
  @Getter private final TableView<ListFilterRow> listFilterTable;
  @Getter private final TableColumn<ListFilterRow, Number> listFilterIdColumn;
  @Getter private final TableColumn<ListFilterRow, String> listFilterTitleColumn;
  @Getter private final TableColumn<ListFilterRow, Integer> listFilterLinkCountColumn;

  public enum ListMode {
    PAGE_KEY,
    FILTER
  }

  @Getter private ListMode activeListMode = ListMode.PAGE_KEY;

  @Getter
  public static final class ListFilterRow {
    private final int noteId;
    private final String title;
    private final Integer linkCount;
    private final boolean bodyMatchOnly;

    /**
     * Erzeugt eine Tabellenzeile für die LIST-Filteransicht.
     *
     * @param noteId Zettelnummer
     * @param title Überschrift
     * @param linkCount Anzahl verknüpfter anderer Zettel; {@code null} bedeutet kein Schlagwort
     * @param bodyMatchOnly {@code true}, wenn der Treffer ausschließlich über den Volltext zustande kam
     */
    public ListFilterRow(int noteId, String title, Integer linkCount, boolean bodyMatchOnly) {
      this.noteId = noteId;
      this.title = title == null ? "" : title;
      this.linkCount = linkCount;
      this.bodyMatchOnly = bodyMatchOnly;
    }
  }

  // Zustand für die Service-Area
  public enum ServiceAreaMode {
    MAGNIFY_LINK,
    KEY,
    BOOK,
    LIST,
    PERFORMANCE_ANALYSIS
  }

  @FunctionalInterface
  public interface KeyKeywordEditCommitHandler {
    /**
     * Reagiert auf das Abschließen einer Inline-Bearbeitung in der Schlagwortspalte.
     *
     * @param row bearbeitete Tabellenzeile
     * @param newKeyword neu eingegebener Wert
     * @return {@code true}, wenn die Änderung übernommen wurde; sonst {@code false}
     */
    boolean handleCommit(KeyTableRow row, String newKeyword);
  }

  @Getter
  public static final class KeyTableRow {
    private final int keywordId;
    private final String keyword;
    private final int count;

    /**
     * Erzeugt eine Tabellenzeile für das KEY-Fenster.
     *
     * @param keywordId ID des Schlagworts
     * @param keyword Schlagworttext
     * @param count Häufigkeit
     */
    public KeyTableRow(int keywordId, String keyword, int count) {
      this.keywordId = keywordId;
      this.keyword = keyword;
      this.count = count;
    }
  }

  @Getter
  public static final class KeyHeaderRow {
    private final int noteId;
    private final String title;

    /**
     * Erzeugt eine Tabellenzeile für die untere Zettelkopf-Tabelle.
     *
     * @param noteId Zettelnummer
     * @param title Zettelüberschrift
     */
    public KeyHeaderRow(int noteId, String title) {
      this.noteId = noteId;
      this.title = title == null ? "" : title;
    }
  }

  /**
   * Gespeicherter primärer Sortierzustand der KEY-Tabelle.
   *
   * @param columnId ID der sortierten Spalte
   * @param sortType Sortierrichtung
   */
  public record KeyTableSortState(String columnId, TableColumn.SortType sortType) {
  }

  /**
   * -- SETTER --
   *  Setzt den Commit-Handler für Inline-Änderungen in der Schlagwortspalte.
   */
  @Setter private KeyKeywordEditCommitHandler keyKeywordEditCommitHandler;

  @Getter private final NoteEditorPane noteEditorPane;
  @Getter private final KeywordsTablePane keywordsTablePane;

  // Service-Area und Zustandsvariablen dazu
  @Getter private final BorderPane serviceArea;

  /**
   * -- GETTER --
   *  Prüft, ob der rechte Arbeitsbereich aktuell eingeklappt ist.
   */
  @Getter private boolean serviceAreaCollapsed;

  /**
   * -- GETTER --
   *  Liefert den aktuell aktiven Modus der rechten Seitenleiste.
   */
  @Getter private ServiceAreaMode activeServiceAreaMode;
  private double[] expandedDividerPositions = new double[] {0.33, 0.66};

  public ZettelWindowView() {
    root = new BorderPane();
    root.getStyleClass().add("zettel-root");

    /* Top: klassische Menüleiste + Toolbar direkt darunter */
    topArea = new VBox();
    topArea.getStyleClass().add("zettel-top-area");

    menuBar = new MenuBar();
    menuBar.getStyleClass().add("zettel-menu-bar");

    menuFile = new Menu("Datei");
    menuEdit = new Menu("Bearbeiten");
    menuView = new Menu("Ansicht");

    menuFile.getItems().addAll(
        new MenuItem("Neu"),
        new SeparatorMenuItem(),
        new MenuItem("Schließen")
    );

    menuEdit.getItems().addAll(
        new MenuItem("Rückgängig"),
        new MenuItem("Wiederholen")
    );

    menuView.getItems().addAll(
        new MenuItem("Links/Mitte/Rechts"),
        new MenuItem("Layout zurücksetzen")
    );

    menuBar.getMenus().addAll(menuFile, menuEdit, menuView);

    topToolBar = new ToolBar();
    topToolBar.getStyleClass().add("zettel-toolbar");

    toolButtonPageAdd = BaseIcon.PAGE_ADD.button();
    toolButtonPageCopy = BaseIcon.PAGE_COPY.button();
    toolButtonPageWithBibliography = BaseIcon.PAGE_RED.button();
    toolButtonLinkEdit = BaseIcon.LINK_EDIT.button();

    toolButtonToggleServiceArea = BaseIcon.LAYOUTS_JOIN.button("Service-Fenster ausblenden");

    toolMenuFollowingPages = new MenuButton();
    toolMenuFollowingPages.setGraphic(BaseIcon.PAGE_LINK.imageView());
    toolMenuFollowingPages.getStyleClass().add("icon-button"); // falls du den Look übernehmen willst
    toolMenuFollowingPages.setFocusTraversable(false);
    toolMenuFollowingPages.setTooltip(new Tooltip("Folgezettel"));

    miFollowing = new MenuItem("Folgezettel", BaseIcon.PAGE_LINK.imageView());
    miIdea      = new MenuItem("Idee",       BaseIcon.PAGE_BULB_ON.imageView());
    miQuestion  = new MenuItem("Frage",      BaseIcon.QUESTION.imageView());
    miCritique  = new MenuItem("Kritik",     BaseIcon.PAGE_LIGHTNING.imageView());
    miTask      = new MenuItem("Aufgabe",    BaseIcon.PAGE_EDIT.imageView());

    toolMenuFollowingPages.getItems().addAll(miFollowing, miIdea, miQuestion, miCritique, miTask);

    // Spacer für die Toolbar
    Region toolbarSpacer = new Region();
    HBox.setHgrow(toolbarSpacer, Priority.ALWAYS);

    topToolBar.getItems().addAll(
        toolButtonPageAdd,
        toolButtonPageCopy,
        toolButtonPageWithBibliography,
        toolMenuFollowingPages,
        toolButtonLinkEdit,
        toolbarSpacer,
        toolButtonToggleServiceArea
    );

    topArea.getChildren().addAll(menuBar, topToolBar);
    root.setTop(topArea);

    /* Bottom: leere Werkzeugleiste (wird später befüllt) */
    bottomToolbar = new BottomToolbar();
    root.setBottom(bottomToolbar.getRoot());
    // Arbeitsbereiche: links, mitte, rechts (verschiebbar)
    workAreaSplitPane = new SplitPane();
    workAreaSplitPane.setOrientation(Orientation.HORIZONTAL);
    workAreaSplitPane.getStyleClass().add("zettel-work-split");

    noteEditorPane = new NoteEditorPane();
    noteEditorPane.getStyleClass().add("zettel-work-pane");

    keywordsTablePane = new KeywordsTablePane();
    keywordsTablePane.getStyleClass().add("zettel-work-pane");

    serviceArea = new BorderPane();
    serviceArea.getStyleClass().addAll("zettel-work-pane", "zettel-right-area");

    serviceAreaToolBar = new ToolBar();
    serviceAreaToolBar.getStyleClass().add("zettel-right-toolbar");

    serviceAreaMagnifyLinkButton = createServiceAreaToggleButton(BaseIcon.MAGNIFY_LINK, "Verweise");
    serviceAreaKeyButton = createServiceAreaToggleButton(BaseIcon.KEY, "Schlagwörter");
    serviceAreaBookButton = createServiceAreaToggleButton(BaseIcon.BOOK, "Literatur");
    serviceAreaListButton = createServiceAreaToggleButton(BaseIcon.LIST, "Überschriften");
    serviceAreaClusterButton = createServiceAreaToggleButton(BaseIcon.PERFORMANCE_ANALYSIS, "Cluster");

    serviceAreaToolBar.getItems().addAll(
        serviceAreaMagnifyLinkButton,
        serviceAreaKeyButton,
        serviceAreaBookButton,
        serviceAreaListButton,
        serviceAreaClusterButton
    );

    serviceAreaContentHost = new StackPane();
    serviceAreaContentHost.getStyleClass().add("zettel-right-content-host");
    serviceAreaContentHost.setPadding(new Insets(10));

    magnifyLinkPane = new BorderPane();
    magnifyLinkPane.getStyleClass().add("zettel-magnify-link-pane");

    magnifyLinkToolBar = new ToolBar();
    magnifyLinkToolBar.getStyleClass().add("zettel-magnify-link-toolbar");

    magnifyLinkNoteIdField = new TextField();
    magnifyLinkNoteIdField.getStyleClass().add("zettel-magnify-link-note-id-field");
    magnifyLinkNoteIdField.setPromptText("Zettel");
    magnifyLinkNoteIdField.setPrefColumnCount(7);
    magnifyLinkNoteIdField.setPrefWidth(90);
    magnifyLinkNoteIdField.setTextFormatter(new TextFormatter<String>(change -> {
      String next = change.getControlNewText();
      return next.matches("\\d*") ? change : null;
    }));

    magnifyLinkIncomingDepthSlider = createDepthSlider();
    magnifyLinkOutgoingDepthSlider = createDepthSlider();
    magnifyLinkDirectedTraversalButton =
        createMagnifyLinkTypeToggleButton(BaseIcon.ARROW_INOUT, "gerichtete Verweise");
    magnifyLinkDirectedTraversalButton.getStyleClass().add("zettel-magnify-link-direction-toggle");

    magnifyLinkSerieButton = createMagnifyLinkTypeToggleButton(BaseIcon.PAGE_LINK, "Folgezettel");
    magnifyLinkIdeaButton = createMagnifyLinkTypeToggleButton(BaseIcon.PAGE_BULB_ON, "Idee");
    magnifyLinkQuestionButton = createMagnifyLinkTypeToggleButton(BaseIcon.QUESTION, "Frage");
    magnifyLinkCritiqueButton = createMagnifyLinkTypeToggleButton(BaseIcon.PAGE_LIGHTNING, "Kritik");
    magnifyLinkTaskButton = createMagnifyLinkTypeToggleButton(BaseIcon.PAGE_EDIT, "Aufgabe");

    magnifyLinkFilterField = new TextField();
    magnifyLinkFilterField.getStyleClass().add("zettel-magnify-link-filter-field");
    magnifyLinkFilterField.setPromptText("Titel filtern");
    magnifyLinkFilterField.setPrefWidth(150);

    Label lblIncoming = new Label("Von:");
    lblIncoming.getStyleClass().add("zettel-magnify-link-depth-label");
    Label lblOutgoing = new Label("Zu:");
    lblOutgoing.getStyleClass().add("zettel-magnify-link-depth-label");

    Region magnifySpacer = new Region();
    HBox.setHgrow(magnifySpacer, Priority.ALWAYS);

    magnifyLinkToolBar.getItems().addAll(
        magnifyLinkNoteIdField,
        new Separator(Orientation.VERTICAL),
        lblIncoming,
        magnifyLinkIncomingDepthSlider,
        lblOutgoing,
        magnifyLinkOutgoingDepthSlider,
        magnifyLinkDirectedTraversalButton,
        new Separator(Orientation.VERTICAL),
        magnifyLinkSerieButton,
        magnifyLinkIdeaButton,
        magnifyLinkQuestionButton,
        magnifyLinkCritiqueButton,
        magnifyLinkTaskButton,
        magnifySpacer,
        magnifyLinkFilterField
    );

    magnifyLinkStatusLabel = new Label();
    magnifyLinkStatusLabel.getStyleClass().add("zettel-magnify-link-status");
    magnifyLinkStatusLabel.setWrapText(false);

    magnifyLinkListBox = new VBox(4);
    magnifyLinkListBox.getStyleClass().add("zettel-magnify-link-list");
    magnifyLinkListBox.setFillWidth(true);

    ScrollPane magnifyScrollPane = new ScrollPane(magnifyLinkListBox);
    magnifyScrollPane.setFitToWidth(true);
    magnifyScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    magnifyScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    magnifyScrollPane.getStyleClass().add("zettel-magnify-link-scroll");

    VBox magnifyCenter = new VBox(8, magnifyLinkStatusLabel, magnifyScrollPane);
    magnifyCenter.getStyleClass().add("zettel-magnify-link-content");
    VBox.setVgrow(magnifyScrollPane, Priority.ALWAYS);
    magnifyCenter.setFillWidth(true);

    magnifyLinkPane.setTop(magnifyLinkToolBar);
    magnifyLinkPane.setCenter(magnifyCenter);

    selectAllMagnifyLinkTypeButtons();

    keyPane = new BorderPane();
    keyPane.getStyleClass().add("zettel-key-pane");

    keyToolBar = new ToolBar();
    keyToolBar.getStyleClass().add("zettel-key-toolbar");

    keyShowHeadersToggleButton = BaseIcon.PAGE_MAGNIFY.toggleButton("Zettelüberschriften anzeigen");
    keyShowHeadersToggleButton.getStyleClass().add("zettel-key-show-headers-toggle");
    keyShowHeadersToggleButton.setFocusTraversable(false);

    keyFilterField = new TextField();
    keyFilterField.getStyleClass().add("zettel-key-filter-field");
    keyFilterField.setPromptText("Schlagwörter suchen");
    keyFilterField.setPrefWidth(170);

    keyKeepField = new TextField();
    keyKeepField.getStyleClass().add("zettel-key-keep-field");
    keyKeepField.setPromptText("Beibehalten");

    keyReplaceField = new ComboBox<>();
    keyReplaceField.getStyleClass().add("zettel-key-replace-field");
    keyReplaceField.setEditable(true);
    keyReplaceField.setPromptText("Ersetzen");
    keyReplaceField.setMaxWidth(Double.MAX_VALUE);
    keyReplaceField.setConverter(new StringConverter<>() {
      @Override
      public String toString(String object) {
        return object == null ? "" : object;
      }

      @Override
      public String fromString(String string) {
        return string == null ? "" : string;
      }
    });

    keyReplaceButton = BaseIcon.BULLET_GO.button("Ersetzen");
    keyReplaceButton.getStyleClass().add("zettel-key-replace-button");

    Region keySpacer = new Region();
    HBox.setHgrow(keySpacer, Priority.ALWAYS);

    keyToolBar.getItems().addAll(
        keyShowHeadersToggleButton,
        keyFilterField,
        new Separator(Orientation.VERTICAL),
        keyKeepField,
        keyReplaceButton,
        keyReplaceField
    );

    keyStatusLabel = new Label();
    keyStatusLabel.getStyleClass().add("zettel-key-status");

    keyTable = new TableView<>();
    keyTable.getStyleClass().add("zettel-key-table");
    keyTable.setEditable(true);
    keyTable.getSelectionModel().setCellSelectionEnabled(true);
    keyTable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
    keyTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

    keyKeywordColumn = new TableColumn<>("Schlagwort");
    keyKeywordColumn.setId("keyword");
    keyKeywordColumn.setEditable(true);
    keyKeywordColumn.setSortable(true);
    keyKeywordColumn.setCellValueFactory(cellData ->
                                             new ReadOnlyStringWrapper(cellData.getValue().getKeyword())
    );
    keyKeywordColumn.setCellFactory(col -> new KeyEditingTableCell());

    keyCountColumn = new TableColumn<>("Häufigkeit");
    keyCountColumn.setId("count");
    keyCountColumn.setSortable(true);
    keyCountColumn.setStyle("-fx-alignment: CENTER-RIGHT;");
    keyCountColumn.setCellValueFactory(cellData ->
                                           new ReadOnlyIntegerWrapper(cellData.getValue().getCount())
    );

    keyTable.getColumns().addAll(keyKeywordColumn, keyCountColumn);

    keyTable.setRowFactory(table -> {
      TableRow<KeyTableRow> row = new TableRow<>();
      row.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
        if (event.getButton() != MouseButton.PRIMARY || row.isEmpty()) {
          return;
        }

        Node target = event.getPickResult().getIntersectedNode();
        while (target != null && target != row) {
          if (target instanceof TableCell<?, ?> cell) {
            if (cell.getTableColumn() == keyKeywordColumn) {
              KeyTableRow item = row.getItem();
              setKeyKeepKeyword(item.getKeyword());
              Platform.runLater(() -> {
                keyTable.getSelectionModel().clearAndSelect(row.getIndex(), keyKeywordColumn);
                keyTable.edit(row.getIndex(), keyKeywordColumn);
              });
              break;
            }
          }
          target = target.getParent();
        }
      });
      return row;
    });

    keyHeaderTable = new TableView<>();
    keyHeaderTable.getStyleClass().add("zettel-key-header-table");
    keyHeaderTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    keyHeaderTable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
    keyHeaderTable.setPlaceholder(new Label("Keine Zettel"));

    keyHeaderIdColumn = new TableColumn<>();
    keyHeaderIdColumn.setReorderable(false);
    keyHeaderIdColumn.setSortable(false);
    keyHeaderIdColumn.setResizable(true);
    keyHeaderIdColumn.setStyle("-fx-alignment: CENTER-RIGHT;");
    keyHeaderIdColumn.setCellValueFactory(cellData ->
                                              new ReadOnlyIntegerWrapper(cellData.getValue().getNoteId())
    );
    keyHeaderIdColumn.setCellFactory(col -> new TableCell<>() {
      @Override
      protected void updateItem(Number item, boolean empty) {
        super.updateItem(item, empty);
        setText(empty || item == null ? null : Integer.toString(item.intValue()));
      }
    });

    keyHeaderTitleColumn = new TableColumn<>();
    keyHeaderTitleColumn.setReorderable(false);
    keyHeaderTitleColumn.setSortable(false);
    keyHeaderTitleColumn.setResizable(true);
    keyHeaderTitleColumn.setCellValueFactory(cellData ->
                                                 new ReadOnlyStringWrapper(cellData.getValue().getTitle())
    );
    keyHeaderTitleColumn.setCellFactory(col -> new TableCell<>() {
      @Override
      protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        if (empty) {
          setText(null);
          setGraphic(null);
          return;
        }
        setText(item == null ? "" : item);
      }
    });

    keyHeaderTable.getColumns().addAll(keyHeaderIdColumn, keyHeaderTitleColumn);
    keyHeaderTable.setFixedCellSize(24);

    keyHeaderPane = new BorderPane(keyHeaderTable);
    keyHeaderPane.getStyleClass().add("zettel-key-header-pane");
    keyHeaderPane.setVisible(false);
    keyHeaderPane.setManaged(false);

    keySplitPane = new SplitPane();
    keySplitPane.setOrientation(Orientation.VERTICAL);
    keySplitPane.getStyleClass().add("zettel-key-split-pane");
    keySplitPane.getItems().add(keyTable);

    VBox keyCenter = new VBox(8, keyStatusLabel, keySplitPane);
    keyCenter.getStyleClass().add("zettel-key-content");
    VBox.setVgrow(keySplitPane, Priority.ALWAYS);

    keyPane.setTop(keyToolBar);
    keyPane.setCenter(keyCenter);

    // List-Bereich (Überschriften)
    listPane = new BorderPane();
    listPane.getStyleClass().add("zettel-list-pane");

    listToolBar = new ToolBar();
    listToolBar.getStyleClass().add("zettel-list-toolbar");

    listPageKeyButton = BaseIcon.PAGE_KEY.button();
    listPageKeyButton.getStyleClass().add("zettel-list-page-key-button");
    listPageKeyButton.setFocusTraversable(false);

    listDepthSlider = new Slider(1, 3, 1);
    listDepthSlider.getStyleClass().add("zettel-list-depth-slider");
    listDepthSlider.setMinWidth(90);
    listDepthSlider.setPrefWidth(90);
    listDepthSlider.setMaxWidth(90);
    listDepthSlider.setMajorTickUnit(1);
    listDepthSlider.setMinorTickCount(0);
    listDepthSlider.setSnapToTicks(true);
    listDepthSlider.setShowTickMarks(true);
    listDepthSlider.setShowTickLabels(true);
    listDepthSlider.setFocusTraversable(false);

    listFilterField = new TextField();
    listFilterField.getStyleClass().add("zettel-list-filter-field");
    listFilterField.setPromptText("Überschriften filtern");
    listFilterField.setPrefWidth(170);

    listFullTextToggleButton = new ToggleButton("Volltext");
    listFullTextToggleButton.getStyleClass().add("zettel-list-fulltext-toggle");
    listFullTextToggleButton.setFocusTraversable(false);

    Label listDepthLabel = new Label("Tiefe:");
    listDepthLabel.getStyleClass().add("zettel-list-depth-label");

    Region listSpacer = new Region();
    HBox.setHgrow(listSpacer, Priority.ALWAYS);

    listToolBar.getItems().addAll(
        listPageKeyButton,
        listDepthLabel,
        listDepthSlider,
        new Separator(Orientation.VERTICAL),
        listSpacer,
        listFilterField,
        listFullTextToggleButton
    );

    listPageKeyBox = new VBox(4);
    listPageKeyBox.getStyleClass().add("zettel-list-page-key-box");
    listPageKeyBox.setFillWidth(true);

    listPageKeyScrollPane = new ScrollPane(listPageKeyBox);
    listPageKeyScrollPane.setFitToWidth(true);
    listPageKeyScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    listPageKeyScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    listPageKeyScrollPane.getStyleClass().add("zettel-list-page-key-scroll");

    listFilterTable = new TableView<>();
    listFilterTable.getStyleClass().add("zettel-list-filter-table");
    listFilterTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    listFilterTable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

    listFilterIdColumn = new TableColumn<>("Nr.");
    listFilterIdColumn.setId("noteId");
    listFilterIdColumn.setSortable(true);
    listFilterIdColumn.setStyle("-fx-alignment: CENTER-RIGHT;");
    listFilterIdColumn.setCellValueFactory(cellData ->
                                               new ReadOnlyIntegerWrapper(cellData.getValue().getNoteId())
    );

    listFilterTitleColumn = new TableColumn<>("Überschrift");
    listFilterTitleColumn.setId("title");
    listFilterTitleColumn.setSortable(true);
    listFilterTitleColumn.setCellValueFactory(cellData ->
                                                  new ReadOnlyStringWrapper(cellData.getValue().getTitle())
    );

    listFilterLinkCountColumn = new TableColumn<>("Verkn.");
    listFilterLinkCountColumn.setId("linkCount");
    listFilterLinkCountColumn.setSortable(true);
    listFilterLinkCountColumn.setStyle("-fx-alignment: CENTER-RIGHT;");
    listFilterLinkCountColumn.setCellValueFactory(cellData ->
                                                      new ReadOnlyObjectWrapper<>(cellData.getValue().getLinkCount())
    );
    listFilterLinkCountColumn.setComparator((left, right) -> {
      int l = left == null ? Integer.MIN_VALUE : left;
      int r = right == null ? Integer.MIN_VALUE : right;
      return Integer.compare(l, r);
    });
    listFilterLinkCountColumn.setCellFactory(col -> new TableCell<>() {
      @Override
      protected void updateItem(Integer item, boolean empty) {
        super.updateItem(item, empty);
        setText(empty ? null : (item == null ? "—" : Integer.toString(item)));
      }
    });

    listFilterTable.getColumns().addAll(
        listFilterIdColumn,
        listFilterTitleColumn,
        listFilterLinkCountColumn
    );
    listFilterTable.setItems(FXCollections.observableArrayList());

    listFilterTable.setRowFactory(table -> {
      TableRow<ListFilterRow> row = new TableRow<>();
      row.itemProperty().addListener((obs, oldItem, newItem) -> {
        row.getStyleClass().remove("zettel-list-filter-body-match-row");
        if (newItem != null && newItem.isBodyMatchOnly()) {
          row.getStyleClass().add("zettel-list-filter-body-match-row");
        }
      });
      return row;
    });

    listContentHost = new StackPane();
    listContentHost.getStyleClass().add("zettel-list-content-host");

    listPane.setTop(listToolBar);
    listPane.setCenter(listContentHost);

    setListMode(ListMode.PAGE_KEY);

    serviceArea.setTop(serviceAreaToolBar);
    serviceArea.setCenter(serviceAreaContentHost);

    workAreaSplitPane.getItems().addAll(noteEditorPane, keywordsTablePane, serviceArea);
    root.setCenter(workAreaSplitPane);

    setServiceAreaContent(magnifyLinkPane);
    setActiveServiceAreaMode(ServiceAreaMode.MAGNIFY_LINK);
    updateServiceAreaToggleButton();
    hideKeyHeaderTableHeader();
  }

  /**
   * Erzeugt einen Toggle-Button für die Toolbar der rechten Seitenleiste.
   *
   * @param icon anzuzeigendes Symbol
   * @param tooltipText Tooltiptext; bei {@code null} wird der Standard-Tooltip des Icons verwendet
   * @return konfigurierter Toggle-Button
   */
  private ToggleButton createServiceAreaToggleButton(BaseIcon icon, String tooltipText) {
    ToggleButton button = new ToggleButton();
    button.getStyleClass().add("icon-button");
    button.setGraphic(icon.imageView());
    button.setFocusTraversable(false);
    button.setMinWidth(34);
    button.setPrefWidth(34);
    button.setMinHeight(30);
    button.setPrefHeight(30);

    if (tooltipText == null || tooltipText.isBlank()) {
      button.setTooltip(new Tooltip(icon.tooltip()));
    } else {
      button.setTooltip(new Tooltip(tooltipText));
    }

    return button;
  }

  /**
   * Setzt den sichtbaren Inhalt im unteren Bereich der rechten Seitenleiste.
   *
   * @param content anzuzeigender Inhalt
   */
  public void setServiceAreaContent(javafx.scene.Node content) {
    serviceAreaContentHost.getChildren().setAll(content);
  }

  /**
   * Setzt den aktiven Modus der rechten Seitenleiste und synchronisiert
   * die Toggle-Zustände der Buttons exklusiv.
   *
   * @param mode neuer Modus; {@code null} deaktiviert alle Buttons
   */
  public void setActiveServiceAreaMode(ServiceAreaMode mode) {
    activeServiceAreaMode = mode;

    serviceAreaMagnifyLinkButton.setSelected(mode == ServiceAreaMode.MAGNIFY_LINK);
    serviceAreaKeyButton.setSelected(mode == ServiceAreaMode.KEY);
    serviceAreaBookButton.setSelected(mode == ServiceAreaMode.BOOK);
    serviceAreaListButton.setSelected(mode == ServiceAreaMode.LIST);
    serviceAreaClusterButton.setSelected(mode == ServiceAreaMode.PERFORMANCE_ANALYSIS);
  }

  /**
   * Klappt den rechten Arbeitsbereich weg oder wieder auf.
   *
   * @param collapsed {@code true}, wenn der rechte Bereich verborgen werden soll
   */
  public void setServiceAreaCollapsed(boolean collapsed) {
    if (this.serviceAreaCollapsed == collapsed) {
      updateServiceAreaToggleButton();
      return;
    }

    if (collapsed) {
      expandedDividerPositions = workAreaSplitPane.getDividerPositions();
      workAreaSplitPane.getItems().remove(serviceArea);
    } else {
      if (!workAreaSplitPane.getItems().contains(serviceArea)) {
        workAreaSplitPane.getItems().add(serviceArea);
      }

      double firstDivider = 0.33;
      double secondDivider = 0.66;

      if (expandedDividerPositions != null) {
        if (expandedDividerPositions.length > 0) {
          firstDivider = expandedDividerPositions[0];
        }
        if (expandedDividerPositions.length > 1) {
          secondDivider = expandedDividerPositions[1];
        }
      }

      final double restoredFirstDivider = firstDivider;
      final double restoredSecondDivider = secondDivider;

      Platform.runLater(() -> workAreaSplitPane.setDividerPositions(restoredFirstDivider, restoredSecondDivider));
    }

    this.serviceAreaCollapsed = collapsed;
    updateServiceAreaToggleButton();
  }

  /**
   * Schaltet den Sichtbarkeitszustand des rechten Arbeitsbereichs um.
   */
  public void toggleServiceAreaCollapsed() {
    setServiceAreaCollapsed(!serviceAreaCollapsed);
  }

  /**
   * Aktualisiert Icon und Tooltip des Buttons zum Ein- und Ausklappen
   * des rechten Arbeitsbereichs.
   */
  private void updateServiceAreaToggleButton() {
    if (serviceAreaCollapsed) {
      toolButtonToggleServiceArea.setGraphic(BaseIcon.LAYOUTS_SPLIT.imageView());
      toolButtonToggleServiceArea.setTooltip(new Tooltip("Rechtes Fenster einblenden"));
    } else {
      toolButtonToggleServiceArea.setGraphic(BaseIcon.LAYOUTS_JOIN.imageView());
      toolButtonToggleServiceArea.setTooltip(new Tooltip("Rechtes Fenster ausblenden"));
    }
  }

  /**
   * Erzeugt vorläufigen Platzhalter-Inhalt für einen Modus der rechten Seitenleiste.
   * Diese Darstellung wird im nächsten Schritt für MAGNIFY_LINK ersetzt.
   *
   * @param mode darzustellender Modus
   * @return Platzhalterknoten
   */
  public Region createModePlaceholder(ServiceAreaMode mode) {
    String text = switch (mode) {
      case MAGNIFY_LINK -> "Verweisfenster";
      case KEY -> "Schlüsselwörter";
      case BOOK -> "Literatur";
      case LIST -> "Überschriften";
      case PERFORMANCE_ANALYSIS -> "Cluster";
    };

    Label label = new Label(text);
    label.getStyleClass().add("zettel-placeholder-label");

    StackPane pane = new StackPane(label);
    pane.setPadding(new Insets(12));
    pane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    return pane;
  }

  /**
   * Erzeugt einen Tiefen-Slider für das Verweisfenster.
   *
   * @return konfigurierter Slider mit Werten von 0 bis 4
   */
  private Slider createDepthSlider() {
    Slider slider = new Slider(0, 8, 0);
    slider.getStyleClass().add("zettel-magnify-link-depth-slider");
    slider.setMinWidth(90);
    slider.setPrefWidth(90);
    slider.setMaxWidth(90);
    slider.setMajorTickUnit(2);
    slider.setMinorTickCount(1);
    slider.setSnapToTicks(true);
    slider.setShowTickMarks(true);
    slider.setShowTickLabels(true);
    slider.setFocusTraversable(false);
    return slider;
  }

  /**
   * Erzeugt einen Toggle-Button für die LinkType-Filter der MAGNIFY_LINK-Toolbar.
   *
   * @param icon Icon des Linktyps
   * @param tooltip Tooltiptext
   * @return konfigurierter Toggle-Button
   */
  private ToggleButton createMagnifyLinkTypeToggleButton(BaseIcon icon, String tooltip) {
    ToggleButton button = new ToggleButton();
    button.getStyleClass().addAll("icon-button", "zettel-magnify-link-type-toggle");
    button.setGraphic(icon.imageView());
    button.setTooltip(new Tooltip(tooltip));
    button.setFocusTraversable(false);
    button.setMinWidth(34);
    button.setPrefWidth(34);
    button.setMinHeight(30);
    button.setPrefHeight(30);
    return button;
  }

  /**
   * Setzt alle LinkType-Filter des Verweisfensters auf aktiv.
   */
  public void selectAllMagnifyLinkTypeButtons() {
    magnifyLinkSerieButton.setSelected(true);
    magnifyLinkIdeaButton.setSelected(true);
    magnifyLinkQuestionButton.setSelected(true);
    magnifyLinkCritiqueButton.setSelected(true);
    magnifyLinkTaskButton.setSelected(true);
  }

  /**
   * Leert die Verweisliste und setzt eine Statusmeldung.
   *
   * @param statusText anzuzeigende Statusmeldung
   */
  public void showMagnifyLinkStatus(String statusText) {
    magnifyLinkStatusLabel.setText(statusText == null ? "" : statusText);
    magnifyLinkListBox.getChildren().clear();
  }

  /**
   * Setzt den Inhalt der Verweisliste neu.
   *
   * @param statusText Statusmeldung oberhalb der Liste
   * @param rows darzustellende Zeilen
   */
  public void setMagnifyLinkRows(String statusText, List<? extends Node> rows) {
    magnifyLinkStatusLabel.setText(statusText == null ? "" : statusText);
    magnifyLinkListBox.getChildren().setAll(rows);
  }

  /**
   * Schaltet zwischen PAGE_KEY- und Filteransicht des LIST-Fensters um.
   *
   * @param mode neuer LIST-Modus
   */
  public void setListMode(ListMode mode) {
    activeListMode = mode == null ? ListMode.PAGE_KEY : mode;

    if (activeListMode == ListMode.PAGE_KEY) {
      listContentHost.getChildren().setAll(listPageKeyScrollPane);
    } else {
      listContentHost.getChildren().setAll(listFilterTable);
    }
  }

  /**
   * Setzt die PAGE_KEY-Knoten neu.
   *
   * @param rows darzustellende Knoten
   */
  public void setListPageKeyRows(List<? extends Node> rows) {
    listPageKeyBox.getChildren().setAll(rows);
  }

  /**
   * Setzt die Zeilen der LIST-Filtertabelle neu.
   * Die aktuelle Sortierung der Tabelle bleibt dabei erhalten,
   * solange das LIST-Fenster geöffnet bleibt.
   *
   * @param rows darzustellende Tabellenzeilen
   */
  public void setListFilterRows(List<ListFilterRow> rows) {
    ObservableList<ListFilterRow> items = listFilterTable.getItems();
    if (items == null) {
      items = FXCollections.observableArrayList();
      listFilterTable.setItems(items);
    }

    items.setAll(rows);

    if (!listFilterTable.getSortOrder().isEmpty()) {
      listFilterTable.sort();
    }
  }

  /**
   * Setzt die Tabellenzeilen des KEY-Fensters neu.
   *
   * @param rows neue Tabellenzeilen
   */
  public void setKeyRows(List<KeyTableRow> rows) {
    ObservableList<KeyTableRow> items = FXCollections.observableArrayList(rows);
    keyTable.setItems(items);
  }

  /**
   * Setzt den Bestandsstatus des KEY-Fensters neu.
   * Dieser wird nur angezeigt, wenn aktuell kein Aktionsstatus aktiv ist.
   *
   * @param text Bestandsstatus
   */
  public void setKeyInventoryStatus(String text) {
    keyInventoryStatusText = text == null ? "" : text;
    refreshKeyStatusLabel();
  }

  /**
   * Setzt einen Aktionsstatus für das KEY-Fenster.
   *
   * @param text Meldungstext
   * @param type Typ der Meldung
   */
  public void setKeyActionStatus(String text, KeyStatusType type) {
    keyActionStatusText = text == null ? "" : text;
    keyActionStatusType = type == null ? KeyStatusType.INFO : type;
    refreshKeyStatusLabel();
  }

  /**
   * Löscht den Aktionsstatus des KEY-Fensters und zeigt wieder den Bestandsstatus.
   */
  public void clearKeyActionStatus() {
    keyActionStatusText = "";
    keyActionStatusType = KeyStatusType.INFO;
    refreshKeyStatusLabel();
  }

  /**
   * Aktualisiert das Statuslabel des KEY-Fensters gemäß Bestands- und Aktionsstatus.
   */
  private void refreshKeyStatusLabel() {
    keyStatusLabel.getStyleClass().removeAll(
        "zettel-key-status-info",
        "zettel-key-status-success",
        "zettel-key-status-warning",
        "zettel-key-status-error"
    );

    if (!keyActionStatusText.isBlank()) {
      keyStatusLabel.setText(keyActionStatusText);

      String styleClass = switch (keyActionStatusType) {
        case SUCCESS -> "zettel-key-status-success";
        case WARNING -> "zettel-key-status-warning";
        case ERROR -> "zettel-key-status-error";
        case INFO -> "zettel-key-status-info";
      };
      keyStatusLabel.getStyleClass().add(styleClass);
      return;
    }

    keyStatusLabel.setText(keyInventoryStatusText);
    keyStatusLabel.getStyleClass().add("zettel-key-status-info");
  }

  /**
   * Setzt die Vorschläge für das zweite Eingabefeld.
   *
   * @param suggestions Vorschlagsliste
   */
  public void setKeyReplaceSuggestions(List<String> suggestions) {
    keyReplaceField.getItems().setAll(suggestions);
  }

  /**
   * Öffnet das Vorschlags-Dropdown des zweiten Feldes, wenn Einträge vorhanden sind.
   */
  public void showKeyReplaceSuggestions() {
    if (!keyReplaceField.getItems().isEmpty()) {
      keyReplaceField.show();
    }
  }

  /**
   * Blendet das Vorschlags-Dropdown des zweiten Feldes aus.
   */
  public void hideKeyReplaceSuggestions() {
    keyReplaceField.hide();
  }

  /**
   * Füllt das erste Eingabefeld für die Ersetzungslogik.
   *
   * @param keyword Schlagworttext
   */
  public void setKeyKeepKeyword(String keyword) {
    keyKeepField.setText(keyword == null ? "" : keyword);
  }

  /**
   * Öffnet die Bearbeitung einer Tabellenzeile erneut und setzt dabei einen
   * vorgegebenen Bearbeitungstext.
   *
   * @param keywordId ID der Zeile
   * @param editText Text, der im Editmodus sichtbar bleiben soll
   */
  public void reopenKeyKeywordEdit(int keywordId, String editText) {
    int rowIndex = findKeyRowIndex(keywordId);
    if (rowIndex < 0) {
      return;
    }

    pendingKeyEditRowId = keywordId;
    pendingKeyEditText = editText == null ? "" : editText;

    keyTable.getSelectionModel().clearAndSelect(rowIndex, keyKeywordColumn);
    keyTable.scrollTo(rowIndex);

    Platform.runLater(() -> keyTable.edit(rowIndex, keyKeywordColumn));
  }

  /**
   * Ermittelt den Tabellenindex zu einer Schlagwort-ID.
   *
   * @param keywordId Schlagwort-ID
   * @return Zeilenindex oder {@code -1}
   */
  private int findKeyRowIndex(int keywordId) {
    ObservableList<KeyTableRow> items = keyTable.getItems();
    for (int i = 0; i < items.size(); i++) {
      if (items.get(i).getKeywordId() == keywordId) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Tabellenzelle für Inline-Bearbeitung der Schlagwortspalte.
   */
  private final class KeyEditingTableCell extends TableCell<KeyTableRow, String> {
    private TextField textField;

    @Override
    public void startEdit() {
      if (isEmpty()) {
        return;
      }
      super.startEdit();

      if (textField == null) {
        textField = new TextField(getItem());
        textField.getStyleClass().add("zettel-key-inline-editor");

        textField.setOnAction(e -> commitOrReject());

        textField.focusedProperty().addListener((obs, oldValue, newValue) -> {
          if (oldValue && !newValue) {
            commitOrReject();
          }
        });
      }

      String startValue = getItem();
      KeyTableRow row = getTableRow() == null ? null : getTableRow().getItem();
      if (row != null && pendingKeyEditRowId != null && pendingKeyEditRowId == row.getKeywordId()) {
        startValue = pendingKeyEditText == null ? row.getKeyword() : pendingKeyEditText;
        pendingKeyEditRowId = null;
        pendingKeyEditText = null;
      }

      textField.setText(startValue == null ? "" : startValue);
      setText(null);
      setGraphic(textField);

      Platform.runLater(() -> {
        textField.requestFocus();
        textField.selectAll();
      });
    }

    @Override
    public void cancelEdit() {
      super.cancelEdit();
      setText(getItem());
      setGraphic(null);
    }

    @Override
    protected void updateItem(String item, boolean empty) {
      super.updateItem(item, empty);

      if (empty) {
        setText(null);
        setGraphic(null);
        return;
      }

      if (isEditing() && textField != null) {
        setText(null);
        setGraphic(textField);
      } else {
        setText(item);
        setGraphic(null);
      }
    }

    private void commitOrReject() {
      if (!isEditing()) {
        return;
      }

      KeyTableRow row = getTableRow() == null ? null : getTableRow().getItem();
      if (row == null || keyKeywordEditCommitHandler == null) {
        cancelEdit();
        return;
      }

      String newValue = textField.getText();
      boolean accepted = keyKeywordEditCommitHandler.handleCommit(row, newValue);

      if (accepted) {
        super.commitEdit(newValue);
      } else {
        Platform.runLater(() -> reopenKeyKeywordEdit(row.getKeywordId(), newValue));
      }
    }
  }

  /**
   * Liest den aktuell sichtbaren primären Sortierzustand der KEY-Tabelle aus.
   * Diese Methode muss vor einem Daten-Refresh aufgerufen werden, solange die
   * Tabelle ihren Sortierzustand noch besitzt.
   *
   * @return Sortierzustand oder {@code null}, wenn aktuell nicht sortiert wird
   */
  public KeyTableSortState snapshotKeyTableSortState() {
    if (keyTable.getSortOrder().isEmpty()) {
      return null;
    }

    TableColumn<KeyTableRow, ?> column = keyTable.getSortOrder().get(0);
    if (column == null || column.getId() == null || column.getSortType() == null) {
      return null;
    }

    return new KeyTableSortState(column.getId(), column.getSortType());
  }

  /**
   * Stellt einen zuvor gesicherten primären Sortierzustand der KEY-Tabelle
   * nach einem Refresh wieder her.
   *
   * @param state zuvor gesicherter Sortierzustand; {@code null} bedeutet:
   *              keine explizite Sortierung wiederherstellen
   */
  public void restoreKeyTableSortState(KeyTableSortState state) {
    keyTable.getSortOrder().clear();

    if (state == null || state.columnId() == null || state.sortType() == null) {
      return;
    }

    TableColumn<KeyTableRow, ?> column = findKeySortColumnById(state.columnId());
    if (column == null) {
      return;
    }

    column.setSortType(state.sortType());
    keyTable.getSortOrder().add(column);
    keyTable.sort();
  }

  /**
   * Findet eine KEY-Spalte anhand ihrer ID.
   *
   * @param columnId ID der Spalte
   * @return gefundene Spalte oder {@code null}
   */
  private TableColumn<KeyTableRow, ?> findKeySortColumnById(String columnId) {
    if (columnId == null || columnId.isBlank()) {
      return null;
    }

    for (TableColumn<KeyTableRow, ?> column : keyTable.getColumns()) {
      if (columnId.equals(column.getId())) {
        return column;
      }
    }
    return null;
  }

  /**
   * Setzt die Zeilen der unteren Zettelkopf-Tabelle.
   *
   * @param rows neue Zettelkopf-Zeilen
   */
  public void setKeyHeaderRows(List<KeyHeaderRow> rows) {
    keyHeaderTable.setItems(FXCollections.observableArrayList(rows));
    updateKeyHeaderIdColumnWidth(rows);
  }

  /**
   * Zeigt oder verbirgt den unteren Zettelkopf-Bereich des KEY-Fensters.
   * Beim Ausblenden wird der Bereich vollständig aus dem SplitPane entfernt,
   * damit kein leerer Restbereich sichtbar bleibt.
   *
   * @param visible {@code true}, wenn der Bereich sichtbar sein soll
   */
  public void setKeyHeaderPaneVisible(boolean visible) {
    if (visible) {
      if (!keySplitPane.getItems().contains(keyHeaderPane)) {
        keySplitPane.getItems().add(keyHeaderPane);
      }

      keyHeaderPane.setVisible(true);
      keyHeaderPane.setManaged(true);

      Platform.runLater(() -> {
        if (keySplitPane.getItems().size() > 1) {
          keySplitPane.setDividerPositions(keyHeaderDividerPosition);
        }
      });
      return;
    }

    if (keySplitPane.getItems().contains(keyHeaderPane)) {
      if (!keySplitPane.getDividers().isEmpty()) {
        keyHeaderDividerPosition = keySplitPane.getDividers().get(0).getPosition();
      }
      keySplitPane.getItems().remove(keyHeaderPane);
    }

    keyHeaderPane.setVisible(false);
    keyHeaderPane.setManaged(false);
  }

  /**
   * Aktualisiert die Breite der ID-Spalte anhand der größten sichtbaren Zettelnummer.
   *
   * @param rows sichtbare Header-Zeilen
   */
  private void updateKeyHeaderIdColumnWidth(List<KeyHeaderRow> rows) {
    int maxDigits = 1;
    if (rows != null) {
      for (KeyHeaderRow row : rows) {
        maxDigits = Math.max(maxDigits, Integer.toString(row.getNoteId()).length());
      }
    }

    double width = 18 + (maxDigits * 8.5);
    keyHeaderIdColumn.setMinWidth(width);
    keyHeaderIdColumn.setPrefWidth(width);
    keyHeaderIdColumn.setMaxWidth(width);
  }

  /**
   * Blendet den Tabellenkopf der unteren KEY-Zettelkopf-Tabelle aus.
   */
  public void hideKeyHeaderTableHeader() {
    Platform.runLater(() -> {
      Node header = keyHeaderTable.lookup("TableHeaderRow");
      if (header != null) {
        header.setManaged(false);
        header.setVisible(false);
        header.setStyle("-fx-max-height: 0; -fx-pref-height: 0; -fx-min-height: 0;");
      }
    });
  }

  /**
   * Wendet eine dezente Akzentfarbe auf Menüleiste und Toolbar an.
   *
   * @param color Hex-Farbe oder {@code null}
   */
  public void applyAccentColor(String color) {
    if (color == null || color.isBlank()) {
      menuBar.setStyle("");
      topToolBar.setStyle("");
      return;
    }

    String menuStyle =
        "-fx-background-color: " + color + ";" +
            "-fx-background-insets: 0;";

    String toolbarStyle =
        "-fx-background-color: derive(" + color + ", 20%);" +
            "-fx-background-insets: 0;";

    menuBar.setStyle(menuStyle);
    topToolBar.setStyle(toolbarStyle);
  }
}
