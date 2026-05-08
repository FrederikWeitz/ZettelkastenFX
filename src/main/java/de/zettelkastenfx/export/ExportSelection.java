package de.zettelkastenfx.export;

/**
 * Beschreibt, welche Bestandteile eines Zettels exportiert werden.
 *
 * @param includeBody ob der Zettelinhalt ausgegeben wird
 * @param includeTitle ob die Ueberschrift ausgegeben wird
 * @param includeNoteNumber ob die Zettelnummer ausgegeben wird
 * @param includeKeywords ob Schlagwoerter ausgegeben werden
 * @param bibliographyLevel Umfang der bibliographischen Angaben
 * @param bibliographyPlacement Position der bibliographischen Angaben
 * @param blankLineBetweenNotes ob zwischen Zetteln eine Leerzeile eingefuegt wird
 */
public record ExportSelection(
    boolean includeBody,
    boolean includeTitle,
    boolean includeNoteNumber,
    boolean includeKeywords,
    BibliographyExportLevel bibliographyLevel,
    BibliographyPlacement bibliographyPlacement,
    boolean blankLineBetweenNotes
) {

  /**
   * Erzeugt eine Exportauswahl mit sicheren Standardwerten fuer optionale Felder.
   *
   * @param includeBody ob der Zettelinhalt ausgegeben wird
   * @param includeTitle ob die Ueberschrift ausgegeben wird
   * @param includeNoteNumber ob die Zettelnummer ausgegeben wird
   * @param includeKeywords ob Schlagwoerter ausgegeben werden
   * @param bibliographyLevel Umfang der bibliographischen Angaben
   * @param bibliographyPlacement Position der bibliographischen Angaben
   * @param blankLineBetweenNotes ob zwischen Zetteln eine Leerzeile eingefuegt wird
   */
  public ExportSelection {
    bibliographyLevel = bibliographyLevel == null ? BibliographyExportLevel.NONE : bibliographyLevel;
    bibliographyPlacement = bibliographyPlacement == null
                                ? BibliographyPlacement.AFTER_EACH_NOTE
                                : bibliographyPlacement;
  }

  /**
   * Liefert eine vollstaendige Standardauswahl.
   *
   * @return Standardauswahl fuer Zettelinhalt, Kopf, Schlagwoerter und Identify-Bibliographie
   */
  public static ExportSelection defaultSelection() {
    return new ExportSelection(
        true,
        true,
        true,
        true,
        BibliographyExportLevel.IDENTIFY,
        BibliographyPlacement.AFTER_EACH_NOTE,
        true
    );
  }
}
