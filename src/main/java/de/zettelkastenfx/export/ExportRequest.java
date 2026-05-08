package de.zettelkastenfx.export;

import java.nio.file.Path;
import java.util.List;

/**
 * Buendelt alle Angaben fuer einen Exportlauf.
 *
 * @param noteIds optionale Zettelauswahl; leer bedeutet alle Zettel
 * @param documentType Zielformat
 * @param selection auszugebende Zettelbestandteile
 * @param outputPath Zielpfad
 * @param splitAfterNotes Anzahl Zettel je Dokument; Werte kleiner gleich 0 deaktivieren die Aufteilung
 */
public record ExportRequest(
    List<Integer> noteIds,
    ExportDocumentType documentType,
    ExportSelection selection,
    Path outputPath,
    int splitAfterNotes
) {

  /**
   * Erzeugt eine Exportanfrage mit defensiven Standardwerten.
   *
   * @param noteIds optionale Zettelauswahl
   * @param documentType Zielformat
   * @param selection auszugebende Zettelbestandteile
   * @param outputPath Zielpfad
   * @param splitAfterNotes Anzahl Zettel je Dokument
   */
  public ExportRequest {
    noteIds = noteIds == null ? List.of() : List.copyOf(noteIds);
    documentType = documentType == null ? ExportDocumentType.DOCX : documentType;
    selection = selection == null ? ExportSelection.defaultSelection() : selection;
    if (outputPath == null) {
      throw new IllegalArgumentException("outputPath darf nicht null sein.");
    }
  }
}
