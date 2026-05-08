package de.zettelkastenfx.export;

import de.zettelkastenfx.export.model.ExportDocument;
import de.zettelkastenfx.export.render.ExportHtmlRenderer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Schreibt Exportdokumente als HTML-Dateien.
 */
public class HtmlExportWriter implements NoteExportWriter {

  private final ExportHtmlRenderer renderer = new ExportHtmlRenderer();

  /**
   * Schreibt ein Exportdokument als HTML.
   *
   * @param document Exportdokument
   * @param outputPath Zielpfad
   */
  @Override
  public void write(ExportDocument document, Path outputPath) {
    try {
      Files.createDirectories(outputPath.toAbsolutePath().getParent());
      Files.writeString(outputPath, renderer.render(document), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("HTML-Export fehlgeschlagen: " + outputPath, e);
    }
  }
}
