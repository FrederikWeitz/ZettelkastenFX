package de.zettelkastenfx.persistence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Prueft die zentrale SQL-Uebersetzung fuer die aktive Zettelspeicherung.
 */
class NoteStorageConfigTest {

  /**
   * Uebersetzt nur ganze notes- und content_blob-Bezeichner.
   */
  @Test
  void rewritesConfiguredNoteIdentifiersOnlyAsWholeTokens() {
    String sql = """
        SELECT n.content_blob, note_keywords.note_id
        FROM notes n
        JOIN note_keywords ON note_keywords.note_id = n.id
        WHERE n.title = 'notes content_blob'
        """;

    String expected = """
        SELECT n.content_text, note_keywords.note_id
        FROM notes_web n
        JOIN note_keywords ON note_keywords.note_id = n.id
        WHERE n.title = 'notes content_blob'
        """;

    assertEquals(expected, NoteStorageConfig.rewriteSql(sql));
  }
}
