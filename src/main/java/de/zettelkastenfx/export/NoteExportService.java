package de.zettelkastenfx.export;

/**
 * Oeffentliche Schnittstelle fuer den modularen Zettelexport.
 */
public interface NoteExportService {

  /**
   * Fuehrt einen Exportlauf aus.
   *
   * @param request Exportanfrage
   * @return Ergebnis mit erzeugten Dateien
   */
  ExportResult export(ExportRequest request);
}
