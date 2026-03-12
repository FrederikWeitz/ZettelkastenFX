package de.zettelkastenfx.notes.controller;

import de.zettelkastenfx.base.BaseIcon;
import de.zettelkastenfx.notes.bottom.BottomToolbar;
import de.zettelkastenfx.notes.editor.NoteEditorPane;
import de.zettelkastenfx.notes.keywords.KeywordsTablePane;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import lombok.Getter;

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

  @Getter private final MenuButton toolMenuFollowingPages;
  @Getter private final MenuItem miFollowing;
  @Getter private final MenuItem miIdea;
  @Getter private final MenuItem miQuestion;
  @Getter private final MenuItem miCritique;
  @Getter private final MenuItem miTask;

  @Getter private final BottomToolbar bottomToolbar;

  @Getter private final SplitPane workAreaSplitPane;

  @Getter private final Region rightArea;

  @Getter private final NoteEditorPane noteEditorPane;
  @Getter private final KeywordsTablePane keywordsTablePane;

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

    topToolBar.getItems().addAll(
        toolButtonPageAdd, toolButtonPageCopy, toolButtonPageWithBibliography,
        toolMenuFollowingPages);

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

    rightArea = createPlaceholderPane("RECHTS");
    rightArea.getStyleClass().add("zettel-work-pane");

    workAreaSplitPane.getItems().addAll(noteEditorPane, keywordsTablePane, rightArea);
    root.setCenter(workAreaSplitPane);
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
