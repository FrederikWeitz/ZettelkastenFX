package de.zettelkastenfx.bibliography.model;

/**
 * Repräsentiert einen bibliographischen Integer-Wert.
 *
 * @param bibtexName technischer Feldname
 * @param value gespeicherter Integer-Wert
 */
public record IntegerBibValue(String bibtexName, Integer value) implements BibValue {

  /**
   * Erzeugt einen Integer-Wert mit bereinigtem Feldnamen.
   *
   * @param bibtexName technischer Feldname
   * @param value gespeicherter Integer-Wert
   */
  public IntegerBibValue {
    bibtexName = sanitizeName(bibtexName);
  }

  @Override
  public BibValueType valueType() {
    return BibValueType.INTEGER;
  }

  private static String sanitizeName(String name) {
    return name == null ? "" : name.trim();
  }
}
