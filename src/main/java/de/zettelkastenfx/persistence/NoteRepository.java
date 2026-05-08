package de.zettelkastenfx.persistence;

import de.zettelkastenfx.bibliography.api.BibliographyService;
import de.zettelkastenfx.bibliography.api.BibliographyServiceImpl;
import de.zettelkastenfx.bibliography.model.BibliographyReference;
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
  private final BibliographyService bibliographyService;

  public NoteRepository(DataSource ds) {
    this(ds, new BibliographyServiceImpl(ds));
  }

  /**
   * Erzeugt das Repository mit explizit übergebener Bibliographie-Fassade.
   *
   * @param ds Datenquelle der Anwendung
   * @param bibliographyService Bibliographie-Fassade
   */
  public NoteRepository(DataSource ds, BibliographyService bibliographyService) {
    this.ds = Objects.requireNonNull(ds, "ds");
    this.bibliographyService = Objects.requireNonNull(bibliographyService, "bibliographyService");
  }

  public record KeywordUsageRow(int id, String keyword, int count) {}

  public record NoteHeaderRow(int noteId, String title) {}

  public record ListBrowseRow(
      int noteId,
      String title,
      byte[] bodyBlob,
      String bodyCodec,
      String keyword
  ) {}

  public record BibliographyUsageRow(
      int noteId,
      String title,
      int bibliographyEntryId
  ) {}

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
      SELECT id, title, content_blob, content_codec, bibliography_ref_id, created_at, updated_at
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

        Integer bibliographyRefId = (Integer) rs.getObject("bibliography_ref_id");
        note.setBibliographyRefId(bibliographyRefId);

        String createdString = rs.getString("created_at");
        note.setCreatedAt((createdString == null || createdString.isEmpty()) ? null : Instant.parse(createdString));

        String updatedString = rs.getString("updated_at");
        note.setUpdatedAt((updatedString == null || updatedString.isEmpty()) ? null : Instant.parse(updatedString));

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
  /**
   * Laedt alle Zettel in stabiler Reihenfolge fuer modulare Exportpfade.
   *
   * @return alle Zettel, aufsteigend nach Zettelnummer sortiert
   */
  public List<Note> loadAllForExport() {
    String sql = """
      SELECT id
      FROM notes
      ORDER BY id ASC
      """;

    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {
      List<Note> notes = new ArrayList<>();
      while (rs.next()) {
        load(rs.getInt("id")).ifPresent(notes::add);
      }
      return notes;
    } catch (SQLException e) {
      throw new IllegalStateException("loadAllForExport fehlgeschlagen", e);
    }
  }

  /**
   * Laedt alle Zettelnummern in stabiler Exportreihenfolge ohne Zettelinhalte.
   *
   * @return alle Zettelnummern, aufsteigend sortiert
   */
  public List<Integer> loadAllNoteIdsForExport() {
    String sql = """
      SELECT id
      FROM notes
      ORDER BY id ASC
      """;

    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {
      List<Integer> noteIds = new ArrayList<>();
      while (rs.next()) {
        noteIds.add(rs.getInt("id"));
      }
      return noteIds;
    } catch (SQLException e) {
      throw new IllegalStateException("loadAllNoteIdsForExport fehlgeschlagen", e);
    }
  }

  /**
   * Laedt eine konkrete Zettelauswahl in der uebergebenen Reihenfolge.
   * Nicht vorhandene IDs werden ignoriert, damit Aufrufer eine bestehende
   * UI-Auswahl ohne eigene Vorfilterung uebergeben koennen.
   *
   * @param noteIds gewuenschte Zettelnummern
   * @return gefundene Zettel in Auswahlreihenfolge
   */
  public List<Note> loadForExport(Collection<Integer> noteIds) {
    if (noteIds == null || noteIds.isEmpty()) {
      return loadAllForExport();
    }

    List<Note> notes = new ArrayList<>();
    for (Integer noteId : noteIds) {
      if (noteId != null && noteId > 0) {
        load(noteId).ifPresent(notes::add);
      }
    }
    return notes;
  }

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
        INSERT INTO notes(title, content_blob, content_codec, bibliography_ref_id, created_at, updated_at)
        VALUES(?, ?, ?, ?, ?, ?)
        ON CONFLICT(id) DO UPDATE SET
        title = excluded.title,
        content_blob = excluded.content_blob,
        content_codec = excluded.content_codec,
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

        if (note.getBibliographyRefId() == null) {
          ps.setNull(4, Types.INTEGER);
        } else {
          ps.setInt(4, note.getBibliographyRefId());
        }

        ps.setString(5, now.toString());
        ps.setString(6, null);
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
        SET title = ?, content_blob = ?, content_codec = ?, bibliography_ref_id = ?, updated_at = ?
        WHERE id = ?
    """;

    Instant now = Instant.now();

    try (Connection c = ds.getConnection()) {
      c.setAutoCommit(false);
      try (PreparedStatement ps = c.prepareStatement(sql)) {
        ps.setString(1, note.getTitle());
        ps.setBytes(2, note.getBodyBlob());
        ps.setString(3, note.getBodyCodec());

        if (note.getBibliographyRefId() == null) {
          ps.setNull(4, Types.INTEGER);
        } else {
          ps.setInt(4, note.getBibliographyRefId());
        }

        ps.setString(5, now.toString());
        ps.setInt(6, note.getId());
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
   * Lädt alle Schlagwörter mit ID und Häufigkeit.
   *
   * @return Schlagwortzeilen für das KEY-Fenster
   */
  public List<KeywordUsageRow> loadKeywordUsageRows() {
    String sql = """
        SELECT k.id, k.keyword, COUNT(nk.note_id) AS cnt
        FROM keywords k
        LEFT JOIN note_keywords nk ON nk.keyword_id = k.id
        GROUP BY k.id, k.keyword
        ORDER BY k.keyword COLLATE NOCASE
        """;

    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {

      List<KeywordUsageRow> out = new ArrayList<>();
      while (rs.next()) {
        out.add(new KeywordUsageRow(
            rs.getInt("id"),
            rs.getString("keyword"),
            rs.getInt("cnt")
        ));
      }
      return out;

    } catch (SQLException e) {
      throw new IllegalStateException("loadKeywordUsageRows fehlgeschlagen", e);
    }
  }

  /**
   * Sucht Schlagwörter per case-insensitiver Enthält-Suche.
   *
   * @param text Suchtext
   * @param limit maximale Trefferzahl
   * @return passende Schlagwörter
   */
  public List<String> searchKeywordsContainingIgnoreCase(String text, int limit) {
    String normalized = text == null ? "" : text.trim();
    if (normalized.isBlank()) {
      return List.of();
    }

    String sql = """
        SELECT keyword
        FROM keywords
        WHERE lower(keyword) LIKE ?
        ORDER BY keyword COLLATE NOCASE
        LIMIT ?
        """;

    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

      ps.setString(1, "%" + normalized.toLowerCase(Locale.ROOT) + "%");
      ps.setInt(2, Math.max(1, limit));

      try (ResultSet rs = ps.executeQuery()) {
        List<String> out = new ArrayList<>();
        while (rs.next()) {
          out.add(rs.getString("keyword"));
        }
        return out;
      }

    } catch (SQLException e) {
      throw new IllegalStateException("searchKeywordsContainingIgnoreCase fehlgeschlagen", e);
    }
  }

  /**
   * Findet ein Schlagwort per case-insensitivem Exaktvergleich.
   *
   * @param keyword gesuchter Text
   * @return gefundenes Schlagwort mit ID und Häufigkeit
   */
  public Optional<KeywordUsageRow> findKeywordByExactIgnoreCase(String keyword) {
    String normalized = keyword == null ? "" : keyword.trim();
    if (normalized.isBlank()) {
      return Optional.empty();
    }

    String sql = """
        SELECT k.id, k.keyword, COUNT(nk.note_id) AS cnt
        FROM keywords k
        LEFT JOIN note_keywords nk ON nk.keyword_id = k.id
        WHERE lower(k.keyword) = ?
        GROUP BY k.id, k.keyword
        LIMIT 1
        """;

    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

      ps.setString(1, normalized.toLowerCase(Locale.ROOT));

      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) {
          return Optional.empty();
        }

        return Optional.of(new KeywordUsageRow(
            rs.getInt("id"),
            rs.getString("keyword"),
            rs.getInt("cnt")
        ));
      }

    } catch (SQLException e) {
      throw new IllegalStateException("findKeywordByExactIgnoreCase fehlgeschlagen", e);
    }
  }

  /**
   * Benennt ein Schlagwort um, ohne seine ID zu ändern.
   *
   * @param keywordId Schlagwort-ID
   * @param newKeyword neuer Text
   */
  public void renameKeyword(int keywordId, String newKeyword) {
    String normalized = newKeyword == null ? "" : newKeyword.trim();
    if (keywordId <= 0) {
      throw new IllegalArgumentException("renameKeyword: keywordId muss > 0 sein.");
    }
    if (normalized.isBlank()) {
      throw new IllegalArgumentException("renameKeyword: newKeyword darf nicht leer sein.");
    }

    String sql = """
        UPDATE keywords
        SET keyword = ?
        WHERE id = ?
        """;

    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setString(1, normalized);
      ps.setInt(2, keywordId);
      ps.executeUpdate();
    } catch (SQLException e) {
      throw new IllegalStateException("renameKeyword fehlgeschlagen (keywordId=" + keywordId + ")", e);
    }
  }

  /**
   * Löscht ein Schlagwort vollständig. Zugehörige Join-Einträge werden über
   * ON DELETE CASCADE entfernt.
   *
   * @param keywordId Schlagwort-ID
   */
  public void deleteKeyword(int keywordId) {
    if (keywordId <= 0) {
      throw new IllegalArgumentException("deleteKeyword: keywordId muss > 0 sein.");
    }

    String sql = "DELETE FROM keywords WHERE id = ?";

    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setInt(1, keywordId);
      ps.executeUpdate();
    } catch (SQLException e) {
      throw new IllegalStateException("deleteKeyword fehlgeschlagen (keywordId=" + keywordId + ")", e);
    }
  }

  /**
   * Führt zwei Schlagwörter zusammen. Das erste bleibt bestehen, das zweite wird
   * entfernt. Bereits vorhandene Zuordnungen werden nicht doppelt angelegt.
   *
   * @param keepKeywordId ID des beizubehaltenden Schlagworts
   * @param removeKeywordId ID des zu löschenden Schlagworts
   */
  public void mergeKeywords(int keepKeywordId, int removeKeywordId) {
    if (keepKeywordId <= 0 || removeKeywordId <= 0) {
      throw new IllegalArgumentException("mergeKeywords: IDs müssen > 0 sein.");
    }
    if (keepKeywordId == removeKeywordId) {
      return;
    }

    String copySql = """
        INSERT OR IGNORE INTO note_keywords(note_id, keyword_id)
        SELECT note_id, ?
        FROM note_keywords
        WHERE keyword_id = ?
        """;

    String deleteJoinSql = """
        DELETE FROM note_keywords
        WHERE keyword_id = ?
        """;

    String deleteKeywordSql = """
        DELETE FROM keywords
        WHERE id = ?
        """;

    try (Connection c = ds.getConnection()) {
      c.setAutoCommit(false);
      try (PreparedStatement copyPs = c.prepareStatement(copySql);
           PreparedStatement deleteJoinPs = c.prepareStatement(deleteJoinSql);
           PreparedStatement deleteKeywordPs = c.prepareStatement(deleteKeywordSql)) {

        copyPs.setInt(1, keepKeywordId);
        copyPs.setInt(2, removeKeywordId);
        copyPs.executeUpdate();

        deleteJoinPs.setInt(1, removeKeywordId);
        deleteJoinPs.executeUpdate();

        deleteKeywordPs.setInt(1, removeKeywordId);
        deleteKeywordPs.executeUpdate();

        c.commit();
      } catch (Exception ex) {
        c.rollback();
        throw ex;
      } finally {
        c.setAutoCommit(true);
      }
    } catch (Exception e) {
      throw new IllegalStateException(
          "mergeKeywords fehlgeschlagen (keep=" + keepKeywordId + ", remove=" + removeKeywordId + ")",
          e
      );
    }
  }

  /**
   * Lädt alle Zettelnummern und Überschriften zu einer Menge sichtbarer Schlagwörter.
   * Mehrfachtreffer werden dedupliziert, die Sortierung erfolgt nach Zettelnummer aufsteigend.
   *
   * @param visibleKeywords aktuell sichtbare Schlagwörter aus der KEY-Tabelle
   * @return deduplizierte Zettelköpfe
   */
  public List<NoteHeaderRow> loadNoteHeadersForVisibleKeywords(Collection<String> visibleKeywords) {
    if (visibleKeywords == null || visibleKeywords.isEmpty()) {
      return List.of();
    }

    List<String> normalized = new ArrayList<>();
    for (String keyword : visibleKeywords) {
      String value = keyword == null ? "" : keyword.trim();
      if (!value.isBlank()) {
        normalized.add(value);
      }
    }

    if (normalized.isEmpty()) {
      return List.of();
    }

    String placeholders = String.join(", ", Collections.nCopies(normalized.size(), "?"));
    String sql = """
        SELECT DISTINCT n.id, n.title
        FROM notes n
        JOIN note_keywords nk ON nk.note_id = n.id
        JOIN keywords k ON k.id = nk.keyword_id
        WHERE k.keyword IN (""" + placeholders + """
        )
        ORDER BY n.id ASC
        """;

    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

      for (int i = 0; i < normalized.size(); i++) {
        ps.setString(i + 1, normalized.get(i));
      }

      try (ResultSet rs = ps.executeQuery()) {
        List<NoteHeaderRow> out = new ArrayList<>();
        while (rs.next()) {
          out.add(new NoteHeaderRow(
              rs.getInt("id"),
              rs.getString("title")
          ));
        }
        return out;
      }

    } catch (SQLException e) {
      throw new IllegalStateException("loadNoteHeadersForVisibleKeywords fehlgeschlagen", e);
    }
  }

  /**
   * Lädt alle Zettel für das LIST-Fenster zusammen mit ihren Schlagwörtern.
   * Durch den LEFT JOIN entsteht pro Schlagwort eine Zeile; Zettel ohne Schlagwort
   * erscheinen genau einmal mit {@code keyword == null}.
   *
   * @return flache Ergebnisliste für LIST-Aufbereitung im Controller
   */
  public List<ListBrowseRow> loadListBrowseRows() {
    String sql = """
        SELECT n.id,
               n.title,
               n.content_blob,
               n.content_codec,
               k.keyword
        FROM notes n
        LEFT JOIN note_keywords nk ON nk.note_id = n.id
        LEFT JOIN keywords k ON k.id = nk.keyword_id
        ORDER BY n.id ASC, k.keyword COLLATE NOCASE
        """;

    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {

      List<ListBrowseRow> out = new ArrayList<>();
      while (rs.next()) {
        out.add(new ListBrowseRow(
            rs.getInt("id"),
            rs.getString("title"),
            rs.getBytes("content_blob"),
            rs.getString("content_codec"),
            rs.getString("keyword")
        ));
      }
      return out;

    } catch (SQLException e) {
      throw new IllegalStateException("loadListBrowseRows fehlgeschlagen", e);
    }
  }

  /**
   * Lädt alle Zettel, die aktuell mit einem bibliographischen Eintrag
   * verknüpft sind.
   * Die Methode dient als BOOK-Datenbasis für:
   * - Zettelanzahl je Medium
   * - spätere untere Zetteltabelle
   *
   * @return flache Zuordnung Zettel -> Bibliographie-Eintrag
   */
  public List<BibliographyUsageRow> loadBibliographyUsageRows() {
    String sql = """
        SELECT n.id,
               n.title,
               n.bibliography_ref_id
        FROM notes n
        WHERE n.bibliography_ref_id IS NOT NULL
        ORDER BY n.bibliography_ref_id ASC, n.id ASC
        """;

    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {

      List<BibliographyUsageRow> out = new ArrayList<>();
      while (rs.next()) {
        Integer bibliographyEntryId = (Integer) rs.getObject("bibliography_ref_id");
        if (bibliographyEntryId == null || bibliographyEntryId <= 0) {
          continue;
        }

        out.add(new BibliographyUsageRow(
            rs.getInt("id"),
            rs.getString("title"),
            bibliographyEntryId
        ));
      }
      return out;
    } catch (SQLException e) {
      throw new IllegalStateException("loadBibliographyUsageRows fehlgeschlagen", e);
    }
  }

  /**
   * Laedt Zettelnummern fuer eine Menge bibliographischer Eintraege.
   * Die Abfrage bleibt bewusst schlank, damit Zaehl- und Auswahlvorschauen im
   * Exportfenster nicht komplette Zettel inklusive Inhalt laden muessen.
   *
   * @param entryIds Bibliographie-IDs
   * @return passende Zettelnummern in Exportreihenfolge
   */
  public List<Integer> loadNoteIdsForBibliographyEntries(Collection<Integer> entryIds) {
    if (entryIds == null || entryIds.isEmpty()) {
      return List.of();
    }

    List<Integer> normalized = entryIds.stream()
                                   .filter(Objects::nonNull)
                                   .filter(id -> id > 0)
                                   .distinct()
                                   .toList();
    if (normalized.isEmpty()) {
      return List.of();
    }

    LinkedHashSet<Integer> noteIds = new LinkedHashSet<>();
    int chunkSize = 800;
    for (int start = 0; start < normalized.size(); start += chunkSize) {
      int end = Math.min(start + chunkSize, normalized.size());
      noteIds.addAll(loadNoteIdsForBibliographyEntryChunk(normalized.subList(start, end)));
    }
    return new ArrayList<>(noteIds);
  }

  private List<Integer> loadNoteIdsForBibliographyEntryChunk(List<Integer> entryIds) {
    String placeholders = String.join(", ", Collections.nCopies(entryIds.size(), "?"));
    String sql = """
        SELECT id
        FROM notes
        WHERE bibliography_ref_id IN (""" + placeholders + """
        )
        ORDER BY id ASC
        """;

    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {
      for (int i = 0; i < entryIds.size(); i++) {
        ps.setInt(i + 1, entryIds.get(i));
      }

      try (ResultSet rs = ps.executeQuery()) {
        List<Integer> noteIds = new ArrayList<>();
        while (rs.next()) {
          noteIds.add(rs.getInt("id"));
        }
        return noteIds;
      }
    } catch (SQLException e) {
      throw new IllegalStateException("loadNoteIdsForBibliographyEntries fehlgeschlagen", e);
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

  /**
   * Lädt die schlanke bibliographische Referenz einer Notiz.
   *
   * @param noteId Zettel-ID
   * @return optionale bibliographische Referenz
   */
  public Optional<BibliographyReference> loadBibliographyReference(int noteId) {
    String sql = """
        SELECT bibliography_ref_id
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

        Integer bibliographyRefId = (Integer) rs.getObject("bibliography_ref_id");
        if (bibliographyRefId == null || bibliographyRefId <= 0) {
          return Optional.empty();
        }

        return bibliographyService.loadReference(bibliographyRefId);
      }
    } catch (SQLException e) {
      throw new IllegalStateException(
          "loadBibliographyReference fehlgeschlagen (noteId=" + noteId + ")",
          e
      );
    }
  }

  /**
   * Verknüpft eine Notiz mit einem bibliographischen Eintrag.
   *
   * @param noteId Zettel-ID
   * @param entryId Bibliographie-Eintrags-ID
   */
  public void linkBibliographyEntry(int noteId, int entryId) {
    if (noteId <= 0) {
      throw new IllegalArgumentException("linkBibliographyEntry: noteId muss > 0 sein.");
    }
    if (entryId <= 0) {
      throw new IllegalArgumentException("linkBibliographyEntry: entryId muss > 0 sein.");
    }

    String sql = """
      UPDATE notes
      SET bibliography_ref_id = ?,
          updated_at = ?
      WHERE id = ?
      """;

    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

      ps.setInt(1, entryId);
      ps.setString(2, Instant.now().toString());
      ps.setInt(3, noteId);
      ps.executeUpdate();

    } catch (SQLException e) {
      throw new IllegalStateException(
          "linkBibliographyEntry fehlgeschlagen (noteId=" + noteId + ", entryId=" + entryId + ")",
          e
      );
    }
  }

  /**
   * Entfernt die bibliographische Verknüpfung einer Notiz.
   *
   * @param noteId Zettel-ID
   */
  public void unlinkBibliographyEntry(int noteId) {
    if (noteId <= 0) {
      throw new IllegalArgumentException("unlinkBibliographyEntry: noteId muss > 0 sein.");
    }

    String sql = """
      UPDATE notes
      SET bibliography_ref_id = NULL,
          updated_at = ?
      WHERE id = ?
      """;

    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

      ps.setString(1, Instant.now().toString());
      ps.setInt(2, noteId);
      ps.executeUpdate();

    } catch (SQLException e) {
      throw new IllegalStateException(
          "unlinkBibliographyEntry fehlgeschlagen (noteId=" + noteId + ")",
          e
      );
    }
  }
}
