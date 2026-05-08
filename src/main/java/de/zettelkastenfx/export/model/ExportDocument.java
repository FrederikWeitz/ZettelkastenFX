package de.zettelkastenfx.export.model;

import de.zettelkastenfx.export.ExportSelection;

import java.util.List;

/**
 * Neutrales Dokumentmodell zwischen Exportservice und Zielformat-Writer.
 *
 * @param notes exportierte Zettel
 * @param endBibliography deduplizierte Endbibliographie
 * @param selection Exportauswahl
 */
public record ExportDocument(
    List<ExportNote> notes,
    List<ExportBibliographyEntry> endBibliography,
    ExportSelection selection
) {

  /**
   * Erzeugt ein Exportdokument mit defensiven Listen.
   *
   * @param notes exportierte Zettel
   * @param endBibliography deduplizierte Endbibliographie
   * @param selection Exportauswahl
   */
  public ExportDocument {
    notes = notes == null ? List.of() : List.copyOf(notes);
    endBibliography = endBibliography == null ? List.of() : List.copyOf(endBibliography);
  }
}
