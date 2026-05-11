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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Baut und kapselt die JavaFX-View des Zettelfensters.
 */
public class ZettelWindowView {

  private static final double DEFAULT_SERVICE_AREA_WIDTH = 360.0;
  private static final double DEFAULT_WORK_AREA_DIVIDER_WIDTH = 8.0;
  private static final String KEY_CONTEXT_ROW_STYLE_CLASS = "zettel-key-context-row";
  private static final String WORK_AREA_DIVIDER_HANDLER_KEY = "zettel-work-divider-handler-installed";

  private final BorderPane root;

  private final VBox topArea;
  private final MenuBar menuBar;
  private final Menu menuFile;
  private final Menu menuEdit;
  private final Menu menuView;

  private final ToolBar topToolBar;
  private final Button toolButtonPageAdd;
  private final Button toolButtonPageCopy;
  private final Button toolButtonPageWithBibliography;
  private final Button toolButtonLinkEdit;
  private final Button toolButtonBibliography;

  // Button zum Aufklappen der Service-Area
  private final Button toolButtonToggleServiceArea;

  private final MenuButton toolMenuFollowingPages;
  private final MenuItem miFollowing;
  private final MenuItem miIdea;
  private final MenuItem miQuestion;
  private final MenuItem miCritique;
  private final MenuItem miTask;

  private final BottomToolbar bottomToolbar;

  private final StackPane workAreaHost;
  private final SplitPane workAreaSplitPane;

  private final ToolBar serviceAreaToolBar;
  private final StackPane serviceAreaContentHost;

  // Toolbar der Service-Area
  private final ToggleButton serviceAreaMagnifyLinkButton;
  private final ToggleButton serviceAreaKeyButton;
  private final ToggleButton serviceAreaBookButton;
  private final ToggleButton serviceAreaListButton;
  private final ToggleButton serviceAreaClusterButton;

  // MAGNIFY_LINK-Komponente
  private final BorderPane magnifyLinkPane;
  private final ToolBar magnifyLinkToolBar;
  private final TextField magnifyLinkNoteIdField;
  private final Slider magnifyLinkIncomingDepthSlider;
  private final Slider magnifyLinkOutgoingDepthSlider;
  private final ToggleButton magnifyLinkDirectedTraversalButton;

  private final ToggleButton magnifyLinkSerieButton;
  private final ToggleButton magnifyLinkIdeaButton;
  private final ToggleButton magnifyLinkQuestionButton;
  private final ToggleButton magnifyLinkCritiqueButton;
  private final ToggleButton magnifyLinkTaskButton;

  private final TextField magnifyLinkFilterField;
  private final Label magnifyLinkStatusLabel;
  private final VBox magnifyLinkListBox;

  // KEY-Komponente
  private final BorderPane keyPane;
  private final ToolBar keyToolBar;
  private final TextField keyFilterField;
  private final ToggleButton keyMediumFilterButton;
  private final ToggleButton keyAiContextFilterButton;
  private final TextField keyKeepField;
  private final ComboBox<String> keyReplaceField;
  private final Button keyReplaceButton;
  private final Label keyStatusLabel;
  private final TableView<KeyTableRow> keyTable;
  private final TableColumn<KeyTableRow, String> keyKeywordColumn;
  private final TableColumn<KeyTableRow, Number> keyCountColumn;
  private final ToggleButton keyShowHeadersToggleButton;
  private final SplitPane keySplitPane;
  private final BorderPane keyHeaderPane;
  private final TableView<KeyHeaderRow> keyHeaderTable;
  private final TableColumn<KeyHeaderRow, Number> keyHeaderIdColumn;
  private final TableColumn<KeyHeaderRow, String> keyHeaderTitleColumn;
  private Consumer<String> keyAltClickHandler = keyword -> {};

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
  private final BorderPane listPane;
  private final ToolBar listToolBar;
  private final Button listPageKeyButton;
  private final Slider listDepthSlider;
  private final ToggleButton listLockToggleButton;
  private final TextField listFilterField;
  private final ToggleButton listFullTextToggleButton;
  private final StackPane listContentHost;
  private final ScrollPane listPageKeyScrollPane;
  private final VBox listPageKeyBox;
  private final TableView<ListFilterRow> listFilterTable;
  private final TableColumn<ListFilterRow, Number> listFilterIdColumn;
  private final TableColumn<ListFilterRow, String> listFilterTitleColumn;
  private final TableColumn<ListFilterRow, Integer> listFilterLinkCountColumn;

  // BOOK-Komponente
  private final BorderPane bookPane;
  private final ToolBar bookToolBar;
  private final ToggleButton bookShowNotesToggleButton;
  private final ToggleButton bookShowMediaColumnToggleButton;
  private final Button bookAuthorModeButton;

  private final TextField bookMediaTypeField;
  private final ContextMenu bookMediaTypeMenu;
  private final TextField bookTitleField;

  private final VBox bookAuthorHost;
  private final ScrollPane bookAuthorScrollPane;
  private final VBox bookAuthorRowsBox;

  private final SplitPane bookSplitPane;
  private final TableView<BookMediaRow> bookMediaTable;
  private final TableColumn<BookMediaRow, String> bookMediaAuthorColumn;
  private final TableColumn<BookMediaRow, String> bookMediaTitleColumn;
  private final TableColumn<BookMediaRow, String> bookMediaTypeColumn;
  private final TableColumn<BookMediaRow, Number> bookMediaNoteCountColumn;

  private final BorderPane bookNotesPane;
  private final TableView<BookNoteRow> bookNotesTable;
  private final TableColumn<BookNoteRow, Number> bookNotesIdColumn;
  private final TableColumn<BookNoteRow, String> bookNotesTitleColumn;
  private final Label bookStatusLabel;

  private final double bookSplitDividerPosition = 0.68;
  private static final double BOOK_AUTHOR_VIEWPORT_HEIGHT = 78;
  private boolean bookResolvedMediaTypeSelection;
  private final List<BookAuthorRow> bookAuthorRowModels = new ArrayList<>();

  /**
   * -- GETTER --
   * liefert den technischen Namen des aktuell aufgelösten BOOK-Medientyps.
   */
  private String bookResolvedMediaTypeBibName = "";

  public enum ListMode {
    PAGE_KEY,
    FILTER
  }

  private ListMode activeListMode = ListMode.PAGE_KEY;

  /**
   * Beschreibt eine Tabellenzeile der LIST-Filteransicht.
   */
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

    /**
     * Liefert die Zettelnummer der Tabellenzeile.
     *
     * @return Zettelnummer
     */
    public int getNoteId() {
      return noteId;
    }

    /**
     * Liefert die Ueberschrift der Tabellenzeile.
     *
     * @return Ueberschrift
     */
    public String getTitle() {
      return title;
    }

    /**
     * Liefert die Anzahl verknuepfter Zettel.
     *
     * @return Anzahl oder {@code null}
     */
    public Integer getLinkCount() {
      return linkCount;
    }

    /**
     * Prueft, ob der Treffer nur im Volltext gefunden wurde.
     *
     * @return {@code true}, wenn nur der Volltext getroffen hat
     */
    public boolean isBodyMatchOnly() {
      return bodyMatchOnly;
    }
  }

  public enum BookAuthorMode {
    OFF,
    SINGLE,
    MULTI
  }

  /**
   * Beschreibt eine Autorenzeile der BOOK-Filtermaske.
   */
  public static final class BookAuthorRow {
    private String lastName = "";
    private String firstName = "";
    private TextField lastNameField;
    private TextField firstNameField;
    private ContextMenu lastNameSuggestionMenu;

    /**
     * Erzeugt eine Autorenzeile für die BOOK-Filtermaske.
     *
     * @param lastName Nachname
     * @param firstName Vorname
     */
    public BookAuthorRow(String lastName, String firstName) {
      this.lastName = lastName == null ? "" : lastName;
      this.firstName = firstName == null ? "" : firstName;
    }

    /**
     * Liefert den Nachnamen.
     *
     * @return Nachname
     */
    public String getLastName() {
      return lastName;
    }

    /**
     * Setzt den Nachnamen.
     *
     * @param lastName Nachname
     */
    public void setLastName(String lastName) {
      this.lastName = lastName;
    }

    /**
     * Liefert den Vornamen.
     *
     * @return Vorname
     */
    public String getFirstName() {
      return firstName;
    }

    /**
     * Setzt den Vornamen.
     *
     * @param firstName Vorname
     */
    public void setFirstName(String firstName) {
      this.firstName = firstName;
    }

    /**
     * Liefert das Nachnamen-Eingabefeld.
     *
     * @return Nachnamen-Eingabefeld
     */
    public TextField getLastNameField() {
      return lastNameField;
    }

    /**
     * Setzt das Nachnamen-Eingabefeld.
     *
     * @param lastNameField Nachnamen-Eingabefeld
     */
    public void setLastNameField(TextField lastNameField) {
      this.lastNameField = lastNameField;
    }

    /**
     * Liefert das Vornamen-Eingabefeld.
     *
     * @return Vornamen-Eingabefeld
     */
    public TextField getFirstNameField() {
      return firstNameField;
    }

    /**
     * Setzt das Vornamen-Eingabefeld.
     *
     * @param firstNameField Vornamen-Eingabefeld
     */
    public void setFirstNameField(TextField firstNameField) {
      this.firstNameField = firstNameField;
    }

    /**
     * Liefert das Vorschlagsmenue fuer Nachnamen.
     *
     * @return Vorschlagsmenue
     */
    public ContextMenu getLastNameSuggestionMenu() {
      return lastNameSuggestionMenu;
    }

    /**
     * Setzt das Vorschlagsmenue fuer Nachnamen.
     *
     * @param lastNameSuggestionMenu Vorschlagsmenue
     */
    public void setLastNameSuggestionMenu(ContextMenu lastNameSuggestionMenu) {
      this.lastNameSuggestionMenu = lastNameSuggestionMenu;
    }
  }

  /**
   * Beschreibt eine Tabellenzeile der BOOK-Medientabelle.
   */
  public static final class BookMediaRow {
    private final int entryId;
    private final String authorDisplay;
    private final String title;
    private final String mediaTypeName;
    private final int noteCount;

    /**
     * Erzeugt eine Tabellenzeile für die BOOK-Medientabelle.
     *
     * @param entryId Eintrags-ID
     * @param authorDisplay Autorendarstellung
     * @param title Titel
     * @param mediaTypeName Anzeigename des Medientyps
     * @param noteCount Anzahl referenzierender Zettel
     */
    public BookMediaRow(int entryId,
                        String authorDisplay,
                        String title,
                        String mediaTypeName,
                        int noteCount) {
      this.entryId = entryId;
      this.authorDisplay = authorDisplay == null ? "" : authorDisplay;
      this.title = title == null ? "" : title;
      this.mediaTypeName = mediaTypeName == null ? "" : mediaTypeName;
      this.noteCount = noteCount;
    }

    /**
     * Liefert die Eintrags-ID.
     *
     * @return Eintrags-ID
     */
    public int getEntryId() {
      return entryId;
    }

    /**
     * Liefert die Autorendarstellung.
     *
     * @return Autorendarstellung
     */
    public String getAuthorDisplay() {
      return authorDisplay;
    }

    /**
     * Liefert den Titel.
     *
     * @return Titel
     */
    public String getTitle() {
      return title;
    }

    /**
     * Liefert den Medientypnamen.
     *
     * @return Medientypname
     */
    public String getMediaTypeName() {
      return mediaTypeName;
    }

    /**
     * Liefert die Anzahl referenzierender Zettel.
     *
     * @return Anzahl referenzierender Zettel
     */
    public int getNoteCount() {
      return noteCount;
    }
  }

  /**
   * Beschreibt eine Tabellenzeile der BOOK-Zetteltabelle.
   */
  public static final class BookNoteRow {
    private final int noteId;
    private final String title;

    /**
     * Erzeugt eine Tabellenzeile für die BOOK-Zetteltabelle.
     *
     * @param noteId Zettel-ID
     * @param title Zettelüberschrift
     */
    public BookNoteRow(int noteId, String title) {
      this.noteId = noteId;
      this.title = title == null ? "" : title;
    }

    /**
     * Liefert die Zettel-ID.
     *
     * @return Zettel-ID
     */
    public int getNoteId() {
      return noteId;
    }

    /**
     * Liefert die Zettelueberschrift.
     *
     * @return Zettelueberschrift
     */
    public String getTitle() {
      return title;
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

  /**
   * Beschreibt eine Tabellenzeile der KEY-Schlagworttabelle.
   */
  public static final class KeyTableRow {
    private final int keywordId;
    private final String keyword;
    private final int count;
    private final boolean contextRow;

    /**
     * Erzeugt eine Tabellenzeile für das KEY-Fenster.
     *
     * @param keywordId ID des Schlagworts
     * @param keyword Schlagworttext
     * @param count Häufigkeit
     */
    public KeyTableRow(int keywordId, String keyword, int count) {
      this(keywordId, keyword, count, false);
    }

    /**
     * Erzeugt eine Tabellenzeile für das KEY-Fenster.
     *
     * @param keywordId ID des Schlagworts
     * @param keyword Schlagworttext
     * @param count Häufigkeit
     * @param contextRow {@code true}, wenn die Zeile zur Zusatzliste gehört
     */
    public KeyTableRow(int keywordId, String keyword, int count, boolean contextRow) {
      this.keywordId = keywordId;
      this.keyword = keyword;
      this.count = count;
      this.contextRow = contextRow;
    }

    /**
     * Liefert die Schlagwort-ID.
     *
     * @return Schlagwort-ID
     */
    public int getKeywordId() {
      return keywordId;
    }

    /**
     * Liefert das Schlagwort.
     *
     * @return Schlagwort
     */
    public String getKeyword() {
      return keyword;
    }

    /**
     * Liefert die Haeufigkeit.
     *
     * @return Haeufigkeit
     */
    public int getCount() {
      return count;
    }

    /**
     * Prüft, ob die Zeile in der hervorgehobenen Zusatzliste steht.
     *
     * @return {@code true}, wenn die Zeile zur Medium- oder KI-Kontextliste gehört
     */
    public boolean isContextRow() {
      return contextRow;
    }
  }

  /**
   * Beschreibt eine Tabellenzeile der KEY-Zettelkopftabelle.
   */
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

    /**
     * Liefert die Zettelnummer.
     *
     * @return Zettelnummer
     */
    public int getNoteId() {
      return noteId;
    }

    /**
     * Liefert die Zettelueberschrift.
     *
     * @return Zettelueberschrift
     */
    public String getTitle() {
      return title;
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
   * Gespeicherter primärer Sortierzustand der BOOK-Medientabelle.
   *
   * @param columnId ID der sortierten Spalte
   * @param sortType Sortierrichtung
   */
  public record BookMediaTableSortState(String columnId, TableColumn.SortType sortType) {
  }

  /**
   * Gespeicherter primärer Sortierzustand der BOOK-Zetteltabelle.
   *
   * @param columnId ID der sortierten Spalte
   * @param sortType Sortierrichtung
   */
  public record BookNoteTableSortState(String columnId, TableColumn.SortType sortType) {
  }

  /**
   * -- SETTER --
   *  Setzt den Commit-Handler für Inline-Änderungen in der Schlagwortspalte.
   */
  private KeyKeywordEditCommitHandler keyKeywordEditCommitHandler;

  private final NoteEditorPane noteEditorPane;
  private final KeywordsTablePane keywordsTablePane;

  // Service-Area und Zustandsvariablen dazu
  private final BorderPane serviceArea;

  /**
   * -- GETTER --
   *  Prüft, ob der rechte Arbeitsbereich aktuell eingeklappt ist.
   */
  private boolean serviceAreaCollapsed;

  /**
   * -- GETTER --
   *  Liefert den aktuell aktiven Modus der rechten Seitenleiste.
   */
  private ServiceAreaMode activeServiceAreaMode;
  private double[] expandedDividerPositions = new double[] {0.33, 0.66};
  private double lastExpandedNoteAreaWidth = 0.0;
  private double lastExpandedKeywordAreaWidth = 0.0;
  private double lastExpandedServiceAreaWidth = DEFAULT_SERVICE_AREA_WIDTH;
  private double lastExpandedWorkAreaWidth = 0.0;
  private double lastWorkAreaDividerWidth = DEFAULT_WORK_AREA_DIVIDER_WIDTH;

  private BookAuthorMode activeBookAuthorMode = BookAuthorMode.OFF;

  /**
   * Setzt den Commit-Handler fuer Inline-Aenderungen in der Schlagwortspalte.
   *
   * @param keyKeywordEditCommitHandler neuer Commit-Handler
   */
  public void setKeyKeywordEditCommitHandler(KeyKeywordEditCommitHandler keyKeywordEditCommitHandler) {
    this.keyKeywordEditCommitHandler = keyKeywordEditCommitHandler;
  }

  /**
   * Liefert den Wert von {@code root}.
   *
   * @return aktueller Wert von {@code root}
   */
  public BorderPane getRoot() {
    return root;
  }

  /**
   * Liefert den Wert von {@code menuFile}.
   *
   * @return aktueller Wert von {@code menuFile}
   */
  public Menu getMenuFile() {
    return menuFile;
  }

  /**
   * Liefert den Wert von {@code toolButtonPageAdd}.
   *
   * @return aktueller Wert von {@code toolButtonPageAdd}
   */
  public Button getToolButtonPageAdd() {
    return toolButtonPageAdd;
  }

  /**
   * Liefert den Wert von {@code toolButtonPageCopy}.
   *
   * @return aktueller Wert von {@code toolButtonPageCopy}
   */
  public Button getToolButtonPageCopy() {
    return toolButtonPageCopy;
  }

  /**
   * Liefert den Wert von {@code toolButtonPageWithBibliography}.
   *
   * @return aktueller Wert von {@code toolButtonPageWithBibliography}
   */
  public Button getToolButtonPageWithBibliography() {
    return toolButtonPageWithBibliography;
  }

  /**
   * Liefert den Wert von {@code toolButtonLinkEdit}.
   *
   * @return aktueller Wert von {@code toolButtonLinkEdit}
   */
  public Button getToolButtonLinkEdit() {
    return toolButtonLinkEdit;
  }

  /**
   * Liefert den Wert von {@code toolButtonBibliography}.
   *
   * @return aktueller Wert von {@code toolButtonBibliography}
   */
  public Button getToolButtonBibliography() {
    return toolButtonBibliography;
  }

  /**
   * Liefert den Wert von {@code toolButtonToggleServiceArea}.
   *
   * @return aktueller Wert von {@code toolButtonToggleServiceArea}
   */
  public Button getToolButtonToggleServiceArea() {
    return toolButtonToggleServiceArea;
  }

  /**
   * Liefert den Wert von {@code miFollowing}.
   *
   * @return aktueller Wert von {@code miFollowing}
   */
  public MenuItem getMiFollowing() {
    return miFollowing;
  }

  /**
   * Liefert den Wert von {@code miIdea}.
   *
   * @return aktueller Wert von {@code miIdea}
   */
  public MenuItem getMiIdea() {
    return miIdea;
  }

  /**
   * Liefert den Wert von {@code miQuestion}.
   *
   * @return aktueller Wert von {@code miQuestion}
   */
  public MenuItem getMiQuestion() {
    return miQuestion;
  }

  /**
   * Liefert den Wert von {@code miCritique}.
   *
   * @return aktueller Wert von {@code miCritique}
   */
  public MenuItem getMiCritique() {
    return miCritique;
  }

  /**
   * Liefert den Wert von {@code miTask}.
   *
   * @return aktueller Wert von {@code miTask}
   */
  public MenuItem getMiTask() {
    return miTask;
  }

  /**
   * Liefert den Wert von {@code bottomToolbar}.
   *
   * @return aktueller Wert von {@code bottomToolbar}
   */
  public BottomToolbar getBottomToolbar() {
    return bottomToolbar;
  }

  /**
   * Liefert den Wert von {@code workAreaSplitPane}.
   *
   * @return aktueller Wert von {@code workAreaSplitPane}
   */
  public SplitPane getWorkAreaSplitPane() {
    return workAreaSplitPane;
  }

  /**
   * Liefert den Wert von {@code serviceAreaMagnifyLinkButton}.
   *
   * @return aktueller Wert von {@code serviceAreaMagnifyLinkButton}
   */
  public ToggleButton getServiceAreaMagnifyLinkButton() {
    return serviceAreaMagnifyLinkButton;
  }

  /**
   * Liefert den Wert von {@code serviceAreaKeyButton}.
   *
   * @return aktueller Wert von {@code serviceAreaKeyButton}
   */
  public ToggleButton getServiceAreaKeyButton() {
    return serviceAreaKeyButton;
  }

  /**
   * Liefert den Wert von {@code serviceAreaBookButton}.
   *
   * @return aktueller Wert von {@code serviceAreaBookButton}
   */
  public ToggleButton getServiceAreaBookButton() {
    return serviceAreaBookButton;
  }

  /**
   * Liefert den Wert von {@code serviceAreaListButton}.
   *
   * @return aktueller Wert von {@code serviceAreaListButton}
   */
  public ToggleButton getServiceAreaListButton() {
    return serviceAreaListButton;
  }

  /**
   * Liefert den Wert von {@code serviceAreaClusterButton}.
   *
   * @return aktueller Wert von {@code serviceAreaClusterButton}
   */
  public ToggleButton getServiceAreaClusterButton() {
    return serviceAreaClusterButton;
  }

  /**
   * Liefert den Wert von {@code magnifyLinkPane}.
   *
   * @return aktueller Wert von {@code magnifyLinkPane}
   */
  public BorderPane getMagnifyLinkPane() {
    return magnifyLinkPane;
  }

  /**
   * Liefert den Wert von {@code magnifyLinkNoteIdField}.
   *
   * @return aktueller Wert von {@code magnifyLinkNoteIdField}
   */
  public TextField getMagnifyLinkNoteIdField() {
    return magnifyLinkNoteIdField;
  }

  /**
   * Liefert den Wert von {@code magnifyLinkIncomingDepthSlider}.
   *
   * @return aktueller Wert von {@code magnifyLinkIncomingDepthSlider}
   */
  public Slider getMagnifyLinkIncomingDepthSlider() {
    return magnifyLinkIncomingDepthSlider;
  }

  /**
   * Liefert den Wert von {@code magnifyLinkOutgoingDepthSlider}.
   *
   * @return aktueller Wert von {@code magnifyLinkOutgoingDepthSlider}
   */
  public Slider getMagnifyLinkOutgoingDepthSlider() {
    return magnifyLinkOutgoingDepthSlider;
  }

  /**
   * Liefert den Wert von {@code magnifyLinkDirectedTraversalButton}.
   *
   * @return aktueller Wert von {@code magnifyLinkDirectedTraversalButton}
   */
  public ToggleButton getMagnifyLinkDirectedTraversalButton() {
    return magnifyLinkDirectedTraversalButton;
  }

  /**
   * Liefert den Wert von {@code magnifyLinkSerieButton}.
   *
   * @return aktueller Wert von {@code magnifyLinkSerieButton}
   */
  public ToggleButton getMagnifyLinkSerieButton() {
    return magnifyLinkSerieButton;
  }

  /**
   * Liefert den Wert von {@code magnifyLinkIdeaButton}.
   *
   * @return aktueller Wert von {@code magnifyLinkIdeaButton}
   */
  public ToggleButton getMagnifyLinkIdeaButton() {
    return magnifyLinkIdeaButton;
  }

  /**
   * Liefert den Wert von {@code magnifyLinkQuestionButton}.
   *
   * @return aktueller Wert von {@code magnifyLinkQuestionButton}
   */
  public ToggleButton getMagnifyLinkQuestionButton() {
    return magnifyLinkQuestionButton;
  }

  /**
   * Liefert den Wert von {@code magnifyLinkCritiqueButton}.
   *
   * @return aktueller Wert von {@code magnifyLinkCritiqueButton}
   */
  public ToggleButton getMagnifyLinkCritiqueButton() {
    return magnifyLinkCritiqueButton;
  }

  /**
   * Liefert den Wert von {@code magnifyLinkTaskButton}.
   *
   * @return aktueller Wert von {@code magnifyLinkTaskButton}
   */
  public ToggleButton getMagnifyLinkTaskButton() {
    return magnifyLinkTaskButton;
  }

  /**
   * Liefert den Wert von {@code magnifyLinkFilterField}.
   *
   * @return aktueller Wert von {@code magnifyLinkFilterField}
   */
  public TextField getMagnifyLinkFilterField() {
    return magnifyLinkFilterField;
  }

  /**
   * Liefert den Wert von {@code keyPane}.
   *
   * @return aktueller Wert von {@code keyPane}
   */
  public BorderPane getKeyPane() {
    return keyPane;
  }

  /**
   * Liefert den Wert von {@code keyFilterField}.
   *
   * @return aktueller Wert von {@code keyFilterField}
   */
  public TextField getKeyFilterField() {
    return keyFilterField;
  }

  /**
   * Liefert den Button für die Medium-Schlagwortliste.
   *
   * @return Medium-Filterbutton des KEY-Fensters
   */
  public ToggleButton getKeyMediumFilterButton() {
    return keyMediumFilterButton;
  }

  /**
   * Liefert den Button für die KI-Kontext-Schlagwortliste.
   *
   * @return KI-Kontextbutton des KEY-Fensters
   */
  public ToggleButton getKeyAiContextFilterButton() {
    return keyAiContextFilterButton;
  }

  /**
   * Liefert den Wert von {@code keyKeepField}.
   *
   * @return aktueller Wert von {@code keyKeepField}
   */
  public TextField getKeyKeepField() {
    return keyKeepField;
  }

  /**
   * Liefert den Wert von {@code keyReplaceField}.
   *
   * @return aktueller Wert von {@code keyReplaceField}
   */
  public ComboBox<String> getKeyReplaceField() {
    return keyReplaceField;
  }

  /**
   * Liefert den Wert von {@code keyReplaceButton}.
   *
   * @return aktueller Wert von {@code keyReplaceButton}
   */
  public Button getKeyReplaceButton() {
    return keyReplaceButton;
  }

  /**
   * Liefert den Wert von {@code keyTable}.
   *
   * @return aktueller Wert von {@code keyTable}
   */
  public TableView<KeyTableRow> getKeyTable() {
    return keyTable;
  }

  /**
   * Liefert den Wert von {@code keyShowHeadersToggleButton}.
   *
   * @return aktueller Wert von {@code keyShowHeadersToggleButton}
   */
  public ToggleButton getKeyShowHeadersToggleButton() {
    return keyShowHeadersToggleButton;
  }

  /**
   * Liefert den Wert von {@code keyHeaderTable}.
   *
   * @return aktueller Wert von {@code keyHeaderTable}
   */
  public TableView<KeyHeaderRow> getKeyHeaderTable() {
    return keyHeaderTable;
  }

  /**
   * Liefert den Wert von {@code listPane}.
   *
   * @return aktueller Wert von {@code listPane}
   */
  public BorderPane getListPane() {
    return listPane;
  }

  /**
   * Liefert den Wert von {@code listPageKeyButton}.
   *
   * @return aktueller Wert von {@code listPageKeyButton}
   */
  public Button getListPageKeyButton() {
    return listPageKeyButton;
  }

  /**
   * Liefert den Wert von {@code listDepthSlider}.
   *
   * @return aktueller Wert von {@code listDepthSlider}
   */
  public Slider getListDepthSlider() {
    return listDepthSlider;
  }

  /**
   * Liefert den Sperrbutton der LIST-Ansicht.
   *
   * @return Sperrbutton
   */
  public ToggleButton getListLockToggleButton() {
    return listLockToggleButton;
  }

  /**
   * Liefert den Wert von {@code listFilterField}.
   *
   * @return aktueller Wert von {@code listFilterField}
   */
  public TextField getListFilterField() {
    return listFilterField;
  }

  /**
   * Liefert den Wert von {@code listFullTextToggleButton}.
   *
   * @return aktueller Wert von {@code listFullTextToggleButton}
   */
  public ToggleButton getListFullTextToggleButton() {
    return listFullTextToggleButton;
  }

  /**
   * Liefert den Wert von {@code listFilterTable}.
   *
   * @return aktueller Wert von {@code listFilterTable}
   */
  public TableView<ListFilterRow> getListFilterTable() {
    return listFilterTable;
  }

  /**
   * Liefert den Wert von {@code bookPane}.
   *
   * @return aktueller Wert von {@code bookPane}
   */
  public BorderPane getBookPane() {
    return bookPane;
  }

  /**
   * Liefert den Wert von {@code bookShowNotesToggleButton}.
   *
   * @return aktueller Wert von {@code bookShowNotesToggleButton}
   */
  public ToggleButton getBookShowNotesToggleButton() {
    return bookShowNotesToggleButton;
  }

  /**
   * Liefert den Wert von {@code bookShowMediaColumnToggleButton}.
   *
   * @return aktueller Wert von {@code bookShowMediaColumnToggleButton}
   */
  public ToggleButton getBookShowMediaColumnToggleButton() {
    return bookShowMediaColumnToggleButton;
  }

  /**
   * Liefert den Wert von {@code bookAuthorModeButton}.
   *
   * @return aktueller Wert von {@code bookAuthorModeButton}
   */
  public Button getBookAuthorModeButton() {
    return bookAuthorModeButton;
  }

  /**
   * Liefert den Wert von {@code bookMediaTypeField}.
   *
   * @return aktueller Wert von {@code bookMediaTypeField}
   */
  public TextField getBookMediaTypeField() {
    return bookMediaTypeField;
  }

  /**
   * Liefert den Wert von {@code bookMediaTypeMenu}.
   *
   * @return aktueller Wert von {@code bookMediaTypeMenu}
   */
  public ContextMenu getBookMediaTypeMenu() {
    return bookMediaTypeMenu;
  }

  /**
   * Liefert den Wert von {@code bookTitleField}.
   *
   * @return aktueller Wert von {@code bookTitleField}
   */
  public TextField getBookTitleField() {
    return bookTitleField;
  }

  /**
   * Liefert den Wert von {@code bookMediaTable}.
   *
   * @return aktueller Wert von {@code bookMediaTable}
   */
  public TableView<BookMediaRow> getBookMediaTable() {
    return bookMediaTable;
  }

  /**
   * Liefert den Wert von {@code bookNotesTable}.
   *
   * @return aktueller Wert von {@code bookNotesTable}
   */
  public TableView<BookNoteRow> getBookNotesTable() {
    return bookNotesTable;
  }

  /**
   * Liefert den Wert von {@code bookResolvedMediaTypeBibName}.
   *
   * @return aktueller Wert von {@code bookResolvedMediaTypeBibName}
   */
  public String getBookResolvedMediaTypeBibName() {
    return bookResolvedMediaTypeBibName;
  }

  /**
   * Liefert den Wert von {@code activeListMode}.
   *
   * @return aktueller Wert von {@code activeListMode}
   */
  public ListMode getActiveListMode() {
    return activeListMode;
  }

  /**
   * Liefert den Wert von {@code noteEditorPane}.
   *
   * @return aktueller Wert von {@code noteEditorPane}
   */
  public NoteEditorPane getNoteEditorPane() {
    return noteEditorPane;
  }

  /**
   * Liefert den Wert von {@code keywordsTablePane}.
   *
   * @return aktueller Wert von {@code keywordsTablePane}
   */
  public KeywordsTablePane getKeywordsTablePane() {
    return keywordsTablePane;
  }

  /**
   * Liefert den Wert von {@code serviceAreaCollapsed}.
   *
   * @return aktueller Wert von {@code serviceAreaCollapsed}
   */
  public boolean isServiceAreaCollapsed() {
    return serviceAreaCollapsed;
  }

  /**
   * Liefert den Wert von {@code activeServiceAreaMode}.
   *
   * @return aktueller Wert von {@code activeServiceAreaMode}
   */
  public ServiceAreaMode getActiveServiceAreaMode() {
    return activeServiceAreaMode;
  }

  /**
   * Liefert den Wert von {@code activeBookAuthorMode}.
   *
   * @return aktueller Wert von {@code activeBookAuthorMode}
   */
  public BookAuthorMode getActiveBookAuthorMode() {
    return activeBookAuthorMode;
  }

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
    toolButtonBibliography = BaseIcon.BIBLIOGRAPHY.button("Medienverwaltung öffnen");

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
        toolButtonBibliography,
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
    workAreaSplitPane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

    workAreaHost = new StackPane(workAreaSplitPane);
    workAreaHost.getStyleClass().add("zettel-work-host");
    StackPane.setAlignment(workAreaSplitPane, Pos.TOP_LEFT);

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

    keyMediumFilterButton = BaseIcon.FILTER_KEY.toggleButton("Medium-Schlagwörter einblenden");
    keyMediumFilterButton.getStyleClass().add("zettel-key-context-toggle");
    keyMediumFilterButton.setFocusTraversable(false);

    keyAiContextFilterButton = BaseIcon.INBOX_DOCUMENT_TEXT.toggleButton("KI-Kontext-Schlagwörter einblenden");
    keyAiContextFilterButton.getStyleClass().add("zettel-key-context-toggle");
    keyAiContextFilterButton.setFocusTraversable(false);
    keyAiContextFilterButton.setVisible(false);
    keyAiContextFilterButton.setManaged(false);
    keyAiContextFilterButton.setDisable(true);

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
        keyMediumFilterButton,
        keyAiContextFilterButton
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
    keyCountColumn.setCellFactory(column -> new TableCell<>() {
      {
        setAlignment(Pos.CENTER_RIGHT);
      }

      @Override
      protected void updateItem(Number item, boolean empty) {
        super.updateItem(item, empty);
        setText(empty || item == null ? null : item.toString());
      }
    });

    keyTable.getColumns().addAll(keyKeywordColumn, keyCountColumn);
    keyTable.setSortPolicy(this::sortKeyTablePreservingContextRows);

    keyTable.setRowFactory(table -> {
      TableRow<KeyTableRow> row = new TableRow<>();
      row.itemProperty().addListener((obs, oldItem, newItem) -> applyKeyContextRowStyle(row, newItem));
      row.emptyProperty().addListener((obs, wasEmpty, isEmpty) -> applyKeyContextRowStyle(row, row.getItem()));
      row.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
        if (event.getButton() != MouseButton.PRIMARY || row.isEmpty()) {
          return;
        }

        if (event.isAltDown()) {
          keyAltClickHandler.accept(row.getItem().getKeyword());
          event.consume();
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

    // Media-Bereich
    bookPane = new BorderPane();
    bookPane.getStyleClass().add("zettel-book-pane");

    bookToolBar = new ToolBar();
    bookToolBar.getStyleClass().add("zettel-book-toolbar");

    bookShowNotesToggleButton = BaseIcon.PAGE_MAGNIFY.toggleButton("Zetteltabelle ein-/ausblenden");
    bookShowMediaColumnToggleButton = BaseIcon.COLUMN_MEDIA.toggleButton("Medienspalte ein-/ausblenden");
    bookShowMediaColumnToggleButton.setSelected(true);
    bookAuthorModeButton = BaseIcon.AUTHOR.button("Personenfilter umschalten");

    bookMediaTypeField = new TextField();
    bookMediaTypeField.getStyleClass().add("zettel-book-media-type-field");
    bookMediaTypeField.setPromptText("Medium");

    bookMediaTypeMenu = new ContextMenu();
    bookMediaTypeMenu.setAutoHide(true);

    Region bookToolbarSpacer = new Region();
    HBox.setHgrow(bookToolbarSpacer, Priority.ALWAYS);

    bookToolBar.getItems().addAll(
        bookShowNotesToggleButton,
        bookShowMediaColumnToggleButton,
        bookAuthorModeButton,
        new Separator(Orientation.VERTICAL),
        bookMediaTypeField,
        bookToolbarSpacer
    );

    bookTitleField = new TextField();
    bookTitleField.getStyleClass().add("zettel-book-title-field");
    bookTitleField.setPromptText("Titel");

    bookAuthorRowsBox = new VBox(6);
    bookAuthorRowsBox.getStyleClass().add("zettel-book-author-rows");

    bookAuthorScrollPane = new ScrollPane(bookAuthorRowsBox);
    bookAuthorScrollPane.getStyleClass().add("zettel-book-author-scroll");
    bookAuthorScrollPane.setFitToWidth(true);
    bookAuthorScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    bookAuthorScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    bookAuthorScrollPane.setPrefViewportHeight(BOOK_AUTHOR_VIEWPORT_HEIGHT);
    bookAuthorScrollPane.setMinViewportHeight(BOOK_AUTHOR_VIEWPORT_HEIGHT);
    bookAuthorScrollPane.setMaxHeight(BOOK_AUTHOR_VIEWPORT_HEIGHT + 2);

    bookAuthorHost = new VBox(6, bookAuthorScrollPane);
    bookAuthorHost.getStyleClass().add("zettel-book-author-host");
    bookAuthorHost.setVisible(false);
    bookAuthorHost.setManaged(false);

    bookStatusLabel = new Label("");
    bookStatusLabel.getStyleClass().add("zettel-book-status");

    bookMediaTable = new TableView<>();
    bookMediaTable.getStyleClass().add("zettel-book-media-table");
    bookMediaTable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
    bookMediaTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

    bookMediaAuthorColumn = new TableColumn<>("Autor");
    bookMediaAuthorColumn.setId("bookMediaAuthorColumn");
    bookMediaAuthorColumn.setCellValueFactory(cell ->
                                                  new ReadOnlyStringWrapper(cell.getValue().getAuthorDisplay())
    );

    bookMediaTitleColumn = new TableColumn<>("Titel");
    bookMediaTitleColumn.setId("bookMediaTitleColumn");
    bookMediaTitleColumn.setCellValueFactory(cell ->
                                                 new ReadOnlyStringWrapper(cell.getValue().getTitle())
    );

    bookMediaTypeColumn = new TableColumn<>("Medium");
    bookMediaTypeColumn.setId("bookMediaTypeColumn");
    bookMediaTypeColumn.setCellValueFactory(cell ->
                                                new ReadOnlyStringWrapper(cell.getValue().getMediaTypeName())
    );

    bookMediaNoteCountColumn = new TableColumn<>("Zettel");
    bookMediaNoteCountColumn.setId("bookMediaNoteCountColumn");
    bookMediaNoteCountColumn.setStyle("-fx-alignment: CENTER-RIGHT;");
    bookMediaNoteCountColumn.setCellValueFactory(cell ->
                                                     new ReadOnlyIntegerWrapper(cell.getValue().getNoteCount())
    );

    bookMediaTable.getColumns().addAll(
        bookMediaAuthorColumn,
        bookMediaTitleColumn,
        bookMediaTypeColumn,
        bookMediaNoteCountColumn
    );

    bookMediaAuthorColumn.setSortType(TableColumn.SortType.ASCENDING);
    bookMediaTable.getSortOrder().setAll(bookMediaAuthorColumn);

    bookNotesPane = new BorderPane();
    bookNotesPane.getStyleClass().add("zettel-book-notes-pane");

    bookNotesTable = new TableView<>();
    bookNotesTable.getStyleClass().add("zettel-book-notes-table");
    bookNotesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

    bookNotesIdColumn = new TableColumn<>("Zettel");
    bookNotesIdColumn.setId("bookNotesIdColumn");
    bookNotesIdColumn.setStyle("-fx-alignment: CENTER-RIGHT;");
    bookNotesIdColumn.setCellValueFactory(cell ->
                                              new ReadOnlyIntegerWrapper(cell.getValue().getNoteId())
    );

    bookNotesTitleColumn = new TableColumn<>("Titel");
    bookNotesTitleColumn.setId("bookNotesTitleColumn");
    bookNotesTitleColumn.setCellValueFactory(cell ->
                                                 new ReadOnlyStringWrapper(cell.getValue().getTitle())
    );

    bookNotesTable.getColumns().addAll(bookNotesIdColumn, bookNotesTitleColumn);
    bookNotesIdColumn.setSortType(TableColumn.SortType.ASCENDING);
    bookNotesTable.getSortOrder().setAll(bookNotesIdColumn);
    bookNotesPane.setCenter(bookNotesTable);

    VBox bookMainBox = new VBox(8, bookTitleField, bookAuthorHost, bookStatusLabel, bookMediaTable);
    bookMainBox.getStyleClass().add("zettel-book-main-box");
    VBox.setVgrow(bookMediaTable, Priority.ALWAYS);

    bookSplitPane = new SplitPane(bookMainBox, bookNotesPane);
    bookSplitPane.setOrientation(Orientation.VERTICAL);
    bookSplitPane.getStyleClass().add("zettel-book-split-pane");
    bookSplitPane.setDividerPositions(bookSplitDividerPosition);

    bookPane.setTop(bookToolBar);
    bookPane.setCenter(bookSplitPane);

    setBookNotesVisible(false);
    setBookAuthorMode(BookAuthorMode.OFF);

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

    listLockToggleButton = BaseIcon.LOCK_OPEN.toggleButton("Liste sperren");
    listLockToggleButton.getStyleClass().add("zettel-list-lock-toggle");
    listLockToggleButton.setFocusTraversable(false);

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
        listLockToggleButton,
        new Separator(Orientation.VERTICAL),
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
    updateListLockIcon();

    serviceArea.setTop(serviceAreaToolBar);
    serviceArea.setCenter(serviceAreaContentHost);

    workAreaSplitPane.getItems().addAll(noteEditorPane, keywordsTablePane, serviceArea);
    SplitPane.setResizableWithParent(noteEditorPane, false);
    SplitPane.setResizableWithParent(keywordsTablePane, false);
    SplitPane.setResizableWithParent(serviceArea, true);
    root.setCenter(workAreaHost);

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
   * Schaltet die Sichtbarkeit der unteren BOOK-Zetteltabelle.
   *
   * @param visible {@code true}, wenn die Zetteltabelle sichtbar sein soll
   */
  public void setBookNotesVisible(boolean visible) {
    bookNotesPane.setVisible(visible);
    bookNotesPane.setManaged(visible);

    if (!visible) {
      if (bookSplitPane.getItems().contains(bookNotesPane)) {
        bookSplitPane.getItems().remove(bookNotesPane);
      }
      return;
    }

    if (!bookSplitPane.getItems().contains(bookNotesPane)) {
      bookSplitPane.getItems().add(bookNotesPane);
      final double divider = bookSplitDividerPosition;
      Platform.runLater(() -> bookSplitPane.setDividerPositions(divider));
    }
  }

  /**
   * Setzt den aktuellen Autorenmodus der BOOK-Maske und synchronisiert
   * Icon, Tooltip und Layout des Autorenbereichs.
   *
   * @param mode neuer Autorenmodus
   */
  public void setBookAuthorMode(BookAuthorMode mode) {
    activeBookAuthorMode = mode == null ? BookAuthorMode.OFF : mode;

    switch (activeBookAuthorMode) {
      case OFF -> {
        bookAuthorModeButton.setGraphic(BaseIcon.AUTHOR.imageView());
        bookAuthorModeButton.setTooltip(new Tooltip("Personenfilter aus"));
      }
      case SINGLE -> {
        bookAuthorModeButton.setGraphic(BaseIcon.AUTHOR.imageView());
        bookAuthorModeButton.setTooltip(new Tooltip("Ein Personenfilter"));
      }
      case MULTI -> {
        bookAuthorModeButton.setGraphic(BaseIcon.AUTHORS.imageView());
        bookAuthorModeButton.setTooltip(new Tooltip("Mehrere Personenfilter"));
      }
    }

    updateBookAuthorLayoutForMode();
  }

  /**
   * Synchronisiert die Darstellung des BOOK-Autorenbereichs mit dem aktuellen
   * Autorenmodus.
   * Im SINGLE-Modus liegt die einzelne Autorenzeile ohne künstlichen Leerraum
   * direkt über der Medientabelle.
   * Im MULTI-Modus bleibt der Bereich auf zwei sichtbare Zeilen begrenzt.
   */
  private void updateBookAuthorLayoutForMode() {
    switch (activeBookAuthorMode) {
      case OFF -> {
        bookAuthorHost.setVisible(false);
        bookAuthorHost.setManaged(false);

        bookAuthorScrollPane.setFitToHeight(false);
        bookAuthorScrollPane.setPrefViewportHeight(Region.USE_COMPUTED_SIZE);
        bookAuthorScrollPane.setMinViewportHeight(Region.USE_COMPUTED_SIZE);
        bookAuthorScrollPane.setMaxHeight(Region.USE_COMPUTED_SIZE);
      }
      case SINGLE -> {
        bookAuthorHost.setVisible(true);
        bookAuthorHost.setManaged(true);

        bookAuthorScrollPane.setFitToHeight(true);
        bookAuthorScrollPane.setPrefViewportHeight(Region.USE_COMPUTED_SIZE);
        bookAuthorScrollPane.setMinViewportHeight(Region.USE_COMPUTED_SIZE);
        bookAuthorScrollPane.setMaxHeight(Region.USE_COMPUTED_SIZE);
      }
      case MULTI -> {
        bookAuthorHost.setVisible(true);
        bookAuthorHost.setManaged(true);

        bookAuthorScrollPane.setFitToHeight(false);
        bookAuthorScrollPane.setPrefViewportHeight(BOOK_AUTHOR_VIEWPORT_HEIGHT);
        bookAuthorScrollPane.setMinViewportHeight(BOOK_AUTHOR_VIEWPORT_HEIGHT);
        bookAuthorScrollPane.setMaxHeight(BOOK_AUTHOR_VIEWPORT_HEIGHT + 2);
      }
    }
  }

  /**
   * Setzt den Text der BOOK-Statuszeile.
   *
   * @param text Statustext
   */
  public void setBookStatus(String text) {
    bookStatusLabel.setText(text == null ? "" : text);
  }

  /**
   * Schaltet die Sichtbarkeit der Medium-Spalte in der BOOK-Tabelle.
   *
   * @param visible {@code true}, wenn die Spalte sichtbar sein soll
   */
  public void setBookMediaTypeColumnVisible(boolean visible) {
    bookMediaTypeColumn.setVisible(visible);
    bookMediaTypeColumn.setResizable(visible);
  }

  /**
   * Setzt die sichtbaren Zeilen der BOOK-Medientabelle.
   *
   * @param rows anzuzeigende Medienzeilen
   */
  public void setBookMediaRows(List<BookMediaRow> rows) {
    ObservableList<BookMediaRow> items = FXCollections.observableArrayList(
        rows == null ? List.of() : rows
    );
    bookMediaTable.setItems(items);
  }

  /**
   * Setzt die sichtbaren Zeilen der BOOK-Zetteltabelle.
   *
   * @param rows anzuzeigende Zettelzeilen
   */
  public void setBookNoteRows(List<BookNoteRow> rows) {
    ObservableList<BookNoteRow> items = FXCollections.observableArrayList(
        rows == null ? List.of() : rows
    );
    bookNotesTable.setItems(items);
  }

  /**
   * Setzt den aktuell aufgelösten Medientypzustand des BOOK-Feldes.
   * Bei konkreter Auswahl wird die Medienspalte ausgeblendet und der
   * COLUMN_MEDIA-Button deaktiviert.
   *
   * @param resolved ob ein konkreter Medientyp aufgelöst ist
   * @param mediaTypeBibName technischer Medientypname
   */
  public void setBookResolvedMediaTypeSelection(boolean resolved, String mediaTypeBibName) {
    this.bookResolvedMediaTypeSelection = resolved;
    this.bookResolvedMediaTypeBibName = mediaTypeBibName == null ? "" : mediaTypeBibName.trim();

    bookShowMediaColumnToggleButton.setDisable(resolved);

    if (resolved) {
      bookShowMediaColumnToggleButton.setSelected(false);
      setBookMediaTypeColumnVisible(false);
      return;
    }

    setBookMediaTypeColumnVisible(bookShowMediaColumnToggleButton.isSelected());
  }

  /**
   * Liefert, ob aktuell ein konkreter BOOK-Medientyp gewählt ist.
   *
   * @return {@code true}, wenn ein Medientyp aufgelöst ist
   */
  public boolean hasBookResolvedMediaTypeSelection() {
    return bookResolvedMediaTypeSelection;
  }

  /**
   * Baut die BOOK-Autorenzeilen neu auf und registriert die zugehörigen
   * Eingabefelder für den Controller.
   *
   * @param rows anzuzeigende Autorenzeilen
   */
  public void setBookAuthorRows(List<BookAuthorRow> rows) {
    bookAuthorRowsBox.getChildren().clear();
    bookAuthorRowModels.clear();

    List<BookAuthorRow> safeRows = rows == null || rows.isEmpty()
                                       ? List.of(new BookAuthorRow("", ""))
                                       : rows;

    for (BookAuthorRow row : safeRows) {
      TextField lastNameField = new TextField(row.getLastName());
      lastNameField.getStyleClass().add("zettel-book-author-last-name-field");
      lastNameField.setPromptText("Nachname");

      TextField firstNameField = new TextField(row.getFirstName());
      firstNameField.getStyleClass().add("zettel-book-author-first-name-field");
      firstNameField.setPromptText("Vorname");

      ContextMenu suggestionMenu = new ContextMenu();
      suggestionMenu.setAutoHide(true);

      row.setLastNameField(lastNameField);
      row.setFirstNameField(firstNameField);
      row.setLastNameSuggestionMenu(suggestionMenu);

      HBox line = new HBox(8, lastNameField, firstNameField);
      line.getStyleClass().add("zettel-book-author-row");
      HBox.setHgrow(lastNameField, Priority.ALWAYS);
      HBox.setHgrow(firstNameField, Priority.ALWAYS);

      bookAuthorRowsBox.getChildren().add(line);
      bookAuthorRowModels.add(row);
      updateBookAuthorLayoutForMode();
    }
  }

  /**
   * Liefert die aktuell sichtbaren BOOK-Autorenzeilen.
   *
   * @return registrierte Autorenzeilen
   */
  public List<BookAuthorRow> getBookAuthorRowModels() {
    return List.copyOf(bookAuthorRowModels);
  }

  /**
   * Liest die aktuellen Werte aller sichtbaren BOOK-Autorenzeilen aus.
   * @return Snapshot der aktuellen Autorenzeilen
   */
  public List<BookAuthorRow> snapshotBookAuthorRows() {
    List<BookAuthorRow> snapshot = new ArrayList<>();

    for (BookAuthorRow row : bookAuthorRowModels) {
      if (row == null) {
        continue;
      }

      String lastName = row.getLastNameField() == null
                            ? row.getLastName()
                            : row.getLastNameField().getText();

      String firstName = row.getFirstNameField() == null
                             ? row.getFirstName()
                             : row.getFirstNameField().getText();

      snapshot.add(new BookAuthorRow(lastName, firstName));
    }

    return snapshot;
  }

  /**
   * Wählt eine Medienzeile anhand der Eintrags-ID aus oder hebt die Auswahl auf.
   *
   * @param entryId Eintrags-ID oder {@code null} zum Deselektieren
   */
  public void selectBookMediaRow(Integer entryId) {
    if (entryId == null || entryId <= 0) {
      bookMediaTable.getSelectionModel().clearSelection();
      return;
    }

    for (BookMediaRow row : bookMediaTable.getItems()) {
      if (row != null && row.getEntryId() == entryId) {
        bookMediaTable.getSelectionModel().select(row);
        bookMediaTable.scrollTo(row);
        return;
      }
    }

    bookMediaTable.getSelectionModel().clearSelection();
  }

  /**
   * Liefert den primären Sortierzustand der BOOK-Medientabelle.
   *
   * @return aktueller Sortierzustand oder Standardzustand
   */
  public BookMediaTableSortState getBookMediaTableSortState() {
    TableColumn<BookMediaRow, ?> sortedColumn = bookMediaTable.getSortOrder().isEmpty()
                                                    ? bookMediaAuthorColumn
                                                    : bookMediaTable.getSortOrder().getFirst();

    TableColumn.SortType sortType = sortedColumn.getSortType() == null
                                        ? TableColumn.SortType.ASCENDING
                                        : sortedColumn.getSortType();

    return new BookMediaTableSortState(sortedColumn.getId(), sortType);
  }

  /**
   * Wendet einen primären Sortierzustand auf die BOOK-Medientabelle an.
   *
   * @param state gewünschter Sortierzustand
   */
  public void applyBookMediaTableSortState(BookMediaTableSortState state) {
    TableColumn<BookMediaRow, ?> target = resolveBookMediaTableColumn(
        state == null ? "" : state.columnId()
    );

    if (target == null) {
      target = bookMediaAuthorColumn;
    }

    target.setSortType(state == null || state.sortType() == null
                           ? TableColumn.SortType.ASCENDING
                           : state.sortType());

    bookMediaTable.getSortOrder().setAll(target);
    bookMediaTable.sort();
  }

  /**
   * Liefert den primären Sortierzustand der BOOK-Zetteltabelle.
   *
   * @return aktueller Sortierzustand oder Standardzustand
   */
  public BookNoteTableSortState getBookNoteTableSortState() {
    TableColumn<BookNoteRow, ?> sortedColumn = bookNotesTable.getSortOrder().isEmpty()
                                                   ? bookNotesIdColumn
                                                   : bookNotesTable.getSortOrder().getFirst();

    TableColumn.SortType sortType = sortedColumn.getSortType() == null
                                        ? TableColumn.SortType.ASCENDING
                                        : sortedColumn.getSortType();

    return new BookNoteTableSortState(sortedColumn.getId(), sortType);
  }

  /**
   * Wendet einen primären Sortierzustand auf die BOOK-Zetteltabelle an.
   *
   * @param state gewünschter Sortierzustand
   */
  public void applyBookNoteTableSortState(BookNoteTableSortState state) {
    TableColumn<BookNoteRow, ?> target = resolveBookNoteTableColumn(
        state == null ? "" : state.columnId()
    );

    if (target == null) {
      target = bookNotesIdColumn;
    }

    target.setSortType(state == null || state.sortType() == null
                           ? TableColumn.SortType.ASCENDING
                           : state.sortType());

    bookNotesTable.getSortOrder().setAll(target);
    bookNotesTable.sort();
  }

  /**
   * Löst eine BOOK-Medienspalte anhand ihrer ID auf.
   *
   * @param columnId Spalten-ID
   * @return Spalte oder {@code null}
   */
  private TableColumn<BookMediaRow, ?> resolveBookMediaTableColumn(String columnId) {
    if (columnId == null || columnId.isBlank()) {
      return null;
    }

    for (TableColumn<BookMediaRow, ?> column : bookMediaTable.getColumns()) {
      if (columnId.equals(column.getId())) {
        return column;
      }
    }

    return null;
  }

  /**
   * Löst eine BOOK-Zettelspalte anhand ihrer ID auf.
   *
   * @param columnId Spalten-ID
   * @return Spalte oder {@code null}
   */
  private TableColumn<BookNoteRow, ?> resolveBookNoteTableColumn(String columnId) {
    if (columnId == null || columnId.isBlank()) {
      return null;
    }

    for (TableColumn<BookNoteRow, ?> column : bookNotesTable.getColumns()) {
      if (columnId.equals(column.getId())) {
        return column;
      }
    }

    return null;
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
   * Liefert die zuletzt bekannte Breite des rechten Arbeitsbereichs.
   *
   * @return aktuelle oder gespeicherte Service-Breite
   */
  public double getCurrentServiceAreaWidth() {
    if (workAreaSplitPane.getItems().contains(serviceArea)) {
      double measuredWidth = resolveNodeWidth(serviceArea, lastExpandedServiceAreaWidth);
      if (measuredWidth > 1.0) {
        lastExpandedServiceAreaWidth = measuredWidth;
      }
    }
    return lastExpandedServiceAreaWidth <= 1.0 ? DEFAULT_SERVICE_AREA_WIDTH : lastExpandedServiceAreaWidth;
  }

  /**
   * Liefert die Breite, um die das Fenster beim Aufklappen erweitert werden soll.
   *
   * @return bevorzugte Service-Breite
   */
  public double getPreferredServiceAreaWidth() {
    return getCurrentServiceAreaWidth();
  }

  /**
   * Liefert die gesamte Breite, um die das Fenster beim Ein- oder Ausklappen
   * der Service-Area korrigiert werden muss.
   * Diese Breite besteht aus der Service-Area selbst plus dem SplitPane-Teiler,
   * der beim Entfernen des dritten Panels ebenfalls verschwindet.
   *
   * @return Servicebreite inklusive eines horizontalen SplitPane-Teilers
   */
  public double getServiceAreaToggleWidthDelta() {
    double collapsedPrimaryWidth = getRememberedCollapsedPrimaryWidth();
    double expandedWorkAreaWidth = getRememberedExpandedWorkAreaWidth();
    if (collapsedPrimaryWidth > 1.0 && expandedWorkAreaWidth > collapsedPrimaryWidth) {
      return expandedWorkAreaWidth - collapsedPrimaryWidth;
    }

    return WorkAreaSplitLayout.serviceAreaToggleWidthDelta(
        getCurrentServiceAreaWidth(),
        getWorkAreaDividerWidth(),
        DEFAULT_SERVICE_AREA_WIDTH,
        DEFAULT_WORK_AREA_DIVIDER_WIDTH
    );
  }

  /**
   * Merkt sich die aktuellen Pixelbreiten der drei Arbeitsbereiche.
   */
  public void rememberCurrentWorkAreaWidths() {
    lastWorkAreaDividerWidth = getWorkAreaDividerWidth();

    if (!serviceAreaCollapsed && workAreaSplitPane.getItems().contains(serviceArea)) {
      lastExpandedNoteAreaWidth = resolveNodeWidth(noteEditorPane, lastExpandedNoteAreaWidth);
      lastExpandedKeywordAreaWidth = resolveNodeWidth(keywordsTablePane, lastExpandedKeywordAreaWidth);
      lastExpandedServiceAreaWidth = resolveNodeWidth(serviceArea, lastExpandedServiceAreaWidth);
      lastExpandedWorkAreaWidth = resolveNodeWidth(workAreaSplitPane, lastExpandedWorkAreaWidth);
    }

    applyRememberedWorkAreaPreferredWidths();
  }

  /**
   * Stellt die gespeicherten Arbeitsbereichsbreiten im naechsten Layout-Takt
   * erneut her.
   */
  public void restoreRememberedWorkAreaWidthsAfterLayout() {
    Platform.runLater(() -> {
      restoreRememberedWorkAreaDividerPositions();
      Platform.runLater(this::restoreRememberedWorkAreaDividerPositions);
    });
  }

  /**
   * Wendet die gespeicherten Breiten als bevorzugte Breiten der SplitPane-Kinder an.
   */
  private void applyRememberedWorkAreaPreferredWidths() {
    applyPreferredWidth(noteEditorPane, lastExpandedNoteAreaWidth);
    applyPreferredWidth(keywordsTablePane, lastExpandedKeywordAreaWidth);
    applyPreferredWidth(serviceArea, lastExpandedServiceAreaWidth);
  }

  /**
   * Fixiert die beiden primaeren Arbeitsbereiche auf ihre gemerkten Pixelbreiten.
   */
  private void lockPrimaryWorkAreaChildrenToRememberedWidths() {
    applyFixedWidth(noteEditorPane, lastExpandedNoteAreaWidth);
    applyFixedWidth(keywordsTablePane, lastExpandedKeywordAreaWidth);
  }

  /**
   * Gibt die Breitenfixierung der primaeren Arbeitsbereiche fuer manuelles Ziehen frei.
   */
  private void releasePrimaryWorkAreaChildLocks() {
    releaseFixedWidth(noteEditorPane, lastExpandedNoteAreaWidth);
    releaseFixedWidth(keywordsTablePane, lastExpandedKeywordAreaWidth);
  }

  /**
   * Setzt die bevorzugte Breite eines Bereichs, sofern ein belastbarer Wert
   * vorliegt.
   *
   * @param region Arbeitsbereich
   * @param width bevorzugte Breite
   */
  private void applyPreferredWidth(Region region, double width) {
    if (region == null || width <= 1.0) {
      return;
    }

    region.setPrefWidth(width);
  }

  /**
   * Setzt Mindest-, bevorzugte und Maximalbreite eines Bereichs auf denselben Wert.
   *
   * @param region Arbeitsbereich
   * @param width feste Breite
   */
  private void applyFixedWidth(Region region, double width) {
    if (region == null || width <= 1.0) {
      return;
    }

    region.setMinWidth(width);
    region.setPrefWidth(width);
    region.setMaxWidth(width);
  }

  /**
   * Gibt eine zuvor feste Breite wieder frei und behaelt die bevorzugte Breite.
   *
   * @param region Arbeitsbereich
   * @param preferredWidth bevorzugte Breite nach dem Freigeben
   */
  private void releaseFixedWidth(Region region, double preferredWidth) {
    if (region == null) {
      return;
    }

    region.setMinWidth(Region.USE_COMPUTED_SIZE);
    region.setMaxWidth(Double.MAX_VALUE);
    applyPreferredWidth(region, preferredWidth);
  }

  /**
   * Stellt die gespeicherten SplitPane-Teiler fuer den aktuellen Klappzustand her.
   */
  private void restoreRememberedWorkAreaDividerPositions() {
    applyRememberedWorkAreaPreferredWidths();
    if (serviceAreaCollapsed) {
      lockWorkAreaToRememberedPrimaryWidth();
      restoreCollapsedDividerPositionFromWidths();
    } else {
      releaseWorkAreaWidthLock();
      restoreExpandedDividerPositionsFromWidths();
    }
  }

  /**
   * Fixiert die SplitPane-Breite im eingeklappten Zustand auf die gespeicherten
   * Breiten von Zettel- und Stichwortbereich plus einen sichtbaren Teiler.
   * Ueberschuessige Fensterbreite bleibt dadurch ausserhalb der SplitPane und
   * kann die Stichwortspalte nicht vergroessern.
   */
  private void lockWorkAreaToRememberedPrimaryWidth() {
    double targetWidth = getRememberedCollapsedPrimaryWidth();
    if (targetWidth <= 1.0) {
      return;
    }

    lockPrimaryWorkAreaChildrenToRememberedWidths();
    workAreaSplitPane.setMinWidth(targetWidth);
    workAreaSplitPane.setPrefWidth(targetWidth);
    workAreaSplitPane.setMaxWidth(targetWidth);
  }

  /**
   * Liefert die gemerkte Zielbreite der eingeklappten Primaerbereiche.
   *
   * @return Breite von Zettelanzeige, Stichwortanzeige und einem sichtbaren Teiler
   */
  private double getRememberedCollapsedPrimaryWidth() {
    return WorkAreaSplitLayout.collapsedPrimaryWidth(
        lastExpandedNoteAreaWidth,
        lastExpandedKeywordAreaWidth,
        getStableWorkAreaDividerWidth()
    );
  }

  /**
   * Liefert eine belastbare aufgeklappte Arbeitsflaechenbreite.
   *
   * @return aktuelle oder zuletzt gemerkte Breite der aufgeklappten SplitPane
   */
  private double getRememberedExpandedWorkAreaWidth() {
    double currentWidth = 0.0;
    if (!serviceAreaCollapsed && workAreaSplitPane.getItems().contains(serviceArea)) {
      currentWidth = resolveNodeWidth(workAreaSplitPane, lastExpandedWorkAreaWidth);
    }
    double resolvedWidth = WorkAreaSplitLayout.resolvePositiveWidth(currentWidth, lastExpandedWorkAreaWidth);
    if (resolvedWidth > 1.0) {
      lastExpandedWorkAreaWidth = resolvedWidth;
    }
    return lastExpandedWorkAreaWidth;
  }

  /**
   * Liefert eine Teilerbreite, die fuer den Wechsel zwischen zwei und drei Panels stabil ist.
   *
   * @return stabile Teilerbreite
   */
  private double getStableWorkAreaDividerWidth() {
    return Math.max(
        WorkAreaSplitLayout.resolveDividerWidth(0.0, lastWorkAreaDividerWidth, DEFAULT_WORK_AREA_DIVIDER_WIDTH),
        DEFAULT_WORK_AREA_DIVIDER_WIDTH
    );
  }

  /**
   * Gibt die SplitPane-Breite wieder frei, sobald die Service-Area sichtbar ist.
   * Dann nimmt ausschliesslich die rechte Service-Area zusaetzliche Breite auf.
   */
  private void releaseWorkAreaWidthLock() {
    workAreaSplitPane.setMinWidth(Region.USE_COMPUTED_SIZE);
    workAreaSplitPane.setPrefWidth(Region.USE_COMPUTED_SIZE);
    workAreaSplitPane.setMaxWidth(Double.MAX_VALUE);
  }

  /**
   * Ermittelt eine nutzbare Breite eines Knotens mit Fallback.
   *
   * @param node zu messender Knoten
   * @param fallback Fallback-Breite
   * @return positive Breite oder Fallback
   */
  private double resolveNodeWidth(Node node, double fallback) {
    if (node == null) {
      return fallback;
    }

    double width = node.getBoundsInParent().getWidth();
    if (width <= 1.0) {
      width = node.getLayoutBounds().getWidth();
    }
    if (width <= 1.0) {
      width = fallback;
    }
    return width;
  }

  /**
   * Ermittelt die aktuelle Breite eines horizontalen SplitPane-Teilers.
   *
   * @return gemessene Teilerbreite oder stabiler Fallback
   */
  private double getWorkAreaDividerWidth() {
    for (Node divider : workAreaSplitPane.lookupAll(".split-pane-divider")) {
      double width = resolveNodeWidth(divider, 0.0);
      double resolvedWidth = WorkAreaSplitLayout.resolveDividerWidth(
          width,
          lastWorkAreaDividerWidth,
          DEFAULT_WORK_AREA_DIVIDER_WIDTH
      );
      if (resolvedWidth > 1.0) {
        lastWorkAreaDividerWidth = resolvedWidth;
        return resolvedWidth;
      }
    }

    lastWorkAreaDividerWidth = WorkAreaSplitLayout.resolveDividerWidth(
        0.0,
        lastWorkAreaDividerWidth,
        DEFAULT_WORK_AREA_DIVIDER_WIDTH
    );
    return lastWorkAreaDividerWidth;
  }

  /**
   * Setzt die SplitPane-Teiler anhand der gespeicherten Pixelbreiten.
   */
  private void restoreExpandedDividerPositionsFromWidths() {
    applyRememberedWorkAreaPreferredWidths();
    lockPrimaryWorkAreaChildrenToRememberedWidths();
    double totalWidth = workAreaSplitPane.getWidth();
    if (totalWidth <= 1.0) {
      double firstDivider = expandedDividerPositions.length > 0 ? expandedDividerPositions[0] : 0.33;
      double secondDivider = expandedDividerPositions.length > 1 ? expandedDividerPositions[1] : 0.66;
      workAreaSplitPane.setDividerPositions(firstDivider, secondDivider);
      installWorkAreaDividerManualResizeHandlers();
      return;
    }

    double noteWidth = WorkAreaSplitLayout.resolvePositiveWidth(lastExpandedNoteAreaWidth, totalWidth / 3.0);
    double keywordWidth = WorkAreaSplitLayout.resolvePositiveWidth(lastExpandedKeywordAreaWidth, totalWidth / 3.0);
    double dividerWidth = getStableWorkAreaDividerWidth();

    double firstDivider = WorkAreaSplitLayout.firstExpandedDividerPosition(noteWidth, totalWidth, dividerWidth);
    double secondDivider = WorkAreaSplitLayout.secondExpandedDividerPosition(
        noteWidth,
        keywordWidth,
        totalWidth,
        dividerWidth,
        firstDivider
    );

    workAreaSplitPane.setDividerPositions(firstDivider, secondDivider);
    installWorkAreaDividerManualResizeHandlers();
  }

  /**
   * Setzt den Teiler im eingeklappten Zwei-Spalten-Zustand anhand der gespeicherten Breiten.
   */
  private void restoreCollapsedDividerPositionFromWidths() {
    applyRememberedWorkAreaPreferredWidths();
    double totalWidth = workAreaSplitPane.getWidth();
    if (totalWidth <= 1.0) {
      return;
    }

    double noteWidth = WorkAreaSplitLayout.resolvePositiveWidth(lastExpandedNoteAreaWidth, totalWidth / 2.0);
    workAreaSplitPane.setDividerPositions(WorkAreaSplitLayout.collapsedDividerPosition(
        noteWidth,
        totalWidth,
        getStableWorkAreaDividerWidth()
    ));
    installWorkAreaDividerManualResizeHandlers();
  }

  /**
   * Installiert Handler, die bei manueller SplitPane-Bedienung die Breitenfixierung freigeben.
   */
  private void installWorkAreaDividerManualResizeHandlers() {
    for (Node divider : workAreaSplitPane.lookupAll(".split-pane-divider")) {
      if (divider.getProperties().containsKey(WORK_AREA_DIVIDER_HANDLER_KEY)) {
        continue;
      }
      divider.getProperties().put(WORK_AREA_DIVIDER_HANDLER_KEY, Boolean.TRUE);
      divider.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> releasePrimaryWorkAreaChildLocks());
      divider.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
        rememberVisiblePrimaryWorkAreaWidths();
        lockPrimaryWorkAreaChildrenToRememberedWidths();
        if (serviceAreaCollapsed) {
          lockWorkAreaToRememberedPrimaryWidth();
        }
      });
    }
  }

  /**
   * Merkt sich die aktuell sichtbaren Primaerbreiten nach einer manuellen Divider-Aktion.
   */
  private void rememberVisiblePrimaryWorkAreaWidths() {
    lastExpandedNoteAreaWidth = resolveNodeWidth(noteEditorPane, lastExpandedNoteAreaWidth);
    lastExpandedKeywordAreaWidth = resolveNodeWidth(keywordsTablePane, lastExpandedKeywordAreaWidth);
    if (!serviceAreaCollapsed && workAreaSplitPane.getItems().contains(serviceArea)) {
      lastExpandedServiceAreaWidth = resolveNodeWidth(serviceArea, lastExpandedServiceAreaWidth);
      lastExpandedWorkAreaWidth = resolveNodeWidth(workAreaSplitPane, lastExpandedWorkAreaWidth);
    }
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
      rememberCurrentWorkAreaWidths();
      expandedDividerPositions = workAreaSplitPane.getDividerPositions();
      workAreaSplitPane.getItems().remove(serviceArea);
      this.serviceAreaCollapsed = true;
      lockWorkAreaToRememberedPrimaryWidth();
      Platform.runLater(this::restoreCollapsedDividerPositionFromWidths);
    } else {
      rememberCurrentWorkAreaWidths();
      releaseWorkAreaWidthLock();
      if (!workAreaSplitPane.getItems().contains(serviceArea)) {
        workAreaSplitPane.getItems().add(serviceArea);
        SplitPane.setResizableWithParent(serviceArea, true);
      }

      this.serviceAreaCollapsed = false;
      Platform.runLater(this::restoreExpandedDividerPositionsFromWidths);
    }

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
   * Scrollt die aktuell sichtbare LIST-Ansicht an den Anfang.
   */
  public void scrollListToTop() {
    Platform.runLater(() -> {
      listPageKeyScrollPane.setVvalue(0.0);
      listFilterTable.scrollTo(0);
    });
  }

  /**
   * Wendet die Hervorhebungsstyleklasse auf eine KEY-Zeile an.
   *
   * @param row Tabellenzeile
   * @param item Datenzeile
   */
  private void applyKeyContextRowStyle(TableRow<KeyTableRow> row, KeyTableRow item) {
    if (row == null) {
      return;
    }

    row.getStyleClass().remove(KEY_CONTEXT_ROW_STYLE_CLASS);
    if (!row.isEmpty() && item != null && item.isContextRow()) {
      row.getStyleClass().add(KEY_CONTEXT_ROW_STYLE_CLASS);
    }
  }

  /**
   * Sortiert die KEY-Tabelle, ohne die hervorgehobene Zusatzliste mit der
   * regulären Schlagwortliste zu vermischen.
   *
   * @param table KEY-Tabelle
   * @return {@code true}, wenn die Sortierung verarbeitet wurde
   */
  private boolean sortKeyTablePreservingContextRows(TableView<KeyTableRow> table) {
    if (table == null || table.getSortOrder().isEmpty() || table.getItems() == null) {
      return true;
    }

    Comparator<KeyTableRow> comparator = createKeyTableComparator(table);
    if (comparator == null) {
      return true;
    }

    List<KeyTableRow> contextRows = table.getItems().stream()
                                         .filter(KeyTableRow::isContextRow)
                                         .sorted(comparator)
                                         .toList();
    List<KeyTableRow> regularRows = table.getItems().stream()
                                         .filter(row -> !row.isContextRow())
                                         .sorted(comparator)
                                         .toList();

    ObservableList<KeyTableRow> sortedRows = FXCollections.observableArrayList();
    sortedRows.addAll(contextRows);
    sortedRows.addAll(regularRows);
    table.getItems().setAll(sortedRows);
    clearTransientKeyTableFocus(table);
    return true;
  }

  /**
   * Entfernt die temporäre Fokus- und Auswahlmarkierung, die JavaFX nach
   * Header-Sortierungen auf die erste Tabellenzeile setzen kann.
   *
   * @param table KEY-Tabelle
   */
  private void clearTransientKeyTableFocus(TableView<KeyTableRow> table) {
    if (table == null) {
      return;
    }

    table.getSelectionModel().clearSelection();
    if (table.getFocusModel() != null) {
      table.getFocusModel().focus(-1);
    }
    Platform.runLater(() -> {
      table.getSelectionModel().clearSelection();
      if (table.getFocusModel() != null) {
        table.getFocusModel().focus(-1);
      }
    });
  }

  /**
   * Erstellt den primären KEY-Comparator aus der aktuell sortierten Spalte.
   *
   * @param table KEY-Tabelle
   * @return Comparator oder {@code null}, wenn keine Sortierspalte aktiv ist
   */
  private Comparator<KeyTableRow> createKeyTableComparator(TableView<KeyTableRow> table) {
    if (table == null || table.getSortOrder().isEmpty()) {
      return null;
    }

    TableColumn<KeyTableRow, ?> column = table.getSortOrder().getFirst();
    Comparator<KeyTableRow> comparator = "count".equals(column.getId())
        ? Comparator.comparingInt(KeyTableRow::getCount)
        : Comparator.comparing(row -> row.getKeyword() == null ? "" : row.getKeyword(),
                               String.CASE_INSENSITIVE_ORDER);

    comparator = comparator.thenComparing(row -> row.getKeyword() == null ? "" : row.getKeyword(),
                                          String.CASE_INSENSITIVE_ORDER)
                           .thenComparingInt(KeyTableRow::getKeywordId);

    if (column.getSortType() == TableColumn.SortType.DESCENDING) {
      comparator = comparator.reversed();
    }

    return comparator;
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
   * Setzt den Handler fuer Alt-Klicks auf KEY-Schlagwortzeilen.
   *
   * @param handler Handler
   */
  public void setKeyAltClickHandler(Consumer<String> handler) {
    this.keyAltClickHandler = handler == null ? keyword -> {} : handler;
  }

  /**
   * Aktualisiert das Icon des LIST-Sperrbuttons.
   */
  public void updateListLockIcon() {
    if (listLockToggleButton.isSelected()) {
      listLockToggleButton.setGraphic(BaseIcon.LOCK.imageView());
      listLockToggleButton.setTooltip(new Tooltip("Liste freigeben"));
    } else {
      listLockToggleButton.setGraphic(BaseIcon.LOCK_OPEN.imageView());
      listLockToggleButton.setTooltip(new Tooltip("Liste sperren"));
    }
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
    private boolean committing;

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
      if (!isEditing() || committing) {
        return;
      }

      KeyTableRow row = getTableRow() == null ? null : getTableRow().getItem();
      if (row == null || keyKeywordEditCommitHandler == null) {
        cancelEdit();
        return;
      }

      String newValue = textField.getText();
      committing = true;
      try {
        boolean accepted = keyKeywordEditCommitHandler.handleCommit(row, newValue);

        if (accepted) {
          super.commitEdit(newValue);
        } else {
          Platform.runLater(() -> reopenKeyKeywordEdit(row.getKeywordId(), newValue));
        }
      } finally {
        committing = false;
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
