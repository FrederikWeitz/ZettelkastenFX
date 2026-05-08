package de.zettelkastenfx.export;

import java.nio.file.Path;
import java.util.List;

/**
 * Beschreibt das Ergebnis eines Exportlaufs.
 *
 * @param writtenFiles erzeugte Dateien
 */
public record ExportResult(List<Path> writtenFiles) {

  /**
   * Erzeugt ein Ergebnis mit defensiver Kopie der Dateiliste.
   *
   * @param writtenFiles erzeugte Dateien
   */
  public ExportResult {
    writtenFiles = writtenFiles == null ? List.of() : List.copyOf(writtenFiles);
  }
}
