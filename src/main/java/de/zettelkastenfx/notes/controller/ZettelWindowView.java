package de.zettelkastenfx.notes.controller;

import de.zettelkastenfx.base.BaseIcon;
import de.zettelkastenfx.notes.editor.NoteEditorPane;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class ZettelWindowView {

  private final BorderPane root;

  private final VBox topArea;
  private final MenuBar menuBar;
  private final Menu menuFile;
  private final Menu menuEdit;
  private final Menu menuView;

  private final ToolBar topToolBar;
  private final Button toolButtonOne;
  private final Button toolButtonTwo;
  private final Button toolButtonThree;

  private final ToolBar bottomToolBar;

  private final SplitPane workAreaSplitPane;

  private final Region centerArea;
  private final Region rightArea;

  private final NoteEditorPane noteEditorPane;

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

    toolButtonOne = BaseIcon.PAGE_ADD.button();
    toolButtonTwo = BaseIcon.PAGE_COPY.button();
    toolButtonThree = BaseIcon.PAGE_FIND.button();

    topToolBar.getItems().addAll(toolButtonOne, toolButtonTwo, toolButtonThree);

    topArea.getChildren().addAll(menuBar, topToolBar);
    root.setTop(topArea);

    /* Bottom: leere Werkzeugleiste (wird später befüllt) */
    bottomToolBar = new ToolBar();
    bottomToolBar.getStyleClass().add("zettel-bottom-toolbar");
    root.setBottom(bottomToolBar);
    // Arbeitsbereiche: links, mitte, rechts (verschiebbar)
    workAreaSplitPane = new SplitPane();
    workAreaSplitPane.setOrientation(Orientation.HORIZONTAL);
    workAreaSplitPane.getStyleClass().add("zettel-work-split");

    noteEditorPane = new NoteEditorPane();
    noteEditorPane.getStyleClass().add("zettel-work-pane");

    centerArea = createPlaceholderPane("MITTE");
    centerArea.getStyleClass().add("zettel-work-pane");

    rightArea = createPlaceholderPane("RECHTS");
    rightArea.getStyleClass().add("zettel-work-pane");

    workAreaSplitPane.getItems().addAll(noteEditorPane, centerArea, rightArea);
    root.setCenter(workAreaSplitPane);
  }

  public BorderPane getRoot() {
    return root;
  }

  public SplitPane getWorkAreaSplitPane() {
    return workAreaSplitPane;
  }

  public NoteEditorPane getNoteEditorPane() {
    return noteEditorPane;
  }

  public Region getCenterArea() {
    return centerArea;
  }

  public Region getRightArea() {
    return rightArea;
  }

  public Menu getMenuFile() {
    return menuFile;
  }

  public Menu getMenuEdit() {
    return menuEdit;
  }

  public Menu getMenuView() {
    return menuView;
  }

  public ToolBar getBottomToolBar() {
    return bottomToolBar;
  }

  public Button getToolButtonOne() {
    return toolButtonOne;
  }

  public Button getToolButtonTwo() {
    return toolButtonTwo;
  }

  public Button getToolButtonThree() {
    return toolButtonThree;
  }

  private MenuButton createMenuButton(String text, String styleClass) {
    MenuButton menuButton = new MenuButton(text);
    menuButton.getStyleClass().addAll("zettel-menu-button", styleClass);
    menuButton.setMaxWidth(Double.MAX_VALUE);
    return menuButton;
  }

  private Region createPlaceholderPane(String title) {
    Label label = new Label(title);
    label.getStyleClass().add("zettel-placeholder-label");

    StackPane pane = new StackPane(label);
    pane.setPadding(new Insets(12));
    return pane;
  }
}
