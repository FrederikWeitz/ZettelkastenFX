package de.zettelkastenfx.bibliography.model;

/**
 * Schlanke Außenansicht eines bibliographischen Eintrags.
 * <p>
 * Dieses Objekt ist für Aufrufer gedacht, die keinen vollständigen
 * {@link DynamicBibliographyEntry} benötigen, sondern nur die Information,
 * welcher Eintrag referenziert wird und wie er angezeigt werden soll.
 *
 * @param entryId Datenbank-ID des bibliographischen Eintrags
 * @param mediaTypeId Datenbank-ID des Medientyps
 * @param mediaTypeBibName technischer Medientypname
 * @param mediaTypeName Anzeigename des Medientyps
 * @param displayText kompakter Anzeigetext des Eintrags
 */
public record BibliographyReference(
    int entryId,
    Integer mediaTypeId,
    String mediaTypeBibName,
    String mediaTypeName,
    String displayText
) {

  /**
   * Erzeugt eine Referenz mit bereinigten Textfeldern.
   *
   * @param entryId Datenbank-ID des bibliographischen Eintrags
   * @param mediaTypeId Datenbank-ID des Medientyps
   * @param mediaTypeBibName technischer Medientypname
   * @param mediaTypeName Anzeigename des Medientyps
   * @param displayText kompakter Anzeigetext des Eintrags
   */
  public BibliographyReference {
    mediaTypeBibName = sanitize(mediaTypeBibName);
    mediaTypeName = sanitize(mediaTypeName);
    displayText = sanitize(displayText);
  }

  private static String sanitize(String value) {
    return value == null ? "" : value.trim();
  }
}