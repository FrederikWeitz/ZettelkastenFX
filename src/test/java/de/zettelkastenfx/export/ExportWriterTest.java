package de.zettelkastenfx.export;

import de.zettelkastenfx.export.model.ExportDocument;
import de.zettelkastenfx.export.model.ExportNote;
import de.zettelkastenfx.export.model.ExportParagraph;
import de.zettelkastenfx.export.model.ExportParagraphStyle;
import de.zettelkastenfx.export.model.ExportTextRun;
import de.zettelkastenfx.export.model.ExportTextStyle;
import de.zettelkastenfx.notes.model.Note;
import de.zettelkastenfx.persistence.HtmlBodyCodec;
import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
   * Prueft, dass eine Absatz-Schriftgroesse in RTF nicht in nachfolgende normale Absaetze hineinwirkt.
   */
  @Test
  void writesRtfDefaultFontSizeForNormalParagraphAfterLargeParagraph() throws Exception {
    Note note = new Note();
    note.setBodyCodec(HtmlBodyCodec.CODEC_NAME);
    note.setBodyBlob("""
        <h1>Gross</h1>
        <p>Normal</p>
        """.getBytes(StandardCharsets.UTF_8));
    NoteBodyTextExtractor extractor = new NoteBodyTextExtractor();
    ExportDocument document = new ExportDocument(
        List.of(new ExportNote(1, "Titel", extractor.extractPlainText(note), extractor.extractStyledParagraphs(note), List.of(), null)),
        List.of(),
        ExportSelection.defaultSelection()
    );
    Path rtf = tempDir.resolve("schriftgroesse.rtf");

    new RtfExportWriter().write(document, rtf);

    String content = Files.readString(rtf);
    assertTrue(content.contains("\\s1\\b\\fs40 Gross"));
    assertTrue(content.contains("\\pard\\plain \\fs20 Normal"));
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
   * Prueft, dass DOCX und RTF unterschiedliche Spaltenbreiten aus dem Exportmodell uebernehmen.
   */
  @Test
  void writesDocxAndRtfTableWithColumnWidths() throws Exception {
    ExportDocument document = weightedTableDocument();
    Path docx = tempDir.resolve("breitentabelle.docx");
    Path rtf = tempDir.resolve("breitentabelle.rtf");

    new DocxExportWriter().write(document, docx);
    new RtfExportWriter().write(document, rtf);

    String documentXml = readDocxEntry(docx, "word/document.xml");
    assertTrue(documentXml.contains("<w:gridCol w:w=\"2250\"/>"));
    assertTrue(documentXml.contains("<w:gridCol w:w=\"6750\"/>"));
    assertTrue(documentXml.contains("<w:tcW w:w=\"2250\" w:type=\"dxa\"/>"));
    assertTrue(documentXml.contains("<w:tcW w:w=\"6750\" w:type=\"dxa\"/>"));

    String rtfContent = Files.readString(rtf);
    assertTrue(rtfContent.contains("\\cellx2250"));
    assertTrue(rtfContent.contains("\\cellx9000"));
  }

  /**
   * Prueft, dass aus HTML-Body-Zetteln formatierte DOCX/RTF/HTML-Dateien entstehen und kein Roh-HTML sichtbar bleibt.
   */
  @Test
  void writesHtmlBodyNotesAsFormattedExportsWithoutRawHtml() throws Exception {
    ExportDocument document = htmlBodyDocument();
    Path docx = tempDir.resolve("htmlbody.docx");
    Path rtfPath = tempDir.resolve("htmlbody.rtf");
    Path html = tempDir.resolve("htmlbody.html");
    Path pdf = tempDir.resolve("htmlbody.pdf");

    new DocxExportWriter().write(document, docx);
    new RtfExportWriter().write(document, rtfPath);
    new HtmlExportWriter().write(document, html);
    new PdfExportWriter().write(document, pdf);

    String documentXml = readDocxEntry(docx, "word/document.xml");
    assertFalseRawHtml(documentXml);
    assertTrue(documentXml.contains("<w:b/>"));
    assertTrue(documentXml.contains("<w:color w:val=\"1B5FBF\"/>"));
    assertTrue(documentXml.contains("<w:color w:val=\"FF0000\"/>"));
    assertTrue(documentXml.contains("<w:rFonts w:ascii=\"Times New Roman\" w:hAnsi=\"Times New Roman\"/>"));
    assertTrue(documentXml.contains("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"EEEEEE\"/>"));
    assertTrue(documentXml.contains("<w:tbl>"));

    String rtf = Files.readString(rtfPath);
    assertFalseRawHtml(rtf);
    assertTrue(rtf.contains("\\b "));
    assertTrue(rtf.contains("\\cf"));
    assertTrue(rtf.contains("\\f"));
    assertTrue(rtf.contains("\\highlight"));
    assertTrue(rtf.contains("\\trowd"));

    String htmlContent = Files.readString(html);
    assertFalse(htmlContent.contains("&lt;p"));
    assertFalse(htmlContent.contains("class=&quot;"));
    assertTrue(htmlContent.contains("font-weight:bold"));
    assertTrue(htmlContent.contains("color:#1b5fbf;"));
    assertTrue(htmlContent.contains("color:#ff0000;"));
    assertTrue(htmlContent.contains("font-family:'Times New Roman';"));
    assertTrue(htmlContent.contains("<table"));

    assertTrue(Files.size(pdf) > 0);
    assertTrue(pdfFontNames(pdf).stream().anyMatch(name -> name.toLowerCase().contains("times")));
  }

  /**
   * Prueft, dass HTML-Body-Listen und Ueberschriften als Struktur in HTML, DOCX und RTF erhalten bleiben.
   */
  @Test
  void writesHtmlBodyHeadingsAndListsAsDocumentStructure() throws Exception {
    Note note = new Note();
    note.setBodyCodec(HtmlBodyCodec.CODEC_NAME);
    note.setBodyBlob("""
        <h1>Kapitel</h1>
        <h2>Abschnitt</h2>
        <ul><li>Alpha</li><li>Beta</li></ul>
        <ol><li>Eins</li><li>Zwei</li></ol>
        <p>Nachlauf</p>
        """.getBytes(StandardCharsets.UTF_8));
    NoteBodyTextExtractor extractor = new NoteBodyTextExtractor();
    ExportDocument document = new ExportDocument(
        List.of(new ExportNote(1, "Titel", extractor.extractPlainText(note), extractor.extractStyledParagraphs(note), List.of(), null)),
        List.of(),
        ExportSelection.defaultSelection()
    );
    Path html = tempDir.resolve("struktur.html");
    Path docx = tempDir.resolve("struktur.docx");
    Path rtf = tempDir.resolve("struktur.rtf");

    new HtmlExportWriter().write(document, html);
    new DocxExportWriter().write(document, docx);
    new RtfExportWriter().write(document, rtf);

    String htmlContent = Files.readString(html);
    assertTrue(htmlContent.contains("<h1>Kapitel</h1>"));
    assertTrue(htmlContent.contains("<h2>Abschnitt</h2>"));
    assertTrue(htmlContent.contains("<ul><li>Alpha</li><li>Beta</li></ul>"));
    assertTrue(htmlContent.contains("<ol><li>Eins</li><li>Zwei</li></ol>"));

    String documentXml = readDocxEntry(docx, "word/document.xml");
    String numberingXml = readDocxEntry(docx, "word/numbering.xml");
    assertTrue(documentXml.contains("<w:pStyle w:val=\"Heading1\"/>"));
    assertTrue(documentXml.contains("<w:pStyle w:val=\"Heading2\"/>"));
    assertTrue(documentXml.contains("<w:numId w:val=\"1\"/>"));
    assertTrue(documentXml.contains("<w:numId w:val=\"2\"/>"));
    assertTrue(numberingXml.contains("<w:numFmt w:val=\"bullet\"/>"));
    assertTrue(numberingXml.contains("<w:numFmt w:val=\"decimal\"/>"));

    String rtfContent = Files.readString(rtf);
    assertTrue(rtfContent.contains("\\s1\\b\\fs40 Kapitel"));
    assertTrue(rtfContent.contains("\\s2\\b\\fs32 Abschnitt"));
    assertTrue(rtfContent.contains("{\\pn\\pnlvlblt"));
    assertTrue(rtfContent.contains("{\\pn\\pnlvlbody"));
    assertTrue(rtfContent.contains("{\\pntext\\u8226?\\tab}Alpha"));
    assertTrue(rtfContent.contains("{\\pntext 1.\\tab}Eins"));
    assertTrue(rtfContent.contains("\\pard\\plain \\fs20 Nachlauf"));
  }

  /**
   * Prueft, dass HTML-Body-Bildabsaetze im Exportmodell landen und in DOCX eingebettet werden.
   */
  @Test
  void writesWrappedHtmlBodyImageAsEmbeddedDocxMedia() throws Exception {
    Path image = createPng();
    Note note = new Note();
    note.setBodyCodec(HtmlBodyCodec.CODEC_NAME);
    note.setBodyBlob(("<p><img src=\"" + image + "\"></p>").getBytes(StandardCharsets.UTF_8));
    NoteBodyTextExtractor extractor = new NoteBodyTextExtractor();
    ExportDocument document = new ExportDocument(
        List.of(new ExportNote(
            1,
            "Titel",
            extractor.extractPlainText(note),
            extractor.extractStyledParagraphs(note),
            List.of(),
            null
        )),
        List.of(),
        ExportSelection.defaultSelection()
    );
    Path docx = tempDir.resolve("htmlbild.docx");

    new DocxExportWriter().write(document, docx);

    try (ZipFile zip = new ZipFile(docx.toFile())) {
      assertNotNull(zip.getEntry("word/media/image1.png"));
    }
    String documentXml = readDocxEntry(docx, "word/document.xml");
    assertFalseRawHtml(documentXml);
    assertTrue(documentXml.contains("<w:drawing>"));
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

  private ExportDocument weightedTableDocument() {
    ExportParagraph paragraph = ExportParagraph.table(
        new de.zettelkastenfx.export.model.ExportTable(
            1,
            2,
            List.of(List.of("Schmal", "Breit")),
            List.of(25, 75)
        )
    );
    return new ExportDocument(
        List.of(new ExportNote(1, "Titel", "", List.of(paragraph), List.of(), null)),
        List.of(),
        ExportSelection.defaultSelection()
    );
  }

  private ExportDocument htmlBodyDocument() {
    Note note = new Note();
    note.setBodyCodec(HtmlBodyCodec.CODEC_NAME);
    note.setBodyBlob("""
        <p class="j q"><span class="b" style="color: #1b5fbf;">Alpha</span></p>
        <p><span style="color: rgb(255 0 0 / 1); font-family: 'Times New Roman', serif;">Rot</span></p>
        <table><tbody><tr><td>A</td><td>B</td></tr></tbody></table>
        """.getBytes(StandardCharsets.UTF_8));
    NoteBodyTextExtractor extractor = new NoteBodyTextExtractor();
    return new ExportDocument(
        List.of(new ExportNote(
            1,
            "Titel",
            extractor.extractPlainText(note),
            extractor.extractStyledParagraphs(note),
            List.of(),
            null
        )),
        List.of(),
        ExportSelection.defaultSelection()
    );
  }

  private void assertFalseRawHtml(String content) {
    assertFalse(content.contains("<p class="));
    assertFalse(content.contains("<span"));
    assertFalse(content.contains("</p>"));
    assertFalse(content.contains("&lt;p"));
  }

  private Set<String> pdfFontNames(Path pdf) throws IOException {
    Set<String> names = new HashSet<>();
    try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
      for (var page : document.getPages()) {
        var resources = page.getResources();
        if (resources == null) {
          continue;
        }
        for (var fontName : resources.getFontNames()) {
          names.add(resources.getFont(fontName).getName());
        }
      }
    }
    return names;
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
