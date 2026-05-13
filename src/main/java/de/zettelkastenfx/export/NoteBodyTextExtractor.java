package de.zettelkastenfx.export;

import de.zettelkastenfx.export.model.ExportParagraph;
import de.zettelkastenfx.export.model.ExportParagraphStyle;
import de.zettelkastenfx.export.model.ExportTable;
import de.zettelkastenfx.export.model.ExportTextRun;
import de.zettelkastenfx.export.model.ExportTextStyle;
import de.zettelkastenfx.notes.editor.media.EditorImageAssetService;
import de.zettelkastenfx.notes.editor.table.EditorTableService;
import de.zettelkastenfx.notes.model.Note;
import de.zettelkastenfx.persistence.HtmlBodyCodec;
import de.zettelkastenfx.persistence.InlineCssRtfxBlobCodec;
import org.fxmisc.richtext.model.Paragraph;
import org.fxmisc.richtext.model.StyledDocument;
import org.fxmisc.richtext.model.StyledSegment;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Wandelt gespeicherte Zettelinhalte in lesbaren Klartext fuer den Export.
 */
public class NoteBodyTextExtractor {

  private final HtmlBodyExportParser htmlBodyParser = new HtmlBodyExportParser();

  /**
   * Dekodiert den Inhalt eines Zettels.
   *
   * @param note Zettel
   * @return lesbarer Zettelinhalt
   */
  public String extractPlainText(Note note) {
    if (note == null || note.getBodyBlob() == null || note.getBodyBlob().length == 0) {
      return "";
    }

    String codec = note.getBodyCodec() == null ? "" : note.getBodyCodec().trim();
    if (InlineCssRtfxBlobCodec.CODEC_NAME.equals(codec)) {
      return removeTrailingBlankLines(InlineCssRtfxBlobCodec.decodeFromGzipToPlainText(note.getBodyBlob()));
    }

    if ("plain".equalsIgnoreCase(codec) || "text/plain".equalsIgnoreCase(codec)) {
      return removeTrailingBlankLines(new String(note.getBodyBlob(), StandardCharsets.UTF_8));
    }

    if (HtmlBodyCodec.CODEC_NAME.equals(codec)) {
      return removeTrailingBlankLines(plainTextFromParagraphs(htmlBodyParser.parse(new String(note.getBodyBlob(), StandardCharsets.UTF_8))));
    }

    return removeTrailingBlankLines(new String(note.getBodyBlob(), StandardCharsets.UTF_8));
  }

  /**
   * Dekodiert den Inhalt eines Zettels inklusive exportrelevanter Inline-Formatierung.
   *
   * @param note Zettel
   * @return formatierte Exportabsaetze
   */
  public List<ExportParagraph> extractStyledParagraphs(Note note) {
    if (note == null || note.getBodyBlob() == null || note.getBodyBlob().length == 0) {
      return List.of();
    }

    String codec = note.getBodyCodec() == null ? "" : note.getBodyCodec().trim();
    if (InlineCssRtfxBlobCodec.CODEC_NAME.equals(codec)) {
      return removeTrailingBlankParagraphs(styledParagraphsFromRtfx(note.getBodyBlob()));
    }

    String text = new String(note.getBodyBlob(), StandardCharsets.UTF_8);
    if (HtmlBodyCodec.CODEC_NAME.equals(codec)) {
      return removeTrailingBlankParagraphs(htmlBodyParser.parse(text));
    }
    return removeTrailingBlankParagraphs(plainParagraphs(text));
  }

  private String plainTextFromParagraphs(List<ExportParagraph> paragraphs) {
    StringBuilder text = new StringBuilder();
    for (ExportParagraph paragraph : paragraphs) {
      if (text.length() > 0) {
        text.append('\n');
      }
      if (paragraph.isImage()) {
        text.append(paragraph.imagePath());
      } else if (paragraph.isTable()) {
        appendTablePlainText(text, paragraph.table());
      } else if (paragraph.style().isListItem()) {
        text.append(listPrefix(paragraph)).append(paragraph.plainText());
      } else {
        text.append(paragraph.plainText());
      }
    }
    return text.toString();
  }

  private String listPrefix(ExportParagraph paragraph) {
    if ("ol".equals(paragraph.style().listType())) {
      return Math.max(1, paragraph.style().listNumber()) + ". ";
    }
    return "\u2022 ";
  }

  private void appendTablePlainText(StringBuilder text, ExportTable table) {
    for (int row = 0; row < table.rows(); row++) {
      if (row > 0) {
        text.append('\n');
      }
      for (int column = 0; column < table.columns(); column++) {
        if (column > 0) {
          text.append('\t');
        }
        text.append(table.cell(row, column));
      }
    }
  }

  /**
   * Dekodiert RTFX-GZIP in formatierte Exportabsaetze.
   *
   * @param bodyBlob gespeicherter Zettelinhalt
   * @return formatierte Exportabsaetze
   */
  private List<ExportParagraph> styledParagraphsFromRtfx(byte[] bodyBlob) {
    StyledDocument<String, String, String> document =
        InlineCssRtfxBlobCodec.decodeFromGzipToStyledDocument(bodyBlob);
    if (document == null) {
      return List.of();
    }

    List<ExportParagraph> paragraphs = new ArrayList<>();
    for (Paragraph<String, String, String> paragraph : document.getParagraphs()) {
      String paragraphText = paragraph.getText();
      if (EditorImageAssetService.isImageReferenceParagraph(paragraphText)) {
        paragraphs.add(ExportParagraph.image(EditorImageAssetService.resolveReferencePath(paragraphText)));
        continue;
      }
      if (EditorTableService.isTableReferenceParagraph(paragraphText)) {
        paragraphs.add(tableParagraph(paragraphText));
        continue;
      }

      List<ExportTextRun> runs = new ArrayList<>();
      for (StyledSegment<String, String> segment : paragraph.getStyledSegments()) {
        String text = segment.getSegment();
        if (text != null && !text.isEmpty()) {
          runs.add(new ExportTextRun(text, ExportTextStyle.fromInlineCss(segment.getStyle())));
        }
      }
      paragraphs.add(new ExportParagraph(runs, ExportParagraphStyle.fromParagraphCss(paragraph.getParagraphStyle())));
    }
    return paragraphs;
  }

  /**
   * Wandelt Klartext in unformatierte Exportabsaetze.
   *
   * @param text Klartext
   * @return unformatierte Exportabsaetze
   */
  private List<ExportParagraph> plainParagraphs(String text) {
    String normalized = text == null ? "" : text;
    if (normalized.isEmpty()) {
      return List.of();
    }
    return normalized.lines()
                     .map(line -> EditorImageAssetService.isImageReferenceParagraph(line)
                                      ? ExportParagraph.image(EditorImageAssetService.resolveReferencePath(line))
                                      : EditorTableService.isTableReferenceParagraph(line)
                                            ? tableParagraph(line)
                                      : ExportParagraph.plain(line))
                     .toList();
  }

  /**
   * Wandelt einen Tabellenmarker in einen Exportabsatz.
   *
   * @param line Tabellenmarker
   * @return Tabellenabsatz
   */
  private ExportParagraph tableParagraph(String line) {
    var tableData = EditorTableService.resolveReference(line);
    List<List<String>> cells = new ArrayList<>();
    for (List<String> row : tableData.cells()) {
      List<String> exportedRow = new ArrayList<>();
      for (String cell : row) {
        exportedRow.add(EditorTableService.plainCellText(cell));
      }
      cells.add(List.copyOf(exportedRow));
    }
    return ExportParagraph.table(new ExportTable(tableData.rows(), tableData.columns(), cells));
  }

  /**
   * Entfernt leere Absaetze am Ende des Zettelinhalts.
   *
   * @param paragraphs Absaetze
   * @return Absaetze ohne abschliessende Leerabsaetze
   */
  private List<ExportParagraph> removeTrailingBlankParagraphs(List<ExportParagraph> paragraphs) {
    if (paragraphs == null || paragraphs.isEmpty()) {
      return List.of();
    }
    int end = paragraphs.size();
    while (end > 0
        && !paragraphs.get(end - 1).isImage()
        && !paragraphs.get(end - 1).isTable()
        && paragraphs.get(end - 1).plainText().isBlank()) {
      end--;
    }
    return List.copyOf(paragraphs.subList(0, end));
  }

  /**
   * Entfernt leere Zeilen am Ende eines Zettelinhalts.
   *
   * @param text dekodierter Zetteltext
   * @return Text ohne abschliessende Leerzeilen
   */
  private String removeTrailingBlankLines(String text) {
    if (text == null || text.isBlank()) {
      return "";
    }
    return text.replaceFirst("(\\R\\s*)+$", "");
  }
}
