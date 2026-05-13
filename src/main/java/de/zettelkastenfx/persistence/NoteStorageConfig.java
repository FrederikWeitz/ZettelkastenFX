package de.zettelkastenfx.persistence;

/**
 * Buendelt die aktuell aktive Zetteltabelle und die Inhaltsspalte fuer SQL-Zugriffe.
 */
public final class NoteStorageConfig {

  public static final String NOTE_TABLE = "notes_web";
  public static final String CONTENT_COLUMN = "content_text";

  public static final boolean CONTENT_IS_TEXT = true;

  private NoteStorageConfig() {
  }

  /**
   * Uebersetzt SQL auf die aktuell konfigurierte Zetteltabelle und Inhaltsspalte.
   *
   * @param sql SQL mit Altbezeichnern
   * @return SQL mit aktuell konfigurierten Bezeichnern
   */
  public static String rewriteSql(String sql) {
    if (sql == null || sql.isBlank()) {
      return sql;
    }
    StringBuilder out = new StringBuilder(sql.length());
    StringBuilder token = new StringBuilder();
    boolean inSingleQuotedLiteral = false;
    for (int i = 0; i < sql.length(); i++) {
      char c = sql.charAt(i);
      if (c == '\'') {
        flushToken(out, token);
        out.append(c);
        if (inSingleQuotedLiteral && i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
          out.append(sql.charAt(++i));
        } else {
          inSingleQuotedLiteral = !inSingleQuotedLiteral;
        }
        continue;
      }
      if (inSingleQuotedLiteral) {
        out.append(c);
        continue;
      }
      if (Character.isLetterOrDigit(c) || c == '_') {
        token.append(c);
      } else {
        flushToken(out, token);
        out.append(c);
      }
    }
    flushToken(out, token);
    return out.toString();
  }

  private static void flushToken(StringBuilder out, StringBuilder token) {
    if (token.isEmpty()) {
      return;
    }
    String value = token.toString();
    if ("notes".equals(value)) {
      out.append(NOTE_TABLE);
    } else if ("content_blob".equals(value)) {
      out.append(CONTENT_COLUMN);
    } else {
      out.append(value);
    }
    token.setLength(0);
  }
}
