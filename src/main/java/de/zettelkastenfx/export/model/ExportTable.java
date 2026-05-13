package de.zettelkastenfx.export.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Beschreibt eine einfache Exporttabelle.
 *
 * @param rows Zeilenanzahl
 * @param columns Spaltenanzahl
 * @param cells Zelltexte
 * @param columnWeights relative Spaltenbreiten
 */
public record ExportTable(int rows, int columns, List<List<String>> cells, List<Integer> columnWeights) {

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
    columnWeights = normalizeColumnWeights(columnWeights, columns);
  }

  /**
   * Erzeugt eine Tabelle ohne explizite Spaltenbreiten.
   *
   * @param rows Zeilenanzahl
   * @param columns Spaltenanzahl
   * @param cells Zelltexte
   */
  public ExportTable(int rows, int columns, List<List<String>> cells) {
    this(rows, columns, cells, List.of());
  }

  /**
   * Erzeugt eine leere Exporttabelle.
   *
   * @param rows Zeilenanzahl
   * @param columns Spaltenanzahl
   */
  public ExportTable(int rows, int columns) {
    this(rows, columns, List.of(), List.of());
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

  /**
   * Berechnet konkrete Spaltenbreiten aus den relativen Spaltengewichten.
   *
   * @param totalWidth gesamte Tabellenbreite
   * @return Spaltenbreiten, deren Summe {@code totalWidth} ergibt
   */
  public List<Integer> columnWidths(int totalWidth) {
    int normalizedTotal = Math.max(columns, totalWidth);
    int weightSum = columnWeights.stream().mapToInt(Integer::intValue).sum();
    List<Integer> widths = new ArrayList<>();
    int assigned = 0;
    for (int column = 0; column < columns; column++) {
      int width = column == columns - 1
          ? normalizedTotal - assigned
          : Math.max(1, normalizedTotal * columnWeights.get(column) / weightSum);
      widths.add(width);
      assigned += width;
    }
    return List.copyOf(widths);
  }

  private static List<Integer> normalizeColumnWeights(List<Integer> weights, int columns) {
    List<Integer> normalized = new ArrayList<>();
    List<Integer> source = weights == null ? List.of() : weights;
    for (int column = 0; column < columns; column++) {
      int weight = column < source.size() && source.get(column) != null ? source.get(column) : 1;
      normalized.add(Math.max(1, weight));
    }
    return List.copyOf(normalized);
  }
}
