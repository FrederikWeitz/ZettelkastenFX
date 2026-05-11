package de.zettelkastenfx.export;

import de.zettelkastenfx.notes.model.Note;
import de.zettelkastenfx.persistence.GzipBytes;
import de.zettelkastenfx.persistence.InlineCssRtfxBlobCodec;
import org.fxmisc.richtext.model.Codec;
import org.fxmisc.richtext.model.ReadOnlyStyledDocument;
import org.fxmisc.richtext.model.SegmentOps;
import org.fxmisc.richtext.model.StyledDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueft die Dekodierung gespeicherter Zettelinhalte fuer den Export.
 */
class NoteBodyTextExtractorTest {

  /**
   * Stellt sicher, dass RTFX-GZIP-Inhalte als Klartext exportiert werden.
   *
   * @throws Exception bei unerwarteten Codec-Fehlern
   */
  @Test
  void extractsPlainTextFromRtfxGzipBlob() throws Exception {
    Note note = new Note();
    note.setBodyCodec(InlineCssRtfxBlobCodec.CODEC_NAME);
    note.setBodyBlob(encodedRtfx("Erste Zeile\nZweite Zeile"));

    assertEquals("Erste Zeile\nZweite Zeile", new NoteBodyTextExtractor().extractPlainText(note));
  }

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
   * Stellt sicher, dass exportrelevante Inline-Formatierungen erhalten bleiben.
   *
   * @throws Exception bei unerwarteten Codec-Fehlern
   */
  @Test
  void extractsStyledParagraphsFromRtfxGzipBlob() throws Exception {
    Note note = new Note();
    note.setBodyCodec(InlineCssRtfxBlobCodec.CODEC_NAME);
    note.setBodyBlob(encodedStyledRtfx());

    var paragraphs = new NoteBodyTextExtractor().extractStyledParagraphs(note);

    assertEquals(1, paragraphs.size());
    assertEquals("Fett kursiv", paragraphs.getFirst().plainText());
    assertTrue(paragraphs.getFirst().runs().getFirst().style().bold());
    assertTrue(paragraphs.getFirst().runs().get(1).style().italic());
    assertEquals("center", paragraphs.getFirst().style().alignment());
    assertEquals(20, paragraphs.getFirst().style().indentPixels());
    assertEquals(3, paragraphs.getFirst().style().spacingBeforePixels());
    assertEquals("#1b5fbf", paragraphs.getFirst().runs().getFirst().style().textColor());
    assertEquals("#fdd835", paragraphs.getFirst().runs().get(1).style().backgroundColor());
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
   * Erstellt einen RTFX-GZIP-Testblob aus Klartext.
   *
   * @param text Klartext
   * @return codierter Blob
   * @throws Exception bei Codec-Fehlern
   */
  private byte[] encodedRtfx(String text) throws Exception {
    StyledDocument<String, String, String> document =
        ReadOnlyStyledDocument.fromString(text, "", "", SegmentOps.styledTextOps());
    Codec<StyledDocument<String, String, String>> codec =
        ReadOnlyStyledDocument.codec(
            Codec.STRING_CODEC,
            Codec.styledSegmentCodec(Codec.STRING_CODEC, Codec.STRING_CODEC),
            SegmentOps.styledTextOps()
        );

    try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
         DataOutputStream dos = new DataOutputStream(baos)) {
      codec.encode(dos, document);
      return GzipBytes.compress(baos.toByteArray());
    }
  }

  /**
   * Erstellt einen RTFX-GZIP-Testblob mit mehreren Inline-Stilen.
   *
   * @return codierter Blob
   * @throws Exception bei Codec-Fehlern
   */
  private byte[] encodedStyledRtfx() throws Exception {
    StyledDocument<String, String, String> document =
        ReadOnlyStyledDocument.fromString(
                                  "Fett",
                                  "-fx-text-alignment: center;-fx-padding: 3 0 0 20;",
                                  "-fx-font-weight: bold;-fx-fill: #1b5fbf;",
                                  SegmentOps.styledTextOps()
                              )
                              .concat(ReadOnlyStyledDocument.fromString(
                                  " kursiv",
                                  "-fx-text-alignment: center;-fx-padding: 3 0 0 20;",
                                  "-fx-font-style: italic;-rtfx-background-color: #fdd835;",
                                  SegmentOps.styledTextOps()
                              ));
    Codec<StyledDocument<String, String, String>> codec =
        ReadOnlyStyledDocument.codec(
            Codec.STRING_CODEC,
            Codec.styledSegmentCodec(Codec.STRING_CODEC, Codec.STRING_CODEC),
            SegmentOps.styledTextOps()
        );

    try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
         DataOutputStream dos = new DataOutputStream(baos)) {
      codec.encode(dos, document);
      return GzipBytes.compress(baos.toByteArray());
    }
  }
}
