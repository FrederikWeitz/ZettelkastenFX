package de.zettelkastenfx.export.model;

/**
 * Beschreibt einen zusammenhaengend formatierten Textbereich.
 *
 * @param text Textinhalt
 * @param style Inline-Formatierung
 */
public record ExportTextRun(String text, ExportTextStyle style) {

  /**
   * Erzeugt einen formatierten Textbereich mit defensiven Nullwerten.
   *
   * @param text Textinhalt
   * @param style Inline-Formatierung
   */
  public ExportTextRun {
    text = text == null ? "" : text;
    style = style == null ? ExportTextStyle.PLAIN : style;
  }
}
