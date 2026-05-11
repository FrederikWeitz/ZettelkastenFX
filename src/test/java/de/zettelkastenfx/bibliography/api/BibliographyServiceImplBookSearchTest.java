package de.zettelkastenfx.bibliography.api;

import de.zettelkastenfx.bibliography.model.BibliographyReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueft den Suchpfad, der die BOOK-Medientabelle mit Referenzen befuellt.
 */
class BibliographyServiceImplBookSearchTest {

  @TempDir
  private Path tempDir;

  /**
   * Die BOOK-Suche soll Referenzdetails gebuendelt laden und nicht pro Treffer neue Verbindungen oeffnen.
   *
   * @throws Exception wenn die Testdatenbank nicht aufgebaut werden kann
   */
  @Test
  void bookSearchLoadsReferencesInBatchesInsteadOfPerEntryConnections() throws Exception {
    CountingDataSource dataSource = new CountingDataSource(sqliteDataSource());
    createBibliographySchema(dataSource);
    insertBookSearchRows(dataSource, 6);

    BibliographyService service = new BibliographyServiceImpl(dataSource);
    dataSource.resetConnectionCount();

    List<BibliographyReference> references = service.searchMediaReferences("", "", 500);

    assertAll(
        () -> assertEquals(6, references.size(), "Alle Testmedien muessen gefunden werden."),
        () -> assertEquals("Title 1", references.getFirst().displayText(), "Der Anzeigetext kommt aus dem Identify-Feld."),
        () -> assertTrue(
            dataSource.connectionCount() <= 5,
            "Die BOOK-Suche oeffnet zu viele Verbindungen: " + dataSource.connectionCount()
        )
    );
  }

  /**
   * Erstellt eine SQLite-Datenquelle fuer eine temporaere Testdatenbank.
   *
   * @return Datenquelle
   */
  private SQLiteDataSource sqliteDataSource() {
    SQLiteDataSource dataSource = new SQLiteDataSource();
    dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("book-search.sqlite"));
    return dataSource;
  }

  /**
   * Legt das minimale Bibliographie-Schema fuer die BOOK-Suche an.
   *
   * @param dataSource Datenquelle
   * @throws SQLException bei Datenbankfehlern
   */
  private static void createBibliographySchema(DataSource dataSource) throws SQLException {
    try (Connection connection = dataSource.getConnection();
         Statement statement = connection.createStatement()) {
      statement.executeUpdate("""
          CREATE TABLE media_types(
            id INTEGER PRIMARY KEY,
            name TEXT,
            bib_name TEXT,
            direct_link INTEGER
          )
          """);
      statement.executeUpdate("""
          CREATE TABLE bibtex_type(
            id INTEGER PRIMARY KEY,
            bibtex_name TEXT,
            name TEXT,
            datatype TEXT,
            requires TEXT
          )
          """);
      statement.executeUpdate("""
          CREATE TABLE media_attributes(
            media_types_id INTEGER,
            bibtex_type_id INTEGER,
            mode TEXT
          )
          """);
      statement.executeUpdate("""
          CREATE TABLE bibliography_entries(
            id INTEGER PRIMARY KEY,
            title TEXT,
            citekey TEXT,
            created_at TEXT,
            updated_at TEXT,
            media_types_id INTEGER
          )
          """);
      statement.executeUpdate("""
          CREATE TABLE bibliography_entries_string(
            entries_id INTEGER,
            bibtex_type_id INTEGER,
            entry TEXT
          )
          """);
      statement.executeUpdate("""
          CREATE TABLE bibliography_entries_integer(
            entries_id INTEGER,
            bibtex_type_id INTEGER,
            entry INTEGER
          )
          """);
      statement.executeUpdate("""
          CREATE TABLE author_mapping(
            person_group_id INTEGER,
            author_id INTEGER,
            author_pos INTEGER
          )
          """);
      statement.executeUpdate("""
          CREATE TABLE authors(
            id INTEGER PRIMARY KEY,
            first_name TEXT,
            last_name TEXT
          )
          """);
    }
  }

  /**
   * Befuellt die Testdatenbank mit Medien, deren Titel Identify-Felder sind.
   *
   * @param dataSource Datenquelle
   * @param count Anzahl Medien
   * @throws SQLException bei Datenbankfehlern
   */
  private static void insertBookSearchRows(DataSource dataSource, int count) throws SQLException {
    try (Connection connection = dataSource.getConnection();
         Statement statement = connection.createStatement()) {
      statement.executeUpdate("INSERT INTO media_types(id, name, bib_name, direct_link) VALUES(1, 'Buch', 'book', 1)");
      statement.executeUpdate("INSERT INTO bibtex_type(id, bibtex_name, name, datatype, requires) VALUES(1, 'title', 'Titel', 'string', NULL)");
      statement.executeUpdate("INSERT INTO media_attributes(media_types_id, bibtex_type_id, mode) VALUES(1, 1, 'identify')");

      for (int id = 1; id <= count; id++) {
        statement.executeUpdate(
            "INSERT INTO bibliography_entries(id, title, citekey, created_at, updated_at, media_types_id) "
                + "VALUES(" + id + ", 'Title " + id + "', NULL, NULL, NULL, 1)"
        );
        statement.executeUpdate(
            "INSERT INTO bibliography_entries_string(entries_id, bibtex_type_id, entry) "
                + "VALUES(" + id + ", 1, 'Title " + id + "')"
        );
      }
    }
  }

  /**
   * Zaehlt Verbindungen, die waehrend eines Testabschnitts geoeffnet werden.
   */
  private static final class CountingDataSource implements DataSource {
    private final DataSource delegate;
    private final AtomicInteger connectionCount = new AtomicInteger();

    /**
     * Erzeugt den zaehlenden Datenquellen-Wrapper.
     *
     * @param delegate eigentliche Datenquelle
     */
    private CountingDataSource(DataSource delegate) {
      this.delegate = delegate;
    }

    /**
     * Setzt den Verbindungszaehler zurueck.
     */
    private void resetConnectionCount() {
      connectionCount.set(0);
    }

    /**
     * Liefert die seit dem letzten Reset geoeffneten Verbindungen.
     *
     * @return Anzahl Verbindungen
     */
    private int connectionCount() {
      return connectionCount.get();
    }

    /**
     * Oeffnet und zaehlt eine Verbindung.
     *
     * @return Verbindung
     * @throws SQLException bei Datenbankfehlern
     */
    @Override
    public Connection getConnection() throws SQLException {
      connectionCount.incrementAndGet();
      return delegate.getConnection();
    }

    /**
     * Oeffnet und zaehlt eine Verbindung mit Zugangsdaten.
     *
     * @param username Benutzername
     * @param password Passwort
     * @return Verbindung
     * @throws SQLException bei Datenbankfehlern
     */
    @Override
    public Connection getConnection(String username, String password) throws SQLException {
      connectionCount.incrementAndGet();
      return delegate.getConnection(username, password);
    }

    /**
     * Liefert den LogWriter der Delegation.
     *
     * @return LogWriter
     * @throws SQLException bei Datenbankfehlern
     */
    @Override
    public PrintWriter getLogWriter() throws SQLException {
      return delegate.getLogWriter();
    }

    /**
     * Setzt den LogWriter der Delegation.
     *
     * @param out LogWriter
     * @throws SQLException bei Datenbankfehlern
     */
    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
      delegate.setLogWriter(out);
    }

    /**
     * Setzt den Login-Timeout der Delegation.
     *
     * @param seconds Sekunden
     * @throws SQLException bei Datenbankfehlern
     */
    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
      delegate.setLoginTimeout(seconds);
    }

    /**
     * Liefert den Login-Timeout der Delegation.
     *
     * @return Timeout in Sekunden
     * @throws SQLException bei Datenbankfehlern
     */
    @Override
    public int getLoginTimeout() throws SQLException {
      return delegate.getLoginTimeout();
    }

    /**
     * Liefert den Parent-Logger der Delegation.
     *
     * @return Logger
     */
    @Override
    public Logger getParentLogger() {
      return Logger.getGlobal();
    }

    /**
     * Delegiert das Entpacken.
     *
     * @param iface Zieltyp
     * @param <T> Zieltyp
     * @return entpacktes Objekt
     * @throws SQLException bei Datenbankfehlern
     */
    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
      return delegate.unwrap(iface);
    }

    /**
     * Delegiert die Wrapper-Pruefung.
     *
     * @param iface Zieltyp
     * @return {@code true}, wenn die Delegation den Typ unterstuetzt
     * @throws SQLException bei Datenbankfehlern
     */
    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
      return delegate.isWrapperFor(iface);
    }
  }
}
