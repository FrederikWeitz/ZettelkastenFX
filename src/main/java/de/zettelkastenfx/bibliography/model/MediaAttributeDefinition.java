package de.zettelkastenfx.bibliography.model;

/**
 * Verknüpft einen Medientyp mit einem bibliographischen Feld und dessen Modus
 * aus der Tabelle {@code media_attributes}.
 *
 * @param mediaTypeId Datenbank-ID des Medientyps
 * @param bibtexTypeId Datenbank-ID des BibTeX-Feldes
 * @param mode fachlicher Modus, z. B. {@code identify}, {@code necessary},
 *             {@code desired}, {@code mandatory} oder {@code forbidden}
 * @param fieldDefinition aufgelöste Felddefinition
 */
public record MediaAttributeDefinition(
    int mediaTypeId,
    int bibtexTypeId,
    String mode,
    BibtexFieldDefinition fieldDefinition
) {

  /**
   * Erzeugt eine Medienattribut-Definition mit bereinigtem Modus.
   *
   * @param mediaTypeId Datenbank-ID des Medientyps
   * @param bibtexTypeId Datenbank-ID des BibTeX-Feldes
   * @param mode fachlicher Modus
   * @param fieldDefinition aufgelöste Felddefinition
   */
  public MediaAttributeDefinition {
    mode = sanitize(mode);
  }

  /**
   * Prüft, ob das Attribut zur Identifikation eines Mediums dient.
   *
   * @return {@code true}, wenn der Modus {@code identify} ist
   */
  public boolean isIdentify() {
    return "identify".equalsIgnoreCase(mode);
  }

  /**
   * Prüft, ob das Attribut fachlich notwendig ist.
   *
   * @return {@code true}, wenn der Modus {@code necessary} ist
   */
  public boolean isNecessary() {
    return "necessary".equalsIgnoreCase(mode);
  }

  /**
   * Prüft, ob das Attribut erwünscht ist.
   *
   * @return {@code true}, wenn der Modus {@code desired} ist
   */
  public boolean isDesired() {
    return "desired".equalsIgnoreCase(mode);
  }

  /**
   * Prüft, ob das Attribut verpflichtend ist.
   *
   * @return {@code true}, wenn der Modus {@code mandatory} ist
   */
  public boolean isMandatory() {
    return "mandatory".equalsIgnoreCase(mode);
  }

  /**
   * Prüft, ob das Attribut verboten ist.
   *
   * @return {@code true}, wenn der Modus {@code forbidden} ist
   */
  public boolean isForbidden() {
    return "forbidden".equalsIgnoreCase(mode);
  }

  private static String sanitize(String value) {
    return value == null ? "" : value.trim();
  }
}