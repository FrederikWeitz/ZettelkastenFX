package de.zettelkastenfx.persistence;

import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class GzipBytes {
  private GzipBytes() {}

  public static byte[] compress(byte[] raw) {
    if (raw == null || raw.length == 0) return new byte[0];
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
         GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
      gzip.write(raw);
      gzip.finish();
      return baos.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("GZIP compress fehlgeschlagen.", e);
    }
  }

  public static byte[] decompress(byte[] gz) {
    if (gz == null || gz.length == 0) return new byte[0];
    try (ByteArrayInputStream bais = new ByteArrayInputStream(gz);
         GZIPInputStream gzip = new GZIPInputStream(bais);
         ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      byte[] buf = new byte[8192];
      int n;
      while ((n = gzip.read(buf)) >= 0) baos.write(buf, 0, n);
      return baos.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("GZIP decompress fehlgeschlagen.", e);
    }
  }
}