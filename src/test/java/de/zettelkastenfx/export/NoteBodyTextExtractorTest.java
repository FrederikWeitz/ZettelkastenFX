package de.zettelkastenfx.export;

import de.zettelkastenfx.notes.model.Note;
import de.zettelkastenfx.persistence.HtmlBodyCodec;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueft die Dekodierung gespeicherter Zettelinhalte fuer den Export.
 */
class NoteBodyTextExtractorTest {

  /**
   * Stellt sicher, dass explizite Klartext-Inhalte unveraendert gelesen werden.
   */
  @Test
  void extractsUtf8PlainText() {
    Note note = new Note();
    note.setBodyCodec("plain");
    note.setBodyBlob("Klartext".getBytes(StandardCharsets.UTF_8));

    assertEquals("Klartext", new NoteBodyTextExtractor().extractPlainText(note));
  }

  /**
   * Stellt sicher, dass leere Zeilen am Ende nicht in die Exportdatei gelangen.
   */
  @Test
  void removesTrailingBlankLines() {
    Note note = new Note();
    note.setBodyCodec("plain");
    note.setBodyBlob("Klartext\n\n".getBytes(StandardCharsets.UTF_8));

    assertEquals("Klartext", new NoteBodyTextExtractor().extractPlainText(note));
  }

  /**
   * Stellt sicher, dass Bildverweise als Bildabsaetze in das Exportmodell gelangen.
   */
  @Test
  void extractsImageReferenceParagraphs() {
    Path image = Path.of("E:/bilder/test.png").toAbsolutePath().normalize();
    Note note = new Note();
    note.setBodyCodec("plain");
    note.setBodyBlob(("[[image:" + image + "]]").getBytes(StandardCharsets.UTF_8));

    var paragraphs = new NoteBodyTextExtractor().extractStyledParagraphs(note);

    assertEquals(1, paragraphs.size());
    assertTrue(paragraphs.getFirst().isImage());
    assertEquals(image, paragraphs.getFirst().imagePath());
  }

  /**
   * Stellt sicher, dass Tabellenverweise als Tabellenabsaetze in das Exportmodell gelangen.
   */
  @Test
  void extractsTableReferenceParagraphs() {
    Note note = new Note();
    note.setBodyCodec("plain");
    var table = de.zettelkastenfx.notes.editor.table.EditorTableService
        .resolveReference("[[table:3x4]]")
        .withCell(0, 1, "Alpha");
    note.setBodyBlob(
        de.zettelkastenfx.notes.editor.table.EditorTableService.referenceText(table).getBytes(StandardCharsets.UTF_8)
    );

    var paragraphs = new NoteBodyTextExtractor().extractStyledParagraphs(note);

    assertEquals(1, paragraphs.size());
    assertTrue(paragraphs.getFirst().isTable());
    assertEquals(3, paragraphs.getFirst().table().rows());
    assertEquals(4, paragraphs.getFirst().table().columns());
    assertEquals("Alpha", paragraphs.getFirst().table().cell(0, 1));
  }

  /**
   * Stellt sicher, dass HTML-Body-Zettel nicht als sichtbarer HTML-Code exportiert werden.
   */
  @Test
  void extractsPlainTextFromHtmlBodyWithoutTags() {
    Note note = htmlBodyNote("""
        <p class="j"><span class="b">Alpha</span> &amp; Beta</p>
        <ul><li>Gamma</li></ul>
        <table><tbody><tr><td>A</td><td>B</td></tr></tbody></table>
        """);

    String text = new NoteBodyTextExtractor().extractPlainText(note);

    assertFalse(text.contains("<p"));
    assertFalse(text.contains("<span"));
    assertTrue(text.contains("Alpha & Beta"));
    assertTrue(text.contains("\u2022 Gamma"));
    assertTrue(text.contains("A\tB"));
  }

  /**
   * Stellt sicher, dass Klassen, Inline-Styles, Listen und Tabellen aus HTML in das Exportmodell gelangen.
   */
  @Test
  void extractsStyledParagraphsFromHtmlBody() {
    Note note = htmlBodyNote("""
        <p class="j q"><span class="b i u x" style="color: #1b5fbf; background-color: rgb(253, 216, 53); font-family: 'Arial';">Alpha</span><sub>2</sub><sup>3</sup></p>
        <ol><li><span style="font-style: italic;">Erster Punkt</span></li><li>Zweiter Punkt</li></ol>
        <h1>Ueberschrift</h1>
        <table><tbody><tr><td>A</td><td>B</td></tr></tbody></table>
        """);

    var paragraphs = new NoteBodyTextExtractor().extractStyledParagraphs(note);

    assertEquals(5, paragraphs.size());
    assertEquals("Alpha23", paragraphs.getFirst().plainText());
    assertEquals("justify", paragraphs.getFirst().style().alignment());
    assertEquals(12, paragraphs.getFirst().style().indentPixels());
    assertEquals(3, paragraphs.getFirst().style().spacingBeforePixels());
    assertEquals("#eeeeee", paragraphs.getFirst().style().backgroundColor());
    assertTrue(paragraphs.getFirst().runs().getFirst().style().bold());
    assertTrue(paragraphs.getFirst().runs().getFirst().style().italic());
    assertTrue(paragraphs.getFirst().runs().getFirst().style().underline());
    assertTrue(paragraphs.getFirst().runs().getFirst().style().strikethrough());
    assertEquals("Arial", paragraphs.getFirst().runs().getFirst().style().fontFamily());
    assertEquals("#1b5fbf", paragraphs.getFirst().runs().getFirst().style().textColor());
    assertEquals("#fdd835", paragraphs.getFirst().runs().getFirst().style().backgroundColor());
    assertTrue(paragraphs.getFirst().runs().get(1).style().subscript());
    assertTrue(paragraphs.getFirst().runs().get(2).style().superscript());
    assertEquals("Erster Punkt", paragraphs.get(1).plainText());
    assertEquals("ol", paragraphs.get(1).style().listType());
    assertEquals(1, paragraphs.get(1).style().listNumber());
    assertTrue(paragraphs.get(1).runs().getFirst().style().italic());
    assertEquals("Zweiter Punkt", paragraphs.get(2).plainText());
    assertEquals("ol", paragraphs.get(2).style().listType());
    assertEquals(2, paragraphs.get(2).style().listNumber());
    assertEquals(20, paragraphs.get(3).style().fontSizePixels());
    assertTrue(paragraphs.get(3).style().bold());
    assertEquals(1, paragraphs.get(3).style().headingLevel());
    assertTrue(paragraphs.get(4).isTable());
    assertEquals("B", paragraphs.get(4).table().cell(0, 1));
  }

  /**
   * Stellt sicher, dass Farb- und Schriftangaben aus editornahem HTML tolerant gelesen werden.
   */
  @Test
  void extractsHtmlBodyTextColorAndFontVariants() {
    Note note = htmlBodyNote("""
        <p><span style="color: rgb(17 34 51 / 1); font-family: 'Times New Roman', serif;">Alpha</span></p>
        <p><font color="red" face="Courier New">Beta</font></p>
        <ul style="color: blue;"><li>Gamma</li></ul>
        """);

    var paragraphs = new NoteBodyTextExtractor().extractStyledParagraphs(note);

    assertEquals("#112233", paragraphs.getFirst().runs().getFirst().style().textColor());
    assertEquals("Times New Roman", paragraphs.getFirst().runs().getFirst().style().fontFamily());
    assertEquals("#ff0000", paragraphs.get(1).runs().getFirst().style().textColor());
    assertEquals("Courier New", paragraphs.get(1).runs().getFirst().style().fontFamily());
    assertEquals("#0000ff", paragraphs.get(2).runs().getFirst().style().textColor());
  }

  /**
   * Stellt sicher, dass Spaltenbreiten aus HTML-Tabellen in das Exportmodell uebernommen werden.
   */
  @Test
  void extractsHtmlTableColumnWidths() {
    Note note = htmlBodyNote("""
        <table><colgroup><col style="width: 25%;"><col style="width: 75%;"></colgroup>
        <tbody><tr><td>A</td><td>B</td></tr></tbody></table>
        """);

    var paragraphs = new NoteBodyTextExtractor().extractStyledParagraphs(note);

    assertTrue(paragraphs.getFirst().isTable());
    assertEquals(List.of(25, 75), paragraphs.getFirst().table().columnWeights());
    assertEquals(List.of(2250, 6750), paragraphs.getFirst().table().columnWidths(9000));
  }

  /**
   * Stellt sicher, dass TinyMCE-Bildabsaetze aus HTML als Bildabsaetze exportiert werden.
   */
  @Test
  void extractsWrappedImageParagraphFromHtmlBody() {
    Path image = Path.of("E:/bilder/test.png").toAbsolutePath().normalize();
    Note note = htmlBodyNote("<p><img src=\"" + image + "\"></p>");

    var paragraphs = new NoteBodyTextExtractor().extractStyledParagraphs(note);

    assertEquals(1, paragraphs.size());
    assertTrue(paragraphs.getFirst().isImage());
    assertEquals(image, paragraphs.getFirst().imagePath());
  }

  /**
   * Stellt sicher, dass Bildabsaetze mit nicht-lokalen Quellen nicht als sichtbarer HTML-Code durchgereicht werden.
   */
  @Test
  void extractsUnsupportedHtmlImageSourceAsPlainFallback() {
    Note note = htmlBodyNote("<p><img src=\"data:image/png;base64,abc\" alt=\"Bild\"></p>");

    String text = new NoteBodyTextExtractor().extractPlainText(note);
    var paragraphs = new NoteBodyTextExtractor().extractStyledParagraphs(note);

    assertEquals("Bild", text);
    assertEquals("Bild", paragraphs.getFirst().plainText());
    assertFalse(paragraphs.getFirst().isImage());
  }

  /**
   * Stellt sicher, dass leere oder externe Bildquellen nicht als lokale Bilddateien exportiert werden.
   */
  @Test
  void extractsBlankAndRemoteHtmlImagesAsPlainFallback() {
    Note blankImage = htmlBodyNote("<p><img src=\"\" alt=\"Leer\"></p>");
    Note remoteImage = htmlBodyNote("<p><img src=\"https://example.org/bild.png\" alt=\"Extern\"></p>");
    NoteBodyTextExtractor extractor = new NoteBodyTextExtractor();

    var blankParagraphs = extractor.extractStyledParagraphs(blankImage);
    var remoteParagraphs = extractor.extractStyledParagraphs(remoteImage);

    assertFalse(blankParagraphs.getFirst().isImage());
    assertEquals("Leer", blankParagraphs.getFirst().plainText());
    assertFalse(remoteParagraphs.getFirst().isImage());
    assertEquals("Extern", remoteParagraphs.getFirst().plainText());
  }

  private Note htmlBodyNote(String html) {
    Note note = new Note();
    note.setBodyCodec(HtmlBodyCodec.CODEC_NAME);
    note.setBodyBlob(html.getBytes(StandardCharsets.UTF_8));
    return note;
  }

}
