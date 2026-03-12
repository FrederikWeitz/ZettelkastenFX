package de.zettelkastenfx.notes.keywords;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class KeywordsTablePane extends BorderPane {

  private static final double COUNT_COL_MIN_WIDTH  = 45;  // ~4 Zeichen (je nach Font)
  private static final double COUNT_COL_PREF_WIDTH = 60;
  private static final double COUNT_COL_MAX_WIDTH  = 140; // damit sie nicht “riesig” wird

  private final ObservableList<Row> items = FXCollections.observableArrayList();
  private final TableView<Row> table = new TableView<>(items);

  private final TableColumn<Row, String> colKeyword = new TableColumn<>();
  private final TableColumn<Row, String> colCount = new TableColumn<>();

  private Runnable onKeywordsCommitted = () -> {}; // für sofortiges Update der Keywords nach Anlegen

  public KeywordsTablePane() {
    getStyleClass().add("keywords-pane");
    table.getStyleClass().add("keywords-table");

    table.setEditable(true);
    table.getSelectionModel().setCellSelectionEnabled(true);
    table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    table.setFocusTraversable(false);

    // Zweispaltig, ohne Header (Header wird per CSS ausgeblendet)
    colKeyword.setSortable(false);
    colKeyword.setEditable(true);
    colKeyword.setCellValueFactory(cd -> cd.getValue().keywordProperty());
    colKeyword.setCellFactory(tc -> new SingleClickEditCell());
    colKeyword.setOnEditCommit(e -> {
      Row row = e.getRowValue();
      row.setKeyword(normalizeKeyword(e.getNewValue()));
      normalizeRows();
      onKeywordsCommitted.run();
    });

    colCount.setSortable(false);
    colCount.setEditable(false);
    colCount.setCellValueFactory(cd -> cd.getValue().countProperty());
    // 2. Spalte: klein, aber verschiebbar
    colCount.setMinWidth(COUNT_COL_MIN_WIDTH);
    colCount.setPrefWidth(COUNT_COL_PREF_WIDTH);
    colCount.setMaxWidth(COUNT_COL_MAX_WIDTH);
    colCount.setResizable(true);

    // 1. Spalte: groß, nimmt den Rest
    colKeyword.setMinWidth(120);
    colKeyword.setPrefWidth(400);
    colKeyword.setResizable(true);

    // zweite Spalte erstmal leer lassen (später: Anzahl Zettel)
    colCount.setCellFactory(tc -> new TableCell<>() {
      @Override
      protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        setText(empty ? null : (item == null ? "" : item));
      }
    });

    table.getColumns().setAll(colKeyword, colCount);
    setCenter(table);

    // Initial: genau eine leere Zeile
    setKeywords(List.of());
  }

  public void applyCounts(Map<String, Integer> keywordCounts) {
    if (keywordCounts == null) keywordCounts = java.util.Collections.emptyMap();

    for (Row r : items) {
      String k = normalizeKeyword(r.getKeyword());
      if (k.isBlank()) {
        r.setCount("");
        continue;
      }
      Integer cnt = keywordCounts.get(k);
      if (cnt == null) {
        // Case-insensitive fallback
        cnt = keywordCounts.get(k.toLowerCase(java.util.Locale.ROOT));
      }
      r.setCount(cnt == null ? "" : Integer.toString(cnt));
    }
  }

  /** Setzt Keywords (ohne Sortierung; Sortierung kommt später in Aufgabe 3.2.4). */
  public void setKeywords(Collection<String> keywords) {
    items.clear();
    if (keywords != null) {
      for (String k : keywords) {
        String n = normalizeKeyword(k);
        if (!n.isBlank()) items.add(new Row(n, ""));
      }
    }
    ensureTrailingEmptyRow();
  }

  /** Liefert Keywords ohne die letzte Leerzeile, ohne leere Strings. */
  public List<String> getKeywords() {
    List<String> out = new ArrayList<>();
    for (Row r : items) {
      String k = normalizeKeyword(r.getKeyword());
      if (!k.isBlank()) out.add(k);
    }
    return out;
  }

  // --- intern ---

  private void normalizeRows() {
    // 1) entferne leere Zeilen außer der letzten
    for (int i = items.size() - 1; i >= 0; i--) {
      Row r = items.get(i);
      boolean blank = normalizeKeyword(r.getKeyword()).isBlank();
      boolean isLast = (i == items.size() - 1);
      if (blank && !isLast) {
        items.remove(i);
      }
    }

    // 2) stelle sicher: letzte Zeile ist leer und existiert
    ensureTrailingEmptyRow();
  }

  private void ensureTrailingEmptyRow() {
    if (items.isEmpty()) {
      items.add(new Row("", ""));
      return;
    }
    Row last = items.get(items.size() - 1);
    if (!normalizeKeyword(last.getKeyword()).isBlank()) {
      items.add(new Row("", ""));
    }
  }

  private static String normalizeKeyword(String s) {
    return s == null ? "" : s.trim();
  }

  // --- Row-Model ---

  public static class Row {
    private final StringProperty keyword = new SimpleStringProperty("");
    private final StringProperty count = new SimpleStringProperty("");

    public Row(String keyword, String count) {
      this.keyword.set(keyword == null ? "" : keyword);
      this.count.set(count == null ? "" : count);
    }

    public StringProperty keywordProperty() { return keyword; }
    public StringProperty countProperty() { return count; }

    public String getKeyword() { return keyword.get(); }
    public void setKeyword(String v) { keyword.set(v == null ? "" : v); }

    public String getCount() { return count.get(); }
    public void setCount(String v) { count.set(v == null ? "" : v); }
  }

  // --- Single-click edit cell (auch für leere letzte Zeile) ---

  private class SingleClickEditCell extends TableCell<Row, String> {
    private TextField textField;

    public SingleClickEditCell() {
      setOnMouseClicked(e -> {
        if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 1 && !isEmpty()) {
          table.edit(getIndex(), colKeyword);
          e.consume();
        }
      });
    }

    @Override
    public void startEdit() {
      super.startEdit();
      if (textField == null) {
        textField = new TextField();

        // Enter commit
        textField.setOnAction(e -> commitEdit(textField.getText()));

        // Klick woanders hin / Fokus weg => commit (statt cancel)
        textField.focusedProperty().addListener((obs, old, now) -> {
          if (!now && isEditing()) {
            commitEdit(textField.getText());
          }
        });

        textField.setOnKeyPressed(e -> {
          if (e.getCode() == KeyCode.ESCAPE) cancelEdit();
          if (e.getCode() == KeyCode.TAB) {
            boolean backwards = e.isShiftDown();
            commitEdit(textField.getText());
            e.consume();
            javafx.application.Platform.runLater(() -> editRelative(backwards ? -1 : 1));
          }
        });
      }
      textField.setText(getItem());
      setGraphic(textField);
      setText(null);
      textField.requestFocus();
      textField.selectAll();
    }

    @Override
    public void cancelEdit() {
      super.cancelEdit();
      setText(getItem());
      setGraphic(null);
    }

    @Override
    public void commitEdit(String newValue) {
      super.commitEdit(newValue);
      setGraphic(null);
      setText(getItem());
    }

    @Override
    protected void updateItem(String item, boolean empty) {
      super.updateItem(item, empty);
      if (empty) {
        setText(null);
        setGraphic(null);
        return;
      }
      if (isEditing()) {
        if (textField != null) textField.setText(item);
        setText(null);
        setGraphic(textField);
      } else {
        setText(item);
        setGraphic(null);
      }
    }

    private void editRelative(int delta) {
      int row = getIndex() + delta;
      if (row < 0) row = 0;
      if (row >= table.getItems().size()) row = table.getItems().size() - 1;

      table.getSelectionModel().clearAndSelect(row, colKeyword);
      table.scrollTo(row);
      table.edit(row, colKeyword);
    }
  }

  public void setOnKeywordsCommitted(Runnable r) {
    this.onKeywordsCommitted = (r == null) ? () -> {} : r;
  }
}