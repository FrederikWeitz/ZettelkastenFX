package de.zettelkastenfx.export;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import de.zettelkastenfx.export.model.ExportDocument;
import de.zettelkastenfx.export.render.ExportHtmlRenderer;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Schreibt Exportdokumente als PDF-Dateien.
 */
public class PdfExportWriter implements NoteExportWriter {

  private final ExportHtmlRenderer renderer = new ExportHtmlRenderer();

  /**
   * Schreibt ein Exportdokument als PDF.
   *
   * @param document Exportdokument
   * @param outputPath Zielpfad
   */
  @Override
  public void write(ExportDocument document, Path outputPath) {
    try {
      Files.createDirectories(outputPath.toAbsolutePath().getParent());
      try (OutputStream out = Files.newOutputStream(outputPath)) {
        new PdfRendererBuilder()
            .withHtmlContent(renderer.render(document), outputPath.toUri().toString())
            .toStream(out)
            .run();
      }
    } catch (IOException e) {
      throw new IllegalStateException("PDF-Export fehlgeschlagen: " + outputPath, e);
    }
  }
}
