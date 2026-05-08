package de.zettelkastenfx.bibliography.ui.field;

/**
 * Beschreibt reine Darstellungsoptionen einer bibliographischen Feldkomponente.
 *
 * @param showLabel ob ein Label angezeigt werden soll
 * @param showRemoveButton ob ein Löschbutton sichtbar sein soll
 * @param compact ob eine kompaktere Darstellung gewünscht ist
 * @param highlightSemanticMode ob identify-/necessary-Markierungen angezeigt werden sollen
 */
public record BibliographyFieldPresentation(
    boolean showLabel,
    boolean showRemoveButton,
    boolean compact,
    boolean highlightSemanticMode
) {

  /**
   * Darstellung für das Bibliographie-Popup.
   *
   * @return Präsentation mit Label
   */
  public static BibliographyFieldPresentation popup() {
    return new BibliographyFieldPresentation(true, false, false, true);
  }

  /**
   * Darstellung für bibliographische Angaben unter dem Zettel.
   *
   * @return Präsentation ohne Label
   */
  public static BibliographyFieldPresentation noteInline() {
    return new BibliographyFieldPresentation(false, false, true, false);
  }

  /**
   * Darstellung für den Servicebereich.
   *
   * @return Präsentation ohne Label
   */
  public static BibliographyFieldPresentation serviceArea() {
    return new BibliographyFieldPresentation(false, false, false, false);
  }

  /**
   * Erzeugt eine Kopie mit geänderter Löschbutton-Sichtbarkeit.
   *
   * @param value neue Sichtbarkeit
   * @return neue Präsentation
   */
  public BibliographyFieldPresentation withRemoveButton(boolean value) {
    return new BibliographyFieldPresentation(showLabel, value, compact, highlightSemanticMode);
  }
}