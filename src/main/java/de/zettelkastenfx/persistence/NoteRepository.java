package de.zettelkastenfx.persistence;

import de.zettelkastenfx.bibliography.model.BibliographyType;
import de.zettelkastenfx.bibliography.persistence.BibliographyRepository;
import de.zettelkastenfx.notes.model.LinkType;
import de.zettelkastenfx.notes.model.Note;
import de.zettelkastenfx.notes.model.NoteLink;
import de.zettelkastenfx.notes.model.NoteReferenceInfo;

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

        List<NoteLink> successors = loadSuccessorsForNote(c, id);
        note.setSuccessors(successors);

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
    VALUES(?, ?, ?, ?, ?, ?, ?)
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
        ps.setString(4, note.getBibliographyType().name());

        if (note.getBibliographyRefId() == null) {
          ps.setNull(5, Types.INTEGER);
        } else {
          ps.setInt(5, note.getBibliographyRefId());
        }

        ps.setString(6, now.toString());
        ps.setString(7, null);
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
    SET title = ?, content_blob = ?, content_codec = ?, bibliography_type = ?, bibliography_ref_id = ?, updated_at = ?
    WHERE id = ?
    """;

    Instant now = Instant.now();

    try (Connection c = ds.getConnection()) {
      c.setAutoCommit(false);
      try (PreparedStatement ps = c.prepareStatement(sql)) {
        ps.setString(1, note.getTitle());
        ps.setBytes(2, note.getBodyBlob());
        ps.setString(3, note.getBodyCodec());
        ps.setString(4, note.getBibliographyType().name());

        if (note.getBibliographyRefId() == null) {
          ps.setNull(5, Types.INTEGER);
        } else {
          ps.setInt(5, note.getBibliographyRefId());
        }

        ps.setString(6, now.toString());
        ps.setInt(7, note.getId());
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

  /**
   * Lädt alle Folgezettel-Verweise eines Zettels.
   *
   * @param c offene Datenbankverbindung
   * @param noteId ID des Quellzettels
   * @return typisierte Folgezettel
   * @throws SQLException bei Datenbankfehlern
   */
  private List<NoteLink> loadSuccessorsForNote(Connection c, int noteId) throws SQLException {
    String sql = """
        SELECT to_note_id, link_type
        FROM note_links
        WHERE from_note_id = ?
        ORDER BY to_note_id
        """;

    try (PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setInt(1, noteId);

      try (ResultSet rs = ps.executeQuery()) {
        List<NoteLink> out = new ArrayList<>();
        while (rs.next()) {
          String rawType = rs.getString("link_type");
          if (rawType == null || rawType.isBlank()) {
            continue;
          }

          out.add(new NoteLink(
              rs.getInt("to_note_id"),
              LinkType.valueOf(rawType)
          ));
        }
        return out;
      }
    }
  }

  /**
   * Lädt alle Folgezettel eines Zettels.
   *
   * @param noteId ID des Quellzettels
   * @return typisierte Folgezettel
   */
  public List<NoteLink> loadSuccessors(int noteId) {
    if (noteId <= 0) {
      return List.of();
    }

    try (Connection c = ds.getConnection()) {
      return loadSuccessorsForNote(c, noteId);
    } catch (SQLException e) {
      throw new IllegalStateException("loadSuccessors fehlgeschlagen (noteId=" + noteId + ")", e);
    }
  }

  /**
   * Lädt die Überschrift eines Zettels.
   *
   * @param noteId ID des Zettels
   * @return optionale Überschrift
   */
  public Optional<String> loadNoteTitle(int noteId) {
    if (noteId <= 0) {
      return Optional.empty();
    }

    String sql = """
        SELECT title
        FROM notes
        WHERE id = ?
        """;

    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setInt(1, noteId);

      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) {
          return Optional.empty();
        }
        return Optional.ofNullable(rs.getString("title"));
      }
    } catch (SQLException e) {
      throw new IllegalStateException("loadNoteTitle fehlgeschlagen (noteId=" + noteId + ")", e);
    }
  }

  /**
   * Sucht die nächstniedrigere existierende Zettelnummer.
   *
   * @param noteId Ausgangs-Zettelnummer
   * @return optionale vorherige Zettelnummer
   */
  public OptionalInt findPreviousExistingNoteId(int noteId) {
    if (noteId <= 1) {
      return OptionalInt.empty();
    }

    String sql = """
        SELECT MAX(id) AS prev_id
        FROM notes
        WHERE id < ?
        """;

    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setInt(1, noteId);

      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) {
          return OptionalInt.empty();
        }

        int value = rs.getInt("prev_id");
        return value > 0 ? OptionalInt.of(value) : OptionalInt.empty();
      }
    } catch (SQLException e) {
      throw new IllegalStateException(
          "findPreviousExistingNoteId fehlgeschlagen (noteId=" + noteId + ")",
          e
      );
    }
  }

  /**
   * Sucht die nächsthöhere existierende Zettelnummer.
   *
   * @param noteId Ausgangs-Zettelnummer
   * @return optionale nächste Zettelnummer
   */
  public OptionalInt findNextExistingNoteId(int noteId) {
    if (noteId < 0) {
      return OptionalInt.empty();
    }

    String sql = """
        SELECT MIN(id) AS next_id
        FROM notes
        WHERE id > ?
        """;

    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setInt(1, noteId);

      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) {
          return OptionalInt.empty();
        }

        int value = rs.getInt("next_id");
        return value > 0 ? OptionalInt.of(value) : OptionalInt.empty();
      }
    } catch (SQLException e) {
      throw new IllegalStateException(
          "findNextExistingNoteId fehlgeschlagen (noteId=" + noteId + ")",
          e
      );
    }
  }

  /**
   * Lädt alle ausgehenden Verweise eines Zettels für das Info-Popup.
   * Neben der Ziel-ID werden auch Referenztyp und Überschrift des Zielzettels geladen.
   *
   * @param noteId ID des Quellzettels
   * @return Liste der ausgehenden Referenzen
   */
  public List<NoteReferenceInfo> loadOutgoingReferenceInfos(int noteId) {
    if (noteId <= 0) {
      return List.of();
    }

    String sql = """
        SELECT nl.to_note_id AS ref_note_id,
               nl.link_type  AS link_type,
               n.title       AS ref_title
        FROM note_links nl
        LEFT JOIN notes n ON n.id = nl.to_note_id
        WHERE nl.from_note_id = ?
        ORDER BY nl.to_note_id
        """;

    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setInt(1, noteId);

      try (ResultSet rs = ps.executeQuery()) {
        List<NoteReferenceInfo> out = new ArrayList<>();
        while (rs.next()) {
          NoteReferenceInfo info = mapReferenceInfo(rs);
          if (info != null) {
            out.add(info);
          }
        }
        return out;
      }
    } catch (SQLException e) {
      throw new IllegalStateException(
          "loadOutgoingReferenceInfos fehlgeschlagen (noteId=" + noteId + ")",
          e
      );
    }
  }

  /**
   * Lädt alle eingehenden Verweise eines Zettels für das Info-Popup.
   * Neben der Quell-ID werden auch Referenztyp und Überschrift des Quellzettels geladen.
   *
   * @param noteId ID des Zielzettels
   * @return Liste der eingehenden Referenzen
   */
  public List<NoteReferenceInfo> loadIncomingReferenceInfos(int noteId) {
    if (noteId <= 0) {
      return List.of();
    }

    String sql = """
        SELECT nl.from_note_id AS ref_note_id,
               nl.link_type    AS link_type,
               n.title         AS ref_title
        FROM note_links nl
        JOIN notes n ON n.id = nl.from_note_id
        WHERE nl.to_note_id = ?
        ORDER BY nl.from_note_id
        """;

    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setInt(1, noteId);

      try (ResultSet rs = ps.executeQuery()) {
        List<NoteReferenceInfo> out = new ArrayList<>();
        while (rs.next()) {
          NoteReferenceInfo info = mapReferenceInfo(rs);
          if (info != null) {
            out.add(info);
          }
        }
        return out;
      }
    } catch (SQLException e) {
      throw new IllegalStateException(
          "loadIncomingReferenceInfos fehlgeschlagen (noteId=" + noteId + ")",
          e
      );
    }
  }

  /**
   * Wandelt eine Ergebniszeile aus einer Referenzabfrage in ein Popup-Modell um.
   * Ungültige oder unbekannte Linktypen werden verworfen, da es dafür keinen
   * fachlich zulässigen Fallback-Typ mehr gibt.
   *
   * @param rs aktuelle Ergebniszeile
   * @return angereicherter Popup-Eintrag oder {@code null}, wenn die Zeile unbrauchbar ist
   * @throws SQLException bei JDBC-Fehlern
   */
  private NoteReferenceInfo mapReferenceInfo(ResultSet rs) throws SQLException {
    int refNoteId = rs.getInt("ref_note_id");
    if (refNoteId <= 0) {
      return null;
    }

    String rawType = rs.getString("link_type");
    if (rawType == null || rawType.isBlank()) {
      return null;
    }

    final LinkType linkType;
    try {
      linkType = LinkType.valueOf(rawType.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      return null;
    }

    String title = rs.getString("ref_title");
    return new NoteReferenceInfo(refNoteId, linkType, title);
  }

  /**
   * Legt einen typisierten Folgezettel-Verweis an oder überschreibt einen bereits
   * vorhandenen Verweis auf dasselbe Ziel mit dem neuen Typ.
   *
   * @param fromNoteId Quellzettel
   * @param toNoteId Zielzettel
   * @param linkType Typ des Verweises
   */
  public void addSuccessorLink(int fromNoteId, int toNoteId, LinkType linkType) {
    if (fromNoteId <= 0) {
      throw new IllegalArgumentException("addSuccessorLink: fromNoteId muss > 0 sein.");
    }
    if (toNoteId <= 0) {
      throw new IllegalArgumentException("addSuccessorLink: toNoteId muss > 0 sein.");
    }
    if (linkType == null) {
      throw new IllegalArgumentException("addSuccessorLink: linkType darf nicht null sein.");
    }

    String sql = """
        INSERT INTO note_links(from_note_id, to_note_id, link_type)
        VALUES(?, ?, ?)
        ON CONFLICT(from_note_id, to_note_id) DO UPDATE SET
          link_type = excluded.link_type
        """;

    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setInt(1, fromNoteId);
      ps.setInt(2, toNoteId);
      ps.setString(3, linkType.name());
      ps.executeUpdate();
    } catch (SQLException e) {
      throw new IllegalStateException(
          "addSuccessorLink fehlgeschlagen (from=" + fromNoteId + ", to=" + toNoteId + ")",
          e
      );
    }
  }

  /**
   * Entfernt einen Folgezettel-Verweis zwischen zwei Zetteln.
   *
   * @param fromNoteId Quellzettel
   * @param toNoteId Zielzettel
   */
  public void removeSuccessorLink(int fromNoteId, int toNoteId) {
    if (fromNoteId <= 0 || toNoteId <= 0) {
      return;
    }

    String sql = """
        DELETE FROM note_links
        WHERE from_note_id = ? AND to_note_id = ?
        """;

    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setInt(1, fromNoteId);
      ps.setInt(2, toNoteId);
      ps.executeUpdate();
    } catch (SQLException e) {
      throw new IllegalStateException(
          "removeSuccessorLink fehlgeschlagen (from=" + fromNoteId + ", to=" + toNoteId + ")",
          e
      );
    }
  }

  /**
   * Entfernt alle Verweise auf einen Zielzettel.
   * Diese Methode wird verwendet, wenn ein Zettel nicht persistiert wird
   * oder als ungültiger letzter Zettel wieder verworfen werden muss.
   *
   * @param toNoteId ID des Zielzettels, auf den verwiesen wird
   */
  public void removeAllIncomingLinksToNote(int toNoteId) {
    if (toNoteId <= 0) {
      return;
    }

    String sql = """
        DELETE FROM note_links
        WHERE to_note_id = ?
        """;

    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setInt(1, toNoteId);
      ps.executeUpdate();
    } catch (SQLException e) {
      throw new IllegalStateException(
          "removeAllIncomingLinksToNote fehlgeschlagen (toNoteId=" + toNoteId + ")",
          e
      );
    }
  }

  /**
   * Prüft, ob ein Zettel mit der angegebenen ID in der Datenbank existiert.
   *
   * @param noteId Zettel-ID
   * @return {@code true}, wenn ein entsprechender Zettel existiert
   */
  public boolean existsNote(int noteId) {
    if (noteId <= 0) {
      return false;
    }

    String sql = """
        SELECT 1
        FROM notes
        WHERE id = ?
        LIMIT 1
        """;

    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setInt(1, noteId);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next();
      }
    } catch (SQLException e) {
      throw new IllegalStateException("existsNote fehlgeschlagen (noteId=" + noteId + ")", e);
    }
  }

  /**
   * Löscht einen Zettel nur dann, wenn er aktuell der letzte Zettel ist.
   * Vor dem Löschen werden alle auf diesen Zettel zeigenden Verweise entfernt,
   * damit keine verwaisten Einträge in {@code note_links} zurückbleiben.
   *
   * @param noteId zu prüfende Zettel-ID
   * @return {@code true}, wenn der Zettel gelöscht wurde, sonst {@code false}
   */
  public boolean deleteNoteIfItIsLast(int noteId) {
    if (noteId <= 0) {
      return false;
    }

    String existsHigherSql = """
        SELECT 1
        FROM notes
        WHERE id > ?
        LIMIT 1
        """;

    String deleteNoteSql = """
        DELETE FROM notes
        WHERE id = ?
        """;

    try (Connection c = ds.getConnection()) {
      c.setAutoCommit(false);
      try {
        boolean hasHigherNote;
        try (PreparedStatement ps = c.prepareStatement(existsHigherSql)) {
          ps.setInt(1, noteId);
          try (ResultSet rs = ps.executeQuery()) {
            hasHigherNote = rs.next();
          }
        }

        if (hasHigherNote) {
          c.rollback();
          return false;
        }

        deleteAllIncomingLinksToNote(c, noteId);

        try (PreparedStatement ps = c.prepareStatement(deleteNoteSql)) {
          ps.setInt(1, noteId);
          boolean deleted = ps.executeUpdate() == 1;
          c.commit();
          return deleted;
        }

      } catch (Exception ex) {
        c.rollback();
        throw ex;
      } finally {
        c.setAutoCommit(true);
      }
    } catch (Exception e) {
      throw new IllegalStateException(
          "deleteNoteIfItIsLast fehlgeschlagen (noteId=" + noteId + ")",
          e
      );
    }
  }

  /**
   * Entfernt alle eingehenden Verweise auf einen Zielzettel innerhalb einer
   * bereits offenen Transaktion.
   *
   * @param c offene Datenbankverbindung
   * @param toNoteId ID des Zielzettels
   * @throws SQLException bei Datenbankfehlern
   */
  private void deleteAllIncomingLinksToNote(Connection c, int toNoteId) throws SQLException {
    String sql = """
        DELETE FROM note_links
        WHERE to_note_id = ?
        """;

    try (PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setInt(1, toNoteId);
      ps.executeUpdate();
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

  /**
   * Dreht einen bestehenden Verweis zwischen zwei Zetteln um.
   * Aus {@code fromNoteId -> toNoteId} wird {@code toNoteId -> fromNoteId}.
   * Der bestehende Verweistyp bleibt erhalten.
   *
   * Falls kein entsprechender Verweis existiert, wird {@code false} zurückgegeben.
   *
   * @param fromNoteId bisheriger Quellzettel
   * @param toNoteId bisheriger Zielzettel
   * @return {@code true}, wenn ein Verweis umgedreht wurde, sonst {@code false}
   */
  public boolean reverseSuccessorLink(int fromNoteId, int toNoteId) {
    if (fromNoteId <= 0 || toNoteId <= 0) {
      return false;
    }

    String selectSql = """
        SELECT link_type
        FROM note_links
        WHERE from_note_id = ? AND to_note_id = ?
        """;

    String deleteSql = """
        DELETE FROM note_links
        WHERE from_note_id = ? AND to_note_id = ?
        """;

    String insertSql = """
        INSERT INTO note_links(from_note_id, to_note_id, link_type)
        VALUES(?, ?, ?)
        ON CONFLICT(from_note_id, to_note_id) DO UPDATE SET
          link_type = excluded.link_type
        """;

    try (Connection c = ds.getConnection()) {
      c.setAutoCommit(false);
      try {
        String rawType;

        try (PreparedStatement ps = c.prepareStatement(selectSql)) {
          ps.setInt(1, fromNoteId);
          ps.setInt(2, toNoteId);

          try (ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
              c.rollback();
              return false;
            }
            rawType = rs.getString("link_type");
          }
        }

        try (PreparedStatement ps = c.prepareStatement(deleteSql)) {
          ps.setInt(1, fromNoteId);
          ps.setInt(2, toNoteId);
          ps.executeUpdate();
        }

        try (PreparedStatement ps = c.prepareStatement(insertSql)) {
          ps.setInt(1, toNoteId);
          ps.setInt(2, fromNoteId);
          ps.setString(3, rawType);
          ps.executeUpdate();
        }

        c.commit();
        return true;

      } catch (Exception ex) {
        c.rollback();
        throw ex;
      } finally {
        c.setAutoCommit(true);
      }
    } catch (Exception e) {
      throw new IllegalStateException(
          "reverseSuccessorLink fehlgeschlagen (from=" + fromNoteId + ", to=" + toNoteId + ")",
          e
      );
    }
  }
}