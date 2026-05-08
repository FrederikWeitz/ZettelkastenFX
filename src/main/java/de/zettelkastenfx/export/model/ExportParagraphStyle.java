package de.zettelkastenfx.export.model;

/**
 * Beschreibt die exportrelevante Formatierung eines Absatzes.
 *
 * @param alignment Textausrichtung oder {@code null}
 * @param indentPixels linker Einzug in Pixeln
 * @param fontSizePixels Absatz-Schriftgroesse oder {@code null}
 * @param bold ob der Absatz fett formatiert ist
 */
public record ExportParagraphStyle(
    String alignment,
    int indentPixels,
    Integer fontSizePixels,
    boolean bold
) {

  public static final ExportParagraphStyle PLAIN = new ExportParagraphStyle(null, 0, null, false);

  /**
   * Erzeugt einen Export-Absatzstil aus dem Absatz-CSS des Editors.
   *
   * @param paragraphCss Absatz-CSS
   * @return exportrelevanter Absatzstil
   */
  public static ExportParagraphStyle fromParagraphCss(String paragraphCss) {
    return new ExportParagraphStyle(
        normalizeAlignment(ExportCssStyleUtil.cssValue(paragraphCss, "-fx-text-alignment")),
        ExportCssStyleUtil.leftPaddingPixels(paragraphCss),
        ExportCssStyleUtil.pixelValue(paragraphCss, "-fx-font-size"),
        ExportCssStyleUtil.cssValueEquals(paragraphCss, "-fx-font-weight", "bold")
    );
  }

  /**
   * Prueft, ob der Absatz abweichende Formatierung besitzt.
   *
   * @return {@code true}, wenn der Absatz formatiert ist
   */
  public boolean hasFormatting() {
    return alignment != null || indentPixels > 0 || fontSizePixels != null || bold;
  }

  /**
   * Normalisiert den CSS-Ausrichtungswert.
   *
   * @param value CSS-Wert
   * @return normalisierte Ausrichtung oder {@code null}
   */
  private static String normalizeAlignment(String value) {
    String normalized = ExportCssStyleUtil.stripQuotes(value);
    if (normalized == null || normalized.isBlank()) {
      return null;
    }
    return switch (normalized.toLowerCase()) {
      case "center", "right", "justify" -> normalized.toLowerCase();
      default -> "left";
    };
  }
}
