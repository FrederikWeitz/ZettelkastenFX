package de.zettelkastenfx.fx.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AppDirectories {

  private AppDirectories() {
  }

  public static Path ensureAppDataDirectoryExists() {
    Path appDirectory = Path.of(System.getProperty("user.home"), ".zettelkastenfx");
    try {
      Files.createDirectories(appDirectory);
    } catch (IOException exception) {
      throw new IllegalStateException("Konnte App-Datenordner nicht anlegen: " + appDirectory, exception);
    }
    return appDirectory;
  }
}