package de.zettelkastenfx.bibliography.ui.field;

import de.zettelkastenfx.bibliography.model.BibValue;
import de.zettelkastenfx.bibliography.model.IntegerBibValue;
import javafx.scene.control.TextFormatter;

/**
 * Bibliographische Feldkomponente für Integer-, Jahres- und Monatswerte.
 */
public class IntegerBibliographyFieldComponent extends AbstractTextBibliographyFieldComponent {

  /**
   * Erzeugt eine Integer-Feldkomponente.
   *
   * @param request Erzeugungsanfrage
   */
  public IntegerBibliographyFieldComponent(BibliographyFieldRequest request) {
    super(request);
    enforceIntegerInput();
  }

  @Override
  protected BibValue readSingleValue() {
    String rawValue = fieldView().getText();

    if (rawValue.isBlank()) {
      return null;
    }

    Integer parsed = parseInteger(rawValue);
    return new IntegerBibValue(bibtexName(), parsed);
  }

  @Override
  protected void writeSingleValue(BibValue value) {
    if (value instanceof IntegerBibValue integerValue) {
      fieldView().setText(integerValue.value() == null ? "" : String.valueOf(integerValue.value()));
    }
  }

  /**
   * Begrenzt die Eingabe auf Ziffern und leeren Feldinhalt.
   */
  private void enforceIntegerInput() {
    fieldView().getEditor().setTextFormatter(new TextFormatter<String>(change -> {
      String nextText = change.getControlNewText();
      if (nextText.isEmpty() || nextText.matches("\\d+")) {
        return change;
      }
      return null;
    }));
  }

  /**
   * Parst einen Integer-Wert tolerant.
   *
   * @param rawValue Rohwert
   * @return Integer oder {@code null}
   */
  private Integer parseInteger(String rawValue) {
    String text = rawValue == null ? "" : rawValue.trim();
    if (text.isBlank()) {
      return null;
    }

    try {
      return Integer.parseInt(text);
    } catch (NumberFormatException ex) {
      return null;
    }
  }
}