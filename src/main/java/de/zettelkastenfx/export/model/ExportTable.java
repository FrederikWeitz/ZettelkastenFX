package de.zettelkastenfx.export.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Beschreibt eine einfache Exporttabelle.
 *
 * @param rows Zeilenanzahl
 * @param columns Spaltenanzahl
 * @param cells Zelltexte
 */
public record ExportTable(int rows, int columns, List<List<String>> cells) {

  /**
   * Erzeugt eine Tabelle mit validierter Groesse.
   *
   * @param rows Zeilenanzahl
   * @param columns Spaltenanzahl
   */
  public ExportTable {
    if (rows < 1 || columns < 1) {
      throw new IllegalArgumentException("Tabellengroesse muss mindestens 1x1 sein.");
    }
    List<List<String>> normalized = new ArrayList<>();
    List<List<String>> source = cells == null ? List.of() : cells;
    for (int row = 0; row < rows; row++) {
      List<String> sourceRow = row < source.size() ? source.get(row) : List.of();
      List<String> targetRow = new ArrayList<>();
      for (int column = 0; column < columns; column++) {
        targetRow.add(column < sourceRow.size() && sourceRow.get(column) != null ? sourceRow.get(column) : "");
      }
      normalized.add(List.copyOf(targetRow));
    }
    cells = List.copyOf(normalized);
  }

  /**
   * Erzeugt eine leere Exporttabelle.
   *
   * @param rows Zeilenanzahl
   * @param columns Spaltenanzahl
   */
  public ExportTable(int rows, int columns) {
    this(rows, columns, List.of());
  }

  /**
   * Liefert den Text einer Tabellenzelle.
   *
   * @param row Zeilenindex
   * @param column Spaltenindex
   * @return Zelltext
   */
  public String cell(int row, int column) {
    return cells.get(row).get(column);
  }
}
