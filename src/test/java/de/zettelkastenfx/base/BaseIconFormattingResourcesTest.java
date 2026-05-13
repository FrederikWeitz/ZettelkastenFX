package de.zettelkastenfx.base;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueft die Icon-Ressourcen fuer die Formatierungsfunktionen.
 */
class BaseIconFormattingResourcesTest {

  /**
   * Laedt alle neuen Formatierungsicons ueber BaseIcon.
   */
  @Test
  void formattingIconsLoadFromResources() {
    List<BaseIcon> icons = List.of(
        BaseIcon.INSERT_CITATION,
        BaseIcon.IMAGE,
        BaseIcon.SEPARATOR,
        BaseIcon.TABLE,
        BaseIcon.TABLE_COLUMNS_INSERT_LEFT,
        BaseIcon.TABLE_COLUMNS_INSERT_RIGHT,
        BaseIcon.TABLE_ROWS_INSERT_ABOVE_WORD,
        BaseIcon.TABLE_ROWS_INSERT_BELOW_WORD,
        BaseIcon.TABLE_ROW_DELETE,
        BaseIcon.TABLE_COLUMN_DELETE
    );

    assertAll(icons.stream()
                   .map(icon -> () -> assertTrue(icon.image().getWidth() > 0.0, icon.name())));
  }
}
