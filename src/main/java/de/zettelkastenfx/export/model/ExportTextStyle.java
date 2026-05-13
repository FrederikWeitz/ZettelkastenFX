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
 * @param subscript ob der Text tiefgestellt ist
 * @param superscript ob der Text hochgestellt ist
 */
public record ExportTextStyle(
    boolean bold,
    boolean italic,
    boolean underline,
    boolean strikethrough,
    String fontFamily,
    String textColor,
    String backgroundColor,
    boolean subscript,
    boolean superscript
) {

  public static final ExportTextStyle PLAIN = new ExportTextStyle(false, false, false, false, null, null, null, false, false);

  /**
   * Erzeugt einen Exportstil ohne Schrift- und Farbangaben.
   *
   * @param bold ob der Text fett ist
   * @param italic ob der Text kursiv ist
   * @param underline ob der Text unterstrichen ist
   * @param strikethrough ob der Text durchgestrichen ist
   */
  public ExportTextStyle(boolean bold, boolean italic, boolean underline, boolean strikethrough) {
    this(bold, italic, underline, strikethrough, null, null, null, false, false);
  }

  /**
   * Erzeugt einen Exportstil ohne Hoch- oder Tiefstellung.
   *
   * @param bold ob der Text fett ist
   * @param italic ob der Text kursiv ist
   * @param underline ob der Text unterstrichen ist
   * @param strikethrough ob der Text durchgestrichen ist
   * @param fontFamily Schriftfamilie
   * @param textColor Zeichenfarbe
   * @param backgroundColor Hintergrundfarbe
   */
  public ExportTextStyle(boolean bold,
                         boolean italic,
                         boolean underline,
                         boolean strikethrough,
                         String fontFamily,
                         String textColor,
                         String backgroundColor) {
    this(bold, italic, underline, strikethrough, fontFamily, textColor, backgroundColor, false, false);
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
   * @param subscript ob der Text tiefgestellt ist
   * @param superscript ob der Text hochgestellt ist
   */
  public ExportTextStyle {
    fontFamily = blankToNull(normalizeFontFamily(fontFamily));
    textColor = ExportCssStyleUtil.normalizeColor(textColor);
    backgroundColor = ExportCssStyleUtil.normalizeColor(backgroundColor);
    if (subscript && superscript) {
      superscript = false;
    }
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
        ExportCssStyleUtil.cssValue(css, "-rtfx-background-color"),
        false,
        false
    );
  }

  /**
   * Prueft, ob der Stil sichtbare Formatierung enthaelt.
   *
   * @return {@code true}, wenn mindestens eine Formatierung gesetzt ist
   */
  public boolean hasFormatting() {
    return bold || italic || underline || strikethrough
        || fontFamily != null || textColor != null || backgroundColor != null
        || subscript || superscript;
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private static String normalizeFontFamily(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String value = raw.trim();
    StringBuilder family = new StringBuilder();
    boolean quoted = false;
    char quote = 0;
    for (int i = 0; i < value.length(); i++) {
      char current = value.charAt(i);
      if ((current == '\'' || current == '"') && (!quoted || current == quote)) {
        quoted = !quoted;
        quote = quoted ? current : 0;
        continue;
      }
      if (current == ',' && !quoted) {
        break;
      }
      family.append(current);
    }
    return ExportCssStyleUtil.stripQuotes(family.toString());
  }
}
