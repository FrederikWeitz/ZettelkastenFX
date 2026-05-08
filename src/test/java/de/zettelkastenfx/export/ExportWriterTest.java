package de.zettelkastenfx.export;

import de.zettelkastenfx.export.model.ExportDocument;
import de.zettelkastenfx.export.model.ExportNote;
import de.zettelkastenfx.export.model.ExportParagraph;
import de.zettelkastenfx.export.model.ExportParagraphStyle;
import de.zettelkastenfx.export.model.ExportTextRun;
import de.zettelkastenfx.export.model.ExportTextStyle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;

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

  /**
   * Prueft, dass DOCX Zettelueberschriften als Heading3 und Inline-Stile als Run-Properties schreibt.
   */
  @Test
  void writesDocxHeading3AndInlineFormatting() throws Exception {
    ExportDocument document = formattedDocument();
    Path docx = tempDir.resolve("formatiert.docx");

    new DocxExportWriter().write(document, docx);

    String documentXml = readDocxEntry(docx, "word/document.xml");
    assertTrue(documentXml.contains("<w:pStyle w:val=\"Heading3\"/>"));
    assertTrue(documentXml.contains("<w:b/>"));
    assertTrue(documentXml.contains("<w:i/>"));
    assertTrue(documentXml.contains("<w:u w:val=\"single\"/>"));
    assertTrue(documentXml.contains("<w:strike/>"));
    assertTrue(documentXml.contains("<w:jc w:val=\"center\"/>"));
    assertTrue(documentXml.contains("<w:ind w:left=\"300\"/>"));
    assertTrue(documentXml.contains("<w:color w:val=\"1B5FBF\"/>"));
    assertTrue(documentXml.contains("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"FDD835\"/>"));
  }

  /**
   * Prueft, dass RTF Ueberschrift 3 und Inline-Stile ausgibt.
   */
  @Test
  void writesRtfHeading3AndInlineFormatting() throws Exception {
    ExportDocument document = formattedDocument();
    Path rtf = tempDir.resolve("formatiert.rtf");

    new RtfExportWriter().write(document, rtf);

    String content = Files.readString(rtf);
    assertTrue(content.contains("\\s3"));
    assertTrue(content.contains("\\b "));
    assertTrue(content.contains("\\i "));
    assertTrue(content.contains("\\ul "));
    assertTrue(content.contains("\\strike "));
    assertTrue(content.contains("\\qc "));
    assertTrue(content.contains("\\li300 "));
    assertTrue(content.contains("\\cf"));
    assertTrue(content.contains("\\highlight"));
  }

  /**
   * Prueft, dass HTML als eigenes Exportformat geschrieben wird.
   */
  @Test
  void writesHtmlFileWithInlineFormatting() throws Exception {
    ExportDocument document = formattedDocument();
    Path html = tempDir.resolve("formatiert.html");

    new HtmlExportWriter().write(document, html);

    String content = Files.readString(html);
    assertTrue(content.contains("<h3>1: Titel</h3>"));
    assertTrue(content.contains("font-weight:bold"));
    assertTrue(content.contains("font-style:italic"));
    assertTrue(content.contains("text-decoration: underline line-through;"));
    assertTrue(content.contains("text-align:center;"));
    assertTrue(content.contains("padding-left:20px;"));
    assertTrue(content.contains("color:#1b5fbf;"));
    assertTrue(content.contains("background-color:#fdd835;"));
  }

  /**
   * Erstellt ein Exportdokument mit mehreren Inline-Formatierungen.
   *
   * @return formatiertes Exportdokument
   */
  private ExportDocument formattedDocument() {
    ExportParagraph paragraph = new ExportParagraph(List.of(
        new ExportTextRun("Fett", new ExportTextStyle(true, false, false, false)),
        new ExportTextRun(" kursiv", new ExportTextStyle(false, true, false, false)),
        new ExportTextRun(
            " Linie",
            new ExportTextStyle(false, false, true, true, "Arial", "#1b5fbf", "#fdd835")
        )
    ), new ExportParagraphStyle("center", 20, 16, true));
    return new ExportDocument(
        List.of(new ExportNote(1, "Titel", paragraph.plainText(), List.of(paragraph), List.of(), null)),
        List.of(),
        ExportSelection.defaultSelection()
    );
  }

  /**
   * Liest einen XML-Eintrag aus dem DOCX-ZIP.
   *
   * @param docx DOCX-Datei
   * @param entryName Eintragsname
   * @return Eintragsinhalt
   * @throws IOException bei Lesefehlern
   */
  private String readDocxEntry(Path docx, String entryName) throws IOException {
    try (ZipFile zip = new ZipFile(docx.toFile())) {
      try (var input = zip.getInputStream(zip.getEntry(entryName))) {
        return new String(input.readAllBytes());
      }
    }
  }
}
