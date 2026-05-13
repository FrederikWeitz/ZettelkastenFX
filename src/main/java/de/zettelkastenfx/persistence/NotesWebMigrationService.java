package de.zettelkastenfx.persistence;

import de.zettelkastenfx.notes.web.RichTextHtmlConverter;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

/**
 * Befuellt die Uebergangstabelle notes_web einmalig aus der bisherigen notes-Tabelle.
 */
public final class NotesWebMigrationService {

  private final DataSource dataSource;
  private final RichTextHtmlConverter converter;

  /**
   * Erstellt den Migrationsservice.
   *
   * @param dataSource Datenquelle
   */
  public NotesWebMigrationService(DataSource dataSource) {
    this(dataSource, new RichTextHtmlConverter());
  }

  /**
   * Erstellt den Migrationsservice mit explizitem HTML-Konverter.
   *
   * @param dataSource Datenquelle
   * @param converter HTML-Konverter
   */
  public NotesWebMigrationService(DataSource dataSource, RichTextHtmlConverter converter) {
    this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    this.converter = Objects.requireNonNull(converter, "converter");
  }

  /**
   * Legt notes_web bei Bedarf an und befuellt die Tabelle, wenn sie leer ist.
   */
  public void migrateIfNecessary() {
    try (Connection connection = dataSource.getConnection()) {
      ensureNotesWebTable(connection);
      if (notesWebHasRows(connection)) {
        return;
      }
      copyNotes(connection);
    } catch (SQLException exception) {
      throw new IllegalStateException("Umcodierung von notes nach notes_web fehlgeschlagen.", exception);
    }
  }

  /**
   * Legt die Zieltabelle an, falls sie manuell entfernt wurde.
   *
   * @param connection Datenbankverbindung
   * @throws SQLException bei Datenbankfehlern
   */
  private void ensureNotesWebTable(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute("""
          CREATE TABLE IF NOT EXISTS notes_web (
          id                  INTEGER PRIMARY KEY,
          title               TEXT    NOT NULL,
          content_text        TEXT    NOT NULL,
          content_codec       TEXT    NOT NULL DEFAULT 'html-body',
          bibliography_ref_id INTEGER,
          created_at          TEXT    NOT NULL,
          updated_at          TEXT
          )
          """);
    }
  }

  /**
   * Prueft, ob notes_web bereits befuellt wurde.
   *
   * @param connection Datenbankverbindung
   * @return {@code true}, wenn notes_web mindestens eine Zeile enthaelt
   * @throws SQLException bei Datenbankfehlern
   */
  private boolean notesWebHasRows(Connection connection) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM notes_web LIMIT 1");
         ResultSet resultSet = statement.executeQuery()) {
      return resultSet.next();
    }
  }

  /**
   * Kopiert alle Alt-Zettel in die HTML-Uebergangstabelle.
   *
   * @param connection Datenbankverbindung
   * @throws SQLException bei Datenbankfehlern
   */
  private void copyNotes(Connection connection) throws SQLException {
    String selectSql = """
        SELECT id, title, content_blob, content_codec, bibliography_ref_id, created_at, updated_at
        FROM notes
        ORDER BY id ASC
        """;
    String insertSql = """
        INSERT INTO notes_web(id, title, content_text, content_codec, bibliography_ref_id, created_at, updated_at)
        VALUES(?, ?, ?, ?, ?, ?, ?)
        """;

    boolean previousAutoCommit = connection.getAutoCommit();
    connection.setAutoCommit(false);
    try (PreparedStatement select = connection.prepareStatement(selectSql);
         PreparedStatement insert = connection.prepareStatement(insertSql);
         ResultSet rows = select.executeQuery()) {
      while (rows.next()) {
        insert.setInt(1, rows.getInt("id"));
        insert.setString(2, rows.getString("title"));
        insert.setString(3, convertContent(rows.getBytes("content_blob"), rows.getString("content_codec")));
        insert.setString(4, HtmlBodyCodec.CODEC_NAME);
        Object bibliographyRefId = rows.getObject("bibliography_ref_id");
        if (bibliographyRefId == null) {
          insert.setNull(5, java.sql.Types.INTEGER);
        } else {
          insert.setInt(5, ((Number) bibliographyRefId).intValue());
        }
        insert.setString(6, rows.getString("created_at"));
        insert.setString(7, rows.getString("updated_at"));
        insert.addBatch();
      }
      insert.executeBatch();
      connection.commit();
    } catch (RuntimeException | SQLException exception) {
      connection.rollback();
      throw exception;
    } finally {
      connection.setAutoCommit(previousAutoCommit);
    }
  }

  /**
   * Wandelt einen gespeicherten Zettelinhalt in ein HTML-Body-Fragment um.
   *
   * @param contentBlob bisheriger Inhaltsblob
   * @param contentCodec bisheriger Codec
   * @return HTML-Body-Fragment
   */
  private String convertContent(byte[] contentBlob, String contentCodec) {
    if (contentBlob == null || contentBlob.length == 0) {
      return "";
    }
    if (InlineCssRtfxBlobCodec.CODEC_NAME.equals(contentCodec)) {
      return converter.toHtmlBody(InlineCssRtfxBlobCodec.decodeFromGzipToStyledDocument(contentBlob));
    }
    return converter.plainTextToHtmlBody(new String(contentBlob, StandardCharsets.UTF_8));
  }
}
