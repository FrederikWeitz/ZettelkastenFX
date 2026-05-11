package de.zettelkastenfx.export;

import de.zettelkastenfx.export.model.ExportBibliographyEntry;
import de.zettelkastenfx.export.model.ExportBibliographyField;
import de.zettelkastenfx.export.model.ExportDocument;
import de.zettelkastenfx.export.model.ExportNote;
import de.zettelkastenfx.export.model.ExportParagraph;
import de.zettelkastenfx.export.model.ExportParagraphStyle;
import de.zettelkastenfx.export.model.ExportTextRun;
import de.zettelkastenfx.export.model.ExportTextStyle;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
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
      Map<Path, EmbeddedImage> images = collectImages(document);
      try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(outputPath))) {
        add(zip, "[Content_Types].xml", contentTypes(images));
        add(zip, "_rels/.rels", rels());
        add(zip, "word/_rels/document.xml.rels", documentRels(images));
        add(zip, "word/document.xml", documentXml(document, images));
        add(zip, "word/styles.xml", stylesXml());
        for (EmbeddedImage image : images.values()) {
          add(zip, "word/media/" + image.mediaName(), Files.readAllBytes(image.path()));
        }
      }
    } catch (IOException e) {
      throw new IllegalStateException("DOCX-Export fehlgeschlagen: " + outputPath, e);
    }
  }

  private String documentXml(ExportDocument document, Map<Path, EmbeddedImage> images) {
    StringBuilder body = new StringBuilder();
    for (int i = 0; i < document.notes().size(); i++) {
      renderNote(body, document.notes().get(i), document, images);
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
        <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
                    xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
                    xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
                    xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                    xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">
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
  private void renderNote(StringBuilder body,
                          ExportNote note,
                          ExportDocument document,
                          Map<Path, EmbeddedImage> images) {
    String header = headerText(note, document);
    if (!header.isBlank()) {
      body.append(paragraph(header, "Heading3"));
    }
    if (document.selection().includeBody() && !note.bodyParagraphs().isEmpty()) {
      for (ExportParagraph paragraph : note.bodyParagraphs()) {
        body.append(paragraph(paragraph, null, images));
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
    return paragraph(paragraph, style, Map.of());
  }

  /**
   * Erzeugt einen formatierten WordprocessingML-Absatz.
   *
   * @param paragraph Exportabsatz
   * @param style optionaler Absatzstil
   * @param images eingebettete Bilder nach Pfad
   * @return XML-Absatz
   */
  private String paragraph(ExportParagraph paragraph, String style, Map<Path, EmbeddedImage> images) {
    if (paragraph.isImage()) {
      EmbeddedImage image = images.get(paragraph.imagePath());
      if (image != null) {
        return imageParagraph(image);
      }
      return paragraph(paragraph.imagePath().toString(), style);
    }
    if (paragraph.isTable()) {
      return table(paragraph);
    }
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
   * Erzeugt eine einfache WordprocessingML-Tabelle.
   *
   * @param paragraph Tabellenabsatz
   * @return XML-Tabelle
   */
  private String table(ExportParagraph paragraph) {
    int columns = paragraph.table().columns();
    int cellWidth = Math.max(1, 9000 / columns);
    StringBuilder xml = new StringBuilder("<w:tbl><w:tblPr><w:tblBorders>")
        .append("<w:top w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"000000\"/>")
        .append("<w:left w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"000000\"/>")
        .append("<w:bottom w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"000000\"/>")
        .append("<w:right w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"000000\"/>")
        .append("<w:insideH w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"000000\"/>")
        .append("<w:insideV w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"000000\"/>")
        .append("</w:tblBorders><w:tblCellMar>")
        .append("<w:top w:w=\"15\" w:type=\"dxa\"/><w:left w:w=\"15\" w:type=\"dxa\"/>")
        .append("<w:bottom w:w=\"15\" w:type=\"dxa\"/><w:right w:w=\"15\" w:type=\"dxa\"/>")
        .append("</w:tblCellMar></w:tblPr><w:tblGrid>");
    for (int column = 0; column < columns; column++) {
      xml.append("<w:gridCol w:w=\"").append(cellWidth).append("\"/>");
    }
    xml.append("</w:tblGrid>");
    for (int row = 0; row < paragraph.table().rows(); row++) {
      xml.append("<w:tr>");
      for (int column = 0; column < columns; column++) {
        xml.append("<w:tc><w:tcPr><w:tcW w:w=\"")
            .append(cellWidth)
            .append("\" w:type=\"dxa\"/></w:tcPr>")
            .append(cellParagraph(paragraph.table().cell(row, column)))
            .append("</w:tc>");
      }
      xml.append("</w:tr>");
    }
    return xml.append("</w:tbl>").toString();
  }

  /**
   * Erzeugt den Inhalt einer Word-Tabellenzelle.
   *
   * @param text Zelltext
   * @return WordprocessingML-Absatz fuer die Zelle
   */
  private String cellParagraph(String text) {
    if (text == null || text.isBlank()) {
      return "<w:p/>";
    }
    return paragraph(text, null);
  }

  /**
   * Erzeugt einen WordprocessingML-Bildabsatz.
   *
   * @param image eingebettetes Bild
   * @return XML-Absatz mit DrawingML
   */
  private String imageParagraph(EmbeddedImage image) {
    long widthEmu = image.widthPixels() * 9525L;
    long heightEmu = image.heightPixels() * 9525L;
    return """
        <w:p><w:r><w:drawing><wp:inline distT="0" distB="0" distL="0" distR="0">
          <wp:extent cx="%d" cy="%d"/>
          <wp:docPr id="%d" name="%s"/>
          <a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">
            <pic:pic>
              <pic:nvPicPr><pic:cNvPr id="%d" name="%s"/><pic:cNvPicPr/></pic:nvPicPr>
              <pic:blipFill><a:blip r:embed="%s"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>
              <pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="%d" cy="%d"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr>
            </pic:pic>
          </a:graphicData></a:graphic>
        </wp:inline></w:drawing></w:r></w:p>
        """.formatted(
            widthEmu,
            heightEmu,
            image.index(),
            escape(image.mediaName()),
            image.index(),
            escape(image.mediaName()),
            image.relationshipId(),
            widthEmu,
            heightEmu
        );
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
    if (paragraphStyle.spacingBeforePixels() > 0) {
      properties.append("<w:spacing w:before=\"").append(paragraphStyle.spacingBeforePixels() * 15).append("\"/>");
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
  private String contentTypes(Map<Path, EmbeddedImage> images) {
    StringBuilder defaults = new StringBuilder();
    Map<String, String> imageContentTypes = new LinkedHashMap<>();
    for (EmbeddedImage image : images.values()) {
      imageContentTypes.putIfAbsent(image.extension(), image.contentType());
    }
    for (Map.Entry<String, String> entry : imageContentTypes.entrySet()) {
      defaults.append("  <Default Extension=\"")
          .append(escape(entry.getKey()))
          .append("\" ContentType=\"")
          .append(escape(entry.getValue()))
          .append("\"/>\n");
    }
    return """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
          <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
          <Default Extension="xml" ContentType="application/xml"/>
        """ + defaults + """
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
  private String documentRels(Map<Path, EmbeddedImage> images) {
    StringBuilder relationships = new StringBuilder();
    for (EmbeddedImage image : images.values()) {
      relationships.append("  <Relationship Id=\"")
          .append(image.relationshipId())
          .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/")
          .append(escape(image.mediaName()))
          .append("\"/>\n");
    }
    return """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rIdStyles" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
        """ + relationships + """
        </Relationships>
        """;
  }

  /**
   * Sammelt alle existierenden Bildabsaetze eines Exportdokuments.
   *
   * @param document Exportdokument
   * @return Bild-Mapping nach normalisiertem Pfad
   * @throws IOException bei Dateizugriffsfehlern
   */
  private Map<Path, EmbeddedImage> collectImages(ExportDocument document) throws IOException {
    Map<Path, EmbeddedImage> images = new LinkedHashMap<>();
    for (ExportNote note : document.notes()) {
      for (ExportParagraph paragraph : note.bodyParagraphs()) {
        if (paragraph.isImage() && Files.isRegularFile(paragraph.imagePath()) && !images.containsKey(paragraph.imagePath())) {
          int index = images.size() + 1;
          images.put(paragraph.imagePath(), embeddedImage(paragraph.imagePath(), index));
        }
      }
    }
    return images;
  }

  private EmbeddedImage embeddedImage(Path path, int index) throws IOException {
    String extension = extension(path);
    String contentType = Files.probeContentType(path);
    if (contentType == null || contentType.isBlank()) {
      contentType = switch (extension) {
        case "png" -> "image/png";
        case "jpg", "jpeg" -> "image/jpeg";
        case "gif" -> "image/gif";
        case "bmp" -> "image/bmp";
        default -> "application/octet-stream";
      };
    }
    int width = 300;
    int height = 200;
    BufferedImage image = ImageIO.read(path.toFile());
    if (image != null) {
      width = image.getWidth();
      height = image.getHeight();
    }
    return new EmbeddedImage(
        path,
        index,
        "image" + index + "." + extension,
        "rIdImage" + index,
        extension,
        contentType,
        width,
        height
    );
  }

  private String extension(Path path) {
    String name = path.getFileName() == null ? "image" : path.getFileName().toString();
    int dot = name.lastIndexOf('.');
    if (dot < 0 || dot == name.length() - 1) {
      return "bin";
    }
    return name.substring(dot + 1).toLowerCase();
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

  private void add(ZipOutputStream zip, String name, byte[] content) throws IOException {
    zip.putNextEntry(new ZipEntry(name));
    zip.write(content);
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

  private record EmbeddedImage(Path path,
                               int index,
                               String mediaName,
                               String relationshipId,
                               String extension,
                               String contentType,
                               int widthPixels,
                               int heightPixels) {
  }
}
