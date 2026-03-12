package de.zettelkastenfx.persistence;

import java.time.*;
import java.time.format.DateTimeFormatter;

public final class SqliteDateTimes {
  private static final DateTimeFormatter SQLITE_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private SqliteDateTimes() {}

  public static LocalDateTime parse(String s) {
    if (s == null || s.isBlank()) return null;
    try {
      // Instant-String wie 2026-03-01T12:34:56Z
      Instant i = Instant.parse(s.trim());
      return LocalDateTime.ofInstant(i, ZoneId.systemDefault());
    } catch (Exception ignored) {
      // SQLite CURRENT_TIMESTAMP: 2026-03-01 12:34:56
      return LocalDateTime.parse(s.trim(), SQLITE_TS);
    }
  }

  public static String format(LocalDateTime dt) {
    if (dt == null) return null;
    // sauber und eindeutig speichern: Instant-String (UTC)
    return dt.atZone(ZoneId.systemDefault()).toInstant().toString();
  }
}