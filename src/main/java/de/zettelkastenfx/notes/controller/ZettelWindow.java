package de.zettelkastenfx.notes.controller;

import javafx.scene.Scene;
import javafx.stage.Stage;

public class ZettelWindow {

  /**
   * Öffnet das Zettelfenster mit Standardtitel und Standarddarstellung.
   *
   * @param stage Ziel-Stage
   */
  public void show(Stage stage) {
    show(stage, null, null);
  }

  /**
   * Öffnet das Zettelfenster mit optionalem Titel und optionaler Randfarbe.
   *
   * @param stage Ziel-Stage
   * @param windowTitle gewünschter Titel; leer oder {@code null} ergibt den Standardtitel
   * @param borderColor gewünschte Randfarbe als CSS-Wert; leer oder {@code null} belässt die Standarddarstellung
   */
  public void show(Stage stage, String windowTitle, String borderColor) {
    ZettelWindowView view = new ZettelWindowView();
    ZettelWindowController controller = new ZettelWindowController(stage, view);

    Scene scene = new Scene(view.getRoot(), 1100, 720);
    scene.getStylesheets().add(getClass().getResource("/styles/zettel-window.css").toExternalForm());
    view.getRoot().setFocusTraversable(true);

    String title = (windowTitle == null || windowTitle.isBlank()) ? "Zettel" : windowTitle;

    stage.setTitle(title);
    stage.setResizable(true);
    stage.setScene(scene);

    view.applyAccentColor(borderColor);

    controller.applyInitialState();

    stage.centerOnScreen();
    stage.show();
  }
}