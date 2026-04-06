package de.zettelkastenfx.bibliography.model;

/**
 * Beschreibt ein bibliographisches Feld aus der Tabelle {@code bibtex_type}.
 *
 * @param id Datenbank-ID des Feldes
 * @param bibtexName technischer Feldname, z. B. {@code title} oder {@code year}
 * @param displayName Anzeigename des Feldes
 * @param datatype fachlicher Datentyp gemäß Metadaten
 * @param requires optionaler technischer Name eines abhängigen Feldes
 */
public record BibtexFieldDefinition(
    int id,
    String bibtexName,
    String displayName,
    String datatype,
    String requires
) {

  /**
   * Erzeugt eine Felddefinition mit bereinigten Textfeldern.
   *
   * @param id Datenbank-ID des Feldes
   * @param bibtexName technischer Feldname
   * @param displayName Anzeigename
   * @param datatype fachlicher Datentyp
   * @param requires optionales Abhängigkeitsfeld
   */
  public BibtexFieldDefinition {
    bibtexName = sanitize(bibtexName);
    displayName = sanitize(displayName);
    datatype = sanitize(datatype);
    requires = sanitize(requires);
  }

  /**
   * Prüft, ob dieses Feld eine definierte Abhängigkeit besitzt.
   *
   * @return {@code true}, wenn {@code requires} nicht leer ist
   */
  public boolean hasRequires() {
    return !requires.isBlank();
  }

  private static String sanitize(String value) {
    return value == null ? "" : value.trim();
  }
}