package de.zettelkastenfx.persistence;

import org.fxmisc.richtext.InlineCssTextArea;
import org.fxmisc.richtext.model.Codec;
import org.fxmisc.richtext.model.ReadOnlyStyledDocument;
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
}