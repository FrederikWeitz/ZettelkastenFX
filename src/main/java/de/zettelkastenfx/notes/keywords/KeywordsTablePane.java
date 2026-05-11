package de.zettelkastenfx.notes.keywords;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
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
  private Function<KeywordSuggestionRequest, List<String>> keywordSuggestionProvider = request -> List.of();
  private boolean prefixSearchMode;
  private boolean tokenAndSearchMode;
  private int pendingEditorTextRow = -1;
  private String pendingEditorText = "";

  /**
   * Beschreibt eine Suchanfrage fuer Stichwortvorschlaege.
   *
   * @param text Eingabetext
   * @param prefixOnly ob nur am Zeichenkettenanfang gesucht wird
   * @param allTokens ob alle Suchteile enthalten sein muessen
    */
  public record KeywordSuggestionRequest(String text, boolean prefixOnly, boolean allTokens) {}

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
      {
        setAlignment(Pos.CENTER_RIGHT);
      }

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
    private List<String> displayedSuggestions = List.of();
    private int selectedSuggestionIndex = -1;
    private boolean splitNextSuggestionAction;
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
        suggestionMenu.setHideOnEscape(false);
        suggestionMenu.addEventFilter(KeyEvent.KEY_PRESSED, this::handleSuggestionMenuKeyPressed);

        textField.textProperty().addListener((obs, oldValue, newValue) -> {
          if (!Objects.equals(oldValue, newValue)) {
            selectedSuggestionIndex = -1;
          }
          refreshSuggestions(newValue);
        });

        // Enter commit
        textField.setOnAction(e -> {
          hideSuggestions();
          commitEdit(textField.getText());
        });

        // Klick woanders hin / Fokus weg => commit (statt cancel)
        textField.focusedProperty().addListener((obs, old, now) -> {
          if (!now) {
            hideSuggestions();
            if (isEditing()) {
              commitEdit(textField.getText());
            }
          }
        });

        textField.addEventFilter(KeyEvent.KEY_PRESSED, this::handleEditorKeyPressed);
      }

      tokenAndSearchMode = false;
      textField.setText(editorTextForRow(getIndex(), getItem()));
      updateEditorSearchModeStyle();
      setGraphic(textField);
      setText(null);

      javafx.application.Platform.runLater(() -> {
        textField.requestFocus();
        textField.setText(editorTextForRow(getIndex(), textField.getText()));
        clearPendingEditorText(getIndex());
        textField.selectAll();
        refreshSuggestions(textField.getText());
      });
    }

    @Override
    public void cancelEdit() {
      super.cancelEdit();
      if (suggestionMenu != null) {
        hideSuggestions();
      }
      setText(getItem());
      setGraphic(null);
    }

    @Override
    public void commitEdit(String newValue) {
      if (suggestionMenu != null) {
        hideSuggestions();
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
          hideSuggestions();
        }
        setText(null);
        setGraphic(null);
        return;
      }

      if (isEditing()) {
        if (textField != null) {
          textField.setText(editorTextForRow(getIndex(), item));
        }
        setText(null);
        setGraphic(textField);
      } else {
        if (suggestionMenu != null) {
          hideSuggestions();
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
        hideSuggestions();
        return;
      }

      List<String> suggestions = keywordSuggestionProvider.apply(
          new KeywordSuggestionRequest(normalized, prefixSearchMode, tokenAndSearchMode)
      );
      if (suggestions == null || suggestions.isEmpty()) {
        hideSuggestions();
        return;
      }

      List<MenuItem> items = new ArrayList<>();
      List<String> visibleSuggestions = new ArrayList<>();
      for (String keyword : suggestions) {
        if (keyword == null || keyword.isBlank()) {
          continue;
        }

        Label label = new Label(keyword);
        label.setMinWidth(Region.USE_PREF_SIZE);
        label.addEventFilter(KeyEvent.KEY_PRESSED, this::handleSuggestionNodeKeyPressed);
        label.addEventFilter(KeyEvent.KEY_PRESSED, this::armSplitSuggestionAction);

        CustomMenuItem item = new CustomMenuItem(label, true);
        item.addEventFilter(KeyEvent.KEY_PRESSED, this::handleSuggestionNodeKeyPressed);
        item.addEventHandler(KeyEvent.KEY_PRESSED, this::armSplitSuggestionAction);
        item.setOnAction(e -> {
          acceptSuggestion(keyword, consumeSplitSuggestionAction());
        });
        items.add(item);
        visibleSuggestions.add(keyword);
      }

      if (items.isEmpty()) {
        hideSuggestions();
        return;
      }

      displayedSuggestions = List.copyOf(visibleSuggestions);
      if (selectedSuggestionIndex >= displayedSuggestions.size()) {
        selectedSuggestionIndex = displayedSuggestions.size() - 1;
      }
      suggestionMenu.getItems().setAll(items);
      updateSuggestionFocusStyle();

      if (textField.getScene() == null
              || textField.getScene().getWindow() == null
              || !textField.getScene().getWindow().isShowing()) {
        return;
      }

      if (!suggestionMenu.isShowing()) {
        suggestionMenu.show(textField, Side.BOTTOM, 0, 0);
      }
      updateSuggestionFocusStyle();
    }

    /**
     * Behandelt Tastaturbefehle fuer Vorschlaege und Suchmodi.
     *
     * @param event Tastaturereignis
     */
    private void handleEditorKeyPressed(KeyEvent event) {
      if (event.getCode() == KeyCode.HOME) {
        prefixSearchMode = !prefixSearchMode;
        updateEditorSearchModeStyle();
        refreshSuggestions(textField.getText());
        event.consume();
        return;
      }
      if (event.isAltDown() && event.getCode() == KeyCode.DOWN) {
        toggleTokenAndSearchMode();
        event.consume();
        return;
      }
      if (event.getCode() == KeyCode.DOWN) {
        if (acceptOnlyVisibleSuggestion()) {
          event.consume();
          return;
        }

        List<String> suggestions = currentSuggestions();
        if (!suggestions.isEmpty()) {
          if (suggestionMenu != null && !suggestionMenu.isShowing()) {
            refreshSuggestions(textField.getText());
          }
          focusSuggestion(Math.min(focusedSuggestionIndex() + 1, suggestions.size() - 1));
        }
        event.consume();
        return;
      }
      if (event.getCode() == KeyCode.UP && focusedSuggestionIndex() >= 0) {
        focusSuggestion(Math.max(0, focusedSuggestionIndex() - 1));
        event.consume();
        return;
      }
      if ((event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.TAB) && focusedSuggestionIndex() >= 0) {
        acceptSuggestion(currentSuggestions().get(focusedSuggestionIndex()), event.isShiftDown() && event.getCode() == KeyCode.ENTER);
        event.consume();
        return;
      }
      if (event.getCode() == KeyCode.ESCAPE) {
        if (suggestionMenu != null && suggestionMenu.isShowing()) {
          leaveSuggestionSelection();
        } else {
          cancelEdit();
        }
        event.consume();
        return;
      }
      if (event.getCode() == KeyCode.TAB) {
        hideSuggestions();
        boolean backwards = event.isShiftDown();
        commitEdit(textField.getText());
        event.consume();
        javafx.application.Platform.runLater(() -> editRelative(backwards ? -1 : 1));
      }
    }

    /**
     * Behandelt Tastaturbefehle, die JavaFX direkt an das geoeffnete
     * Vorschlagsmenue schickt.
     *
     * @param event Tastaturereignis im Vorschlagsmenue
     */
    private void handleSuggestionMenuKeyPressed(KeyEvent event) {
      if (event.isAltDown() && event.getCode() == KeyCode.DOWN) {
        toggleTokenAndSearchMode();
        event.consume();
        return;
      }
      if (event.getCode() == KeyCode.ESCAPE) {
        leaveSuggestionSelection();
        event.consume();
        return;
      }
      if (event.isShiftDown() && event.getCode() == KeyCode.ENTER) {
        splitNextSuggestionAction = true;
        if (acceptFocusedSuggestion(true)) {
          event.consume();
        }
        return;
      }
      if (event.getCode() == KeyCode.DOWN && acceptOnlyVisibleSuggestion()) {
        event.consume();
      }
    }

    /**
     * Behandelt Tastaturbefehle, die direkt auf einem Vorschlagsknoten landen.
     *
     * @param event Tastaturereignis
     */
    private void handleSuggestionNodeKeyPressed(KeyEvent event) {
      if (event.isAltDown() && event.getCode() == KeyCode.DOWN) {
        toggleTokenAndSearchMode();
        event.consume();
        return;
      }
      if (event.getCode() == KeyCode.ESCAPE) {
        leaveSuggestionSelection();
        event.consume();
      }
    }

    /**
     * Verlaesst nur die Auswahl im sichtbaren Dropdown, ohne das Dropdown zu
     * schliessen. Falls JavaFX das Popup trotzdem automatisch schliesst, wird es
     * im naechsten UI-Takt wieder fuer die aktuelle Eingabe geoeffnet.
     */
    private void leaveSuggestionSelection() {
      clearSuggestionFocus();
      javafx.application.Platform.runLater(() -> {
        if (isEditing()
                && textField != null
                && suggestionMenu != null
                && !normalizeKeyword(textField.getText()).isBlank()) {
          refreshSuggestions(textField.getText());
        }
      });
    }

    /**
     * Schaltet die Mehrwort-UND-Suche um, ohne die normale Dropdown-Navigation
     * der Pfeil-runter-Taste auszufuehren.
     */
    private void toggleTokenAndSearchMode() {
      tokenAndSearchMode = !tokenAndSearchMode;
      clearSuggestionFocus();
      updateEditorSearchModeStyle();
      refreshSuggestions(textField.getText());
    }

    /**
     * Merkt fuer die folgende Menue-Action vor, dass die aktuelle Eingabe in die
     * naechste Zeile kopiert werden soll.
     *
     * @param event Tastaturereignis
     */
    private void armSplitSuggestionAction(KeyEvent event) {
      if (event.isShiftDown() && event.getCode() == KeyCode.ENTER) {
        splitNextSuggestionAction = true;
      }
    }

    /**
     * Liefert und loescht die Vormerkung fuer eine Shift-Enter-Menueaktion.
     *
     * @return {@code true}, wenn die folgende Uebernahme den Eingabetext kopieren soll
     */
    private boolean consumeSplitSuggestionAction() {
      boolean split = splitNextSuggestionAction;
      splitNextSuggestionAction = false;
      return split;
    }

    /**
     * Uebernimmt den aktuell markierten Dropdown-Vorschlag.
     *
     * @param splitCurrentInput ob die bisherige Eingabe in die Folgezeile soll
     * @return {@code true}, wenn ein markierter Vorschlag uebernommen wurde
     */
    private boolean acceptFocusedSuggestion(boolean splitCurrentInput) {
      int index = focusedSuggestionIndex();
      if (displayedSuggestions == null || index < 0 || index >= displayedSuggestions.size()) {
        return false;
      }

      acceptSuggestion(displayedSuggestions.get(index), splitCurrentInput);
      return true;
    }

    /**
     * Uebernimmt den einzigen sichtbaren Dropdown-Vorschlag.
     *
     * @return {@code true}, wenn genau ein sichtbarer Vorschlag uebernommen wurde
     */
    private boolean acceptOnlyVisibleSuggestion() {
      if (displayedSuggestions == null || displayedSuggestions.size() != 1) {
        return false;
      }

      acceptSuggestion(displayedSuggestions.getFirst(), false);
      return true;
    }

    /**
     * Uebernimmt einen Vorschlag und wechselt danach in die naechste leere Zeile.
     *
     * @param suggestion Vorschlag
     * @param splitCurrentInput ob der bisherige Eingabetext in die Folgezeile soll
     */
    private void acceptSuggestion(String suggestion, boolean splitCurrentInput) {
      String previousInput = normalizeKeyword(textField.getText());
      hideSuggestions();
      commitEdit(suggestion);
      javafx.application.Platform.runLater(() -> {
        normalizeRows();
        int row = items.size() - 1;
        if (splitCurrentInput && !previousInput.isBlank() && !previousInput.equals(suggestion)) {
          setPendingEditorText(row, previousInput);
        }
        table.getSelectionModel().clearAndSelect(row, colKeyword);
        table.scrollTo(row);
        table.edit(row, colKeyword);
      });
    }

    /**
     * Liefert die aktuellen Vorschlaege.
     *
     * @return Vorschlagsliste
     */
    private List<String> currentSuggestions() {
      String normalized = normalizeKeyword(textField.getText());
      if (normalized.isBlank()) {
        return List.of();
      }
      if (displayedSuggestions == null || displayedSuggestions.isEmpty()) {
        refreshSuggestions(normalized);
      }
      return displayedSuggestions == null ? List.of() : displayedSuggestions;
    }

    /**
     * Markiert einen Vorschlag optisch.
     *
     * @param index Vorschlagsindex
     */
    private void focusSuggestion(int index) {
      selectedSuggestionIndex = index;
      updateSuggestionFocusStyle();
    }

    /**
     * Liefert den markierten Vorschlagsindex.
     *
     * @return Index oder {@code -1}
     */
    private int focusedSuggestionIndex() {
      return selectedSuggestionIndex;
    }

    /**
     * Entfernt die optische Vorschlagsmarkierung.
     */
    private void clearSuggestionFocus() {
      selectedSuggestionIndex = -1;
      updateSuggestionFocusStyle();
    }

    /**
     * Aktualisiert die optische Vorschlagsmarkierung anhand des gespeicherten Index.
     */
    private void updateSuggestionFocusStyle() {
      if (suggestionMenu == null) {
        return;
      }
      for (int i = 0; i < suggestionMenu.getItems().size(); i++) {
        MenuItem item = suggestionMenu.getItems().get(i);
        item.getStyleClass().remove("focused");
        Node content = item instanceof CustomMenuItem customItem ? customItem.getContent() : null;
        if (content != null) {
          content.setStyle("");
        }
        if (i == selectedSuggestionIndex) {
          item.getStyleClass().add("focused");
          if (content != null) {
            content.setStyle("-fx-background-color: #dbeafe; -fx-text-fill: #111827; -fx-padding: 3 8 3 8;");
          }
        }
      }
    }

    /**
     * Blendet die Vorschlagsliste aus und setzt ihren Navigationszustand zurueck.
     */
    private void hideSuggestions() {
      selectedSuggestionIndex = -1;
      displayedSuggestions = List.of();
      splitNextSuggestionAction = false;
      if (suggestionMenu != null) {
        suggestionMenu.hide();
      }
    }

    /**
     * Merkt den Text vor, der beim Oeffnen einer Tabellenzeile direkt in deren
     * Editor eingetragen werden soll.
     *
     * @param rowIndex Zielzeile
     * @param text Text fuer das Eingabefeld
     */
    private void setPendingEditorText(int rowIndex, String text) {
      pendingEditorTextRow = rowIndex;
      pendingEditorText = normalizeKeyword(text);
    }

    /**
     * Liefert einen vorgemerkten Editortext fuer die angefragte Zeile.
     *
     * @param rowIndex angefragte Zeile
     * @param fallback Standardwert der Tabellenzeile
     * @return vorgemerkter Text oder Standardwert
     */
    private String editorTextForRow(int rowIndex, String fallback) {
      if (rowIndex != pendingEditorTextRow) {
        return fallback;
      }

      return pendingEditorText;
    }

    /**
     * Loescht den vorgemerkten Editortext, falls er fuer die angegebene Zeile
     * verbraucht wurde.
     *
     * @param rowIndex Zeile, deren Vormerkung geloescht werden soll
     */
    private void clearPendingEditorText(int rowIndex) {
      if (rowIndex != pendingEditorTextRow) {
        return;
      }

      pendingEditorTextRow = -1;
      pendingEditorText = "";
    }

    /**
     * Markiert das Eingabefeld bei aktiven Suchmodi.
     */
    private void updateEditorSearchModeStyle() {
      if (tokenAndSearchMode) {
        textField.setStyle("-fx-control-inner-background: #e7f6df;");
        return;
      }
      textField.setStyle(prefixSearchMode ? "-fx-control-inner-background: #fff0d6;" : "");
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
  public void setKeywordSuggestionProvider(Function<KeywordSuggestionRequest, List<String>> provider) {
    this.keywordSuggestionProvider = (provider == null) ? request -> List.of() : provider;
  }

  public void setOnKeywordsCommitted(Runnable r) {
    this.onKeywordsCommitted = (r == null) ? () -> {} : r;
  }
}
