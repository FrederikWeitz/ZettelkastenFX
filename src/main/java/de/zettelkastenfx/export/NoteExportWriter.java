package de.zettelkastenfx.export;

import de.zettelkastenfx.export.model.ExportDocument;

import java.nio.file.Path;

/**
 * Schreibt ein neutrales Exportdokument in ein konkretes Zielformat.
 */
public interface NoteExportWriter {

  /**
   * Schreibt das Dokument an den Zielpfad.
   *
   * @param document Exportdokument
   * @param outputPath Zielpfad
   */
  void write(ExportDocument document, Path outputPath);
}
