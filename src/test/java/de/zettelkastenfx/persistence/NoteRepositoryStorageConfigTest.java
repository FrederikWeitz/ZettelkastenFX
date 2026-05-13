package de.zettelkastenfx.persistence;

import de.zettelkastenfx.bibliography.api.BibliographyService;
import de.zettelkastenfx.notes.model.Note;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueft zentrale Repository-Operationen ueber die abstrahierte Zetteltabelle.
 */
class NoteRepositoryStorageConfigTest {

  @TempDir
  Path tempDir;

  /**
   * Laedt, speichert, aktualisiert und loescht Zettel weiter gegen die aktuelle Zetteltabelle.
   *
   * @throws Exception bei Datenbankfehlern
   */
  @Test
  void repositoryCrudStillUsesConfiguredNoteStorage() throws Exception {
    SQLiteDataSource dataSource = dataSource();
    createSchema(dataSource);

    NoteRepository repository = new NoteRepository(dataSource, bibliographyService());
    Note note = new Note();
    note.setTitle("Titel");
    note.setBodyBlob("Alpha".getBytes(StandardCharsets.UTF_8));
    note.setBodyCodec("plain");
    note.setKeywords(List.of("eins", "zwei"));

    int id = repository.save(note);

    assertEquals(1, id);
    assertEquals(1, repository.countNotes());
    assertEquals(1, repository.maxNoteId());
    assertTrue(repository.load(id).isPresent());
    assertEquals("Alpha", new String(repository.load(id).orElseThrow().getBodyBlob(), StandardCharsets.UTF_8));

    note.setTitle("Titel 2");
    note.setBodyBlob("Beta".getBytes(StandardCharsets.UTF_8));
    repository.save(note);

    assertEquals("Titel 2", repository.loadNoteTitle(id).orElseThrow());
    assertEquals("Beta", new String(repository.load(id).orElseThrow().getBodyBlob(), StandardCharsets.UTF_8));
    assertEquals(List.of(id), repository.loadAllNoteIdsForExport());

    assertTrue(repository.deleteNoteIfItIsLast(id));
    assertFalse(repository.existsNote(id));
  }

  /**
   * Laedt bestehenden HTML-Text aus notes_web.content_text in den Zettel-Body.
   *
   * @throws Exception bei Datenbankfehlern
   */
  @Test
  void loadReadsHtmlBodyFromNotesWebContentText() throws Exception {
    SQLiteDataSource dataSource = dataSource();
    createSchema(dataSource);
    try (Connection connection = dataSource.getConnection();
         Statement statement = connection.createStatement()) {
      statement.execute("""
          INSERT INTO notes_web(id, title, content_text, content_codec, created_at)
          VALUES(7, 'Titel', '<p class="j">Alpha</p>', 'html-body', '2026-05-12T12:00:00Z')
          """);
    }

    NoteRepository repository = new NoteRepository(dataSource, bibliographyService());
    Note note = repository.load(7).orElseThrow();

    assertEquals("html-body", note.getBodyCodec());
    assertEquals("<p class=\"j\">Alpha</p>", new String(note.getBodyBlob(), StandardCharsets.UTF_8));
  }

  private SQLiteDataSource dataSource() {
    SQLiteDataSource dataSource = new SQLiteDataSource();
    dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("note-storage.sqlite"));
    return dataSource;
  }

  private void createSchema(SQLiteDataSource dataSource) throws SQLException {
    try (Connection connection = dataSource.getConnection();
         Statement statement = connection.createStatement()) {
      statement.execute("""
          CREATE TABLE notes_web (
          id INTEGER PRIMARY KEY,
          title TEXT NOT NULL,
          content_text TEXT NOT NULL,
          content_codec TEXT NOT NULL,
          bibliography_ref_id INTEGER,
          created_at TEXT NOT NULL,
          updated_at TEXT
          )
          """);
      statement.execute("CREATE TABLE keywords(id INTEGER PRIMARY KEY, keyword TEXT NOT NULL UNIQUE)");
      statement.execute("""
          CREATE TABLE note_keywords(
          note_id INTEGER NOT NULL,
          keyword_id INTEGER NOT NULL,
          PRIMARY KEY(note_id, keyword_id)
          )
          """);
      statement.execute("""
          CREATE TABLE note_links(
          from_note_id INTEGER NOT NULL,
          to_note_id INTEGER NOT NULL,
          link_type TEXT NOT NULL,
          PRIMARY KEY(from_note_id, to_note_id)
          )
          """);
    }
  }

  private static BibliographyService bibliographyService() {
    return (BibliographyService) Proxy.newProxyInstance(
        BibliographyService.class.getClassLoader(),
        new Class<?>[]{BibliographyService.class},
        (proxy, method, args) -> {
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
