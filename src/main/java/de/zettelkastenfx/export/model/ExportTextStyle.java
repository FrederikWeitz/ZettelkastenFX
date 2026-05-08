package de.zettelkastenfx.export.model;

/**
 * Beschreibt die exportrelevanten Inline-Formatierungen eines Textbereichs.
 *
 * @param bold ob der Text fett ist
 * @param italic ob der Text kursiv ist
 * @param underline ob der Text unterstrichen ist
 * @param strikethrough ob der Text durchgestrichen ist
 * @param fontFamily Schriftfamilie oder {@code null}
 * @param textColor Zeichenfarbe als Hex-Farbe oder {@code null}
 * @param backgroundColor Hintergrundfarbe als Hex-Farbe oder {@code null}
 */
public record ExportTextStyle(
    boolean bold,
    boolean italic,
    boolean underline,
    boolean strikethrough,
    String fontFamily,
    String textColor,
    String backgroundColor
) {

  public static final ExportTextStyle PLAIN = new ExportTextStyle(false, false, false, false, null, null, null);

  /**
   * Erzeugt einen Exportstil ohne Schrift- und Farbangaben.
   *
   * @param bold ob der Text fett ist
   * @param italic ob der Text kursiv ist
   * @param underline ob der Text unterstrichen ist
   * @param strikethrough ob der Text durchgestrichen ist
   */
  public ExportTextStyle(boolean bold, boolean italic, boolean underline, boolean strikethrough) {
    this(bold, italic, underline, strikethrough, null, null, null);
  }

  /**
   * Erzeugt einen Exportstil mit bereinigten optionalen Werten.
   *
   * @param bold ob der Text fett ist
   * @param italic ob der Text kursiv ist
   * @param underline ob der Text unterstrichen ist
   * @param strikethrough ob der Text durchgestrichen ist
   * @param fontFamily Schriftfamilie
   * @param textColor Zeichenfarbe
   * @param backgroundColor Hintergrundfarbe
   */
  public ExportTextStyle {
    fontFamily = blankToNull(ExportCssStyleUtil.stripQuotes(fontFamily));
    textColor = ExportCssStyleUtil.normalizeColor(textColor);
    backgroundColor = ExportCssStyleUtil.normalizeColor(backgroundColor);
  }

  /**
   * Erzeugt einen Exportstil aus einem Inline-CSS-String des Editors.
   *
   * @param inlineCss Inline-CSS des RichTextFX-Segments
   * @return exportrelevanter Textstil
   */
  public static ExportTextStyle fromInlineCss(String inlineCss) {
    String css = inlineCss == null ? "" : inlineCss;
    return new ExportTextStyle(
        ExportCssStyleUtil.cssValueEquals(css, "-fx-font-weight", "bold"),
        ExportCssStyleUtil.cssValueEquals(css, "-fx-font-style", "italic"),
        ExportCssStyleUtil.cssValueEquals(css, "-fx-underline", "true"),
        ExportCssStyleUtil.cssValueEquals(css, "-fx-strikethrough", "true"),
        ExportCssStyleUtil.cssValue(css, "-fx-font-family"),
        ExportCssStyleUtil.cssValue(css, "-fx-fill"),
        ExportCssStyleUtil.cssValue(css, "-rtfx-background-color")
    );
  }

  /**
   * Prueft, ob der Stil sichtbare Formatierung enthaelt.
   *
   * @return {@code true}, wenn mindestens eine Formatierung gesetzt ist
   */
  public boolean hasFormatting() {
    return bold || italic || underline || strikethrough
        || fontFamily != null || textColor != null || backgroundColor != null;
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
