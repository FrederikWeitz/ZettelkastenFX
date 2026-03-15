package de.zettelkastenfx.notes.controller;

import de.zettelkastenfx.base.BaseIcon;
import de.zettelkastenfx.notes.bottom.BottomToolbar;
import de.zettelkastenfx.notes.editor.NoteEditorPane;
import de.zettelkastenfx.notes.keywords.KeywordsTablePane;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import lombok.Getter;

public class ZettelWindowView {

  // public static Object ServiceAreaMode;

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

  // Zustand für die Service-Area
  public enum ServiceAreaMode {
    MAGNIFY_LINK,
    KEY,
    BOOK,
    LIST,
    PERFORMANCE_ANALYSIS
  }

  @Getter private final NoteEditorPane noteEditorPane;
  @Getter private final KeywordsTablePane keywordsTablePane;

  // Service-Area und Zustandsvariablen dazu
  @Getter private final BorderPane serviceArea;
  @Getter private boolean serviceAreaCollapsed;
  private ServiceAreaMode activeServiceAreaMode;
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

    // Spaver für die Toolbar
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

    serviceArea.setTop(serviceAreaToolBar);
    serviceArea.setCenter(serviceAreaContentHost);

    workAreaSplitPane.getItems().addAll(noteEditorPane, keywordsTablePane, serviceArea);
    root.setCenter(workAreaSplitPane);

    setServiceAreaContent(createModePlaceholder(ServiceAreaMode.MAGNIFY_LINK));
    setActiveServiceAreaMode(ServiceAreaMode.MAGNIFY_LINK);
    updateServiceAreaToggleButton();
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
   * Liefert den aktuell aktiven Modus der rechten Seitenleiste.
   *
   * @return aktiver Modus oder {@code null}
   */
  public ServiceAreaMode getActiveServiceAreaMode() {
    return activeServiceAreaMode;
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
   * Prüft, ob der rechte Arbeitsbereich aktuell eingeklappt ist.
   *
   * @return {@code true}, wenn der rechte Bereich ausgeblendet ist
   */
  public boolean isServiceAreaCollapsed() {
    return serviceAreaCollapsed;
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
}
