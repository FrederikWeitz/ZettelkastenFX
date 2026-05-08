package de.zettelkastenfx.export.model;

import java.util.List;

/**
 * Beschreibt einen fuer die Writer neutral aufbereiteten Zettel.
 *
 * @param noteId Zettelnummer
 * @param title Ueberschrift
 * @param bodyText dekodierter Zettelinhalt
 * @param bodyParagraphs formatierter Zettelinhalt
 * @param keywords Schlagwoerter
 * @param bibliographyEntry bibliographischer Eintrag oder {@code null}
 */
public record ExportNote(
    int noteId,
    String title,
    String bodyText,
    List<ExportParagraph> bodyParagraphs,
    List<String> keywords,
    ExportBibliographyEntry bibliographyEntry
) {

  /**
   * Erzeugt einen Exportzettel aus unformatiertem Klartext.
   *
   * @param noteId Zettelnummer
   * @param title Ueberschrift
   * @param bodyText dekodierter Zettelinhalt
   * @param keywords Schlagwoerter
   * @param bibliographyEntry bibliographischer Eintrag
   */
  public ExportNote(int noteId,
                    String title,
                    String bodyText,
                    List<String> keywords,
                    ExportBibliographyEntry bibliographyEntry) {
    this(noteId, title, bodyText, paragraphsFromPlainText(bodyText), keywords, bibliographyEntry);
  }

  /**
   * Erzeugt einen Exportzettel mit bereinigten Textwerten.
   *
   * @param noteId Zettelnummer
   * @param title Ueberschrift
   * @param bodyText dekodierter Zettelinhalt
   * @param bodyParagraphs formatierter Zettelinhalt
   * @param keywords Schlagwoerter
   * @param bibliographyEntry bibliographischer Eintrag
   */
  public ExportNote {
    title = title == null ? "" : title;
    bodyText = bodyText == null ? "" : bodyText;
    bodyParagraphs = bodyParagraphs == null ? paragraphsFromPlainText(bodyText) : List.copyOf(bodyParagraphs);
    keywords = keywords == null ? List.of() : List.copyOf(keywords);
  }

  /**
   * Wandelt Klartext in unformatierte Exportabsaetze.
   *
   * @param text Klartext
   * @return unformatierte Absaetze
   */
  private static List<ExportParagraph> paragraphsFromPlainText(String text) {
    String normalized = text == null ? "" : text;
    if (normalized.isEmpty()) {
      return List.of();
    }
    return normalized.lines()
                     .map(ExportParagraph::plain)
                     .toList();
  }
}
