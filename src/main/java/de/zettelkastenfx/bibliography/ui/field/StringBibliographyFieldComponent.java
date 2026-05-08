package de.zettelkastenfx.bibliography.ui.field;

import de.zettelkastenfx.bibliography.model.BibValue;
import de.zettelkastenfx.bibliography.model.StringBibValue;

/**
 * Bibliographische Feldkomponente für String-Werte.
 */
public class StringBibliographyFieldComponent extends AbstractTextBibliographyFieldComponent {

  /**
   * Erzeugt eine String-Feldkomponente.
   *
   * @param request Erzeugungsanfrage
   */
  public StringBibliographyFieldComponent(BibliographyFieldRequest request) {
    super(request);
  }

  @Override
  protected BibValue readSingleValue() {
    String value = fieldView().getText();
    if (value.isBlank()) {
      return null;
    }
    return new StringBibValue(bibtexName(), value);
  }

  @Override
  protected void writeSingleValue(BibValue value) {
    if (value instanceof StringBibValue stringValue) {
      fieldView().setText(stringValue.value());
    }
  }
}