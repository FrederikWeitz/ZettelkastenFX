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
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
