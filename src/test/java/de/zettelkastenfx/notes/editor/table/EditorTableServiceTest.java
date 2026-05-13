package de.zettelkastenfx.notes.editor.table;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueft die gespeicherten Tabellenmarker des Zetteleditors.
 */
class EditorTableServiceTest {

  /**
   * Erzeugt und liest einen Tabellenmarker.
   */
  @Test
  void createsAndResolvesTableReference() {
    String reference = EditorTableService.referenceText(3, 4);

    assertTrue(EditorTableService.isTableReferenceParagraph(reference));
    var tableSize = EditorTableService.resolveReference(reference);
    assertEquals(3, tableSize.rows());
    assertEquals(4, tableSize.columns());
  }

  /**
   * Speichert und liest Zellinhalte im Tabellenmarker.
   */
  @Test
  void keepsCellContentInReference() {
    var table = EditorTableService.resolveReference(EditorTableService.referenceText(2, 2))
                                  .withCell(0, 1, "Alpha")
                                  .withCell(1, 0, "Beta");

    var resolved = EditorTableService.resolveReference(EditorTableService.referenceText(table));

    assertEquals("Alpha", resolved.cell(0, 1));
    assertEquals("Beta", resolved.cell(1, 0));
  }

  /**
   * Fuegt Zeilen und Spalten ein, ohne vorhandene Zellinhalte zu verlieren.
   */
  @Test
  void insertsRowsAndColumnsAroundExistingContent() {
    var table = EditorTableService.resolveReference(EditorTableService.referenceText(2, 2))
                                  .withCell(0, 0, "A")
                                  .withCell(1, 1, "B")
                                  .insertRow(1)
                                  .insertColumn(1);

    assertEquals(3, table.rows());
    assertEquals(3, table.columns());
    assertEquals("A", table.cell(0, 0));
    assertEquals("B", table.cell(2, 2));
    assertEquals("", table.cell(1, 1));
  }

  /**
   * Loescht Zeilen und Spalten, ohne verbleibende Zellinhalte zu verschieben.
   */
  @Test
  void deletesRowsAndColumns() {
    var table = EditorTableService.resolveReference(EditorTableService.referenceText(3, 3))
                                  .withCell(0, 0, "A")
                                  .withCell(2, 2, "B")
                                  .deleteRow(1)
                                  .deleteColumn(1);

    assertEquals(2, table.rows());
    assertEquals(2, table.columns());
    assertEquals("A", table.cell(0, 0));
    assertEquals("B", table.cell(1, 1));
  }
}
