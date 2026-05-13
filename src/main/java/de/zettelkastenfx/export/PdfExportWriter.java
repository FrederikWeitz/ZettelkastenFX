package de.zettelkastenfx.export;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import de.zettelkastenfx.export.model.ExportDocument;
import de.zettelkastenfx.export.render.ExportHtmlRenderer;

import java.awt.Font;
import java.awt.FontFormatException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

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
        PdfRendererBuilder builder = new PdfRendererBuilder()
            .withHtmlContent(renderer.render(document), outputPath.toUri().toString())
            .toStream(out);
        registerSystemFonts(builder);
        builder.run();
      }
    } catch (IOException e) {
      throw new IllegalStateException("PDF-Export fehlgeschlagen: " + outputPath, e);
    }
  }

  private void registerSystemFonts(PdfRendererBuilder builder) {
    Path fontsDirectory = Path.of(System.getenv().getOrDefault("WINDIR", "C:\\Windows"), "Fonts");
    if (!Files.isDirectory(fontsDirectory)) {
      return;
    }
    try (Stream<Path> fontFiles = Files.list(fontsDirectory)) {
      for (Path fontFile : fontFiles.filter(this::isFontFile).toList()) {
        registerFont(builder, fontFile);
      }
    } catch (IOException ignored) {
      // Ohne Systemfonts faellt OpenHTMLToPDF auf die eingebauten Fonts zurueck.
    }
  }

  private boolean isFontFile(Path path) {
    if (!Files.isRegularFile(path)) {
      return false;
    }
    String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
    return name.endsWith(".ttf") || name.endsWith(".otf");
  }

  private void registerFont(PdfRendererBuilder builder, Path path) {
    try {
      Font font = Font.createFont(Font.TRUETYPE_FONT, path.toFile());
      String fontName = font.getFontName(Locale.ENGLISH).toLowerCase(Locale.ROOT);
      int weight = font.isBold() || fontName.contains("bold") ? 700 : 400;
      FontStyle style = font.isItalic() || fontName.contains("italic") || fontName.contains("oblique")
          ? FontStyle.ITALIC
          : FontStyle.NORMAL;
      for (String family : fontFamilies(font)) {
        builder.useFont(path.toFile(), family, weight, style, true);
      }
    } catch (FontFormatException | IOException ignored) {
      // Einzelne defekte oder fuer AWT unlesbare Fonts duerfen den Export nicht abbrechen.
    }
  }

  private Set<String> fontFamilies(Font font) {
    Set<String> families = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    families.add(font.getFamily());
    families.add(font.getFamily(Locale.ENGLISH));
    families.removeIf(String::isBlank);
    return families;
  }
}
