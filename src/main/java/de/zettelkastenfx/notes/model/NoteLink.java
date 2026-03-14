package de.zettelkastenfx.notes.model;

/**
 * Verknüpfung von einem Zettel zu einem Folgezettel.
 *
 * @param toNoteId Ziel-Zettelnummer
 * @param type fachlicher Typ der Verknüpfung
 */
public record NoteLink(int toNoteId, LinkType type) {

  /**
   * Erzeugt eine typisierte Zettelverknüpfung.
   *
   * @throws IllegalArgumentException wenn die Ziel-Zettelnummer ungültig ist
   * @throws NullPointerException wenn kein Linktyp angegeben wurde
   */
  public NoteLink {
    if (toNoteId <= 0) {
      throw new IllegalArgumentException("toNoteId muss größer als 0 sein.");
    }
    if (type == null) {
      throw new NullPointerException("type darf nicht null sein.");
    }
  }
}