package de.zettelkastenfx.notes.controller;

import javafx.scene.Scene;
import javafx.stage.Stage;

public class ZettelWindow {

  /**
   * Handle auf ein geoeffnetes Zettelfenster fuer spaetere Profilaenderungen.
   */
  public static final class ZettelWindowHandle {
    private final Stage stage;
    private final ZettelWindowView view;

    /**
     * Erzeugt ein Handle fuer ein Zettelfenster.
     *
     * @param stage Stage des Zettelfensters
     * @param view View des Zettelfensters
     */
    private ZettelWindowHandle(Stage stage, ZettelWindowView view) {
      this.stage = stage;
      this.view = view;
    }

    /**
     * Aktualisiert Fenstertitel und Akzentfarbe des Zettelfensters.
     *
     * @param windowTitle neuer Fenstertitel
     * @param borderColor neue Akzentfarbe
     */
    public void applyWindowProfile(String windowTitle, String borderColor) {
      stage.setTitle(resolveWindowTitle(windowTitle));
      view.applyAccentColor(borderColor);
    }

    /**
     * Liefert die Stage des geoeffneten Zettelfensters.
     *
     * @return Stage des Zettelfensters
     */
    public Stage stage() {
      return stage;
    }
  }

  /**
   * Oeffnet das Zettelfenster mit Standardtitel und Standarddarstellung.
   *
   * @param stage Ziel-Stage
   * @return Handle des geoeffneten Zettelfensters
   */
  public ZettelWindowHandle show(Stage stage) {
    return show(stage, null, null);
  }

  /**
   * Oeffnet das Zettelfenster mit optionalem Titel und optionaler Randfarbe.
   *
   * @param stage Ziel-Stage
   * @param windowTitle gewuenschter Titel; leer oder {@code null} ergibt den Standardtitel
   * @param borderColor gewuenschte Randfarbe als CSS-Wert; leer oder {@code null} belaesst die Standarddarstellung
   * @return Handle des geoeffneten Zettelfensters
   */
  public ZettelWindowHandle show(Stage stage, String windowTitle, String borderColor) {
    ZettelWindowView view = new ZettelWindowView();
    ZettelWindowController controller = new ZettelWindowController(stage, view);
    ZettelWindowHandle handle = new ZettelWindowHandle(stage, view);

    Scene scene = new Scene(view.getRoot(), 1100, 720);
    scene.getStylesheets().add(getClass().getResource("/styles/zettel-window.css").toExternalForm());
    view.getRoot().setFocusTraversable(true);

    stage.setResizable(true);
    stage.setScene(scene);
    handle.applyWindowProfile(windowTitle, borderColor);

    controller.applyInitialState();

    stage.centerOnScreen();
    stage.show();
    return handle;
  }

  /**
   * Ermittelt den effektiven Fenstertitel.
   *
   * @param windowTitle gewuenschter Titel
   * @return bereinigter Titel oder Standardtitel
   */
  private static String resolveWindowTitle(String windowTitle) {
    return (windowTitle == null || windowTitle.isBlank()) ? "Zettel" : windowTitle;
  }
}
