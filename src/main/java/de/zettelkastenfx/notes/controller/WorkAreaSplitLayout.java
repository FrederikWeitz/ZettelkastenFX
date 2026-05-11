package de.zettelkastenfx.notes.controller;

/**
 * Buendelt die stabilen Breiten- und Teilerberechnungen der Arbeitsflaeche.
 */
final class WorkAreaSplitLayout {

  private static final double MIN_STABLE_WIDTH = 1.0;
  private static final double MIN_DIVIDER_POSITION = 0.05;
  private static final double MAX_DIVIDER_POSITION = 0.95;
  private static final double DEFAULT_DIVIDER_POSITION = 0.5;

  /**
   * Verhindert Instanzen der reinen Hilfsklasse.
   */
  private WorkAreaSplitLayout() {
  }

  /**
   * Liefert einen belastbaren positiven Breitenwert oder einen belastbaren Fallback.
   *
   * @param width zu pruefender Breitenwert
   * @param fallback Ersatzwert
   * @return positive Breite oder {@code 0.0}, wenn beide Werte unbrauchbar sind
   */
  static double resolvePositiveWidth(double width, double fallback) {
    if (isStableWidth(width)) {
      return width;
    }
    return isStableWidth(fallback) ? fallback : 0.0;
  }

  /**
   * Ermittelt eine stabile Teilerbreite aus Messwert, Vorwert und Standardwert.
   *
   * @param measuredWidth gemessene Breite des SplitPane-Teilers
   * @param previousWidth zuletzt bekannte Teilerbreite
   * @param defaultWidth Standardbreite
   * @return nutzbare Teilerbreite oder {@code 0.0}, wenn kein Wert belastbar ist
   */
  static double resolveDividerWidth(double measuredWidth, double previousWidth, double defaultWidth) {
    if (isStableWidth(measuredWidth)) {
      return measuredWidth;
    }
    return resolvePositiveWidth(previousWidth, defaultWidth);
  }

  /**
   * Berechnet die Fensteraenderung beim Ein- und Ausklappen der Service-Area.
   *
   * @param serviceAreaWidth bekannte Breite der Service-Area
   * @param dividerWidth bekannte Breite des entfernten oder eingefuegten Teilers
   * @param defaultServiceAreaWidth Standardbreite der Service-Area
   * @param defaultDividerWidth Standardbreite des SplitPane-Teilers
   * @return Service-Breite plus Teilerbreite
   */
  static double serviceAreaToggleWidthDelta(
      double serviceAreaWidth,
      double dividerWidth,
      double defaultServiceAreaWidth,
      double defaultDividerWidth
  ) {
    return resolvePositiveWidth(serviceAreaWidth, defaultServiceAreaWidth)
        + resolveDividerWidth(dividerWidth, 0.0, defaultDividerWidth);
  }

  /**
   * Berechnet die Zielbreite der Arbeitsflaeche im eingeklappten Zustand.
   *
   * @param noteAreaWidth gespeicherte Breite der Zettelanzeige
   * @param keywordAreaWidth gespeicherte Breite der Stichwortanzeige
   * @param dividerWidth gespeicherte Breite des sichtbaren Teilers
   * @return Breite aus Zettelanzeige, Stichwortanzeige und Teiler oder {@code 0.0}
   */
  static double collapsedPrimaryWidth(double noteAreaWidth, double keywordAreaWidth, double dividerWidth) {
    if (!isStableWidth(noteAreaWidth) || !isStableWidth(keywordAreaWidth)) {
      return 0.0;
    }
    return noteAreaWidth + keywordAreaWidth + resolvePositiveWidth(dividerWidth, 0.0);
  }

  /**
   * Berechnet die Teilerposition fuer den eingeklappten Zwei-Spalten-Zustand.
   *
   * @param noteAreaWidth gespeicherte Breite der Zettelanzeige
   * @param totalWidth aktuelle Breite der Arbeitsflaeche
   * @return begrenzte Teilerposition
   */
  static double collapsedDividerPosition(double noteAreaWidth, double totalWidth) {
    if (!isStableWidth(noteAreaWidth) || !isStableWidth(totalWidth)) {
      return DEFAULT_DIVIDER_POSITION;
    }
    return clampDividerPosition(noteAreaWidth / totalWidth);
  }

  /**
   * Berechnet die Teilerposition fuer den eingeklappten Zustand ueber die Mitte des Teilers.
   *
   * @param noteAreaWidth gespeicherte Breite der Zettelanzeige
   * @param totalWidth aktuelle Breite der Arbeitsflaeche
   * @param dividerWidth Breite des sichtbaren Teilers
   * @return begrenzte Teilerposition
   */
  static double collapsedDividerPosition(double noteAreaWidth, double totalWidth, double dividerWidth) {
    if (!isStableWidth(noteAreaWidth) || !isStableWidth(totalWidth)) {
      return DEFAULT_DIVIDER_POSITION;
    }
    double dividerCenterOffset = resolvePositiveWidth(dividerWidth, 0.0) / 2.0;
    return clampDividerPosition((noteAreaWidth + dividerCenterOffset) / totalWidth);
  }

  /**
   * Berechnet die erste Teilerposition fuer den aufgeklappten Drei-Spalten-Zustand.
   *
   * @param noteAreaWidth gespeicherte Breite der Zettelanzeige
   * @param totalWidth aktuelle Breite der Arbeitsflaeche
   * @return begrenzte erste Teilerposition
   */
  static double firstExpandedDividerPosition(double noteAreaWidth, double totalWidth) {
    if (!isStableWidth(noteAreaWidth) || !isStableWidth(totalWidth)) {
      return DEFAULT_DIVIDER_POSITION;
    }
    return clampDividerPosition(noteAreaWidth / totalWidth);
  }

  /**
   * Berechnet die erste Teilerposition ueber die Mitte des ersten Teilers.
   *
   * @param noteAreaWidth gespeicherte Breite der Zettelanzeige
   * @param totalWidth aktuelle Breite der Arbeitsflaeche
   * @param dividerWidth Breite eines horizontalen SplitPane-Teilers
   * @return begrenzte erste Teilerposition
   */
  static double firstExpandedDividerPosition(double noteAreaWidth, double totalWidth, double dividerWidth) {
    if (!isStableWidth(noteAreaWidth) || !isStableWidth(totalWidth)) {
      return DEFAULT_DIVIDER_POSITION;
    }
    double dividerCenterOffset = resolvePositiveWidth(dividerWidth, 0.0) / 2.0;
    return clampDividerPosition((noteAreaWidth + dividerCenterOffset) / totalWidth);
  }

  /**
   * Berechnet die zweite Teilerposition fuer den aufgeklappten Drei-Spalten-Zustand.
   *
   * @param noteAreaWidth gespeicherte Breite der Zettelanzeige
   * @param keywordAreaWidth gespeicherte Breite der Stichwortanzeige
   * @param totalWidth aktuelle Breite der Arbeitsflaeche
   * @param firstDividerPosition erste Teilerposition
   * @return begrenzte zweite Teilerposition mit Mindestabstand zur ersten Position
   */
  static double secondExpandedDividerPosition(
      double noteAreaWidth,
      double keywordAreaWidth,
      double totalWidth,
      double firstDividerPosition
  ) {
    double secondDividerPosition;
    if (!isStableWidth(noteAreaWidth) || !isStableWidth(keywordAreaWidth) || !isStableWidth(totalWidth)) {
      secondDividerPosition = firstDividerPosition + MIN_DIVIDER_POSITION;
    } else {
      secondDividerPosition = (noteAreaWidth + keywordAreaWidth) / totalWidth;
    }

    secondDividerPosition = clampDividerPosition(secondDividerPosition);
    if (secondDividerPosition <= firstDividerPosition + MIN_DIVIDER_POSITION) {
      secondDividerPosition = clampDividerPosition(firstDividerPosition + MIN_DIVIDER_POSITION);
    }
    return secondDividerPosition;
  }

  /**
   * Berechnet die zweite Teilerposition ueber die Mitte des zweiten Teilers.
   *
   * @param noteAreaWidth gespeicherte Breite der Zettelanzeige
   * @param keywordAreaWidth gespeicherte Breite der Stichwortanzeige
   * @param totalWidth aktuelle Breite der Arbeitsflaeche
   * @param dividerWidth Breite eines horizontalen SplitPane-Teilers
   * @param firstDividerPosition erste Teilerposition
   * @return begrenzte zweite Teilerposition mit Mindestabstand zur ersten Position
   */
  static double secondExpandedDividerPosition(
      double noteAreaWidth,
      double keywordAreaWidth,
      double totalWidth,
      double dividerWidth,
      double firstDividerPosition
  ) {
    double secondDividerPosition;
    if (!isStableWidth(noteAreaWidth) || !isStableWidth(keywordAreaWidth) || !isStableWidth(totalWidth)) {
      secondDividerPosition = firstDividerPosition + MIN_DIVIDER_POSITION;
    } else {
      double divider = resolvePositiveWidth(dividerWidth, 0.0);
      secondDividerPosition = (noteAreaWidth + keywordAreaWidth + divider + divider / 2.0) / totalWidth;
    }

    secondDividerPosition = clampDividerPosition(secondDividerPosition);
    if (secondDividerPosition <= firstDividerPosition + MIN_DIVIDER_POSITION) {
      secondDividerPosition = clampDividerPosition(firstDividerPosition + MIN_DIVIDER_POSITION);
    }
    return secondDividerPosition;
  }

  /**
   * Begrenzt eine SplitPane-Teilerposition auf einen stabilen Innenbereich.
   *
   * @param value gewuenschter Teilerwert
   * @return begrenzte Teilerposition
   */
  static double clampDividerPosition(double value) {
    if (!Double.isFinite(value)) {
      return DEFAULT_DIVIDER_POSITION;
    }
    return Math.max(MIN_DIVIDER_POSITION, Math.min(MAX_DIVIDER_POSITION, value));
  }

  /**
   * Prueft, ob ein Breitenwert fuer Layout-Rechnungen nutzbar ist.
   *
   * @param width Breitenwert
   * @return {@code true}, wenn der Wert endlich und groesser als ein Pixel ist
   */
  private static boolean isStableWidth(double width) {
    return Double.isFinite(width) && width > MIN_STABLE_WIDTH;
  }
}
