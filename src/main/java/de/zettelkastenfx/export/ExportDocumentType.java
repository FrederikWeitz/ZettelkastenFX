package de.zettelkastenfx.export;

/**
 * Beschreibt das Zielformat eines Zettelexports.
 */
public enum ExportDocumentType {
  DOCX(".docx"),
  RTF(".rtf"),
  PDF(".pdf"),
  HTML(".html");

  private final String extension;

  ExportDocumentType(String extension) {
    this.extension = extension;
  }

  /**
   * Liefert die Dateiendung des Zielformats.
   *
   * @return Dateiendung inklusive Punkt
   */
  public String extension() {
    return extension;
  }
}
