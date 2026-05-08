package de.zettelkastenfx.export.model;

import java.util.List;

/**
 * Beschreibt einen fuer die Writer neutral aufbereiteten Zettel.
 *
 * @param noteId Zettelnummer
 * @param title Ueberschrift
 * @param bodyText dekodierter Zettelinhalt
 * @param keywords Schlagwoerter
 * @param bibliographyEntry bibliographischer Eintrag oder {@code null}
 */
public record ExportNote(
    int noteId,
    String title,
    String bodyText,
    List<String> keywords,
    ExportBibliographyEntry bibliographyEntry
) {

  /**
   * Erzeugt einen Exportzettel mit bereinigten Textwerten.
   *
   * @param noteId Zettelnummer
   * @param title Ueberschrift
   * @param bodyText dekodierter Zettelinhalt
   * @param keywords Schlagwoerter
   * @param bibliographyEntry bibliographischer Eintrag
   */
  public ExportNote {
    title = title == null ? "" : title;
    bodyText = bodyText == null ? "" : bodyText;
    keywords = keywords == null ? List.of() : List.copyOf(keywords);
  }
}
