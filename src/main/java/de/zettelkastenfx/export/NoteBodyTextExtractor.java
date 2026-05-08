package de.zettelkastenfx.export;

import de.zettelkastenfx.export.model.ExportParagraph;
import de.zettelkastenfx.export.model.ExportParagraphStyle;
import de.zettelkastenfx.export.model.ExportTextRun;
import de.zettelkastenfx.export.model.ExportTextStyle;
import de.zettelkastenfx.notes.model.Note;
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
    return removeTrailingBlankParagraphs(plainParagraphs(text));
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
                     .map(ExportParagraph::plain)
                     .toList();
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
    while (end > 0 && paragraphs.get(end - 1).plainText().isBlank()) {
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
