package de.zettelkastenfx.persistence;

import org.fxmisc.richtext.InlineCssTextArea;
import org.fxmisc.richtext.model.Codec;
import org.fxmisc.richtext.model.ReadOnlyStyledDocument;
import org.fxmisc.richtext.model.SegmentOps;
import org.fxmisc.richtext.model.StyledDocument;

import java.io.*;

public final class InlineCssRtfxBlobCodec {

  private InlineCssRtfxBlobCodec() {}

  public static final String CODEC_NAME = "rtfx-gzip";

  public static byte[] encodeToGzip(InlineCssTextArea area) {
    try {
      Codec<StyledDocument<String, String, String>> codec =
          ReadOnlyStyledDocument.codec(
              Codec.STRING_CODEC,
              Codec.styledSegmentCodec(Codec.STRING_CODEC, Codec.STRING_CODEC),
              area.getSegOps()
          );

      StyledDocument<String, String, String> doc = area.getDocument();

      try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
           DataOutputStream dos = new DataOutputStream(baos)) {
        codec.encode(dos, doc);
        return GzipBytes.compress(baos.toByteArray());
      }
    } catch (IOException e) {
      throw new IllegalStateException("RTFX encode fehlgeschlagen.", e);
    }
  }

  public static void decodeFromGzipInto(InlineCssTextArea area, byte[] gzBytes) {
    try {
      byte[] raw = GzipBytes.decompress(gzBytes);

      Codec<StyledDocument<String, String, String>> codec =
          ReadOnlyStyledDocument.codec(
              Codec.STRING_CODEC,
              Codec.styledSegmentCodec(Codec.STRING_CODEC, Codec.STRING_CODEC),
              area.getSegOps()
          );

      try (ByteArrayInputStream bais = new ByteArrayInputStream(raw);
           DataInputStream dis = new DataInputStream(bais)) {

        StyledDocument<String, String, String> doc = codec.decode(dis);

        area.clear();
        if (doc != null) {
          area.replaceSelection(doc);
        }
      }
    } catch (IOException e) {
      throw new IllegalStateException("RTFX decode fehlgeschlagen.", e);
    }
  }

  /**
   * Dekodiert einen gespeicherten RTFX-GZIP-Blob in lesbaren Klartext.
   * Diese Methode benoetigt keine JavaFX-Editorinstanz und eignet sich deshalb
   * fuer Export- und Testpfade.
   *
   * @param gzBytes gespeicherter Zettelinhalt
   * @return dekodierter Klartext
   */
  public static String decodeFromGzipToPlainText(byte[] gzBytes) {
    StyledDocument<String, String, String> document = decodeFromGzipToStyledDocument(gzBytes);
    return document == null ? "" : document.getText();
  }

  /**
   * Dekodiert einen gespeicherten RTFX-GZIP-Blob inklusive Absatz- und Inline-Stilen.
   *
   * @param gzBytes gespeicherter Zettelinhalt
   * @return dekodiertes StyledDocument oder {@code null}
   */
  public static StyledDocument<String, String, String> decodeFromGzipToStyledDocument(byte[] gzBytes) {
    if (gzBytes == null || gzBytes.length == 0) {
      return null;
    }

    try {
      byte[] raw = GzipBytes.decompress(gzBytes);
      Codec<StyledDocument<String, String, String>> codec =
          ReadOnlyStyledDocument.codec(
              Codec.STRING_CODEC,
              Codec.styledSegmentCodec(Codec.STRING_CODEC, Codec.STRING_CODEC),
              SegmentOps.styledTextOps()
          );

      try (ByteArrayInputStream bais = new ByteArrayInputStream(raw);
           DataInputStream dis = new DataInputStream(bais)) {
        return codec.decode(dis);
      }
    } catch (IOException e) {
      throw new IllegalStateException("RTFX-Dekodierung fehlgeschlagen.", e);
    }
  }
}
