package de.zettelkastenfx.bibliography.ui.support;

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.geometry.Side;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;

import java.util.List;

/**
 * Wiederverwendbare Feldansicht für bibliographische Eingaben.
 * Das Feld zeigt im Bearbeitungsmodus einen {@link TextField} und im Lesemodus
 * ein gebundenes {@link Label}. Zusätzlich verwaltet die Klasse ein Vorschlagsmenü.
 */
public class BibliographyFieldView extends StackPane {

  /** Eingabefeld des Views. */
  private final TextField editor = new TextField();

  /** Lesemodus-Label des Views. */
  private final Label label = new Label();

  /** Kontextmenü für Vorschläge. */
  public final ContextMenu suggestionMenu = new ContextMenu();

  /**
   * Erzeugt eine neue Feldansicht.
   *
   * @param prompt Prompttext für das Eingabefeld.
   * @param editOn Property, die den Bearbeitungsmodus steuert.
   */
  public BibliographyFieldView(String prompt, BooleanProperty editOn) {
    editor.setPromptText(prompt);

    label.textProperty().bind(editor.textProperty());
    label.setWrapText(false);
    label.setTextOverrun(OverrunStyle.ELLIPSIS);
    label.setMaxWidth(Double.MAX_VALUE);

    editor.visibleProperty().bind(editOn);
    editor.managedProperty().bind(editOn);

    var hasText = Bindings.createBooleanBinding(
        () -> !getText().isBlank(),
        editor.textProperty()
    );

    label.visibleProperty().bind(editOn.not().and(hasText));
    label.managedProperty().bind(editOn.not().and(hasText));

    editor.focusedProperty().addListener((obs, old, focused) -> {
      if (!focused) {
        suggestionMenu.hide();
      }
    });

    getChildren().addAll(editor, label);
  }

  /**
   * Liefert das Eingabefeld.
   *
   * @return Textfeld des Bearbeitungsmodus
   */
  public TextField getEditor() {
    return editor;
  }

  /**
   * Liefert das Lesemodus-Label.
   *
   * @return Label des Lesemodus
   */
  public Label getLabel() {
    return label;
  }

  /**
   * Liefert den normalisierten Text des Feldes.
   *
   * @return getrimmter Feldinhalt oder ein leerer String.
   */
  public String getText() {
    return editor.getText() == null ? "" : editor.getText().trim();
  }

  /**
   * Setzt den Text des Feldes.
   *
   * @param value neuer Feldwert; {@code null} wird als leerer String behandelt.
   */
  public void setText(String value) {
    editor.setText(value == null ? "" : value);
  }

  /**
   * Zeigt Vorschläge unterhalb des Eingabefeldes an.
   *
   * @param items anzuzeigende Menüeinträge.
   */
  public void showSuggestions(List<? extends MenuItem> items) {
    suggestionMenu.getItems().setAll(items);

    if (items.isEmpty()) {
      suggestionMenu.hide();
      return;
    }

    if (editor.getScene() == null
            || editor.getScene().getWindow() == null
            || !editor.getScene().getWindow().isShowing()) {
      return;
    }

    if (suggestionMenu.isShowing()) {
      suggestionMenu.hide();
    }

    suggestionMenu.show(editor, Side.BOTTOM, 0, 0);
  }

  /**
   * Blendet das Vorschlagsmenü aus.
   */
  public void hideSuggestions() {
    suggestionMenu.hide();
  }
}
