package de.zettelkastenfx.export.model;

import java.util.List;

/**
 * Beschreibt einen fuer den Export aufbereiteten bibliographischen Eintrag.
 *
 * @param entryId Datenbank-ID
 * @param fields auszugebende Feldzeilen
 */
public record ExportBibliographyEntry(int entryId, List<ExportBibliographyField> fields) {

  /**
   * Erzeugt einen Exporteintrag mit defensiver Feldkopie.
   *
   * @param entryId Datenbank-ID
   * @param fields auszugebende Feldzeilen
   */
  public ExportBibliographyEntry {
    fields = fields == null ? List.of() : List.copyOf(fields);
  }

  /**
   * Prueft, ob der Eintrag auszugebende Felder besitzt.
   *
   * @return {@code true}, wenn mindestens ein Feld vorhanden ist
   */
  public boolean hasFields() {
    return !fields.isEmpty();
  }
}
