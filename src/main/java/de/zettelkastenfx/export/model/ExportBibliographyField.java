package de.zettelkastenfx.export.model;

/**
 * Beschreibt eine exportierbare bibliographische Feldzeile.
 *
 * @param name Anzeigename des Feldes
 * @param value formatierter Feldwert
 */
public record ExportBibliographyField(String name, String value) {

  /**
   * Erzeugt eine Feldzeile mit bereinigten Textwerten.
   *
   * @param name Anzeigename des Feldes
   * @param value formatierter Feldwert
   */
  public ExportBibliographyField {
    name = sanitize(name);
    value = sanitize(value);
  }

  private static String sanitize(String value) {
    return value == null ? "" : value.trim();
  }
}
