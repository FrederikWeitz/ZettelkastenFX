package de.zettelkastenfx.base.util;

import javafx.application.Application;

/**
 * Buendelt den Zugriff auf die JavaFX-HostServices zum Oeffnen externer Browser-URLs.
 */
public class BrowserConnection {
  private static Application appInstance;

  /**
   * Setzt die laufende JavaFX-Anwendung, deren HostServices verwendet werden.
   *
   * @param appInstance laufende Anwendung
   */
  public static void setAppInstance(Application appInstance) {
    BrowserConnection.appInstance = appInstance;
  }

  /**
   * Oeffnet eine URL im Standardbrowser.
   *
   * @param url zu oeffnende URL
   */
  public static void openBrowser(String url) {
    if (url.isBlank()) {
      return;
    }

    url = url.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*$") ? url : "https://" + url;
    if (appInstance != null) {
      appInstance.getHostServices().showDocument(url);
    }
  }
}
