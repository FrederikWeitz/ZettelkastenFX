package de.zettelkastenfx.fx.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * Stellt die externe CSS-Datei neben der Datenbank bereit.
 */
public final class ExternalStylesheetService {

  public static final String CONFIG_DIRECTORY_NAME = "config";
  public static final String STYLESHEET_FILE_NAME = "zettelkastenFX.css";
  private static final String DEFAULT_STYLESHEET_RESOURCE = "/config/" + STYLESHEET_FILE_NAME;
  private static Path configuredAppDataDirectory;

  private ExternalStylesheetService() {
  }

  /**
   * Stellt sicher, dass die externe CSS-Datei im App-Datenordner existiert.
   *
   * @return Pfad zur externen CSS-Datei
   */
  public static Path ensureExternalStylesheet() {
    return ensureExternalStylesheet(AppDirectories.ensureAppDataDirectoryExists());
  }

  /**
   * Liefert die externe CSS-Datei, wenn sie im aktuell konfigurierten App-Datenordner existiert.
   *
   * @return optionaler Pfad zur externen CSS-Datei
   */
  public static Optional<Path> externalStylesheetIfExists() {
    Path appDataDirectory = configuredAppDataDirectory;
    if (appDataDirectory == null) {
      appDataDirectory = AppDirectories.ensureAppDataDirectoryExists();
    }
    return externalStylesheetIfExists(appDataDirectory);
  }

  /**
   * Liefert die externe CSS-Datei, wenn sie im angegebenen App-Datenordner existiert.
   *
   * @param appDataDirectory App-Datenordner
   * @return optionaler Pfad zur externen CSS-Datei
   */
  public static Optional<Path> externalStylesheetIfExists(Path appDataDirectory) {
    if (appDataDirectory == null) {
      return Optional.empty();
    }
    Path stylesheet = appDataDirectory.resolve(CONFIG_DIRECTORY_NAME).resolve(STYLESHEET_FILE_NAME);
    return Files.isRegularFile(stylesheet) ? Optional.of(stylesheet) : Optional.empty();
  }

  /**
   * Stellt sicher, dass die externe CSS-Datei im angegebenen App-Datenordner existiert.
   *
   * @param appDataDirectory App-Datenordner
   * @return Pfad zur externen CSS-Datei
   */
  public static Path ensureExternalStylesheet(Path appDataDirectory) {
    if (appDataDirectory == null) {
      throw new IllegalArgumentException("App-Datenordner darf nicht null sein.");
    }
    configuredAppDataDirectory = appDataDirectory.toAbsolutePath().normalize();
    Path configDirectory = configuredAppDataDirectory.resolve(CONFIG_DIRECTORY_NAME);
    Path stylesheet = configDirectory.resolve(STYLESHEET_FILE_NAME);
    try {
      Files.createDirectories(configDirectory);
      if (Files.exists(stylesheet)) {
        return stylesheet;
      }
      try (InputStream stream = ExternalStylesheetService.class.getResourceAsStream(DEFAULT_STYLESHEET_RESOURCE)) {
        if (stream == null) {
          throw new IllegalStateException("Default-CSS wurde nicht gefunden: " + DEFAULT_STYLESHEET_RESOURCE);
        }
        Files.copy(stream, stylesheet, StandardCopyOption.REPLACE_EXISTING);
      }
      return stylesheet;
    } catch (IOException exception) {
      throw new IllegalStateException("Konnte externe CSS-Datei nicht bereitstellen: " + stylesheet, exception);
    }
  }
}
