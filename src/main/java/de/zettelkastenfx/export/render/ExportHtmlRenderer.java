package de.zettelkastenfx.export.render;

import de.zettelkastenfx.export.BibliographyPlacement;
import de.zettelkastenfx.export.model.ExportBibliographyEntry;
import de.zettelkastenfx.export.model.ExportBibliographyField;
import de.zettelkastenfx.export.model.ExportDocument;
import de.zettelkastenfx.export.model.ExportNote;

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
            h3 { font-size: 1.17em; margin: 0 0 0.7em 0; font-weight: bold; }
            p { margin: 0 0 0.7em 0; }
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

    if (document.selection().bibliographyPlacement() == BibliographyPlacement.END_OF_DOCUMENT
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
    if (document.selection().includeBody() && !note.bodyText().isBlank()) {
      for (String paragraph : note.bodyText().split("\\R", -1)) {
        html.append("<p>").append(escape(paragraph).replace("  ", " &#160;")).append("</p>");
      }
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
