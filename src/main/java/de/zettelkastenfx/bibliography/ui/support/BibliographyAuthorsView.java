package de.zettelkastenfx.bibliography.ui.support;

import de.zettelkastenfx.bibliography.model.Author;
import de.zettelkastenfx.bibliography.ui.lookup.AuthorSuggestion;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.geometry.Side;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Bearbeitungs- und Anzeigeansicht für Autorenlisten innerhalb der Bibliographie.
 * Die Klasse kapselt Zeilenverwaltung, Autocomplete-Anzeige und die Umwandlung
 * zwischen UI-Zustand und {@link Author}-Modellen.
 */
public class BibliographyAuthorsView extends StackPane {

  private final String ownerMediaTypeBibName;
  private final Function<AuthorRow, List<AuthorSuggestion>> suggestionProvider;
  private final Consumer<String> authorsEditedHandler;
  private final Runnable refreshSelectionHandler;
  private final Supplier<Boolean> suppressLookupCallbackSupplier;
  private final BiConsumer<String, Integer> selectedEntryUpdater;

  private final VBox editorRoot = new VBox(4);
  private final VBox rowsBox = new VBox(4);
  private final ScrollPane scrollPane = new ScrollPane(rowsBox);
  private final Label label = new Label();
  private final List<AuthorRow> rows = new ArrayList<>();
  private boolean suppressInternalEvents;

  /**
   * Erzeugt eine neue Autorenansicht.
   *
   * @param ownerMediaTypeBibName technischer Medientypname des Formulars
   * @param editOn Property für den Bearbeitungsmodus
   * @param suggestionProvider Callback zum Laden von Autorvorschlägen
   * @param authorsEditedHandler Callback bei Abschluss einer Autorenbearbeitung
   * @param refreshSelectionHandler Callback für reine Anzeigeaktualisierungen
   * @param suppressLookupCallbackSupplier liefert, ob Lookup-Callbacks derzeit unterdrückt sind
   * @param selectedEntryUpdater Callback zum Zurücksetzen oder Setzen einer selektierten Eintrags-ID
   */
  public BibliographyAuthorsView(
      String ownerMediaTypeBibName,
      BooleanProperty editOn,
      Function<AuthorRow, List<AuthorSuggestion>> suggestionProvider,
      Consumer<String> authorsEditedHandler,
      Runnable refreshSelectionHandler,
      Supplier<Boolean> suppressLookupCallbackSupplier,
      BiConsumer<String, Integer> selectedEntryUpdater
  ) {
    this.ownerMediaTypeBibName = normalizeMediaTypeBibName(ownerMediaTypeBibName);
    this.suggestionProvider = suggestionProvider;
    this.authorsEditedHandler = authorsEditedHandler;
    this.refreshSelectionHandler = refreshSelectionHandler;
    this.suppressLookupCallbackSupplier = suppressLookupCallbackSupplier;
    this.selectedEntryUpdater = selectedEntryUpdater;

    GridPane header = buildHeader();

    scrollPane.setFitToWidth(true);
    scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    scrollPane.setPrefViewportHeight(4 * 30.0 + 12);
    scrollPane.setMinHeight(4 * 30.0 + 12);
    scrollPane.setMaxHeight(4 * 30.0 + 12);

    editorRoot.getChildren().addAll(header, scrollPane);

    addEmptyRow();
    refreshReadOnlyText();

    label.setWrapText(false);
    label.setTextOverrun(OverrunStyle.ELLIPSIS);
    label.setMaxWidth(Double.MAX_VALUE);

    editorRoot.visibleProperty().bind(editOn);
    editorRoot.managedProperty().bind(editOn);

    var hasText = Bindings.createBooleanBinding(
        () -> !rawText().isBlank(),
        label.textProperty()
    );
    label.visibleProperty().bind(editOn.not().and(hasText));
    label.managedProperty().bind(editOn.not().and(hasText));

    getChildren().addAll(editorRoot, label);
  }

  /**
   * Erzeugt aus den sichtbaren Zeilen eine Autorenliste.
   *
   * @return normalisierte Autorenliste ohne leere Zeilen.
   */
  public List<Author> toAuthors() {
    var out = new ArrayList<Author>();
    int pos = 1;

    for (AuthorRow row : rows) {
      if (row.isBlank()) {
        continue;
      }

      var author = new Author();
      author.setPosition(pos++);
      author.setLastName(row.getLastName());
      author.setFirstName(row.getFirstName());
      out.add(author);
    }

    return out;
  }

  /**
   * Schreibt eine Autorenliste in die UI.
   *
   * @param authors Autoren, die angezeigt werden sollen.
   */
  public void fromAuthors(List<Author> authors) {
    suppressInternalEvents = true;
    try {
      rows.clear();
      rowsBox.getChildren().clear();

      if (authors != null) {
        for (Author author : authors) {
          AuthorRow row = new AuthorRow();
          row.lastNameField.setText(author.getLastName() == null ? "" : author.getLastName());
          row.firstNameField.setText(author.getFirstName() == null ? "" : author.getFirstName());
          addRow(row);
        }
      }

      ensureTrailingEmptyRow();
      refreshPositions();
      refreshReadOnlyText();
    } finally {
      suppressInternalEvents = false;
    }
  }

  /**
   * Liefert die aktuelle Lesetext-Darstellung.
   *
   * @return formatierter Autorenstring.
   */
  public String rawText() {
    return label.getText() == null ? "" : label.getText().trim();
  }

  private GridPane buildHeader() {
    GridPane header = new GridPane();
    header.getStyleClass().add("biblio-authors-header");

    ColumnConstraints c0 = new ColumnConstraints();
    c0.setMinWidth(42);
    c0.setPrefWidth(42);
    c0.setMaxWidth(42);

    ColumnConstraints c1 = new ColumnConstraints();
    c1.setHgrow(Priority.ALWAYS);

    ColumnConstraints c2 = new ColumnConstraints();
    c2.setHgrow(Priority.ALWAYS);

    header.getColumnConstraints().addAll(c0, c1, c2);

    Label posHead = new Label("#");
    Label lastHead = new Label("Nachname");
    Label firstHead = new Label("Vorname");

    header.add(posHead, 0, 0);
    header.add(lastHead, 1, 0);
    header.add(firstHead, 2, 0);
    return header;
  }

  private void addEmptyRow() {
    addRow(new AuthorRow());
  }

  private void addRow(AuthorRow row) {
    rows.add(row);
    rowsBox.getChildren().add(row);
    hookRow(row);
    refreshPositions();
  }

  private void hookRow(AuthorRow row) {
    row.lastNameField.textProperty().addListener((obs, old, value) -> {
      refreshReadOnlyText();

      if (isLookupSuppressed()) {
        return;
      }

      showAuthorSuggestions(row);
    });

    row.firstNameField.textProperty().addListener((obs, old, value) -> {
      refreshReadOnlyText();
    });

    row.lastNameField.focusedProperty().addListener((obs, old, focused) -> {
      if (!focused) {
        row.hideSuggestions();
        handleRowCommit(row);
      }
    });

    row.firstNameField.focusedProperty().addListener((obs, old, focused) -> {
      if (!focused) {
        handleRowCommit(row);
      }
    });

    row.lastNameField.setOnKeyPressed(event -> {
      if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.TAB) {
        row.hideSuggestions();
        handleRowCommit(row);
      } else if (event.getCode() == KeyCode.ESCAPE) {
        row.hideSuggestions();
      }
    });

    row.firstNameField.setOnKeyPressed(event -> {
      if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.TAB) {
        handleRowCommit(row);
      }
    });
  }

  private void showAuthorSuggestions(AuthorRow row) {
    List<AuthorSuggestion> hits = suggestionProvider.apply(row);
    if (hits == null || hits.isEmpty()) {
      row.hideSuggestions();
      return;
    }

    List<CustomMenuItem> items = new ArrayList<>();
    for (AuthorSuggestion hit : hits) {
      Label option = new Label(hit.displayText());
      CustomMenuItem item = new CustomMenuItem(option, true);
      item.setOnAction(e -> applySuggestion(row, hit));
      items.add(item);
    }

    row.showSuggestions(items);
  }

  private void applySuggestion(AuthorRow row, AuthorSuggestion suggestion) {
    suppressInternalEvents = true;
    try {
      row.lastNameField.setText(suggestion.lastName());
      row.firstNameField.setText(suggestion.firstName());
    } finally {
      suppressInternalEvents = false;
    }

    refreshReadOnlyText();
    refreshSelectionHandler.run();
    row.hideSuggestions();
    handleRowCommit(row);
  }

  private void handleRowCommit(AuthorRow row) {
    if (suppressInternalEvents) {
      return;
    }

    if (!rows.isEmpty() && row == rows.get(rows.size() - 1) && row.hasAnyText()) {
      addEmptyRow();
    }

    refreshPositions();
    refreshReadOnlyText();
    authorsEditedHandler.accept(ownerMediaTypeBibName);
  }

  private void ensureTrailingEmptyRow() {
    if (rows.isEmpty()) {
      addEmptyRow();
      return;
    }

    AuthorRow last = rows.get(rows.size() - 1);
    if (last.hasAnyText()) {
      addEmptyRow();
    }
  }

  private void refreshPositions() {
    for (int i = 0; i < rows.size(); i++) {
      rows.get(i).setPosition(i + 1);
    }
  }

  private void refreshReadOnlyText() {
    StringBuilder sb = new StringBuilder();

    for (AuthorRow row : rows) {
      String display = row.toDisplayText();
      if (display.isBlank()) {
        continue;
      }

      if (sb.length() > 0) {
        sb.append("; ");
      }
      sb.append(display);
    }

    label.setText(sb.toString());
  }

  private boolean isLookupSuppressed() {
    return suppressInternalEvents || Boolean.TRUE.equals(suppressLookupCallbackSupplier.get());
  }

  /**
   * Normalisiert einen technischen Medientypnamen.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @return normalisierter Medientypname oder leerer String
   */
  private String normalizeMediaTypeBibName(String mediaTypeBibName) {
    return mediaTypeBibName == null ? "" : mediaTypeBibName.trim().toLowerCase();
  }

  /**
   * Einzelne Autorenzeile der Autorenansicht.
   */
  public static final class AuthorRow extends GridPane {

    final Label posLabel = new Label();
    final TextField lastNameField = new TextField();
    final TextField firstNameField = new TextField();
    final ContextMenu suggestionMenu = new ContextMenu();

    /**
     * Erzeugt eine leere Autorenzeile.
     */
    public AuthorRow() {
      getStyleClass().add("biblio-author-row");
      setHgap(8);

      ColumnConstraints c0 = new ColumnConstraints();
      c0.setMinWidth(42);
      c0.setPrefWidth(42);
      c0.setMaxWidth(42);

      ColumnConstraints c1 = new ColumnConstraints();
      c1.setHgrow(Priority.ALWAYS);

      ColumnConstraints c2 = new ColumnConstraints();
      c2.setHgrow(Priority.ALWAYS);

      getColumnConstraints().addAll(c0, c1, c2);

      lastNameField.setPromptText("Nachname");
      firstNameField.setPromptText("Vorname");

      lastNameField.focusedProperty().addListener((obs, old, focused) -> {
        if (!focused) {
          suggestionMenu.hide();
        }
      });

      add(posLabel, 0, 0);
      add(lastNameField, 1, 0);
      add(firstNameField, 2, 0);
    }

    /**
     * Setzt die sichtbare Positionsnummer.
     *
     * @param pos anzuzeigende Position.
     */
    public void setPosition(int pos) {
      posLabel.setText(Integer.toString(pos));
    }

    /**
     * Liefert den getrimmten Nachnamen.
     *
     * @return Nachname oder leerer String.
     */
    public String getLastName() {
      return lastNameField.getText() == null ? "" : lastNameField.getText().trim();
    }

    /**
     * Liefert den getrimmten Vornamen.
     *
     * @return Vorname oder leerer String.
     */
    public String getFirstName() {
      return firstNameField.getText() == null ? "" : firstNameField.getText().trim();
    }

    /**
     * Prüft, ob mindestens eines der beiden Felder belegt ist.
     *
     * @return {@code true}, wenn Vor- oder Nachname vorhanden ist.
     */
    public boolean hasAnyText() {
      return !getLastName().isBlank() || !getFirstName().isBlank();
    }

    /**
     * Prüft, ob beide Felder leer sind.
     *
     * @return {@code true}, wenn die Zeile leer ist.
     */
    public boolean isBlank() {
      return !hasAnyText();
    }

    /**
     * Erzeugt eine kompakte Anzeigerepräsentation der Zeile.
     *
     * @return formatierter Name oder leerer String.
     */
    public String toDisplayText() {
      String lastName = getLastName();
      String firstName = getFirstName();

      if (lastName.isBlank() && firstName.isBlank()) {
        return "";
      }
      if (lastName.isBlank()) {
        return firstName;
      }
      if (firstName.isBlank()) {
        return lastName;
      }
      return lastName + ", " + firstName;
    }

    /**
     * Zeigt ein Vorschlagsmenü unterhalb des Nachnamenfeldes an.
     *
     * @param items anzuzeigende Menüelemente.
     */
    public void showSuggestions(List<? extends MenuItem> items) {
      suggestionMenu.getItems().setAll(items);

      if (items.isEmpty()) {
        suggestionMenu.hide();
        return;
      }

      if (lastNameField.getScene() == null
              || lastNameField.getScene().getWindow() == null
              || !lastNameField.getScene().getWindow().isShowing()) {
        return;
      }

      if (suggestionMenu.isShowing()) {
        suggestionMenu.hide();
      }

      suggestionMenu.show(lastNameField, Side.BOTTOM, 0, 0);
    }

    /**
     * Blendet das Vorschlagsmenü aus.
     */
    public void hideSuggestions() {
      suggestionMenu.hide();
    }
  }
}
