package de.zettelkastenfx.bibliography.model;

/**
 * Repräsentiert eine einzelne Person innerhalb eines bibliographischen
 * Personenfeldes.
 *
 * @param id Personen-ID aus der Datenbank
 * @param firstName Vorname
 * @param lastName Nachname
 * @param position Reihenfolge innerhalb des Feldes
 */
public record PersonRef(Integer id, String firstName, String lastName, Integer position) {

  /**
   * Erzeugt eine Person mit bereinigten Namensfeldern.
   *
   * @param id Personen-ID aus der Datenbank
   * @param firstName Vorname
   * @param lastName Nachname
   * @param position Reihenfolge innerhalb des Feldes
   */
  public PersonRef {
    firstName = sanitize(firstName);
    lastName = sanitize(lastName);
  }

  /**
   * Liefert den zusammengesetzten Anzeigenamen.
   *
   * @return Vorname und Nachname als Anzeigetext
   */
  public String displayName() {
    return (firstName + " " + lastName).trim();
  }

  private static String sanitize(String value) {
    return value == null ? "" : value.trim();
  }
}