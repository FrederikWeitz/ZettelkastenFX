package de.zettelkastenfx.export;

import de.zettelkastenfx.notes.model.Note;
import de.zettelkastenfx.persistence.InlineCssRtfxBlobCodec;

import java.nio.charset.StandardCharsets;

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
      return InlineCssRtfxBlobCodec.decodeFromGzipToPlainText(note.getBodyBlob());
    }

    if ("plain".equalsIgnoreCase(codec) || "text/plain".equalsIgnoreCase(codec)) {
      return new String(note.getBodyBlob(), StandardCharsets.UTF_8);
    }

    return new String(note.getBodyBlob(), StandardCharsets.UTF_8);
  }
}
