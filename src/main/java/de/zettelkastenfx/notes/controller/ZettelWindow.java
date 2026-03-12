package de.zettelkastenfx.notes.controller;

import de.zettelkastenfx.base.util.PersistenceUtil;
import de.zettelkastenfx.persistence.DbBootstrap;
import de.zettelkastenfx.persistence.NoteRepository;
import javafx.scene.Scene;
import javafx.stage.Stage;

import javax.sql.DataSource;

public class ZettelWindow {

  public void show(Stage stage) {
    ZettelWindowView view = new ZettelWindowView();
    DataSource ds = DbBootstrap.initAndMigrate();
    PersistenceUtil.setDatasource(ds);

    ZettelWindowController controller = new ZettelWindowController(stage, view);

    Scene scene = new Scene(view.getRoot(), 1100, 720);
    scene.getStylesheets().add(getClass().getResource("/styles/zettel-window.css").toExternalForm());
    view.getRoot().setFocusTraversable(true);

    stage.setTitle("Zettel");
    stage.setResizable(true);
    stage.setScene(scene);

    controller.applyInitialState();

    stage.centerOnScreen();
    stage.show();
  }
}
