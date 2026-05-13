package de.zettelkastenfx.export.model;

/**
 * Beschreibt die exportrelevante Formatierung eines Absatzes.
 *
 * @param alignment Textausrichtung oder {@code null}
 * @param indentPixels linker Einzug in Pixeln
 * @param spacingBeforePixels Abstand vor dem Absatz in Pixeln
 * @param fontSizePixels Absatz-Schriftgroesse oder {@code null}
 * @param bold ob der Absatz fett formatiert ist
 * @param backgroundColor Hintergrundfarbe als Hex-Farbe oder {@code null}
 * @param headingLevel Ueberschriftenebene oder {@code null}
 * @param listType Listentyp oder {@code null}
 * @param listNumber laufende Nummer bei geordneten Listen
 */
public record ExportParagraphStyle(
    String alignment,
    int indentPixels,
    int spacingBeforePixels,
    Integer fontSizePixels,
    boolean bold,
    String backgroundColor,
    Integer headingLevel,
    String listType,
    int listNumber
) {

  public static final ExportParagraphStyle PLAIN = new ExportParagraphStyle(null, 0, 0, null, false, null, null, null, 0);

  /**
   * Erzeugt einen Absatzstil ohne Hintergrundfarbe.
   *
   * @param alignment Textausrichtung oder {@code null}
   * @param indentPixels linker Einzug in Pixeln
   * @param spacingBeforePixels Abstand vor dem Absatz in Pixeln
   * @param fontSizePixels Absatz-Schriftgroesse oder {@code null}
   * @param bold ob der Absatz fett formatiert ist
   */
  public ExportParagraphStyle(String alignment,
                              int indentPixels,
                              int spacingBeforePixels,
                              Integer fontSizePixels,
                              boolean bold) {
    this(alignment, indentPixels, spacingBeforePixels, fontSizePixels, bold, null, null, null, 0);
  }

  /**
   * Erzeugt einen Absatzstil ohne Listen- oder Ueberschriftenmetadaten.
   *
   * @param alignment Textausrichtung oder {@code null}
   * @param indentPixels linker Einzug in Pixeln
   * @param spacingBeforePixels Abstand vor dem Absatz in Pixeln
   * @param fontSizePixels Absatz-Schriftgroesse oder {@code null}
   * @param bold ob der Absatz fett formatiert ist
   * @param backgroundColor Hintergrundfarbe
   */
  public ExportParagraphStyle(String alignment,
                              int indentPixels,
                              int spacingBeforePixels,
                              Integer fontSizePixels,
                              boolean bold,
                              String backgroundColor) {
    this(alignment, indentPixels, spacingBeforePixels, fontSizePixels, bold, backgroundColor, null, null, 0);
  }

  /**
   * Erzeugt einen Absatzstil mit normalisierten optionalen Werten.
   *
   * @param alignment Textausrichtung oder {@code null}
   * @param indentPixels linker Einzug in Pixeln
   * @param spacingBeforePixels Abstand vor dem Absatz in Pixeln
   * @param fontSizePixels Absatz-Schriftgroesse oder {@code null}
   * @param bold ob der Absatz fett formatiert ist
   * @param backgroundColor Hintergrundfarbe
   * @param headingLevel Ueberschriftenebene
   * @param listType Listentyp
   * @param listNumber laufende Nummer bei geordneten Listen
   */
  public ExportParagraphStyle {
    alignment = normalizeAlignment(alignment);
    indentPixels = Math.max(0, indentPixels);
    spacingBeforePixels = Math.max(0, spacingBeforePixels);
    backgroundColor = ExportCssStyleUtil.normalizeColor(backgroundColor);
    headingLevel = headingLevel == null || headingLevel < 1 || headingLevel > 2 ? null : headingLevel;
    listType = normalizeListType(listType);
    listNumber = listNumber < 1 ? 0 : listNumber;
  }

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
        ExportCssStyleUtil.topPaddingPixels(paragraphCss),
        ExportCssStyleUtil.pixelValue(paragraphCss, "-fx-font-size"),
        ExportCssStyleUtil.cssValueEquals(paragraphCss, "-fx-font-weight", "bold"),
        ExportCssStyleUtil.cssValue(paragraphCss, "-fx-background-color"),
        null,
        null,
        0
    );
  }

  /**
   * Prueft, ob der Absatz abweichende Formatierung besitzt.
   *
   * @return {@code true}, wenn der Absatz formatiert ist
   */
  public boolean hasFormatting() {
    return alignment != null || indentPixels > 0 || spacingBeforePixels > 0 || fontSizePixels != null || bold
        || backgroundColor != null || headingLevel != null || listType != null;
  }

  /**
   * Prueft, ob der Absatz eine Ueberschrift ist.
   *
   * @return {@code true}, wenn eine Ueberschriftenebene gesetzt ist
   */
  public boolean isHeading() {
    return headingLevel != null;
  }

  /**
   * Prueft, ob der Absatz ein Listeneintrag ist.
   *
   * @return {@code true}, wenn ein Listentyp gesetzt ist
   */
  public boolean isListItem() {
    return listType != null;
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

  private static String normalizeListType(String value) {
    String normalized = ExportCssStyleUtil.stripQuotes(value);
    if (normalized == null || normalized.isBlank()) {
      return null;
    }
    return switch (normalized.toLowerCase()) {
      case "ul", "bullet", "unordered" -> "ul";
      case "ol", "ordered", "decimal" -> "ol";
      default -> null;
    };
  }
}
