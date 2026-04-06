package de.zettelkastenfx.bibliography.model;

/**
 * Repräsentiert einen bibliographischen String-Wert.
 *
 * @param bibtexName technischer Feldname
 * @param value gespeicherter String-Wert
 */
public record StringBibValue(String bibtexName, String value) implements BibValue {

  /**
   * Erzeugt einen String-Wert mit bereinigtem Feldnamen und Wert.
   *
   * @param bibtexName technischer Feldname
   * @param value gespeicherter String-Wert
   */
  public StringBibValue {
    bibtexName = sanitizeName(bibtexName);
    value = sanitizeValue(value);
  }

  @Override
  public BibValueType valueType() {
    return BibValueType.STRING;
  }

  private static String sanitizeName(String name) {
    return name == null ? "" : name.trim();
  }

  private static String sanitizeValue(String value) {
    return value == null ? "" : value.trim();
  }
}
