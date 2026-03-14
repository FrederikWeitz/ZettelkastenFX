package de.zettelkastenfx;

import de.zettelkastenfx.fx.ZettelkastenFxLauncher;
import javafx.application.Application;

/**
 * Haupt-Einstiegspunkt der Anwendung.
 * Startet ausschließlich den JavaFX-Launcher ohne SpringBoot-Kontext.
 */
public final class ZettelkastenFxApplication {

  private ZettelkastenFxApplication() {
    // Utility-Klasse: keine Instanzierung
  }

  /**
   * Startet die JavaFX-Anwendung.
   *
   * @param args Kommandozeilenargumente
   */
  public static void main(String[] args) {
    Application.launch(ZettelkastenFxLauncher.class, args);
  }
}