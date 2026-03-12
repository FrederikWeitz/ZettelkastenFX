package de.zettelkastenfx.base.util;

import javafx.application.Application;
import lombok.Getter;
import lombok.Setter;

public class BrowserConnection {
  @Getter @Setter private static Application appInstance;

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
