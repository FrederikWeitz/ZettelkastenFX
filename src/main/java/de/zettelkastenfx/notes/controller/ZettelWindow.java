package de.zettelkastenfx.notes.controller;

import javafx.scene.Scene;
import javafx.stage.Stage;

public class ZettelWindow {

  public void show(Stage stage) {
    ZettelWindowView view = new ZettelWindowView();
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
