package de.zettelkastenfx.export;

import de.zettelkastenfx.export.model.ExportDocument;
import de.zettelkastenfx.export.render.ExportTextRenderer;

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

  private final ExportTextRenderer renderer = new ExportTextRenderer();

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
        add(zip, "word/document.xml", documentXml(document));
        add(zip, "word/styles.xml", stylesXml());
      }
    } catch (IOException e) {
      throw new IllegalStateException("DOCX-Export fehlgeschlagen: " + outputPath, e);
    }
  }

  private String documentXml(ExportDocument document) {
    StringBuilder body = new StringBuilder();
    String[] lines = renderer.render(document).split("\\R", -1);
    for (int i = 0; i < lines.length; i++) {
      String style = i == 0 || isHeaderLine(lines[i]) ? "Heading3" : null;
      body.append(paragraph(lines[i], style));
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
   * Prueft, ob eine Textzeile als Ueberschrift formatiert werden soll.
   *
   * @param line Textzeile
   * @return {@code true}, wenn die Zeile wie eine Zettelkopfzeile aussieht
   */
  private boolean isHeaderLine(String line) {
    return line != null && line.matches("\\d+: .+");
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
