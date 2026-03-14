package de.zettelkastenfx.notes.model;

/**
 * Anreicherungsobjekt für Verweislisten im Info-Popup.
 * Enthält die Ziel- oder Quellzettelnummer, den Referenztyp und die
 * aktuelle Überschrift des referenzierten Zettels.
 *
 * @param noteId ID des referenzierten Zettels
 * @param linkType Typ des Verweises
 * @param title Überschrift des referenzierten Zettels
 */
public record NoteReferenceInfo(int noteId, LinkType linkType, String title) {

  /**
   * Erzeugt einen neuen Popup-Eintrag für Zettelreferenzen.
   *
   * @throws IllegalArgumentException wenn die Zettelnummer ungültig ist
   * @throws NullPointerException wenn der Referenztyp fehlt
   */
  public NoteReferenceInfo {
    if (noteId <= 0) {
      throw new IllegalArgumentException("noteId muss größer als 0 sein.");
    }
    if (linkType == null) {
      throw new NullPointerException("linkType darf nicht null sein.");
    }
    title = title == null ? "" : title.trim();
  }

  /**
   * Liefert den Tooltiptext im Format
   * {@code Referenztyp: Überschrift}.
   *
   * @return Tooltiptext für die Oberfläche
   */
  public String tooltipText() {
    String resolvedTitle = title == null || title.isBlank() ? "—" : title;
    return linkType.displayName() + ": " + resolvedTitle;
  }
}
