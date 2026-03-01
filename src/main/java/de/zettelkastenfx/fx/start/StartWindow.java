package de.zettelkastenfx.fx.start;

import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class StartWindow {

  public void show(Stage stage) {
    StartWindowView view = new StartWindowView();
    StartWindowController controller = new StartWindowController(stage, view);

    Scene scene = new Scene(view.getRoot(), controller.getCollapsedDiameter(), controller.getHeight());
    scene.setFill(Color.TRANSPARENT);
    scene.getStylesheets().add(getClass().getResource("/styles/start-window.css").toExternalForm());

    stage.initStyle(StageStyle.TRANSPARENT);
    stage.setResizable(false);
    stage.setAlwaysOnTop(false);
    stage.setScene(scene);

    // Startposition: links oben. Später gerne konfigurierbar.
    stage.setX(24);
    stage.setY(24);

    controller.applyInitialState();
    stage.show();
  }
}
