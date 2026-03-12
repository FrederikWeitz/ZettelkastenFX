package de.zettelkastenfx.persistence;

import de.zettelkastenfx.bibliography.model.BibliographyType;
import de.zettelkastenfx.bibliography.model.BookEntry;
import de.zettelkastenfx.bibliography.persistence.BibliographyRepository;
import de.zettelkastenfx.notes.model.Note;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;

public class NoteRepository {

  private final DataSource ds;
  private final BibliographyRepository bibliographyRepo;

  public NoteRepository(DataSource ds) {
    this.ds = ds;
    this.bibliographyRepo = new BibliographyRepository(ds);
  }

  public int countNotes() {
    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM notes");
         ResultSet rs = ps.executeQuery()) {
      return rs.next() ? rs.getInt(1) : 0;
    } catch (SQLException e) {
      throw new IllegalStateException("countNotes fehlgeschlagen", e);
    }
  }

  public int maxNoteId() {
    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement("SELECT COALESCE(MAX(id), 0) FROM notes");
         ResultSet rs = ps.executeQuery()) {
      return rs.next() ? rs.getInt(1) : 0;
    } catch (SQLException e) {
      throw new IllegalStateException("maxNoteId fehlgeschlagen", e);
    }
  }

  public Optional<Note> load(int id) {
    String sql = """
      SELECT id, title, content_blob, content_codec, bibliography_type, bibliography_ref_id, created_at, updated_at
      FROM notes
      WHERE id = ?
      """;

    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

      ps.setInt(1, id);
      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) {
          return Optional.empty();
        }

        Note note = new Note();
        note.setId(rs.getInt("id"));
        note.setTitle(rs.getString("title"));
        note.setBodyBlob(rs.getBytes("content_blob"));
        note.setBodyCodec(rs.getString("content_codec"));

        String bibliographyType = rs.getString("bibliography_type");
        note.setBibliographyType(
            bibliographyType == null || bibliographyType.isBlank()
                ? BibliographyType.USER
                : BibliographyType.valueOf(bibliographyType)
        );

        Integer bibliographyRefId = (Integer) rs.getObject("bibliography_ref_id");
        note.setBibliographyRefId(bibliographyRefId);

        String createdString = rs.getString("created_at");
        note.setCreatedAt((createdString == null || createdString.isEmpty()) ? null : Instant.parse(createdString));

        String updatedString = rs.getString("updated_at");
        note.setUpdatedAt((updatedString == null || updatedString.isEmpty()) ? null : Instant.parse(updatedString));

        note.setBibliographyEntry(null);
        if (note.getBibliographyType() == BibliographyType.BOOK
                && bibliographyRefId != null
                && bibliographyRefId > 0) {
          note.setBibliographyEntry(
              bibliographyRepo.load(bibliographyRefId, BibliographyType.BOOK)
          );
        }

        List<String> keywords = loadKeywordsForNote(c, id);
        note.setKeywords(keywords);

        return Optional.of(note);
      }

    } catch (SQLException e) {
      throw new IllegalStateException("load fehlgeschlagen (id=" + id + ")", e);
    }
  }

  /**
   * Insert wenn id <= 0, sonst Update. Gibt Note-ID zurück.
   */
  public int save(Note note) {
    if (note == null) {
      throw new IllegalArgumentException("save: note darf nicht null sein.");
    }

    if (note.getId() <= 0 || note.getId() > maxNoteId()) {
      int newId = insert(note);
      note.setId(newId);
      return newId;
    }

    update(note);
    return note.getId();
  }

  private int insert(Note note) {
    String sql = """
        INSERT INTO notes(title, content_blob, content_codec, bibliography_type, bibliography_ref_id, created_at, updated_at)
        VALUES(?, ?, ?, 'USER', NULL, ?, ?)
        ON CONFLICT(id) DO UPDATE SET
          title = excluded.title,
          content_blob = excluded.content_blob,
          content_codec = excluded.content_codec,
          bibliography_type = excluded.bibliography_type,
          bibliography_ref_id = excluded.bibliography_ref_id,
          updated_at = excluded.updated_at;
        """;

    Instant now = Instant.now();

    try (Connection c = ds.getConnection()) {
      c.setAutoCommit(false);
      try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
        ps.setString(1, note.getTitle());
        ps.setBytes(2, note.getBodyBlob());
        ps.setString(3, note.getBodyCodec());
        ps.setString(4, now.toString());
        ps.setString(5, null);
        ps.executeUpdate();

        int newId;
        try (ResultSet keys = ps.getGeneratedKeys()) {
          if (!keys.next())
            throw new IllegalStateException("Keine ID von SQLite erhalten.");
          newId = keys.getInt(1);
        }

        setKeywordsForNote(c, newId, note.getKeywords());
        c.commit();

        note.setId(newId);
        note.setCreatedAt(now);
        note.setUpdatedAt(null);

        return newId;

      } catch (Exception ex) {
        c.rollback();
        throw ex;
      } finally {
        c.setAutoCommit(true);
      }
    } catch (Exception e) {
      throw new IllegalStateException("insert fehlgeschlagen", e);
    }
  }

  private void update(Note note) {
    String sql = """
        UPDATE notes
        SET title = ?, content_blob = ?, content_codec = ?, updated_at = ?
        WHERE id = ?
        """;

    Instant now = Instant.now();

    try (Connection c = ds.getConnection()) {
      c.setAutoCommit(false);
      try (PreparedStatement ps = c.prepareStatement(sql)) {
        ps.setString(1, note.getTitle());
        ps.setBytes(2, note.getBodyBlob());
        ps.setString(3, note.getBodyCodec());
        ps.setString(4, now.toString());
        ps.setInt(5, note.getId());
        ps.executeUpdate();

        setKeywordsForNote(c, note.getId(), note.getKeywords());
        c.commit();
        note.setUpdatedAt(now);

      } catch (Exception ex) {
        c.rollback();
        throw ex;
      } finally {
        c.setAutoCommit(true);
      }
    } catch (Exception e) {
      throw new IllegalStateException("update fehlgeschlagen (id=" + note.getId() + ")", e);
    }
  }

  public Map<String, Integer> loadKeywordCounts() {
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

      Map<String, Integer> out = new LinkedHashMap<>();
      while (rs.next())
        out.put(rs.getString(1), rs.getInt(2));
      return out;

    } catch (SQLException e) {
      throw new IllegalStateException("loadKeywordCounts fehlgeschlagen", e);
    }
  }

  private List<String> loadKeywordsForNote(Connection c, int noteId) throws SQLException {
    String sql = """
        SELECT k.keyword
        FROM keywords k
        JOIN note_keywords nk ON nk.keyword_id = k.id
        WHERE nk.note_id = ?
        ORDER BY k.keyword COLLATE NOCASE
        """;

    try (PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setInt(1, noteId);
      try (ResultSet rs = ps.executeQuery()) {
        List<String> out = new ArrayList<>();
        while (rs.next())
          out.add(rs.getString(1));
        return out;
      }
    }
  }

  private void setKeywordsForNote(Connection c, int noteId, List<String> keywords) throws SQLException {
    // normalize + dedup (case-insensitive)
    LinkedHashMap<String, String> dedup = new LinkedHashMap<>();
    if (keywords != null) {
      for (String k : keywords) {
        String n = (k == null) ? "" : k.trim();
        if (n.isBlank())
          continue;
        dedup.putIfAbsent(n.toLowerCase(Locale.ROOT), n);
      }
    }
    List<String> normalized = new ArrayList<>(dedup.values());

    try (PreparedStatement del = c.prepareStatement("DELETE FROM note_keywords WHERE note_id = ?")) {
      del.setInt(1, noteId);
      del.executeUpdate();
    }

    try (PreparedStatement insKw = c.prepareStatement("INSERT OR IGNORE INTO keywords(keyword) VALUES(?)")) {
      for (String k : normalized) {
        insKw.setString(1, k);
        insKw.addBatch();
      }
      insKw.executeBatch();
    }

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

  public void unlinkBibliography(int noteId) {
    if (noteId <= 0) {
      return;
    }

    String sql = "UPDATE notes SET bibliography_type = 'USER', bibliography_ref_id = NULL WHERE id = ?";
    try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
      ps.setInt(1, noteId);
      int changed = ps.executeUpdate();
      if (changed != 1) {
        throw new IllegalStateException("unlinkBibliography: keine Note aktualisiert (noteId=" + noteId + ")");
      }
    } catch (java.sql.SQLException e) {
      throw new IllegalStateException("unlinkBibliography fehlgeschlagen (noteId=" + noteId + ")", e);
    }
  }

  public void linkBibliography(int noteId, BibliographyType type, int entryId) {
    if (noteId <= 0) {
      throw new IllegalArgumentException("linkBibliography: noteId muss > 0 sein.");
    }
    if (type == null || type == BibliographyType.USER) {
      throw new IllegalArgumentException("linkBibliography: type muss ein echter Bibliographie-Typ sein.");
    }
    if (entryId <= 0) {
      throw new IllegalArgumentException("linkBibliography: entryId muss > 0 sein.");
    }

    String sql = "UPDATE notes SET bibliography_type = ?, bibliography_ref_id = ? WHERE id = ?";
    try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
      ps.setString(1, type.name());
      ps.setInt(2, entryId);
      ps.setInt(3, noteId);

      int changed = ps.executeUpdate();
      if (changed != 1) {
        throw new IllegalStateException("linkBibliography: keine Note aktualisiert (noteId=" + noteId + ")");
      }
    } catch (java.sql.SQLException e) {
      throw new IllegalStateException("linkBibliography fehlgeschlagen (noteId=" + noteId + ")", e);
    }
  }
}