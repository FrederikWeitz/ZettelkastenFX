package de.zettelkastenfx.notes.model;

public enum LinkType {
  SERIE,
  IDEE,
  FRAGE,
  KRITIK,
  AUFGABE;

  /**
   * Liefert die für die Oberfläche bestimmte deutschsprachige Bezeichnung
   * des Referenztyps.
   *
   * @return Anzeigename des Referenztyps
   */
  public String displayName() {
    return switch (this) {
      case SERIE -> "Folgezettel";
      case IDEE -> "Idee";
      case FRAGE -> "Frage";
      case KRITIK -> "Kritik";
      case AUFGABE -> "Aufgabe";
    };
  }
}