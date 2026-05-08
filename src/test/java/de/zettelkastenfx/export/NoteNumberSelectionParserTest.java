package de.zettelkastenfx.export;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prueft die manuelle Zettelnummernauswahl des Exportfensters.
 */
class NoteNumberSelectionParserTest {

  /**
   * Parst Einzelnummern und aufsteigende Bereiche.
   */
  @Test
  void parsesNumbersAndAscendingRanges() {
    assertEquals(List.of(1, 3, 4, 5, 8), new NoteNumberSelectionParser().parse("1; 3-5; 8"));
  }

  /**
   * Parst absteigende Bereiche einschliesslich beider Grenzen.
   */
  @Test
  void parsesDescendingRanges() {
    assertEquals(List.of(5, 4, 3), new NoteNumberSelectionParser().parse("5-3"));
  }

  /**
   * Meldet das erste unerlaubte Zeichen.
   */
  @Test
  void rejectsInvalidCharacters() {
    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> new NoteNumberSelectionParser().parse("1; x")
    );

    assertTrue(exception.getMessage().contains("x"));
  }
}
