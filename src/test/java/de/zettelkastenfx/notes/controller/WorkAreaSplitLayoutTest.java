package de.zettelkastenfx.notes.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Prueft die Breitenlogik der Zettel-Arbeitsflaeche.
 */
class WorkAreaSplitLayoutTest {

  private static final double EPSILON = 0.000_001;

  /**
   * Nutzt positive Breiten und ersetzt nicht nutzbare Werte durch den Fallback.
   */
  @Test
  void resolvesPositiveWidthsAndRejectsInvalidValues() {
    assertEquals(120.0, WorkAreaSplitLayout.resolvePositiveWidth(120.0, 80.0), EPSILON);
    assertEquals(80.0, WorkAreaSplitLayout.resolvePositiveWidth(0.0, 80.0), EPSILON);
    assertEquals(80.0, WorkAreaSplitLayout.resolvePositiveWidth(Double.NaN, 80.0), EPSILON);
    assertEquals(0.0, WorkAreaSplitLayout.resolvePositiveWidth(Double.POSITIVE_INFINITY, -1.0), EPSILON);
  }

  /**
   * Verwendet bei der Teilerbreite zuerst Messwert, dann Vorwert und dann Standardwert.
   */
  @Test
  void resolvesDividerWidthFromMeasuredRememberedAndDefaultValues() {
    assertEquals(7.0, WorkAreaSplitLayout.resolveDividerWidth(7.0, 9.0, 8.0), EPSILON);
    assertEquals(9.0, WorkAreaSplitLayout.resolveDividerWidth(0.0, 9.0, 8.0), EPSILON);
    assertEquals(8.0, WorkAreaSplitLayout.resolveDividerWidth(Double.NaN, 0.0, 8.0), EPSILON);
  }

  /**
   * Beruecksichtigt beim Fenster-Toggle die Service-Area und den verschwindenden Teiler.
   */
  @Test
  void addsServiceAreaAndDividerForToggleDelta() {
    assertEquals(
        368.0,
        WorkAreaSplitLayout.serviceAreaToggleWidthDelta(360.0, 8.0, 320.0, 6.0),
        EPSILON
    );
  }

  /**
   * Nutzt Standardwerte fuer das Toggle-Delta, wenn aktuelle Messwerte fehlen.
   */
  @Test
  void usesFallbacksForToggleDeltaWhenMeasurementsAreMissing() {
    assertEquals(
        326.0,
        WorkAreaSplitLayout.serviceAreaToggleWidthDelta(0.0, Double.NaN, 320.0, 6.0),
        EPSILON
    );
  }

  /**
   * Fixiert im eingeklappten Zustand nur Zettelanzeige, Stichwortanzeige und den sichtbaren Teiler.
   */
  @Test
  void computesCollapsedPrimaryWidthFromNoteKeywordAndDividerOnly() {
    assertEquals(708.0, WorkAreaSplitLayout.collapsedPrimaryWidth(420.0, 280.0, 8.0), EPSILON);
  }

  /**
   * Verhindert eine falsche Breitenfixierung, wenn eine primaere Spalte noch nicht gemessen wurde.
   */
  @Test
  void rejectsCollapsedPrimaryWidthWhenOnePrimaryColumnIsMissing() {
    assertEquals(0.0, WorkAreaSplitLayout.collapsedPrimaryWidth(0.0, 280.0, 8.0), EPSILON);
    assertEquals(0.0, WorkAreaSplitLayout.collapsedPrimaryWidth(420.0, Double.NaN, 8.0), EPSILON);
    assertEquals(0.0, WorkAreaSplitLayout.collapsedPrimaryWidth(Double.NEGATIVE_INFINITY, 280.0, 8.0), EPSILON);
  }

  /**
   * Stellt sicher, dass wiederholtes Einklappen keine ueberschuessige Fensterbreite aufsummiert.
   */
  @Test
  void collapsedPrimaryWidthDoesNotAccumulateWindowSurplusAcrossToggleCycles() {
    double expectedCollapsedWidth = WorkAreaSplitLayout.collapsedPrimaryWidth(420.0, 280.0, 8.0);

    for (int cycle = 0; cycle < 5; cycle++) {
      assertEquals(expectedCollapsedWidth, WorkAreaSplitLayout.collapsedPrimaryWidth(420.0, 280.0, 8.0), EPSILON);
    }
  }

  /**
   * Setzt den eingeklappten Teiler auf den gemerkten Anteil der Zettelanzeige.
   */
  @Test
  void collapsedDividerPositionUsesLockedPrimaryWidth() {
    double lockedWidth = WorkAreaSplitLayout.collapsedPrimaryWidth(420.0, 280.0, 8.0);

    assertEquals(420.0 / lockedWidth, WorkAreaSplitLayout.collapsedDividerPosition(420.0, lockedWidth), EPSILON);
  }

  /**
   * Haelt im aufgeklappten Zustand die rechte Kante der Stichwortanzeige auf der gemerkten Pixelposition.
   */
  @Test
  void expandedDividerPositionsKeepKeywordEdgeAtRememberedPixelWidth() {
    double totalWidth = 1208.0;
    double firstDivider = WorkAreaSplitLayout.firstExpandedDividerPosition(420.0, totalWidth);
    double secondDivider = WorkAreaSplitLayout.secondExpandedDividerPosition(420.0, 280.0, totalWidth, firstDivider);

    assertEquals(420.0, firstDivider * totalWidth, EPSILON);
    assertEquals(700.0, secondDivider * totalWidth, EPSILON);
  }

  /**
   * Haelt einen Mindestabstand zwischen den Teilern und begrenzt den Maximalwert.
   */
  @Test
  void expandedDividerPositionsMaintainMinimumDistanceAndUpperBound() {
    double firstDivider = WorkAreaSplitLayout.firstExpandedDividerPosition(940.0, 1000.0);
    double secondDivider = WorkAreaSplitLayout.secondExpandedDividerPosition(940.0, 5.0, 1000.0, firstDivider);

    assertEquals(0.94, firstDivider, EPSILON);
    assertEquals(0.95, secondDivider, EPSILON);
  }

  /**
   * Begrenzt Teilerwerte und ersetzt nicht endliche Werte durch einen neutralen Mittelwert.
   */
  @Test
  void clampsDividerPositionsAndHandlesNonFiniteValues() {
    assertEquals(0.05, WorkAreaSplitLayout.clampDividerPosition(-0.5), EPSILON);
    assertEquals(0.95, WorkAreaSplitLayout.clampDividerPosition(1.4), EPSILON);
    assertEquals(0.4, WorkAreaSplitLayout.clampDividerPosition(0.4), EPSILON);
    assertEquals(0.5, WorkAreaSplitLayout.clampDividerPosition(Double.NaN), EPSILON);
    assertEquals(0.5, WorkAreaSplitLayout.clampDividerPosition(Double.POSITIVE_INFINITY), EPSILON);
  }
}
