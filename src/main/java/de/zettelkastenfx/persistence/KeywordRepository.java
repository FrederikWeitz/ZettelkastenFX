package de.zettelkastenfx.persistence;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

public class KeywordRepository {

  private final DataSource ds;

  public KeywordRepository(DataSource ds) {
    this.ds = ds;
  }

  public List<String> loadKeywordsForNote(int noteId) {
    String sql = """
        SELECT k.keyword
        FROM keywords k
        JOIN note_keywords nk ON nk.keyword_id = k.id
        WHERE nk.note_id = ?
        ORDER BY k.keyword COLLATE NOCASE
        """;

    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

      ps.setInt(1, noteId);
      try (ResultSet rs = ps.executeQuery()) {
        List<String> out = new ArrayList<>();
        while (rs.next()) out.add(rs.getString(1));
        return out;
      }

    } catch (SQLException e) {
      throw new IllegalStateException("Keywords load fehlgeschlagen (noteId=" + noteId + ")", e);
    }
  }

  /** Wird aus NoteRepository innerhalb einer TX mit gleicher Connection aufgerufen. */
  void setKeywordsForNote(Connection c, int noteId, List<String> keywords) throws SQLException {
    // normalisieren + deduplizieren (case-insensitive)
    LinkedHashMap<String, String> dedup = new LinkedHashMap<>();
    if (keywords != null) {
      for (String k : keywords) {
        String n = normalize(k);
        if (n.isBlank()) continue;
        String key = n.toLowerCase(Locale.ROOT);
        dedup.putIfAbsent(key, n);
      }
    }
    List<String> normalized = new ArrayList<>(dedup.values());

    try (PreparedStatement del = c.prepareStatement("DELETE FROM note_keywords WHERE note_id = ?")) {
      del.setInt(1, noteId);
      del.executeUpdate();
    }

    // Keywords sicherstellen
    try (PreparedStatement insKw = c.prepareStatement("INSERT OR IGNORE INTO keywords(keyword) VALUES(?)")) {
      for (String k : normalized) {
        insKw.setString(1, k);
        insKw.addBatch();
      }
      insKw.executeBatch();
    }

    // Join eintragen
    String joinSql = """
        INSERT OR IGNORE INTO note_keywords(note_id, keyword_id)
        SELECT ?, id FROM keywords WHERE keyword = ?
        """;
    try (PreparedStatement join = c.prepareStatement(joinSql)) {
      for (String k : normalized) {
        join.setInt(1, noteId);
        join.setString(2, k);
        join.addBatch();
      }
      join.executeBatch();
    }
  }

  public List<KeywordCount> listKeywordsWithCount() {
    String sql = """
        SELECT k.keyword, COUNT(nk.note_id) AS cnt
        FROM keywords k
        LEFT JOIN note_keywords nk ON nk.keyword_id = k.id
        GROUP BY k.id
        ORDER BY k.keyword COLLATE NOCASE
        """;

    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {

      List<KeywordCount> out = new ArrayList<>();
      while (rs.next()) {
        out.add(new KeywordCount(rs.getString(1), rs.getInt(2)));
      }
      return out;

    } catch (SQLException e) {
      throw new IllegalStateException("Keyword counts query fehlgeschlagen", e);
    }
  }

  private static String normalize(String s) {
    return s == null ? "" : s.trim();
  }

  public record KeywordCount(String keyword, int count) {}
}