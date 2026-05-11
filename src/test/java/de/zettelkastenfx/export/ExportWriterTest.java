package de.zettelkastenfx.export;

import de.zettelkastenfx.export.model.ExportDocument;
import de.zettelkastenfx.export.model.ExportNote;
import de.zettelkastenfx.export.model.ExportParagraph;
import de.zettelkastenfx.export.model.ExportParagraphStyle;
import de.zettelkastenfx.export.model.ExportTextRun;
import de.zettelkastenfx.export.model.ExportTextStyle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    assertTrue(documentXml.contains("<w:spacing w:before=\"45\"/>"));
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
    assertTrue(content.contains("\\sb45 "));
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
    assertTrue(content.contains("padding-top:3px;"));
    assertTrue(content.contains("color:#1b5fbf;"));
    assertTrue(content.contains("background-color:#fdd835;"));
  }

  /**
   * Prueft, dass Bildabsaetze in HTML als lokale Bildreferenz ausgegeben werden.
   */
  @Test
  void writesHtmlImageParagraphWithLocalPath() throws Exception {
    Path image = createPng();
    ExportDocument document = imageDocument(image);
    Path html = tempDir.resolve("bild.html");

    new HtmlExportWriter().write(document, html);

    String content = Files.readString(html);
    assertTrue(content.contains("<img src=\"" + image.toUri()));
    assertTrue(content.contains("max-width:100%;height:auto;"));
  }

  /**
   * Prueft, dass DOCX Bildabsaetze als eingebettete Medien schreibt.
   */
  @Test
  void writesDocxImageParagraphAsEmbeddedMedia() throws Exception {
    Path image = createPng();
    ExportDocument document = imageDocument(image);
    Path docx = tempDir.resolve("bild.docx");

    new DocxExportWriter().write(document, docx);

    try (ZipFile zip = new ZipFile(docx.toFile())) {
      assertNotNull(zip.getEntry("word/media/image1.png"));
    }
    String documentXml = readDocxEntry(docx, "word/document.xml");
    String rels = readDocxEntry(docx, "word/_rels/document.xml.rels");
    assertTrue(documentXml.contains("<w:drawing>"));
    assertTrue(documentXml.contains("r:embed=\"rIdImage1\""));
    assertTrue(rels.contains("Target=\"media/image1.png\""));
  }

  /**
   * Prueft, dass RTF Bildabsaetze als Picture-Daten schreibt.
   */
  @Test
  void writesRtfImageParagraphAsPicture() throws Exception {
    Path image = createPng();
    ExportDocument document = imageDocument(image);
    Path rtf = tempDir.resolve("bild.rtf");

    new RtfExportWriter().write(document, rtf);

    String content = Files.readString(rtf);
    assertTrue(content.contains("\\pict\\pngblip"));
    assertTrue(content.contains("\\picw2"));
    assertTrue(content.contains("\\pich2"));
  }

  /**
   * Prueft, dass Tabellenabsaetze in HTML als Tabelle ausgegeben werden.
   */
  @Test
  void writesHtmlTableParagraphAsTable() throws Exception {
    ExportDocument document = tableDocument();
    Path html = tempDir.resolve("tabelle.html");

    new HtmlExportWriter().write(document, html);

    String content = Files.readString(html);
    assertTrue(content.contains("<table"));
    assertTrue(content.contains("border:1px solid #000"));
    assertTrue(content.contains("<tr><td"));
    assertTrue(content.contains("Alpha"));
  }

  /**
   * Prueft, dass Tabellenabsaetze in DOCX als WordprocessingML-Tabelle ausgegeben werden.
   */
  @Test
  void writesDocxTableParagraphAsTable() throws Exception {
    ExportDocument document = tableDocument();
    Path docx = tempDir.resolve("tabelle.docx");

    new DocxExportWriter().write(document, docx);

    String documentXml = readDocxEntry(docx, "word/document.xml");
    assertTrue(documentXml.contains("<w:tbl>"));
    assertTrue(documentXml.contains("<w:tblBorders>"));
    assertTrue(documentXml.contains("<w:tc>"));
    assertTrue(documentXml.contains("Alpha"));
  }

  /**
   * Prueft, dass Tabellenabsaetze in RTF als RTF-Tabelle ausgegeben werden.
   */
  @Test
  void writesRtfTableParagraphAsTable() throws Exception {
    ExportDocument document = tableDocument();
    Path rtf = tempDir.resolve("tabelle.rtf");

    new RtfExportWriter().write(document, rtf);

    String content = Files.readString(rtf);
    assertTrue(content.contains("\\trowd"));
    assertTrue(content.contains("\\clbrdrt\\brdrs\\brdrw10"));
    assertTrue(content.contains("\\cellx"));
    assertTrue(content.contains("\\row"));
    assertTrue(content.contains("Alpha"));
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
    ), new ExportParagraphStyle("center", 20, 3, 16, true));
    return new ExportDocument(
        List.of(new ExportNote(1, "Titel", paragraph.plainText(), List.of(paragraph), List.of(), null)),
        List.of(),
        ExportSelection.defaultSelection()
    );
  }

  private ExportDocument imageDocument(Path image) {
    ExportParagraph paragraph = ExportParagraph.image(image);
    return new ExportDocument(
        List.of(new ExportNote(1, "Titel", "", List.of(paragraph), List.of(), null)),
        List.of(),
        ExportSelection.defaultSelection()
    );
  }

  private ExportDocument tableDocument() {
    ExportParagraph paragraph = ExportParagraph.table(
        new de.zettelkastenfx.export.model.ExportTable(
            2,
            3,
            List.of(
                List.of("", "Alpha", ""),
                List.of("", "", "")
            )
        )
    );
    return new ExportDocument(
        List.of(new ExportNote(1, "Titel", "", List.of(paragraph), List.of(), null)),
        List.of(),
        ExportSelection.defaultSelection()
    );
  }

  private Path createPng() throws IOException {
    Path image = tempDir.resolve("bild.png");
    BufferedImage bufferedImage = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
    ImageIO.write(bufferedImage, "png", image.toFile());
    return image;
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
