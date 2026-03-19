package de.zettelkastenfx.notes.keywords;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Side;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;

import java.util.*;
import java.util.function.Function;

public class KeywordsTablePane extends BorderPane {

  private static final double COUNT_COL_MIN_WIDTH  = 45;  // ~4 Zeichen (je nach Font)
  private static final double COUNT_COL_PREF_WIDTH = 60;
  private static final double COUNT_COL_MAX_WIDTH  = 140; // damit sie nicht “riesig” wird

  private final ObservableList<Row> items = FXCollections.observableArrayList();
  private final TableView<Row> table = new TableView<>(items);

  private final TableColumn<Row, String> colKeyword = new TableColumn<>();
  private final TableColumn<Row, String> colCount = new TableColumn<>();
  private Function<String, List<String>> keywordSuggestionProvider = text -> List.of();

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
      setKeywords(getKeywords());
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

    Set<String> set = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    set.addAll(keywords);

    if (!set.isEmpty()) {
      for (String k : set) {
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
    private ContextMenu suggestionMenu;

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
        textField.getStyleClass().add("keywords-inline-editor");

        suggestionMenu = new ContextMenu();
        suggestionMenu.setAutoHide(true);

        textField.textProperty().addListener((obs, oldValue, newValue) ->
                                                 refreshSuggestions(newValue)
        );

        // Enter commit
        textField.setOnAction(e -> {
          suggestionMenu.hide();
          commitEdit(textField.getText());
        });

        // Klick woanders hin / Fokus weg => commit (statt cancel)
        textField.focusedProperty().addListener((obs, old, now) -> {
          if (!now) {
            suggestionMenu.hide();
            if (isEditing()) {
              commitEdit(textField.getText());
            }
          }
        });

        textField.setOnKeyPressed(e -> {
          if (e.getCode() == KeyCode.ESCAPE) {
            suggestionMenu.hide();
            cancelEdit();
          }
          if (e.getCode() == KeyCode.TAB) {
            suggestionMenu.hide();
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

      javafx.application.Platform.runLater(() -> {
        textField.requestFocus();
        textField.selectAll();
        refreshSuggestions(textField.getText());
      });
    }

    @Override
    public void cancelEdit() {
      super.cancelEdit();
      if (suggestionMenu != null) {
        suggestionMenu.hide();
      }
      setText(getItem());
      setGraphic(null);
    }

    @Override
    public void commitEdit(String newValue) {
      if (suggestionMenu != null) {
        suggestionMenu.hide();
      }
      super.commitEdit(newValue);
      setGraphic(null);
      setText(getItem());
    }

    @Override
    protected void updateItem(String item, boolean empty) {
      super.updateItem(item, empty);

      if (empty) {
        if (suggestionMenu != null) {
          suggestionMenu.hide();
        }
        setText(null);
        setGraphic(null);
        return;
      }

      if (isEditing()) {
        if (textField != null) {
          textField.setText(item);
        }
        setText(null);
        setGraphic(textField);
      } else {
        if (suggestionMenu != null) {
          suggestionMenu.hide();
        }
        setText(item);
        setGraphic(null);
      }
    }

    /**
     * Aktualisiert das Vorschlagsmenü gemäß aktueller Eingabe.
     *
     * @param typedText aktuell eingegebener Text
     */
    private void refreshSuggestions(String typedText) {
      if (suggestionMenu == null || textField == null || !isEditing()) {
        return;
      }

      String normalized = normalizeKeyword(typedText);
      if (normalized.isBlank()) {
        suggestionMenu.hide();
        return;
      }

      List<String> suggestions = keywordSuggestionProvider.apply(normalized);
      if (suggestions == null || suggestions.isEmpty()) {
        suggestionMenu.hide();
        return;
      }

      List<MenuItem> items = new ArrayList<>();
      for (String keyword : suggestions) {
        if (keyword == null || keyword.isBlank()) {
          continue;
        }

        Label label = new Label(keyword);
        label.setMinWidth(Region.USE_PREF_SIZE);

        CustomMenuItem item = new CustomMenuItem(label, true);
        item.setOnAction(e -> {
          textField.setText(keyword);
          textField.positionCaret(keyword.length());
          suggestionMenu.hide();
        });
        items.add(item);
      }

      if (items.isEmpty()) {
        suggestionMenu.hide();
        return;
      }

      suggestionMenu.getItems().setAll(items);

      if (textField.getScene() == null
              || textField.getScene().getWindow() == null
              || !textField.getScene().getWindow().isShowing()) {
        return;
      }

      if (suggestionMenu.isShowing()) {
        suggestionMenu.hide();
      }
      suggestionMenu.show(textField, Side.BOTTOM, 0, 0);
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

  /**
   * Setzt den Callback zum Laden von Schlagwort-Vorschlägen für das Inline-Edit.
   *
   * @param provider Callback, der zur aktuell eingegebenen Zeichenkette passende
   *                 Schlagwörter liefert
   */
  public void setKeywordSuggestionProvider(Function<String, List<String>> provider) {
    this.keywordSuggestionProvider = (provider == null) ? text -> List.of() : provider;
  }

  public void setOnKeywordsCommitted(Runnable r) {
    this.onKeywordsCommitted = (r == null) ? () -> {} : r;
  }
}