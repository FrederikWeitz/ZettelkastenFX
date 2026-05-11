package de.zettelkastenfx.export.render;

import de.zettelkastenfx.export.BibliographyPlacement;
import de.zettelkastenfx.export.model.ExportBibliographyEntry;
import de.zettelkastenfx.export.model.ExportBibliographyField;
import de.zettelkastenfx.export.model.ExportDocument;
import de.zettelkastenfx.export.model.ExportNote;
import de.zettelkastenfx.export.model.ExportParagraph;

/**
 * Rendert das Exportmodell in eine einfache Textstruktur fuer RTF.
 */
public class ExportTextRenderer {

  /**
   * Rendert das Dokument als zeilenorientierten Text.
   *
   * @param document Exportdokument
   * @return Textinhalt
   */
  public String render(ExportDocument document) {
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < document.notes().size(); i++) {
      renderNote(out, document.notes().get(i), document);
      if (document.selection().blankLineBetweenNotes() && i < document.notes().size() - 1) {
        out.append(System.lineSeparator());
      }
    }
    if ((document.selection().bibliographyPlacement() == BibliographyPlacement.END_OF_DOCUMENT
        || document.selection().bibliographyPlacement() == BibliographyPlacement.END_OF_LAST_DOCUMENT
        || document.selection().bibliographyPlacement() == BibliographyPlacement.SEPARATE_FILE)
        && !document.endBibliography().isEmpty()) {
      out.append(System.lineSeparator()).append("Bibliographische Angaben").append(System.lineSeparator());
      for (ExportBibliographyEntry entry : document.endBibliography()) {
        renderBibliography(out, entry);
        out.append(System.lineSeparator());
      }
    }
    return out.toString();
  }

  /**
   * Rendert einen einzelnen Zettel in den Textpuffer.
   *
   * @param out Zielpuffer
   * @param note Exportzettel
   * @param document Exportdokument
   */
  private void renderNote(StringBuilder out, ExportNote note, ExportDocument document) {
    String header = headerText(note, document);
    if (!header.isBlank()) {
      out.append(header).append(System.lineSeparator());
    }
    if (document.selection().includeBody() && !note.bodyParagraphs().isEmpty()) {
      for (ExportParagraph paragraph : note.bodyParagraphs()) {
        if (paragraph.isImage()) {
          out.append(paragraph.imagePath()).append(System.lineSeparator());
        } else if (paragraph.isTable()) {
          renderTable(out, paragraph);
        } else {
          out.append(paragraph.plainText()).append(System.lineSeparator());
        }
      }
    }
    if (document.selection().includeKeywords() && !note.keywords().isEmpty()) {
      out.append("Stichwörter:").append(System.lineSeparator());
      for (String keyword : note.keywords()) {
        out.append('\t').append(keyword).append(System.lineSeparator());
      }
    }
    if (document.selection().bibliographyPlacement() == BibliographyPlacement.AFTER_EACH_NOTE
        && note.bibliographyEntry() != null) {
      renderBibliography(out, note.bibliographyEntry());
    }
  }

  private void renderTable(StringBuilder out, ExportParagraph paragraph) {
    out.append("[Tabelle ")
        .append(paragraph.table().rows())
        .append(" x ")
        .append(paragraph.table().columns())
        .append("]")
        .append(System.lineSeparator());
    for (int row = 0; row < paragraph.table().rows(); row++) {
      for (int column = 0; column < paragraph.table().columns(); column++) {
        if (column > 0) {
          out.append('\t');
        }
        out.append(paragraph.table().cell(row, column));
      }
      out.append(System.lineSeparator());
    }
  }

  /**
   * Rendert bibliographische Feldzeilen in den Textpuffer.
   *
   * @param out Zielpuffer
   * @param entry bibliographischer Exporteintrag
   */
  private void renderBibliography(StringBuilder out, ExportBibliographyEntry entry) {
    for (ExportBibliographyField field : entry.fields()) {
      out.append(field.name()).append(": ").append(field.value()).append(System.lineSeparator());
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
}
