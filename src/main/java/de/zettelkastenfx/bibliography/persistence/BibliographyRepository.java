package de.zettelkastenfx.bibliography.persistence;

import de.zettelkastenfx.bibliography.model.*;
import de.zettelkastenfx.bibliography.ui.lookup.AuthorSuggestion;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class BibliographyRepository {

  private final DataSource ds;
  private static final DateTimeFormatter SQLITE_TS =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private String lastErrorMessage = "";

  public BibliographyRepository(DataSource ds) {
    this.ds = ds;
  }

  public String getLastErrorMessage() {
    return lastErrorMessage == null ? "" : lastErrorMessage;
  }

  private void rememberError(String context, Exception ex) {
    String message = (ex == null || ex.getMessage() == null || ex.getMessage().isBlank())
                         ? "Unbekannter Fehler"
                         : ex.getMessage();

    lastErrorMessage = context + ": " + ex.getClass().getSimpleName() + " - " + message;
  }

  public BibliographyEntry load(int id, BibliographyType type) {
    try {
      return switch (type) {
        case BOOK -> loadBookDetails(id);
        case COLLECTION -> loadCollection(id);
        case ARTICLE_IN_COLLECTION -> loadArticleInCollection(id);
        case JOURNAL -> loadJournal(id);
        case JOURNAL_ARTICLE -> loadJournalArticle(id);
        case THESIS -> loadThesis(id);
        case INTERNET -> loadInternet(id);
        case EDITION -> loadEdition(id);
        case AI -> loadAi(id);
        default -> null;
      };
    } catch (Exception ex) {
      System.out.println("Fehler in BibliographyRepository.load()");
      ex.printStackTrace();
      return null;
    }
  }

  public int save(BibliographyEntry entry, BibliographyType type) {
    lastErrorMessage = "";
    if (entry == null || type == null || type == BibliographyType.USER) {
      return -1;
    }

    try (Connection c = ds.getConnection()) {
      boolean oldAutoCommit = c.getAutoCommit();
      c.setAutoCommit(false);

      try {
        int id = switch (type) {
          case BOOK -> saveBook(c, (BookEntry) entry);
          case COLLECTION -> saveCollection(c, (CollectionEntry) entry);
          case ARTICLE_IN_COLLECTION -> saveArticleInCollection(c, (ArticleInCollectionEntry) entry);
          case JOURNAL -> saveJournal(c, (JournalEntry) entry);
          case JOURNAL_ARTICLE -> saveJournalArticle(c, (JournalArticleEntry) entry);
          case THESIS -> saveThesis(c, (ThesisEntry) entry);
          case INTERNET -> saveInternet(c, (InternetEntry) entry);
          case EDITION -> saveEdition(c, (EditionEntry) entry);
          case AI -> saveAi(c, (AiEntry) entry);
          default -> throw new UnsupportedOperationException("Noch nicht implementiert: " + type);
        };

        c.commit();
        return id;
      } catch (Exception ex) {
        c.rollback();
        rememberError("BibliographyRepository.save()", ex);
        System.out.println("Fehler in BibliographyRepository.save()");
        ex.printStackTrace();
        return -1;
      } finally {
        c.setAutoCommit(oldAutoCommit);
      }
    } catch (SQLException ex) {
      rememberError("BibliographyRepository.save()", ex);
      System.out.println("Fehler in BibliographyRepository.save()");
      ex.printStackTrace();
      return -1;
    }
  }

  private void loadAuthorsInto(Connection c, BibliographyEntry entry) throws SQLException {
    String sql = """
      SELECT a.id, a.first_name, a.last_name, am.author_pos
      FROM authors a
      JOIN author_mapping am
        ON am.author_id = a.id
      WHERE am.entry_id = ?
      ORDER BY am.author_pos ASC
      """;

    try (PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setInt(1, entry.getId());

      try (ResultSet rs = ps.executeQuery()) {
        entry.getAuthors().clear();

        while (rs.next()) {
          Author au = new Author();
          au.setId(rs.getInt("id"));
          au.setFirstName(rs.getString("first_name"));
          au.setLastName(rs.getString("last_name"));
          au.setPosition(rs.getInt("author_pos"));
          entry.getAuthors().add(au);
        }
      }
    }
  }

  private void persistAuthorsAndJoin(Connection c, BibliographyEntry entry) throws SQLException {
    try (PreparedStatement del = c.prepareStatement(
        "DELETE FROM author_mapping WHERE entry_id = ?")) {
      del.setInt(1, entry.getId());
      del.executeUpdate();
    }

    int pos = 1;
    for (Author a : entry.getAuthors()) {
      Integer authorId = ensureAuthorId(c, a);
      if (authorId == null) {
        continue; // komplett leere Autorenzeilen ignorieren
      }

      a.setId(authorId);
      a.setPosition(pos);

      try (PreparedStatement ins = c.prepareStatement(
          "INSERT INTO author_mapping(entry_id, author_id, author_pos) VALUES(?, ?, ?)")) {
        ins.setInt(1, entry.getId());
        ins.setInt(2, authorId);
        ins.setInt(3, pos);
        ins.executeUpdate();
      }

      pos++;
    }
  }

  private Integer ensureAuthorId(Connection c, Author a) throws SQLException {
    String fn = a.getFirstName() == null ? "" : a.getFirstName().trim();
    String ln = a.getLastName() == null ? "" : a.getLastName().trim();

    if (fn.isBlank() && ln.isBlank()) {
      return null;
    }

    a.setFirstName(fn);
    a.setLastName(ln);

    try (PreparedStatement sel = c.prepareStatement(
        "SELECT id FROM authors WHERE first_name = ? AND last_name = ?")) {
      sel.setString(1, fn);
      sel.setString(2, ln);

      try (ResultSet rs = sel.executeQuery()) {
        if (rs.next()) {
          return rs.getInt(1);
        }
      }
    }

    try (PreparedStatement ins = c.prepareStatement(
        "INSERT INTO authors(first_name, last_name) VALUES(?, ?)",
        Statement.RETURN_GENERATED_KEYS)) {
      ins.setString(1, fn);
      ins.setString(2, ln);
      ins.executeUpdate();

      try (ResultSet keys = ins.getGeneratedKeys()) {
        if (!keys.next()) {
          throw new IllegalStateException("Keine Author-ID erhalten.");
        }
        return keys.getInt(1);
      }
    }
  }

  private void loadArticleInCollectionDetails(Connection c, ArticleInCollectionEntry e)
      throws SQLException {

    String sql = """
      SELECT collection_entry_id, pages
      FROM bibliography_article_in_collection
      WHERE id = ?
      """;

    try (PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setInt(1, e.getId());
      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) return;

        Integer cid = (Integer) rs.getObject("collection_entry_id");
        e.setCollectionEntryId(cid);
        e.setPages(rs.getString("pages"));

        // Collection-Titel auflösen (für UI)
        if (cid != null) {
          e.setCollectionTitle(loadEntryTitleById(c, cid));
        } else {
          e.setCollectionTitle("");
        }
      }
    }
  }

  private BookEntry loadBookDetails(int entryId) throws SQLException {
    String sql = """
      SELECT
          be.id,
          be.title,
          be.created_at,
          be.updated_at,
          b.subtitle,
          b.publisher,
          b.place,
          b.year,
          b.isbn,
          b.run,
          b.series,
          b.series_number
      FROM bibliography_entries be
      JOIN bibliography_book b
        ON b.id = be.id
      WHERE be.id = ?
        AND be.type = ?
      """;

    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

      ps.setInt(1, entryId);
      ps.setString(2, BibliographyType.BOOK.name());

      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) return null;

        BookEntry e = new BookEntry();
        e.setId(rs.getInt("id"));
        e.setTitle(rs.getString("title"));
        e.setSubtitle(rs.getString("subtitle"));
        e.setPublisher(rs.getString("publisher"));
        e.setPlace(rs.getString("place"));
        e.setYear(rs.getString("year"));
        e.setIsbn(rs.getString("isbn"));
        e.setRun(rs.getString("run"));
        e.setSeries(rs.getString("series"));
        e.setSeriesNumber(rs.getString("series_number"));
        e.setCreatedAt(parseSqliteTextToLocalDateTime(rs.getString("created_at")));
        e.setUpdatedAt(parseSqliteTextToLocalDateTime(rs.getString("updated_at")));

        loadAuthorsInto(c, e);
        return e;
      }
    }
  }

  private void saveBookDetails(Connection c, BookEntry e) throws SQLException {
    String sql = """
      INSERT INTO bibliography_book(
          id, subtitle, publisher, place, year, isbn, run, series, series_number
      )
      VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT(id) DO UPDATE SET
          subtitle = excluded.subtitle,
          publisher = excluded.publisher,
          place = excluded.place,
          year = excluded.year,
          isbn = excluded.isbn,
          run = excluded.run,
          series = excluded.series,
          series_number = excluded.series_number
      """;

    try (PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setInt(1, e.getId());
      ps.setString(2, e.getSubtitle());
      ps.setString(3, e.getPublisher());
      ps.setString(4, e.getPlace());
      ps.setString(5, e.getYear());
      ps.setString(6, e.getIsbn());
      ps.setString(7, e.getRun());
      ps.setString(8, e.getSeries());
      ps.setString(9, e.getSeriesNumber());
      ps.executeUpdate();
    }
  }

  private int saveBook(Connection c, BookEntry e) throws SQLException {
    saveOrUpdateBaseEntry(c, e, BibliographyType.BOOK);
    saveBookDetails(c, e);
    persistAuthorsAndJoin(c, e);
    return e.getId();
  }

  private CollectionEntry loadCollection(int entryId) throws SQLException {
    String sql = """
      SELECT
          be.id,
          be.title,
          be.created_at,
          be.updated_at,
          bc.subtitle,
          bc.publisher,
          bc.place,
          bc.year,
          bc.series
      FROM bibliography_entries be
      JOIN bibliography_collection bc
        ON bc.id = be.id
      WHERE be.id = ?
        AND be.type = ?
      """;

    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

      ps.setInt(1, entryId);
      ps.setString(2, BibliographyType.COLLECTION.name());

      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) {
          return null;
        }

        CollectionEntry e = new CollectionEntry();
        e.setId(rs.getInt("id"));
        e.setTitle(rs.getString("title"));
        e.setSubtitle(rs.getString("subtitle"));
        e.setPublisher(rs.getString("publisher"));
        e.setPlace(rs.getString("place"));
        e.setYear(rs.getString("year"));
        e.setSeries(rs.getString("series"));
        e.setCreatedAt(parseSqliteTextToLocalDateTime(rs.getString("created_at")));
        e.setUpdatedAt(parseSqliteTextToLocalDateTime(rs.getString("updated_at")));

        loadAuthorsInto(c, e);
        return e;
      }
    }
  }

  private int saveCollection(Connection c, CollectionEntry e) throws SQLException {
    saveOrUpdateBaseEntry(c, e, BibliographyType.COLLECTION);
    saveCollectionDetails(c, e);
    persistAuthorsAndJoin(c, e);
    return e.getId();
  }

  private void saveCollectionDetails(Connection c, CollectionEntry e) throws SQLException {
    String sql = """
      INSERT INTO bibliography_collection(
          id, subtitle, publisher, place, year, series
      )
      VALUES(?, ?, ?, ?, ?, ?)
      ON CONFLICT(id) DO UPDATE SET
          subtitle = excluded.subtitle,
          publisher = excluded.publisher,
          place = excluded.place,
          year = excluded.year,
          series = excluded.series
      """;

    try (PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setInt(1, e.getId());
      ps.setString(2, e.getSubtitle());
      ps.setString(3, e.getPublisher());
      ps.setString(4, e.getPlace());
      ps.setString(5, e.getYear());
      ps.setString(6, e.getSeries());
      ps.executeUpdate();
    }
  }

  private ArticleInCollectionEntry loadArticleInCollection(int entryId) throws SQLException {
    String sql = """
      SELECT
          be.id,
          be.title,
          be.created_at,
          be.updated_at,
          baic.collection_entry_id,
          baic.pages
      FROM bibliography_entries be
      JOIN bibliography_article_in_collection baic
        ON baic.id = be.id
      WHERE be.id = ?
        AND be.type = ?
      """;

    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

      ps.setInt(1, entryId);
      ps.setString(2, BibliographyType.ARTICLE_IN_COLLECTION.name());

      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) {
          return null;
        }

        ArticleInCollectionEntry e = new ArticleInCollectionEntry();
        String title = rs.getString("title");

        e.setId(rs.getInt("id"));
        e.setTitle(title);
        e.setArticleInCollectionTitle(title);
        e.setPages(rs.getString("pages"));
        e.setCreatedAt(parseSqliteTextToLocalDateTime(rs.getString("created_at")));
        e.setUpdatedAt(parseSqliteTextToLocalDateTime(rs.getString("updated_at")));

        Integer cid = (Integer) rs.getObject("collection_entry_id");
        e.setCollectionEntryId(cid);
        e.setCollectionTitle(cid == null ? "" : loadEntryTitleById(c, cid));

        loadAuthorsInto(c, e);
        return e;
      }
    }
  }

  private int saveArticleInCollection(Connection c, ArticleInCollectionEntry e) throws SQLException {
    saveOrUpdateBaseEntry(c, e, BibliographyType.ARTICLE_IN_COLLECTION);
    saveArticleInCollectionDetails(c, e);
    persistAuthorsAndJoin(c, e);
    return e.getId();
  }

  private void saveArticleInCollectionDetails(Connection c, ArticleInCollectionEntry e)
      throws SQLException {

    Integer resolvedCollectionId = e.getCollectionEntryId();
    String requestedCollectionTitle =
        e.getCollectionTitle() == null ? "" : e.getCollectionTitle().trim();

    if (requestedCollectionTitle.isBlank()) {
      e.clearCollectionReference();
      resolvedCollectionId = null;
    } else if (resolvedCollectionId == null) {
      resolvedCollectionId = findCollectionIdByTitle(c, requestedCollectionTitle);
      if (resolvedCollectionId != null) {
        e.setCollectionEntryId(resolvedCollectionId);
        e.setCollectionTitle(loadEntryTitleById(c, resolvedCollectionId));
      } else {
        e.clearCollectionReference();
      }
    }

    String sql = """
      INSERT INTO bibliography_article_in_collection(
          id, collection_entry_id, pages
      )
      VALUES(?, ?, ?)
      ON CONFLICT(id) DO UPDATE SET
          collection_entry_id = excluded.collection_entry_id,
          pages = excluded.pages
      """;

    try (PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setInt(1, e.getId());

      if (resolvedCollectionId == null) {
        ps.setNull(2, Types.INTEGER);
      } else {
        ps.setInt(2, resolvedCollectionId);
      }

      ps.setString(3, e.getPages());
      ps.executeUpdate();
    }
  }

  private void loadJournalArticleDetails(JournalArticleEntry e) throws SQLException {
    String sql = """
      SELECT journal_title, volume, issue, year, pages, doi
      FROM bibliography_journal_article
      WHERE id = ?
      """;
    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setInt(1, e.getId());
      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) return;
        e.setJournalTitle(rs.getString("journal_title"));
        e.setVolume(rs.getString("volume"));
        e.setIssue(rs.getString("issue"));
        e.setYear(rs.getString("year"));
        e.setPages(rs.getString("pages"));
        e.setDoi(rs.getString("doi"));
      }
    }
  }

  private JournalEntry loadJournal(int entryId) throws SQLException {
    String sql = """
    SELECT
        be.id,
        be.title,
        be.created_at,
        be.updated_at,
        bj.subtitle,
        bj.issn,
        bj.publisher,
        bj.place,
        bj.start_year,
        bj.end_year,
        bj.note
    FROM bibliography_entries be
    JOIN bibliography_journal bj
      ON bj.id = be.id
    WHERE be.id = ?
      AND be.type = ?
    """;

    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

      ps.setInt(1, entryId);
      ps.setString(2, BibliographyType.JOURNAL.name());

      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) {
          return null;
        }

        JournalEntry e = new JournalEntry();
        e.setId(rs.getInt("id"));
        e.setTitle(rs.getString("title"));
        e.setSubtitle(rs.getString("subtitle"));
        e.setIssn(rs.getString("issn"));
        e.setPublisher(rs.getString("publisher"));
        e.setPlace(rs.getString("place"));
        e.setStartYear(rs.getString("start_year"));
        e.setEndYear(rs.getString("end_year"));
        e.setNote(rs.getString("note"));
        e.setCreatedAt(parseSqliteTextToLocalDateTime(rs.getString("created_at")));
        e.setUpdatedAt(parseSqliteTextToLocalDateTime(rs.getString("updated_at")));

        return e;
      }
    }
  }

  private int saveJournal(Connection c, JournalEntry e) throws SQLException {
    saveOrUpdateBaseEntry(c, e, BibliographyType.JOURNAL);
    saveJournalDetails(c, e);
    return e.getId();
  }

  private void saveJournalDetails(Connection c, JournalEntry e) throws SQLException {
    String sql = """
    INSERT INTO bibliography_journal(
        id, subtitle, issn, publisher, place, start_year, end_year, note
    )
    VALUES(?, ?, ?, ?, ?, ?, ?, ?)
    ON CONFLICT(id) DO UPDATE SET
        subtitle = excluded.subtitle,
        issn = excluded.issn,
        publisher = excluded.publisher,
        place = excluded.place,
        start_year = excluded.start_year,
        end_year = excluded.end_year,
        note = excluded.note
    """;

    try (PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setInt(1, e.getId());
      ps.setString(2, e.getSubtitle());
      ps.setString(3, e.getIssn());
      ps.setString(4, e.getPublisher());
      ps.setString(5, e.getPlace());
      ps.setString(6, e.getStartYear());
      ps.setString(7, e.getEndYear());
      ps.setString(8, e.getNote());
      ps.executeUpdate();
    }
  }

  private JournalArticleEntry loadJournalArticle(int entryId) throws SQLException {
    String sql = """
    SELECT
        be.id,
        be.title,
        be.created_at,
        be.updated_at,
        bja.subtitle,
        bja.publisher,
        bja.journal_entry_id,
        bja.journal_title,
        bja.volume,
        bja.issue,
        bja.year,
        bja.pages,
        bja.doi
    FROM bibliography_entries be
    JOIN bibliography_journal_article bja
      ON bja.id = be.id
    WHERE be.id = ?
      AND be.type = ?
    """;

    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

      ps.setInt(1, entryId);
      ps.setString(2, BibliographyType.JOURNAL_ARTICLE.name());

      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) {
          return null;
        }

        JournalArticleEntry e = new JournalArticleEntry();
        e.setId(rs.getInt("id"));
        e.setTitle(rs.getString("title"));
        e.setSubtitle(rs.getString("subtitle"));
        e.setPublisher(rs.getString("publisher"));
        e.setVolume(rs.getString("volume"));
        e.setIssue(rs.getString("issue"));
        e.setYear(rs.getString("year"));
        e.setPages(rs.getString("pages"));
        e.setDoi(rs.getString("doi"));
        e.setCreatedAt(parseSqliteTextToLocalDateTime(rs.getString("created_at")));
        e.setUpdatedAt(parseSqliteTextToLocalDateTime(rs.getString("updated_at")));

        Integer journalId = (Integer) rs.getObject("journal_entry_id");
        e.setJournalEntryId(journalId);

        if (journalId != null) {
          e.setJournalTitle(loadEntryTitleById(c, journalId));
        } else {
          e.setJournalTitle(rs.getString("journal_title"));
        }

        loadAuthorsInto(c, e);
        return e;
      }
    }
  }

  private int saveJournalArticle(Connection c, JournalArticleEntry e) throws SQLException {
    saveOrUpdateBaseEntry(c, e, BibliographyType.JOURNAL_ARTICLE);
    saveJournalArticleDetails(c, e);
    persistAuthorsAndJoin(c, e);
    return e.getId();
  }

  private void saveJournalArticleDetails(Connection c, JournalArticleEntry e) throws SQLException {
    Integer resolvedJournalId = e.getJournalEntryId();
    String requestedJournalTitle =
        e.getJournalTitle() == null ? "" : e.getJournalTitle().trim();

    if (requestedJournalTitle.isBlank()) {
      e.clearJournalReference();
      resolvedJournalId = null;
    } else if (resolvedJournalId == null) {
      resolvedJournalId = findJournalIdByTitle(c, requestedJournalTitle);
      if (resolvedJournalId != null) {
        e.setJournalEntryId(resolvedJournalId);
        e.setJournalTitle(loadEntryTitleById(c, resolvedJournalId));
      } else {
        e.setJournalEntryId(null);
        e.setJournalTitle(requestedJournalTitle);
      }
    }

    String sql = """
    INSERT INTO bibliography_journal_article(
        id, subtitle, publisher, journal_entry_id, journal_title, volume, issue, year, pages, doi
    )
    VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    ON CONFLICT(id) DO UPDATE SET
        subtitle = excluded.subtitle,
        publisher = excluded.publisher,
        journal_entry_id = excluded.journal_entry_id,
        journal_title = excluded.journal_title,
        volume = excluded.volume,
        issue = excluded.issue,
        year = excluded.year,
        pages = excluded.pages,
        doi = excluded.doi
    """;

    try (PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setInt(1, e.getId());
      ps.setString(2, e.getSubtitle());
      ps.setString(3, e.getPublisher());

      if (resolvedJournalId == null) {
        ps.setNull(4, Types.INTEGER);
      } else {
        ps.setInt(4, resolvedJournalId);
      }

      ps.setString(5, e.getJournalTitle());
      ps.setString(6, e.getVolume());
      ps.setString(7, e.getIssue());
      ps.setString(8, e.getYear());
      ps.setString(9, e.getPages());
      ps.setString(10, e.getDoi());
      ps.executeUpdate();
    }
  }

  private Integer findJournalIdByTitle(Connection c, String title) throws SQLException {
    if (title == null || title.trim().isEmpty()) {
      return null;
    }

    String sql = """
    SELECT be.id
    FROM bibliography_entries be
    JOIN bibliography_journal bj
      ON bj.id = be.id
    WHERE be.type = ?
      AND LOWER(TRIM(be.title)) = LOWER(TRIM(?))
    LIMIT 1
    """;

    try (PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setString(1, BibliographyType.JOURNAL.name());
      ps.setString(2, title.trim());

      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? rs.getInt(1) : null;
      }
    }
  }

  private void loadCollectionDetails(CollectionEntry e) throws SQLException {
    String sql = """
      SELECT subtitle, publisher, place, year, series
      FROM bibliography_collection
      WHERE id = ?
      """;
    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setInt(1, e.getId());
      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) return;
        e.setSubtitle(rs.getString("subtitle"));
        e.setPublisher(rs.getString("publisher"));
        e.setPlace(rs.getString("place"));
        e.setYear(rs.getString("year"));
        e.setSeries(rs.getString("series"));
      }
    }
  }

  private ThesisEntry loadThesis(int entryId) throws SQLException {
    String sql = """
    SELECT
        be.id,
        be.title,
        be.created_at,
        be.updated_at,
        bt.subtitle,
        bt.thesis_type,
        bt.university,
        bt.place,
        bt.year,
        bt.advisor,
        bt.series,
        bt.note
    FROM bibliography_entries be
    JOIN bibliography_thesis bt
      ON bt.id = be.id
    WHERE be.id = ?
      AND be.type = ?
    """;

    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

      ps.setInt(1, entryId);
      ps.setString(2, BibliographyType.THESIS.name());

      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) {
          return null;
        }

        ThesisEntry e = new ThesisEntry();
        e.setId(rs.getInt("id"));
        e.setTitle(rs.getString("title"));
        e.setSubtitle(rs.getString("subtitle"));

        String rawType = rs.getString("thesis_type");
        try {
          e.setThesisType(rawType == null || rawType.isBlank()
                              ? ThesisType.DISSERTATION
                              : ThesisType.valueOf(rawType));
        } catch (Exception ex) {
          e.setThesisType(ThesisType.DISSERTATION);
        }

        e.setUniversity(rs.getString("university"));
        e.setPlace(rs.getString("place"));
        e.setYear(rs.getString("year"));
        e.setAdvisor(rs.getString("advisor"));
        e.setSeries(rs.getString("series"));
        e.setNote(rs.getString("note"));
        e.setCreatedAt(parseSqliteTextToLocalDateTime(rs.getString("created_at")));
        e.setUpdatedAt(parseSqliteTextToLocalDateTime(rs.getString("updated_at")));

        loadAuthorsInto(c, e);
        return e;
      }
    }
  }

  private InternetEntry loadInternet(int entryId) throws SQLException {
    String sql = """
    SELECT
        be.id,
        be.title,
        be.created_at,
        be.updated_at,
        bi.url,
        bi.host_name,
        bi.published,
        bi.accessed_at
    FROM bibliography_entries be
    JOIN bibliography_internet bi
      ON bi.id = be.id
    WHERE be.id = ?
      AND be.type = ?
    """;

    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

      ps.setInt(1, entryId);
      ps.setString(2, BibliographyType.INTERNET.name());

      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) {
          return null;
        }

        InternetEntry e = new InternetEntry();
        e.setId(rs.getInt("id"));
        e.setTitle(rs.getString("title"));
        e.setUrl(rs.getString("url"));
        e.setHostName(rs.getString("host_name"));
        e.setPublished(rs.getString("published"));
        e.setAccessedAt(parseSqliteTextToLocalDateTime(rs.getString("accessed_at")));
        e.setCreatedAt(parseSqliteTextToLocalDateTime(rs.getString("created_at")));
        e.setUpdatedAt(parseSqliteTextToLocalDateTime(rs.getString("updated_at")));

        loadAuthorsInto(c, e);
        return e;
      }
    }
  }

  private EditionEntry loadEdition(int entryId) throws SQLException {
    String sql = """
    SELECT
        be.id,
        be.title,
        be.created_at,
        be.updated_at,
        bed.publisher,
        bed.place,
        bed.year,
        bed.series,
        bed.volume,
        bed.isbn,
        bed.run
    FROM bibliography_entries be
    JOIN bibliography_edition bed
      ON bed.id = be.id
    WHERE be.id = ?
      AND be.type = ?
    """;

    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

      ps.setInt(1, entryId);
      ps.setString(2, BibliographyType.EDITION.name());

      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) {
          return null;
        }

        EditionEntry e = new EditionEntry();
        e.setId(rs.getInt("id"));
        e.setTitle(rs.getString("title"));
        e.setPublisher(rs.getString("publisher"));
        e.setPlace(rs.getString("place"));
        e.setYear(rs.getString("year"));
        e.setSeries(rs.getString("series"));
        e.setVolume(rs.getString("volume"));
        e.setIsbn(rs.getString("isbn"));
        e.setRun(rs.getString("run"));
        e.setCreatedAt(parseSqliteTextToLocalDateTime(rs.getString("created_at")));
        e.setUpdatedAt(parseSqliteTextToLocalDateTime(rs.getString("updated_at")));

        loadAuthorsInto(c, e);
        return e;
      }
    }
  }

  private AiEntry loadAi(int entryId) throws SQLException {
    String sql = """
    SELECT
        be.id,
        be.title,
        be.created_at,
        be.updated_at,
        bai.provider,
        bai.model,
        bai.context,
        bai.prompt_title,
        bai.used_at
    FROM bibliography_entries be
    JOIN bibliography_ai bai
      ON bai.id = be.id
    WHERE be.id = ?
      AND be.type = ?
    """;

    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

      ps.setInt(1, entryId);
      ps.setString(2, BibliographyType.AI.name());

      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) {
          return null;
        }

        AiEntry e = new AiEntry();
        e.setId(rs.getInt("id"));
        e.setTitle(rs.getString("title"));
        e.setProvider(rs.getString("provider"));
        e.setModel(rs.getString("model"));
        e.setContext(rs.getString("context"));
        e.setPromptTitle(rs.getString("prompt_title"));
        e.setUsedAt(parseSqliteTextToLocalDateTime(rs.getString("used_at")));
        e.setCreatedAt(parseSqliteTextToLocalDateTime(rs.getString("created_at")));
        e.setUpdatedAt(parseSqliteTextToLocalDateTime(rs.getString("updated_at")));

        return e;
      }
    }
  }

  private int saveThesis(Connection c, ThesisEntry e) throws SQLException {
    saveOrUpdateBaseEntry(c, e, BibliographyType.THESIS);
    saveThesisDetails(c, e);
    persistAuthorsAndJoin(c, e);
    return e.getId();
  }

  private void saveThesisDetails(Connection c, ThesisEntry e) throws SQLException {
    String sql = """
    INSERT INTO bibliography_thesis(
        id, subtitle, thesis_type, university, place, year, advisor, series, note
    )
    VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)
    ON CONFLICT(id) DO UPDATE SET
        subtitle = excluded.subtitle,
        thesis_type = excluded.thesis_type,
        university = excluded.university,
        place = excluded.place,
        year = excluded.year,
        advisor = excluded.advisor,
        series = excluded.series,
        note = excluded.note
    """;

    try (PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setInt(1, e.getId());
      ps.setString(2, e.getSubtitle());
      ps.setString(3, e.getThesisType() == null ? ThesisType.DISSERTATION.name() : e.getThesisType().name());
      ps.setString(4, e.getUniversity());
      ps.setString(5, e.getPlace());
      ps.setString(6, e.getYear());
      ps.setString(7, e.getAdvisor());
      ps.setString(8, e.getSeries());
      ps.setString(9, e.getNote());
      ps.executeUpdate();
    }
  }

  private int saveInternet(Connection c, InternetEntry e) throws SQLException {
    saveOrUpdateBaseEntry(c, e, BibliographyType.INTERNET);
    saveInternetDetails(c, e);
    persistAuthorsAndJoin(c, e);
    return e.getId();
  }

  private void saveInternetDetails(Connection c, InternetEntry e) throws SQLException {
    String sql = """
    INSERT INTO bibliography_internet(
        id, url, host_name, published, accessed_at
    )
    VALUES(?, ?, ?, ?, ?)
    ON CONFLICT(id) DO UPDATE SET
        url = excluded.url,
        host_name = excluded.host_name,
        published = excluded.published,
        accessed_at = excluded.accessed_at
    """;

    try (PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setInt(1, e.getId());
      ps.setString(2, e.getUrl());
      ps.setString(3, e.getHostName());
      ps.setString(4, e.getPublished());
      ps.setString(5, toSqliteTimestamp(e.getAccessedAt()));
      ps.executeUpdate();
    }
  }

  private int saveEdition(Connection c, EditionEntry e) throws SQLException {
    saveOrUpdateBaseEntry(c, e, BibliographyType.EDITION);
    saveEditionDetails(c, e);
    persistAuthorsAndJoin(c, e);
    return e.getId();
  }

  private void saveEditionDetails(Connection c, EditionEntry e) throws SQLException {
    String sql = """
    INSERT INTO bibliography_edition(
    id, publisher, place, year, isbn, run, series, volume
    )
    VALUES(?, ?, ?, ?, ?, ?, ?, ?)
    ON CONFLICT(id) DO UPDATE SET
      publisher = excluded.publisher,
      place = excluded.place,
      year = excluded.year,
      isbn = excluded.isbn,
      run = excluded.run,
      series = excluded.series,
      volume = excluded.volume
    """;

    try (PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setInt(1, e.getId());
      ps.setString(2, e.getPublisher());
      ps.setString(3, e.getPlace());
      ps.setString(4, e.getYear());
      ps.setString(5, e.getIsbn());
      ps.setString(6, e.getRun());
      ps.setString(7, e.getSeries());
      ps.setString(8, e.getVolume());
      ps.executeUpdate();
    }
  }

  private int saveAi(Connection c, AiEntry e) throws SQLException {
    saveOrUpdateBaseEntry(c, e, BibliographyType.AI);
    saveAiDetails(c, e);
    return e.getId();
  }

  private void saveAiDetails(Connection c, AiEntry e) throws SQLException {
    String sql = """
    INSERT INTO bibliography_ai(
        id, provider, model, context, prompt_title, used_at
    )
    VALUES(?, ?, ?, ?, ?, ?)
    ON CONFLICT(id) DO UPDATE SET
        provider = excluded.provider,
        model = excluded.model,
        context = excluded.context,
        prompt_title = excluded.prompt_title,
        used_at = excluded.used_at
    """;

    try (PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setInt(1, e.getId());
      ps.setString(2, e.getProvider());
      ps.setString(3, e.getModel());
      ps.setString(4, e.getContext());
      ps.setString(5, e.getPromptTitle());
      ps.setString(6, toSqliteTimestamp(e.getUsedAt()));
      ps.executeUpdate();
    }
  }

  public void rememberAiProviderModel(String provider, String model) {
    String p = provider == null ? "" : provider.trim();
    String m = model == null ? "" : model.trim();

    if (p.isBlank() || m.isBlank()) {
      return;
    }

    String sql = """
      INSERT INTO bibliography_ai_provider_model_history(provider, model, last_used_at, use_count)
      VALUES(?, ?, ?, 1)
      ON CONFLICT(provider, model) DO UPDATE SET
          last_used_at = excluded.last_used_at,
          use_count = bibliography_ai_provider_model_history.use_count + 1
      """;

    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setString(1, p);
      ps.setString(2, m);
      ps.setString(3, java.time.LocalDateTime.now().toString());
      ps.executeUpdate();
    } catch (SQLException ex) {
      lastErrorMessage = ex.getMessage();
    }
  }

  public List<String> findAiProviders(String prefix, String modelFilter, int limit) {
    String pfx = prefix == null ? "" : prefix.trim();
    if (pfx.length() < 2) {
      return List.of();
    }

    String model = modelFilter == null ? "" : modelFilter.trim();

    String sql = model.isBlank()
                     ? """
        SELECT DISTINCT provider
        FROM bibliography_ai_provider_model_history
        WHERE LOWER(provider) LIKE LOWER(?)
        ORDER BY LOWER(provider)
        LIMIT ?
        """
                     : """
        SELECT DISTINCT provider
        FROM bibliography_ai_provider_model_history
        WHERE LOWER(provider) LIKE LOWER(?)
          AND LOWER(model) = LOWER(?)
        ORDER BY LOWER(provider)
        LIMIT ?
        """;

    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

      ps.setString(1, pfx + "%");
      if (model.isBlank()) {
        ps.setInt(2, limit);
      } else {
        ps.setString(2, model);
        ps.setInt(3, limit);
      }

      try (ResultSet rs = ps.executeQuery()) {
        List<String> result = new ArrayList<>();
        while (rs.next()) {
          result.add(rs.getString(1));
        }
        return result;
      }
    } catch (SQLException ex) {
      lastErrorMessage = ex.getMessage();
      return List.of();
    }
  }

  public List<String> findAiModels(String prefix, String providerFilter, int limit) {
    String pfx = prefix == null ? "" : prefix.trim();
    if (pfx.length() < 2) {
      return List.of();
    }

    String provider = providerFilter == null ? "" : providerFilter.trim();

    String sql = provider.isBlank()
                     ? """
        SELECT DISTINCT model
        FROM bibliography_ai_provider_model_history
        WHERE LOWER(model) LIKE LOWER(?)
        ORDER BY LOWER(model)
        LIMIT ?
        """
                     : """
        SELECT DISTINCT model
        FROM bibliography_ai_provider_model_history
        WHERE LOWER(model) LIKE LOWER(?)
          AND LOWER(provider) = LOWER(?)
        ORDER BY LOWER(model)
        LIMIT ?
        """;

    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

      ps.setString(1, pfx + "%");
      if (provider.isBlank()) {
        ps.setInt(2, limit);
      } else {
        ps.setString(2, provider);
        ps.setInt(3, limit);
      }

      try (ResultSet rs = ps.executeQuery()) {
        List<String> result = new ArrayList<>();
        while (rs.next()) {
          result.add(rs.getString(1));
        }
        return result;
      }
    } catch (SQLException ex) {
      lastErrorMessage = ex.getMessage();
      return List.of();
    }
  }

  public List<String> loadRecentAiProviders(int limit) {
    String sql = """
      SELECT provider
      FROM bibliography_ai_provider_model_history
      GROUP BY provider
      ORDER BY MAX(last_used_at) DESC
      LIMIT ?
      """;

    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setInt(1, limit);

      try (ResultSet rs = ps.executeQuery()) {
        List<String> result = new ArrayList<>();
        while (rs.next()) {
          result.add(rs.getString(1));
        }
        return result;
      }
    } catch (SQLException ex) {
      lastErrorMessage = ex.getMessage();
      return List.of();
    }
  }

  public List<String> loadRecentAiModels(int limit) {
    String sql = """
      SELECT model
      FROM bibliography_ai_provider_model_history
      GROUP BY model
      ORDER BY MAX(last_used_at) DESC
      LIMIT ?
      """;

    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setInt(1, limit);

      try (ResultSet rs = ps.executeQuery()) {
        List<String> result = new ArrayList<>();
        while (rs.next()) {
          result.add(rs.getString(1));
        }
        return result;
      }
    } catch (SQLException ex) {
      lastErrorMessage = ex.getMessage();
      return List.of();
    }
  }

  private String loadEntryTitleById(Connection c, int id) throws SQLException {
    try (PreparedStatement ps = c.prepareStatement("SELECT title FROM bibliography_entries WHERE id = ?")) {
      ps.setInt(1, id);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? (rs.getString(1) == null ? "" : rs.getString(1).trim()) : "";
      }
    }
  }

  private Integer findCollectionIdByTitle(Connection c, String title) throws SQLException {
    if (title == null || title.trim().isEmpty()) {
      return null;
    }

    String sql = """
      SELECT be.id
      FROM bibliography_entries be
      JOIN bibliography_collection bc
        ON bc.id = be.id
      WHERE be.type = ?
        AND LOWER(TRIM(be.title)) = LOWER(TRIM(?))
      LIMIT 1
      """;

    try (PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setString(1, BibliographyType.COLLECTION.name());
      ps.setString(2, title.trim());

      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? rs.getInt(1) : null;
      }
    }
  }

  private void saveOrUpdateBaseEntry(Connection c, BibliographyEntry entry, BibliographyType type)
      throws SQLException {

    LocalDateTime now = LocalDateTime.now();

    if (entry.getId() == null || entry.getId() <= 0) {
      String sql = """
        INSERT INTO bibliography_entries(type, title, created_at, updated_at)
        VALUES(?, ?, ?, ?)
        """;

      try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
        ps.setString(1, type.name());
        ps.setString(2, entry.getTitle());
        ps.setString(3, toSqliteTimestamp(now));
        ps.setNull(4, Types.VARCHAR);
        ps.executeUpdate();

        try (ResultSet keys = ps.getGeneratedKeys()) {
          if (!keys.next()) {
            throw new IllegalStateException("Keine BibliographyEntry-ID erhalten.");
          }
          entry.setId(keys.getInt(1));
        }
      }

      entry.setCreatedAt(now);
      entry.setUpdatedAt(null);
      return;
    }

    String sql = """
      UPDATE bibliography_entries
      SET type = ?, title = ?, updated_at = ?
      WHERE id = ?
      """;

    try (PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setString(1, type.name());
      ps.setString(2, entry.getTitle());
      ps.setString(3, toSqliteTimestamp(now));
      ps.setInt(4, entry.getId());
      ps.executeUpdate();
    }

    entry.setUpdatedAt(now);
  }

  private static LocalDateTime parseSqliteTextToLocalDateTime(String s) {
    if (s == null || s.isBlank()) {
      return null;
    }

    String text = s.trim();

    try {
      Instant i = Instant.parse(text);
      return LocalDateTime.ofInstant(i, ZoneId.systemDefault());
    } catch (Exception ignored) {
    }

    try {
      return LocalDateTime.parse(text); // ISO_LOCAL_DATE_TIME, z. B. 2026-03-08T12:34:56.123
    } catch (Exception ignored) {
    }

    try {
      return LocalDateTime.parse(text, SQLITE_TS); // yyyy-MM-dd HH:mm:ss
    } catch (Exception ignored) {
    }

    throw new IllegalArgumentException("Unbekanntes Datumsformat in bibliography: " + text);
  }

  private static String toSqliteTimestamp(LocalDateTime value) {
    return value == null ? null : value.format(SQLITE_TS);
  }

  public record LookupTitle(Integer entryId, String title, List<Author> authors) {}

  public List<AuthorSuggestion> findAuthorSuggestions(BibliographyType type,
                                                      String lastNamePrefix,
                                                      Integer selectedEntryId) {
    String prefix = lastNamePrefix == null ? "" : lastNamePrefix.trim();
    if (prefix.length() < 3) {
      return List.of();
    }

    try (Connection c = ds.getConnection()) {
      if (selectedEntryId != null && selectedEntryId > 0) {
        return findAuthorSuggestionsForEntry(c, selectedEntryId, prefix);
      }
      return findAuthorSuggestionsGlobal(c, prefix);
    } catch (SQLException ex) {
      System.out.println("Fehler in findAuthorSuggestions()");
      ex.printStackTrace();
      return List.of();
    }
  }

  private List<AuthorSuggestion> findAuthorSuggestionsGlobal(Connection c,
                                                         String prefix) throws SQLException {
    String sql = """
      SELECT DISTINCT a.id, a.last_name, a.first_name
      FROM authors a
      WHERE LOWER(a.last_name) LIKE LOWER(?)
      ORDER BY LOWER(a.last_name), LOWER(a.first_name)
      LIMIT 20
      """;

    try (PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setString(1, prefix.trim() + "%");

      try (ResultSet rs = ps.executeQuery()) {
        List<AuthorSuggestion> out = new ArrayList<>();
        while (rs.next()) {
          out.add(new AuthorSuggestion(
              rs.getInt("id"),
              rs.getString("first_name"),
              rs.getString("last_name")
          ));
        }
        return out;
      }
    }
  }

  public List<LookupTitle> findTitleSuggestions(BibliographyType type,
                                                String query,
                                                List<Author> requiredAuthors) {
    String q = query == null ? "" : query.trim();
    if (q.length() < 3 && normalizedAuthorFilters(requiredAuthors).isEmpty()) {
      return List.of();
    }

    try (Connection c = ds.getConnection()) {
      return findTitleSuggestionsInternal(c, type, q, requiredAuthors, false);
    } catch (SQLException ex) {
      System.out.println("Fehler in findTitleSuggestions()");
      ex.printStackTrace();
      return List.of();
    }
  }

  public LookupTitle resolveExactTitle(BibliographyType type,
                                       String exactTitle,
                                       List<Author> requiredAuthors) {
    String q = exactTitle == null ? "" : exactTitle.trim();
    if (q.isBlank()) {
      return null;
    }

    try (Connection c = ds.getConnection()) {
      List<LookupTitle> hits = findTitleSuggestionsInternal(c, type, q, requiredAuthors, true);
      return hits.size() == 1 ? hits.get(0) : null;
    } catch (SQLException ex) {
      System.out.println("Fehler in resolveExactTitle()");
      ex.printStackTrace();
      return null;
    }
  }

  public LookupTitle resolveUniqueTitleForAuthors(BibliographyType type,
                                                  List<Author> requiredAuthors) {
    List<Author> filters = normalizedAuthorFilters(requiredAuthors);
    if (filters.isEmpty()) {
      return null;
    }

    try (Connection c = ds.getConnection()) {
      List<LookupTitle> hits = findTitleSuggestionsInternal(c, type, "", filters, false);
      return hits.size() == 1 ? hits.get(0) : null;
    } catch (SQLException ex) {
      System.out.println("Fehler in resolveUniqueTitleForAuthors()");
      ex.printStackTrace();
      return null;
    }
  }

  private List<AuthorSuggestion> findAuthorSuggestionsForEntry(Connection c,
                                                           int entryId,
                                                           String prefix) throws SQLException {
    String sql = """
      SELECT DISTINCT a.id, a.last_name, a.first_name
      FROM authors a
      JOIN author_mapping am
        ON am.author_id = a.id
      WHERE am.entry_id = ?
        AND LOWER(a.last_name) LIKE LOWER(?)
      ORDER BY LOWER(a.last_name), LOWER(a.first_name)
      LIMIT 20
      """;

    try (PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setInt(1, entryId);
      ps.setString(2, prefix.trim() + "%");

      try (ResultSet rs = ps.executeQuery()) {
        List<AuthorSuggestion> out = new ArrayList<>();
        while (rs.next()) {
          out.add(new AuthorSuggestion(
              rs.getInt("id"),
              rs.getString("first_name"),
              rs.getString("last_name")
          ));
        }
        return out;
      }
    }
  }

  private List<LookupTitle> findTitleSuggestionsInternal(Connection c,
                                                         BibliographyType type,
                                                         String query,
                                                         List<Author> requiredAuthors,
                                                         boolean exactMatch) throws SQLException {
    List<Author> filters = normalizedAuthorFilters(requiredAuthors);

    StringBuilder sql = new StringBuilder("""
      SELECT DISTINCT be.id, be.title
      FROM bibliography_entries be
      """);

    for (int i = 0; i < filters.size(); i++) {
      sql.append("""
         JOIN author_mapping am%d
           ON am%d.entry_id = be.id
         JOIN authors a%d
           ON a%d.id = am%d.author_id
        """.formatted(i, i, i, i, i));
    }

    sql.append(" WHERE be.type = ? ");

    if (query != null && !query.isBlank()) {
      if (exactMatch) {
        sql.append(" AND LOWER(TRIM(be.title)) = LOWER(TRIM(?)) ");
      } else {
        sql.append(" AND LOWER(be.title) LIKE LOWER(?) ");
      }
    }

    for (int i = 0; i < filters.size(); i++) {
      sql.append(" AND LOWER(a").append(i).append(".last_name) = LOWER(?) ");
      sql.append(" AND LOWER(a").append(i).append(".first_name) = LOWER(?) ");
    }

    sql.append(" ORDER BY LOWER(be.title) LIMIT 20 ");

    try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
      int idx = 1;
      ps.setString(idx++, type.name());

      if (query != null && !query.isBlank()) {
        ps.setString(idx++, exactMatch ? query.trim() : "%" + query.trim() + "%");
      }

      for (Author a : filters) {
        ps.setString(idx++, safe(a.getLastName()));
        ps.setString(idx++, safe(a.getFirstName()));
      }

      try (ResultSet rs = ps.executeQuery()) {
        List<LookupTitle> out = new ArrayList<>();
        while (rs.next()) {
          int entryId = rs.getInt("id");
          out.add(new LookupTitle(
              entryId,
              rs.getString("title"),
              loadAuthorsForEntry(c, entryId)
          ));
        }
        return out;
      }
    }
  }

  private List<Author> loadAuthorsForEntry(Connection c, int entryId) throws SQLException {
    String sql = """
      SELECT a.id, a.first_name, a.last_name, am.author_pos
      FROM authors a
      JOIN author_mapping am
        ON am.author_id = a.id
      WHERE am.entry_id = ?
      ORDER BY am.author_pos
      """;

    try (PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setInt(1, entryId);

      try (ResultSet rs = ps.executeQuery()) {
        List<Author> out = new ArrayList<>();
        while (rs.next()) {
          Author a = new Author();
          a.setId(rs.getInt("id"));
          a.setFirstName(rs.getString("first_name"));
          a.setLastName(rs.getString("last_name"));
          a.setPosition(rs.getInt("author_pos"));
          out.add(a);
        }
        return out;
      }
    }
  }

  private List<Author> normalizedAuthorFilters(List<Author> authors) {
    List<Author> out = new ArrayList<>();
    if (authors == null) {
      return out;
    }

    for (Author a : authors) {
      if (a == null) {
        continue;
      }

      String last = safe(a.getLastName()).trim();
      String first = safe(a.getFirstName()).trim();

      if (last.isBlank() && first.isBlank()) {
        continue;
      }

      Author copy = new Author();
      copy.setLastName(last);
      copy.setFirstName(first);
      out.add(copy);
    }

    return out;
  }

  private static String safe(String s) {
    return s == null ? "" : s;
  }

  public LookupTitle resolveExactCollectionTitle(String exactTitle) {
    String q = exactTitle == null ? "" : exactTitle.trim();
    if (q.isBlank()) {
      return null;
    }

    try (Connection c = ds.getConnection()) {
      List<LookupTitle> hits = findTitleSuggestionsInternal(
          c,
          BibliographyType.COLLECTION,
          q,
          List.of(),
          true
      );
      return hits.size() == 1 ? hits.get(0) : null;
    } catch (SQLException ex) {
      System.out.println("Fehler in resolveExactCollectionTitle()");
      ex.printStackTrace();
      return null;
    }
  }

  public List<LookupTitle> findCollectionSuggestions(String query) {
    String q = query == null ? "" : query.trim();
    if (q.length() < 3) {
      return List.of();
    }

    try (Connection c = ds.getConnection()) {
      return findTitleSuggestionsInternal(
          c,
          BibliographyType.COLLECTION,
          q,
          List.of(),
          false
      );
    } catch (SQLException ex) {
      System.out.println("Fehler in findCollectionSuggestions()");
      ex.printStackTrace();
      return List.of();
    }
  }

  public record LookupSeries(String value) {}

  public List<LookupSeries> findSeriesSuggestions(BibliographyType type, String query) {
    String q = query == null ? "" : query.trim();
    if (q.length() < 3) {
      return List.of();
    }

    try (Connection c = ds.getConnection()) {
      return findSeriesSuggestionsInternal(c, type, q);
    } catch (SQLException ex) {
      System.out.println("Fehler in findSeriesSuggestions()");
      ex.printStackTrace();
      return List.of();
    }
  }

  private List<LookupSeries> findSeriesSuggestionsInternal(Connection c,
                                                           BibliographyType type,
                                                           String query) throws SQLException {
    String table = switch (type) {
      case BOOK -> "bibliography_book";
      case COLLECTION -> "bibliography_collection";
      default -> null;
    };

    if (table == null) {
      return List.of();
    }

    String sql = """
      SELECT DISTINCT series
      FROM %s
      WHERE series IS NOT NULL
        AND TRIM(series) <> ''
        AND LOWER(series) LIKE LOWER(?)
      ORDER BY LOWER(series)
      LIMIT 20
      """.formatted(table);

    try (PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setString(1, "%" + query.trim() + "%");

      try (ResultSet rs = ps.executeQuery()) {
        List<LookupSeries> out = new ArrayList<>();
        while (rs.next()) {
          out.add(new LookupSeries(rs.getString("series")));
        }
        return out;
      }
    }
  }
}
