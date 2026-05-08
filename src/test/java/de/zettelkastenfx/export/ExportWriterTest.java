package de.zettelkastenfx.export;

import de.zettelkastenfx.export.model.ExportDocument;
import de.zettelkastenfx.export.model.ExportNote;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueft die erzeugten Exportdateien der Writer.
 */
class ExportWriterTest {

  @TempDir
  Path tempDir;

  /**
   * Schreibt DOCX und RTF und prueft die erzeugten Dateien.
   */
  @Test
  void writesDocxAndRtfFiles() throws Exception {
    ExportDocument document = new ExportDocument(
        List.of(new ExportNote(1, "Titel", "Text", List.of(), null)),
        List.of(),
        ExportSelection.defaultSelection()
    );
    Path docx = tempDir.resolve("zettel.docx");
    Path rtf = tempDir.resolve("zettel.rtf");

    new DocxExportWriter().write(document, docx);
    new RtfExportWriter().write(document, rtf);

    assertTrue(Files.size(docx) > 0);
    assertTrue(Files.readString(rtf).contains("1: Titel"));
  }
}
