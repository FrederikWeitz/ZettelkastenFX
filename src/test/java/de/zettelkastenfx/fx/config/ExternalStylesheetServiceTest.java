package de.zettelkastenfx.fx.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueft die Bereitstellung der externen CSS-Datei.
 */
class ExternalStylesheetServiceTest {

  @TempDir
  Path tempDir;

  /**
   * Kopiert die Default-CSS nur dann, wenn extern noch keine Datei existiert.
   *
   * @throws Exception bei Dateisystemfehlern
   */
  @Test
  void copiesDefaultStylesheetAndKeepsExistingFile() throws Exception {
    Path stylesheet = ExternalStylesheetService.ensureExternalStylesheet(tempDir);

    assertTrue(Files.exists(stylesheet));
    assertTrue(Files.readString(stylesheet).contains("font-size:"));

    Files.writeString(stylesheet, "body { font-size: 12px; }");
    Path second = ExternalStylesheetService.ensureExternalStylesheet(tempDir);

    assertEquals(stylesheet, second);
    assertEquals("body { font-size: 12px; }", Files.readString(second));
  }

  /**
   * Meldet eine externe CSS-Datei nur dann, wenn sie wirklich existiert.
   *
   * @throws Exception bei Dateisystemfehlern
   */
  @Test
  void findsExternalStylesheetOnlyWhenItExists() throws Exception {
    assertFalse(ExternalStylesheetService.externalStylesheetIfExists(tempDir).isPresent());

    Path stylesheet = tempDir.resolve(ExternalStylesheetService.CONFIG_DIRECTORY_NAME)
                             .resolve(ExternalStylesheetService.STYLESHEET_FILE_NAME);
    Files.createDirectories(stylesheet.getParent());
    Files.writeString(stylesheet, "body { font-size: 15px; }");

    assertEquals(stylesheet, ExternalStylesheetService.externalStylesheetIfExists(tempDir).orElseThrow());
  }
}
