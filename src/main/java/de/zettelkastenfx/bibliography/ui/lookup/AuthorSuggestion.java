package de.zettelkastenfx.bibliography.ui.lookup;

/**
 * Vorschlag für einen Autor aus dem Bibliographie-Lookup.
 *
 * @param authorId ID des Autors
 * @param firstName Vorname
 * @param lastName Nachname
 */
public record AuthorSuggestion(
    Integer authorId,
    String firstName,
    String lastName
) {

  /**
   * Liefert einen kompakten Anzeigetext für den Vorschlag.
   *
   * @return kombinierter Anzeigename aus Vor- und Nachname
   */
  /**
   * Erzeugt den Anzeige-Text für einen Autorvorschlag.
   *
   * @return formatierter Name.
   */
  public String displayText() {
    if ((lastName == null || lastName.isBlank()) && (firstName == null || firstName.isBlank())) {
      return "";
    }
    if (firstName == null || firstName.isBlank()) {
      return lastName == null ? "" : lastName;
    }
    if (lastName == null || lastName.isBlank()) {
      return firstName;
    }
    return lastName + ", " + firstName;
  }
}