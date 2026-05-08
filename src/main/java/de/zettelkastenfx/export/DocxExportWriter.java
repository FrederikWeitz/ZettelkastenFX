package de.zettelkastenfx.export;

import de.zettelkastenfx.export.model.ExportBibliographyEntry;
import de.zettelkastenfx.export.model.ExportBibliographyField;
import de.zettelkastenfx.export.model.ExportDocument;
import de.zettelkastenfx.export.model.ExportNote;
import de.zettelkastenfx.export.model.ExportParagraph;
import de.zettelkastenfx.export.model.ExportParagraphStyle;
import de.zettelkastenfx.export.model.ExportTextRun;
import de.zettelkastenfx.export.model.ExportTextStyle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Schreibt Exportdokumente als einfache DOCX-Dateien.
 */
public class DocxExportWriter implements NoteExportWriter {

  /**
   * Schreibt ein Exportdokument als DOCX.
   *
   * @param document Exportdokument
   * @param outputPath Zielpfad
   */
  @Override
  public void write(ExportDocument document, Path outputPath) {
    try {
      Files.createDirectories(outputPath.toAbsolutePath().getParent());
      try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(outputPath))) {
        add(zip, "[Content_Types].xml", contentTypes());
        add(zip, "_rels/.rels", rels());
        add(zip, "word/_rels/document.xml.rels", documentRels());
        add(zip, "word/document.xml", documentXml(document));
        add(zip, "word/styles.xml", stylesXml());
      }
    } catch (IOException e) {
      throw new IllegalStateException("DOCX-Export fehlgeschlagen: " + outputPath, e);
    }
  }

  private String documentXml(ExportDocument document) {
    StringBuilder body = new StringBuilder();
    for (int i = 0; i < document.notes().size(); i++) {
      renderNote(body, document.notes().get(i), document);
      if (document.selection().blankLineBetweenNotes() && i < document.notes().size() - 1) {
        body.append(paragraph("", null));
      }
    }
    if (hasEndBibliography(document)) {
      body.append(paragraph("Bibliographische Angaben", "Heading3"));
      for (ExportBibliographyEntry entry : document.endBibliography()) {
        renderBibliography(body, entry);
        body.append(paragraph("", null));
      }
    }
    return """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
          <w:body>
        """ + body + """
            <w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440"/></w:sectPr>
          </w:body>
        </w:document>
        """;
  }

  /**
   * Rendert einen Zettel in WordprocessingML.
   *
   * @param body Zielpuffer
   * @param note Exportzettel
   * @param document Exportdokument
   */
  private void renderNote(StringBuilder body, ExportNote note, ExportDocument document) {
    String header = headerText(note, document);
    if (!header.isBlank()) {
      body.append(paragraph(header, "Heading3"));
    }
    if (document.selection().includeBody() && !note.bodyText().isBlank()) {
      for (ExportParagraph paragraph : note.bodyParagraphs()) {
        body.append(paragraph(paragraph, null));
      }
    }
    if (document.selection().includeKeywords() && !note.keywords().isEmpty()) {
      body.append(paragraph("Stichwoerter:", null));
      for (String keyword : note.keywords()) {
        body.append(paragraph("\t" + keyword, null));
      }
    }
    if (document.selection().bibliographyPlacement() == BibliographyPlacement.AFTER_EACH_NOTE
        && note.bibliographyEntry() != null) {
      renderBibliography(body, note.bibliographyEntry());
    }
  }

  /**
   * Erzeugt einen WordprocessingML-Absatz.
   *
   * @param text Absatztext
   * @param style optionaler Absatzstil
   * @return XML-Absatz
   */
  private String paragraph(String text, String style) {
    String styleXml = style == null ? "" : "<w:pPr><w:pStyle w:val=\"" + style + "\"/></w:pPr>";
    return "<w:p>" + styleXml + "<w:r><w:t xml:space=\"preserve\">" + escape(text) + "</w:t></w:r></w:p>";
  }

  /**
   * Erzeugt einen formatierten WordprocessingML-Absatz.
   *
   * @param paragraph Exportabsatz
   * @param style optionaler Absatzstil
   * @return XML-Absatz
   */
  private String paragraph(ExportParagraph paragraph, String style) {
    StringBuilder xml = new StringBuilder("<w:p>");
    String paragraphProperties = paragraphProperties(paragraph.style(), style);
    if (!paragraphProperties.isBlank()) {
      xml.append("<w:pPr>").append(paragraphProperties).append("</w:pPr>");
    }
    for (ExportTextRun run : paragraph.runs()) {
      xml.append(run(run, paragraph.style()));
    }
    xml.append("</w:p>");
    return xml.toString();
  }

  /**
   * Baut WordprocessingML-Eigenschaften fuer einen Absatz.
   *
   * @param paragraphStyle Export-Absatzstil
   * @param style optionaler Word-Absatzstil
   * @return Absatz-Properties
   */
  private String paragraphProperties(ExportParagraphStyle paragraphStyle, String style) {
    StringBuilder properties = new StringBuilder();
    if (style != null) {
      properties.append("<w:pStyle w:val=\"").append(style).append("\"/>");
    }
    if (paragraphStyle.alignment() != null) {
      properties.append("<w:jc w:val=\"").append(wordAlignment(paragraphStyle.alignment())).append("\"/>");
    }
    if (paragraphStyle.indentPixels() > 0) {
      properties.append("<w:ind w:left=\"").append(paragraphStyle.indentPixels() * 15).append("\"/>");
    }
    return properties.toString();
  }

  /**
   * Rendert einen formatierten Textbereich.
   *
   * @param run Export-Run
   * @return WordprocessingML-Run
   */
  private String run(ExportTextRun run, ExportParagraphStyle paragraphStyle) {
    StringBuilder xml = new StringBuilder("<w:r>");
    String properties = runProperties(run.style(), paragraphStyle);
    if (!properties.isBlank()) {
      xml.append("<w:rPr>").append(properties).append("</w:rPr>");
    }
    xml.append("<w:t xml:space=\"preserve\">").append(escape(run.text())).append("</w:t></w:r>");
    return xml.toString();
  }

  /**
   * Baut WordprocessingML-Eigenschaften fuer einen Exportstil.
   *
   * @param style Exportstil
   * @return Run-Properties
   */
  private String runProperties(ExportTextStyle style, ExportParagraphStyle paragraphStyle) {
    StringBuilder properties = new StringBuilder();
    if (style.bold() || paragraphStyle.bold()) {
      properties.append("<w:b/>");
    }
    if (style.italic()) {
      properties.append("<w:i/>");
    }
    if (style.underline()) {
      properties.append("<w:u w:val=\"single\"/>");
    }
    if (style.strikethrough()) {
      properties.append("<w:strike/>");
    }
    if (style.fontFamily() != null) {
      properties.append("<w:rFonts w:ascii=\"")
          .append(escape(style.fontFamily()))
          .append("\" w:hAnsi=\"")
          .append(escape(style.fontFamily()))
          .append("\"/>");
    }
    if (paragraphStyle.fontSizePixels() != null) {
      properties.append("<w:sz w:val=\"").append(paragraphStyle.fontSizePixels() * 2).append("\"/>");
    }
    if (style.textColor() != null) {
      properties.append("<w:color w:val=\"").append(wordColor(style.textColor())).append("\"/>");
    }
    if (style.backgroundColor() != null) {
      properties.append("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"")
          .append(wordColor(style.backgroundColor()))
          .append("\"/>");
    }
    return properties.toString();
  }

  /**
   * Rendert bibliographische Angaben.
   *
   * @param body Zielpuffer
   * @param entry bibliographischer Eintrag
   */
  private void renderBibliography(StringBuilder body, ExportBibliographyEntry entry) {
    for (ExportBibliographyField field : entry.fields()) {
      body.append(paragraph(field.name() + ": " + field.value(), null));
    }
  }

  /**
   * Prueft, ob eine Endbibliographie ausgegeben wird.
   *
   * @param document Exportdokument
   * @return {@code true}, wenn eine Endbibliographie vorhanden ist
   */
  private boolean hasEndBibliography(ExportDocument document) {
    return (document.selection().bibliographyPlacement() == BibliographyPlacement.END_OF_DOCUMENT
        || document.selection().bibliographyPlacement() == BibliographyPlacement.END_OF_LAST_DOCUMENT
        || document.selection().bibliographyPlacement() == BibliographyPlacement.SEPARATE_FILE)
        && !document.endBibliography().isEmpty();
  }

  /**
   * Erstellt die Kopfzeile eines Zettels entsprechend der Auswahl.
   *
   * @param note Exportzettel
   * @param document Exportdokument
   * @return Kopfzeilentext
   */
  private String headerText(ExportNote note, ExportDocument document) {
    boolean number = document.selection().includeNoteNumber();
    boolean title = document.selection().includeTitle();
    if (number && title) {
      return note.noteId() + ": " + note.title();
    }
    if (number) {
      return Integer.toString(note.noteId());
    }
    return title ? note.title() : "";
  }

  /**
   * Erzeugt die minimale Word-Styles-Datei.
   *
   * @return Styles-XML
   */
  private String stylesXml() {
    return """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
          <w:style w:type="paragraph" w:styleId="Heading3"><w:name w:val="heading 3"/><w:rPr><w:b/><w:sz w:val="28"/></w:rPr></w:style>
        </w:styles>
        """;
  }

  /**
   * Erzeugt die Content-Types-Datei des DOCX-Pakets.
   *
   * @return Content-Types-XML
   */
  private String contentTypes() {
    return """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
          <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
          <Default Extension="xml" ContentType="application/xml"/>
          <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
          <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
        </Types>
        """;
  }

  /**
   * Erzeugt die Paket-Relationship zur Hauptdokumentdatei.
   *
   * @return Relationships-XML
   */
  private String rels() {
    return """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
        </Relationships>
        """;
  }

  /**
   * Erzeugt die Relationship-Datei des Word-Dokuments.
   *
   * @return Relationships-XML
   */
  private String documentRels() {
    return """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rIdStyles" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
        </Relationships>
        """;
  }

  /**
   * Wandelt CSS-Ausrichtung in WordprocessingML-Ausrichtung.
   *
   * @param alignment CSS-Ausrichtung
   * @return WordprocessingML-Wert
   */
  private String wordAlignment(String alignment) {
    return "justify".equals(alignment) ? "both" : alignment;
  }

  /**
   * Wandelt eine Hex-Farbe in WordprocessingML-Schreibweise.
   *
   * @param color Hex-Farbe mit fuehrendem #
   * @return Hex-Farbe ohne #
   */
  private String wordColor(String color) {
    return color == null ? "" : color.replace("#", "").toUpperCase();
  }

  /**
   * Fuegt einem DOCX-ZIP-Paket einen Eintrag hinzu.
   *
   * @param zip Ziel-ZIP
   * @param name Eintragsname
   * @param content Eintragsinhalt
   * @throws IOException bei Schreibfehlern
   */
  private void add(ZipOutputStream zip, String name, String content) throws IOException {
    zip.putNextEntry(new ZipEntry(name));
    zip.write(content.getBytes(StandardCharsets.UTF_8));
    zip.closeEntry();
  }

  /**
   * Maskiert Text fuer WordprocessingML.
   *
   * @param value Rohtext
   * @return XML-maskierter Text
   */
  private String escape(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
