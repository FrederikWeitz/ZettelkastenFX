package de.zettelkastenfx.persistence;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class GzipUtf8 {

  private GzipUtf8() {}

  public static byte[] encode(String s) {
    if (s == null) s = "";
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
         GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
      gzip.write(s.getBytes(StandardCharsets.UTF_8));
      gzip.finish();
      return baos.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("GZIP encode fehlgeschlagen.", e);
    }
  }

  public static String decode(byte[] bytes) {
    if (bytes == null || bytes.length == 0) return "";
    try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
         GZIPInputStream gzip = new GZIPInputStream(bais);
         InputStreamReader isr = new InputStreamReader(gzip, StandardCharsets.UTF_8);
         BufferedReader br = new BufferedReader(isr)) {

      StringBuilder sb = new StringBuilder();
      char[] buf = new char[4096];
      int n;
      while ((n = br.read(buf)) >= 0) sb.append(buf, 0, n);
      return sb.toString();

    } catch (IOException e) {
      throw new IllegalStateException("GZIP decode fehlgeschlagen.", e);
    }
  }
}