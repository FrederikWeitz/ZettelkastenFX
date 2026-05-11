package de.zettelkastenfx.persistence;

import de.zettelkastenfx.bibliography.api.BibliographyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Prueft schlanke BOOK-Nutzungsabfragen auf dem Zettel-Repository.
 */
class NoteRepositoryBookUsageTest {

  @TempDir
  private Path tempDir;

  /**
   * Aggregierte Bibliographie-Nutzungen ignorieren leere und ungueltige Referenzen.
   *
   * @throws Exception wenn die Testdatenbank nicht aufgebaut werden kann
   */
  @Test
  void bibliographyUsageCountsAggregateValidReferencesOnly() throws Exception {
    SQLiteDataSource dataSource = sqliteDataSource();
    createNotesSchema(dataSource);
    insertNotes(dataSource);

    NoteRepository repository = new NoteRepository(dataSource, bibliographyService());

    assertEquals(Map.of(5, 2, 7, 1), repository.loadBibliographyUsageCountsByEntryId());
  }

  /**
   * Erstellt eine SQLite-Datenquelle fuer eine temporaere Testdatenbank.
   *
   * @return Datenquelle
   */
  private SQLiteDataSource sqliteDataSource() {
    SQLiteDataSource dataSource = new SQLiteDataSource();
    dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("book-usage.sqlite"));
    return dataSource;
  }

  /**
   * Legt das minimale Zettelschema fuer Nutzungszaehlungen an.
   *
   * @param dataSource Datenquelle
   * @throws SQLException bei Datenbankfehlern
   */
  private static void createNotesSchema(SQLiteDataSource dataSource) throws SQLException {
    try (Connection connection = dataSource.getConnection();
         Statement statement = connection.createStatement()) {
      statement.executeUpdate("""
          CREATE TABLE notes(
            id INTEGER PRIMARY KEY,
            title TEXT,
            content_blob BLOB,
            content_codec TEXT,
            bibliography_ref_id INTEGER,
            created_at TEXT,
            updated_at TEXT
          )
          """);
    }
  }

  /**
   * Befuellt die Testdatenbank mit gueltigen und ungueltigen Bibliographie-Referenzen.
   *
   * @param dataSource Datenquelle
   * @throws SQLException bei Datenbankfehlern
   */
  private static void insertNotes(SQLiteDataSource dataSource) throws SQLException {
    try (Connection connection = dataSource.getConnection();
         Statement statement = connection.createStatement()) {
      statement.executeUpdate("INSERT INTO notes(id, title, bibliography_ref_id) VALUES(1, 'A', 5)");
      statement.executeUpdate("INSERT INTO notes(id, title, bibliography_ref_id) VALUES(2, 'B', 5)");
      statement.executeUpdate("INSERT INTO notes(id, title, bibliography_ref_id) VALUES(3, 'C', 7)");
      statement.executeUpdate("INSERT INTO notes(id, title, bibliography_ref_id) VALUES(4, 'D', NULL)");
      statement.executeUpdate("INSERT INTO notes(id, title, bibliography_ref_id) VALUES(5, 'E', 0)");
      statement.executeUpdate("INSERT INTO notes(id, title, bibliography_ref_id) VALUES(6, 'F', -1)");
    }
  }

  /**
   * Erzeugt eine minimale Bibliographie-Fassade fuer den Repository-Konstruktor.
   *
   * @return Test-Service
   */
  private static BibliographyService bibliographyService() {
    return (BibliographyService) Proxy.newProxyInstance(
        BibliographyService.class.getClassLoader(),
        new Class<?>[]{BibliographyService.class},
        (proxy, method, args) -> {
          if ("loadReference".equals(method.getName())) {
            return Optional.empty();
          }
          if (method.getReturnType() == List.class) {
            return List.of();
          }
          if (method.getReturnType() == Optional.class) {
            return Optional.empty();
          }
          if (method.getReturnType() == Boolean.TYPE) {
            return false;
          }
          if (method.getReturnType() == Integer.TYPE) {
            return 0;
          }
          return null;
        }
    );
  }
}
