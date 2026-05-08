package de.zettelkastenfx.export;

import de.zettelkastenfx.export.model.ExportBibliographyEntry;
import de.zettelkastenfx.export.model.ExportBibliographyField;
import de.zettelkastenfx.export.model.ExportDocument;
import de.zettelkastenfx.export.model.ExportNote;
import de.zettelkastenfx.export.model.ExportParagraph;
import de.zettelkastenfx.export.model.ExportParagraphStyle;
import de.zettelkastenfx.export.model.ExportTextRun;
import de.zettelkastenfx.export.model.ExportTextStyle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Schreibt Exportdokumente als RTF-Dateien.
 */
public class RtfExportWriter implements NoteExportWriter {

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
      Files.writeString(outputPath, toRtf(document), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("RTF-Export fehlgeschlagen: " + outputPath, e);
    }
  }

  /**
   * Wandelt ein Exportdokument in ein RTF-Dokument.
   *
   * @param document Exportdokument
   * @return RTF-Dokument
   */
  private String toRtf(ExportDocument document) {
    ColorTable colors = ColorTable.from(document);
    FontTable fonts = FontTable.from(document);
    StringBuilder rtf = new StringBuilder("{\\rtf1\\ansi\\deff0\n");
    rtf.append(fonts.rtfFontTable());
    rtf.append(colors.rtfColorTable());
    rtf.append("{\\stylesheet{\\s3\\b\\fs28 heading 3;}}\n");
    for (int i = 0; i < document.notes().size(); i++) {
      renderNote(rtf, document.notes().get(i), document, colors, fonts);
      if (document.selection().blankLineBetweenNotes() && i < document.notes().size() - 1) {
        rtf.append("\\par\n");
      }
    }
    if (hasEndBibliography(document)) {
      appendPlainParagraph(rtf, "Bibliographische Angaben", true);
      for (ExportBibliographyEntry entry : document.endBibliography()) {
        renderBibliography(rtf, entry);
        rtf.append("\\par\n");
      }
    }
    rtf.append('}');
    return rtf.toString();
  }

  /**
   * Rendert einen Zettel in RTF.
   *
   * @param rtf Zielpuffer
   * @param note Exportzettel
   * @param document Exportdokument
   */
  private void renderNote(StringBuilder rtf,
                          ExportNote note,
                          ExportDocument document,
                          ColorTable colors,
                          FontTable fonts) {
    String header = headerText(note, document);
    if (!header.isBlank()) {
      appendPlainParagraph(rtf, header, true);
    }
    if (document.selection().includeBody() && !note.bodyText().isBlank()) {
      for (ExportParagraph paragraph : note.bodyParagraphs()) {
        appendStyledParagraph(rtf, paragraph, colors, fonts);
      }
    }
    if (document.selection().includeKeywords() && !note.keywords().isEmpty()) {
      appendPlainParagraph(rtf, "Stichwoerter:", false);
      for (String keyword : note.keywords()) {
        appendPlainParagraph(rtf, "\t" + keyword, false);
      }
    }
    if (document.selection().bibliographyPlacement() == BibliographyPlacement.AFTER_EACH_NOTE
        && note.bibliographyEntry() != null) {
      renderBibliography(rtf, note.bibliographyEntry());
    }
  }

  /**
   * Fuegt einen unformatierten Absatz an.
   *
   * @param rtf Zielpuffer
   * @param text Absatztext
   * @param heading ob Ueberschrift 3 genutzt wird
   */
  private void appendPlainParagraph(StringBuilder rtf, String text, boolean heading) {
    rtf.append(heading ? "\\s3\\b\\fs28 " : "\\pard ");
    rtf.append(escape(text));
    rtf.append(heading ? "\\b0\\fs22\\par\n" : "\\par\n");
  }

  /**
   * Fuegt einen formatierten Absatz an.
   *
   * @param rtf Zielpuffer
   * @param paragraph Exportabsatz
   */
  private void appendStyledParagraph(StringBuilder rtf,
                                     ExportParagraph paragraph,
                                     ColorTable colors,
                                     FontTable fonts) {
    appendParagraphPrefix(rtf, paragraph.style());
    for (ExportTextRun run : paragraph.runs()) {
      appendRun(rtf, run, paragraph.style(), colors, fonts);
    }
    if (paragraph.style().bold()) {
      rtf.append("\\b0 ");
    }
    rtf.append("\\par\n");
  }

  /**
   * Fuegt die Absatzformatierung an.
   *
   * @param rtf Zielpuffer
   * @param style Absatzstil
   */
  private void appendParagraphPrefix(StringBuilder rtf, ExportParagraphStyle style) {
    rtf.append("\\pard ");
    if (style.alignment() != null) {
      rtf.append(switch (style.alignment()) {
        case "center" -> "\\qc ";
        case "right" -> "\\qr ";
        case "justify" -> "\\qj ";
        default -> "\\ql ";
      });
    }
    if (style.indentPixels() > 0) {
      rtf.append("\\li").append(style.indentPixels() * 15).append(' ');
    }
    if (style.fontSizePixels() != null) {
      rtf.append("\\fs").append(style.fontSizePixels() * 2).append(' ');
    }
    if (style.bold()) {
      rtf.append("\\b ");
    }
  }

  /**
   * Fuegt einen formatierten Textbereich an.
   *
   * @param rtf Zielpuffer
   * @param run Export-Run
   */
  private void appendRun(StringBuilder rtf,
                         ExportTextRun run,
                         ExportParagraphStyle paragraphStyle,
                         ColorTable colors,
                         FontTable fonts) {
    ExportTextStyle style = run.style();
    if (style.fontFamily() != null) {
      rtf.append("\\f").append(fonts.indexOf(style.fontFamily())).append(' ');
    }
    if (style.bold()) {
      rtf.append("\\b ");
    }
    if (style.italic()) {
      rtf.append("\\i ");
    }
    if (style.underline()) {
      rtf.append("\\ul ");
    }
    if (style.strikethrough()) {
      rtf.append("\\strike ");
    }
    if (style.textColor() != null) {
      rtf.append("\\cf").append(colors.indexOf(style.textColor())).append(' ');
    }
    if (style.backgroundColor() != null) {
      rtf.append("\\highlight").append(colors.indexOf(style.backgroundColor())).append(' ');
    }
    rtf.append(escape(run.text()));
    if (style.backgroundColor() != null) {
      rtf.append("\\highlight0 ");
    }
    if (style.textColor() != null) {
      rtf.append("\\cf0 ");
    }
    if (style.strikethrough()) {
      rtf.append("\\strike0 ");
    }
    if (style.underline()) {
      rtf.append("\\ul0 ");
    }
    if (style.italic()) {
      rtf.append("\\i0 ");
    }
    if (style.bold()) {
      rtf.append("\\b0 ");
    }
    if (paragraphStyle.bold()) {
      rtf.append("\\b ");
    }
  }

  /**
   * Rendert bibliographische Angaben.
   *
   * @param rtf Zielpuffer
   * @param entry bibliographischer Eintrag
   */
  private void renderBibliography(StringBuilder rtf, ExportBibliographyEntry entry) {
    for (ExportBibliographyField field : entry.fields()) {
      appendPlainParagraph(rtf, field.name() + ": " + field.value(), false);
    }
  }

  /**
   * Prueft, ob eine Endbibliographie ausgegeben wird.
   *
   * @param document Exportdokument
   * @return {@code true}, wenn eine Endbibliographie vorhanden ist
   */
  private boolean hasEndBibliography(ExportDocument document) {
    return (document.selection().bibliographyPlacement() == BibliographyPlacement.END_OF_DOCUMENT
        || document.selection().bibliographyPlacement() == BibliographyPlacement.END_OF_LAST_DOCUMENT
        || document.selection().bibliographyPlacement() == BibliographyPlacement.SEPARATE_FILE)
        && !document.endBibliography().isEmpty();
  }

  /**
   * Erstellt die Kopfzeile eines Zettels entsprechend der Auswahl.
   *
   * @param note Exportzettel
   * @param document Exportdokument
   * @return Kopfzeilentext
   */
  private String headerText(ExportNote note, ExportDocument document) {
    boolean number = document.selection().includeNoteNumber();
    boolean title = document.selection().includeTitle();
    if (number && title) {
      return note.noteId() + ": " + note.title();
    }
    if (number) {
      return Integer.toString(note.noteId());
    }
    return title ? note.title() : "";
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

  /**
   * Verwaltet die RTF-Farbtabelle fuer Zeichen- und Hintergrundfarben.
   */
  private static final class ColorTable {

    private final Map<String, Integer> indexes;

    private ColorTable(Map<String, Integer> indexes) {
      this.indexes = indexes;
    }

    /**
     * Erstellt eine Farbtabelle aus allen formatierten Runs.
     *
     * @param document Exportdokument
     * @return Farbtabelle
     */
    static ColorTable from(ExportDocument document) {
      Map<String, Integer> indexes = new LinkedHashMap<>();
      for (ExportNote note : document.notes()) {
        for (ExportParagraph paragraph : note.bodyParagraphs()) {
          for (ExportTextRun run : paragraph.runs()) {
            add(indexes, run.style().textColor());
            add(indexes, run.style().backgroundColor());
          }
        }
      }
      return new ColorTable(indexes);
    }

    /**
     * Liefert den RTF-Index einer Farbe.
     *
     * @param color Hex-Farbe
     * @return RTF-Farbindex
     */
    int indexOf(String color) {
      return indexes.getOrDefault(color, 0);
    }

    /**
     * Rendert die RTF-Farbtabelle.
     *
     * @return RTF-Farbtabelle
     */
    String rtfColorTable() {
      StringBuilder table = new StringBuilder("{\\colortbl ;");
      for (String color : indexes.keySet()) {
        int r = Integer.parseInt(color.substring(1, 3), 16);
        int g = Integer.parseInt(color.substring(3, 5), 16);
        int b = Integer.parseInt(color.substring(5, 7), 16);
        table.append("\\red").append(r)
            .append("\\green").append(g)
            .append("\\blue").append(b)
            .append(';');
      }
      table.append("}\n");
      return table.toString();
    }

    private static void add(Map<String, Integer> indexes, String color) {
      if (color != null && !indexes.containsKey(color)) {
        indexes.put(color, indexes.size() + 1);
      }
    }
  }

  /**
   * Verwaltet die RTF-Schrifttabelle fuer formatierte Textbereiche.
   */
  private static final class FontTable {

    private final Map<String, Integer> indexes;

    private FontTable(Map<String, Integer> indexes) {
      this.indexes = indexes;
    }

    /**
     * Erstellt eine Schrifttabelle aus allen formatierten Runs.
     *
     * @param document Exportdokument
     * @return Schrifttabelle
     */
    static FontTable from(ExportDocument document) {
      Map<String, Integer> indexes = new LinkedHashMap<>();
      for (ExportNote note : document.notes()) {
        for (ExportParagraph paragraph : note.bodyParagraphs()) {
          for (ExportTextRun run : paragraph.runs()) {
            add(indexes, run.style().fontFamily());
          }
        }
      }
      return new FontTable(indexes);
    }

    /**
     * Liefert den RTF-Index einer Schriftfamilie.
     *
     * @param fontFamily Schriftfamilie
     * @return RTF-Fontindex
     */
    int indexOf(String fontFamily) {
      return indexes.getOrDefault(fontFamily, 0);
    }

    /**
     * Rendert die RTF-Schrifttabelle.
     *
     * @return RTF-Schrifttabelle
     */
    String rtfFontTable() {
      StringBuilder table = new StringBuilder("{\\fonttbl{\\f0\\fnil sans-serif;}");
      for (Map.Entry<String, Integer> entry : indexes.entrySet()) {
        table.append("{\\f").append(entry.getValue())
            .append("\\fnil ")
            .append(entry.getKey().replace("\\", "").replace("{", "").replace("}", ""))
            .append(";}");
      }
      table.append("}\n");
      return table.toString();
    }

    private static void add(Map<String, Integer> indexes, String fontFamily) {
      if (fontFamily != null && !indexes.containsKey(fontFamily)) {
        indexes.put(fontFamily, indexes.size() + 1);
      }
    }
  }
}
