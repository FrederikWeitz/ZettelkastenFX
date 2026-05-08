package de.zettelkastenfx.export;

import de.zettelkastenfx.export.model.ExportDocument;
import de.zettelkastenfx.export.render.ExportTextRenderer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Schreibt Exportdokumente als RTF-Dateien.
 */
public class RtfExportWriter implements NoteExportWriter {

  private final ExportTextRenderer renderer = new ExportTextRenderer();

  /**
   * Schreibt ein Exportdokument als RTF.
   *
   * @param document Exportdokument
   * @param outputPath Zielpfad
   */
  @Override
  public void write(ExportDocument document, Path outputPath) {
    try {
      Files.createDirectories(outputPath.toAbsolutePath().getParent());
      Files.writeString(outputPath, toRtf(renderer.render(document)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("RTF-Export fehlgeschlagen: " + outputPath, e);
    }
  }

  /**
   * Wandelt zeilenorientierten Text in ein minimales RTF-Dokument.
   *
   * @param text Textinhalt
   * @return RTF-Dokument
   */
  private String toRtf(String text) {
    StringBuilder rtf = new StringBuilder("{\\rtf1\\ansi\\deff0\n");
    for (String line : text.split("\\R", -1)) {
      rtf.append(escape(line)).append("\\par\n");
    }
    rtf.append('}');
    return rtf.toString();
  }

  /**
   * Maskiert eine Textzeile fuer RTF.
   *
   * @param value Rohtext
   * @return RTF-maskierter Text
   */
  private String escape(String value) {
    StringBuilder escaped = new StringBuilder();
    for (char ch : value.toCharArray()) {
      if (ch == '\\' || ch == '{' || ch == '}') {
        escaped.append('\\').append(ch);
      } else if (ch > 127) {
        escaped.append("\\u").append((int) ch).append('?');
      } else if (ch == '\t') {
        escaped.append("\\tab ");
      } else {
        escaped.append(ch);
      }
    }
    return escaped.toString();
  }
}
