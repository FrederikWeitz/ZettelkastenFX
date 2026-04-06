package de.zettelkastenfx.bibliography.model;

/**
 * Beschreibt einen Medientyp aus der Tabelle {@code media_types}.
 *
 * @param id Datenbank-ID des Medientyps
 * @param name Anzeigename des Medientyps
 * @param bibName technischer BibTeX-Name des Medientyps
 * @param directLink Kennzeichen, ob der Medientyp direkt mit einem Zettel
 *                   verknüpft werden darf
 */
public record MediaTypeDefinition(
    int id,
    String name,
    String bibName,
    boolean directLink
) {

  /**
   * Erzeugt eine Medientyp-Definition mit bereinigten Textfeldern.
   *
   * @param id Datenbank-ID des Medientyps
   * @param name Anzeigename des Medientyps
   * @param bibName technischer BibTeX-Name des Medientyps
   * @param directLink Kennzeichen für direkte Verknüpfbarkeit
   */
  public MediaTypeDefinition {
    name = sanitize(name);
    bibName = sanitize(bibName);
  }

  private static String sanitize(String value) {
    return value == null ? "" : value.trim();
  }
}