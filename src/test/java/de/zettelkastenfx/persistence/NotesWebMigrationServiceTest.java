package de.zettelkastenfx.persistence;

import org.fxmisc.richtext.model.Codec;
import org.fxmisc.richtext.model.ReadOnlyStyledDocument;
import org.fxmisc.richtext.model.SegmentOps;
import org.fxmisc.richtext.model.StyledDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueft die einmalige Umcodierung von notes nach notes_web.
 */
class NotesWebMigrationServiceTest {

  @TempDir
  Path tempDir;

  /**
   * Befuellt notes_web mit HTML-Body-Fragmenten und laesst eine bestehende Befuellung unveraendert.
   *
   * @throws Exception bei Datenbank- oder Codec-Fehlern
   */
  @Test
  void migratesNotesToHtmlBodyOnce() throws Exception {
    SQLiteDataSource dataSource = dataSource();
    createNotesSchema(dataSource);
    insertRtfxNote(dataSource);

    new NotesWebMigrationService(dataSource).migrateIfNecessary();

    try (Connection connection = dataSource.getConnection();
         Statement statement = connection.createStatement();
         ResultSet resultSet = statement.executeQuery("SELECT id, content_text, content_codec FROM notes_web")) {
      assertTrue(resultSet.next());
      assertEquals(1, resultSet.getInt("id"));
      assertEquals(HtmlBodyCodec.CODEC_NAME, resultSet.getString("content_codec"));
      assertEquals("<p class=\"j\"><span class=\"i\">Alpha &amp; Beta</span></p>", resultSet.getString("content_text"));
      System.out.println(resultSet.getString("content_text"));
    }

    try (Connection connection = dataSource.getConnection();
         Statement statement = connection.createStatement()) {
      statement.executeUpdate("UPDATE notes_web SET content_text = 'manuell'");
    }

    new NotesWebMigrationService(dataSource).migrateIfNecessary();

    try (Connection connection = dataSource.getConnection();
         Statement statement = connection.createStatement();
         ResultSet resultSet = statement.executeQuery("SELECT content_text FROM notes_web WHERE id = 1")) {
      assertTrue(resultSet.next());
      assertEquals("manuell", resultSet.getString("content_text"));
    }
  }

  private SQLiteDataSource dataSource() {
    SQLiteDataSource dataSource = new SQLiteDataSource();
    dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("notes-web.sqlite"));
    return dataSource;
  }

  private void createNotesSchema(SQLiteDataSource dataSource) throws Exception {
    try (Connection connection = dataSource.getConnection();
         Statement statement = connection.createStatement()) {
      statement.execute("""
          CREATE TABLE notes (
          id INTEGER PRIMARY KEY,
          title TEXT NOT NULL,
          content_blob BLOB NOT NULL,
          content_codec TEXT NOT NULL,
          bibliography_ref_id INTEGER,
          created_at TEXT NOT NULL,
          updated_at TEXT
          )
          """);
    }
  }

  private void insertRtfxNote(SQLiteDataSource dataSource) throws Exception {
    try (Connection connection = dataSource.getConnection();
         var statement = connection.prepareStatement("""
             INSERT INTO notes(id, title, content_blob, content_codec, bibliography_ref_id, created_at, updated_at)
             VALUES(1, 'Titel', ?, ?, NULL, '2026-05-12T00:00:00Z', NULL)
             """)) {
      statement.setBytes(1, encodedRtfx());
      statement.setString(2, InlineCssRtfxBlobCodec.CODEC_NAME);
      statement.executeUpdate();
    }
  }

  private byte[] encodedRtfx() throws Exception {
    StyledDocument<String, String, String> document = ReadOnlyStyledDocument.fromString(
        "Alpha & Beta",
        "-fx-text-alignment: justify;",
        "-fx-font-style: italic;",
        SegmentOps.styledTextOps()
    );
    Codec<StyledDocument<String, String, String>> codec =
        ReadOnlyStyledDocument.codec(
            Codec.STRING_CODEC,
            Codec.styledSegmentCodec(Codec.STRING_CODEC, Codec.STRING_CODEC),
            SegmentOps.styledTextOps()
        );
    try (ByteArrayOutputStream output = new ByteArrayOutputStream();
         DataOutputStream dataOutput = new DataOutputStream(output)) {
      codec.encode(dataOutput, document);
      return GzipBytes.compress(output.toByteArray());
    }
  }
}
