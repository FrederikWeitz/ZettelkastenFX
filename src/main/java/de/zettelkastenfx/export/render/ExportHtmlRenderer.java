package de.zettelkastenfx.export.render;

import de.zettelkastenfx.export.BibliographyPlacement;
import de.zettelkastenfx.export.model.ExportBibliographyEntry;
import de.zettelkastenfx.export.model.ExportBibliographyField;
import de.zettelkastenfx.export.model.ExportDocument;
import de.zettelkastenfx.export.model.ExportNote;
import de.zettelkastenfx.export.model.ExportParagraph;
import de.zettelkastenfx.export.model.ExportParagraphStyle;
import de.zettelkastenfx.export.model.ExportTextRun;
import de.zettelkastenfx.export.model.ExportTextStyle;

/**
 * Rendert das neutrale Exportmodell nach XHTML.
 */
public class ExportHtmlRenderer {

  /**
   * Rendert ein vollstaendiges XHTML-Dokument.
   *
   * @param document Exportdokument
   * @return XHTML-Inhalt
   */
  public String render(ExportDocument document) {
    StringBuilder html = new StringBuilder();
    html.append("""
        <!DOCTYPE html>
        <html xmlns="http://www.w3.org/1999/xhtml">
        <head>
          <meta charset="UTF-8" />
          <style>
            body { font-family: sans-serif; font-size: 11pt; line-height: 1.35; }
            h1 { font-size: 1.82em; margin: 0.67em 0; font-weight: bold; }
            h2 { font-size: 1.5em; margin: 0.83em 0; font-weight: bold; }
            h3 { font-size: 1.17em; margin: 0 0 0.7em 0; font-weight: bold; }
            p { margin: 0 0 0.7em 0; }
            ul, ol { margin: 0 0 0.7em 1.6em; padding-left: 1.2em; }
            li { margin: 0 0 0.2em 0; }
            .keywords-title, .bibliography-line { margin-top: 0.7em; }
            .keyword { margin-left: 2em; }
          </style>
        </head>
        <body>
        """);

    for (int i = 0; i < document.notes().size(); i++) {
      renderNote(html, document.notes().get(i), document);
      if (document.selection().blankLineBetweenNotes() && i < document.notes().size() - 1) {
        html.append("<p>&#160;</p>");
      }
    }

    if ((document.selection().bibliographyPlacement() == BibliographyPlacement.END_OF_DOCUMENT
        || document.selection().bibliographyPlacement() == BibliographyPlacement.END_OF_LAST_DOCUMENT
        || document.selection().bibliographyPlacement() == BibliographyPlacement.SEPARATE_FILE)
        && !document.endBibliography().isEmpty()) {
      html.append("<h3>Bibliographische Angaben</h3>");
      for (ExportBibliographyEntry entry : document.endBibliography()) {
        renderBibliography(html, entry);
        html.append("<p>&#160;</p>");
      }
    }

    html.append("</body></html>");
    return html.toString();
  }

  /**
   * Rendert einen einzelnen Zettel in den HTML-Puffer.
   *
   * @param html Zielpuffer
   * @param note Exportzettel
   * @param document Exportdokument
   */
  private void renderNote(StringBuilder html, ExportNote note, ExportDocument document) {
    String header = headerText(note, document);
    if (!header.isBlank()) {
      html.append("<h3>").append(escape(header)).append("</h3>");
    }
    if (document.selection().includeBody() && !note.bodyParagraphs().isEmpty()) {
      String openList = null;
      for (ExportParagraph paragraph : note.bodyParagraphs()) {
        if (!paragraph.style().isListItem()) {
          openList = closeOpenList(html, openList);
        }
        if (paragraph.isImage()) {
          renderImageParagraph(html, paragraph);
          continue;
        }
        if (paragraph.isTable()) {
          renderTable(html, paragraph);
          continue;
        }
        if (paragraph.style().isListItem()) {
          String listType = paragraph.style().listType();
          if (!listType.equals(openList)) {
            openList = closeOpenList(html, openList);
            html.append("<").append(listType).append(">");
            openList = listType;
          }
          html.append("<li>");
          renderRuns(html, paragraph);
          html.append("</li>");
          continue;
        }
        if (paragraph.style().isHeading()) {
          String tag = "h" + paragraph.style().headingLevel();
          html.append("<").append(tag).append(">");
          renderRuns(html, paragraph);
          html.append("</").append(tag).append(">");
          continue;
        }
        html.append("<p");
        String paragraphCss = paragraphCss(paragraph.style());
        if (!paragraphCss.isBlank()) {
          html.append(" style=\"").append(paragraphCss).append("\"");
        }
        html.append(">");
        renderRuns(html, paragraph);
        html.append("</p>");
      }
      closeOpenList(html, openList);
    }
    if (document.selection().includeKeywords() && !note.keywords().isEmpty()) {
      html.append("<p class=\"keywords-title\">Stichwörter:</p>");
      for (String keyword : note.keywords()) {
        html.append("<p class=\"keyword\">").append(escape(keyword)).append("</p>");
      }
    }
    if (document.selection().bibliographyPlacement() == BibliographyPlacement.AFTER_EACH_NOTE
        && note.bibliographyEntry() != null) {
      renderBibliography(html, note.bibliographyEntry());
    }
  }

  private String closeOpenList(StringBuilder html, String openList) {
    if (openList != null) {
      html.append("</").append(openList).append(">");
    }
    return null;
  }

  /**
   * Rendert einen Bildabsatz in HTML.
   *
   * @param html Zielpuffer
   * @param paragraph Bildabsatz
   */
  private void renderImageParagraph(StringBuilder html, ExportParagraph paragraph) {
    html.append("<p");
    String paragraphCss = paragraphCss(paragraph.style());
    if (!paragraphCss.isBlank()) {
      html.append(" style=\"").append(paragraphCss).append("\"");
    }
    html.append("><img src=\"")
        .append(escape(paragraph.imagePath().toUri().toString()))
        .append("\" alt=\"\" style=\"max-width:100%;height:auto;\" /></p>");
  }

  /**
   * Rendert einen Tabellenabsatz in HTML.
   *
   * @param html Zielpuffer
   * @param paragraph Tabellenabsatz
   */
  private void renderTable(StringBuilder html, ExportParagraph paragraph) {
    html.append("<table style=\"border-collapse:collapse;width:100%;margin:0 0 0.7em 0;\">");
    for (int row = 0; row < paragraph.table().rows(); row++) {
      html.append("<tr>");
      for (int column = 0; column < paragraph.table().columns(); column++) {
        String cellText = paragraph.table().cell(row, column);
        html.append("<td style=\"border:1px solid #000;padding:1px;\">")
            .append(cellText == null || cellText.isBlank() ? "&#160;" : escape(cellText).replace("\n", "<br />"))
            .append("</td>");
      }
      html.append("</tr>");
    }
    html.append("</table>");
  }

  /**
   * Rendert bibliographische Feldzeilen in den HTML-Puffer.
   *
   * @param html Zielpuffer
   * @param entry bibliographischer Exporteintrag
   */
  private void renderBibliography(StringBuilder html, ExportBibliographyEntry entry) {
    for (ExportBibliographyField field : entry.fields()) {
      html.append("<p class=\"bibliography-line\">")
          .append(escape(field.name()))
          .append(": ")
          .append(escape(field.value()))
          .append("</p>");
    }
  }

  /**
   * Rendert formatierte Textbereiche in den HTML-Puffer.
   *
   * @param html Zielpuffer
   * @param paragraph Exportabsatz
   */
  private void renderRuns(StringBuilder html, ExportParagraph paragraph) {
    for (ExportTextRun run : paragraph.runs()) {
      ExportTextStyle style = run.style();
      String css = inlineCss(style);
      if (css.isBlank()) {
        html.append(escape(run.text()).replace("  ", " &#160;"));
      } else {
        html.append("<span style=\"")
            .append(css)
            .append("\">")
            .append(escape(run.text()).replace("  ", " &#160;"))
            .append("</span>");
      }
    }
  }

  /**
   * Baut CSS fuer exportrelevante Inline-Formatierungen.
   *
   * @param style Exportstil
   * @return CSS-Deklarationen
   */
  private String inlineCss(ExportTextStyle style) {
    StringBuilder css = new StringBuilder();
    if (style.bold()) {
      css.append("font-weight:bold;");
    }
    if (style.italic()) {
      css.append("font-style:italic;");
    }
    if (style.underline() || style.strikethrough()) {
      css.append("text-decoration:");
      if (style.underline()) {
        css.append(" underline");
      }
      if (style.strikethrough()) {
        css.append(" line-through");
      }
      css.append(";");
    }
    if (style.fontFamily() != null) {
      css.append("font-family:'").append(escapeCss(style.fontFamily())).append("';");
    }
    if (style.textColor() != null) {
      css.append("color:").append(style.textColor()).append(";");
    }
    if (style.backgroundColor() != null) {
      css.append("background-color:").append(style.backgroundColor()).append(";");
    }
    if (style.subscript()) {
      css.append("vertical-align:sub;font-size:smaller;");
    }
    if (style.superscript()) {
      css.append("vertical-align:super;font-size:smaller;");
    }
    return css.toString();
  }

  /**
   * Baut CSS fuer exportrelevante Absatzformatierungen.
   *
   * @param style Absatzstil
   * @return CSS-Deklarationen
   */
  private String paragraphCss(ExportParagraphStyle style) {
    StringBuilder css = new StringBuilder();
    if (style.alignment() != null) {
      css.append("text-align:").append(style.alignment()).append(";");
    }
    if (style.indentPixels() > 0) {
      css.append("padding-left:").append(style.indentPixels()).append("px;");
    }
    if (style.spacingBeforePixels() > 0) {
      css.append("padding-top:").append(style.spacingBeforePixels()).append("px;");
    }
    if (style.fontSizePixels() != null) {
      css.append("font-size:").append(style.fontSizePixels()).append("px;");
    }
    if (style.bold()) {
      css.append("font-weight:bold;");
    }
    if (style.backgroundColor() != null) {
      css.append("background-color:").append(style.backgroundColor()).append(";");
    }
    return css.toString();
  }

  /**
   * Maskiert Text fuer CSS-Stringwerte.
   *
   * @param value Rohwert
   * @return CSS-maskierter Wert
   */
  private String escapeCss(String value) {
    return value == null ? "" : value.replace("\\", "\\\\").replace("'", "\\'");
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
   * Maskiert Text fuer XML/HTML.
   *
   * @param value Rohtext
   * @return maskierter Text
   */
  private String escape(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("&", "&amp;")
               .replace("<", "&lt;")
               .replace(">", "&gt;")
               .replace("\"", "&quot;");
  }
}
