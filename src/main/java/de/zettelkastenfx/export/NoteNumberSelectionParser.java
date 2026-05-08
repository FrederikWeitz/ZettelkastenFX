package de.zettelkastenfx.export;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Parst manuelle Zettelnummern-Auswahlen fuer den Export.
 */
public class NoteNumberSelectionParser {

  /**
   * Parst Einzelnummern und Bereiche aus einem Semikolon-getrennten Text.
   *
   * @param text Eingabetext
   * @return eindeutige Zettelnummern in Eingabereihenfolge
   */
  public List<Integer> parse(String text) {
    String safeText = text == null ? "" : text.trim();
    if (safeText.isBlank()) {
      return List.of();
    }

    int invalidIndex = firstInvalidCharacterIndex(safeText);
    if (invalidIndex >= 0) {
      throw new IllegalArgumentException(
          "Unerlaubtes Zeichen '" + safeText.charAt(invalidIndex) + "' an Position " + (invalidIndex + 1) + "."
      );
    }

    LinkedHashSet<Integer> numbers = new LinkedHashSet<>();
    for (String rawPart : safeText.split(";")) {
      String part = rawPart.trim();
      if (part.isBlank()) {
        continue;
      }

      if (part.contains("-")) {
        addRange(part, numbers);
      } else {
        numbers.add(Integer.parseInt(part));
      }
    }
    return new ArrayList<>(numbers);
  }

  /**
   * Ermittelt die erste Position eines unerlaubten Zeichens.
   *
   * @param text Eingabetext
   * @return nullbasierte Position oder {@code -1}
   */
  public int firstInvalidCharacterIndex(String text) {
    if (text == null) {
      return -1;
    }
    for (int i = 0; i < text.length(); i++) {
      char ch = text.charAt(i);
      if (!Character.isDigit(ch) && !Character.isWhitespace(ch) && ch != ';' && ch != '-') {
        return i;
      }
    }
    return -1;
  }

  /**
   * Erweitert einen Bereich in die Zielmenge.
   *
   * @param part Bereichstext
   * @param numbers Zielmenge
   */
  private void addRange(String part, LinkedHashSet<Integer> numbers) {
    String[] bounds = part.split("-");
    if (bounds.length != 2 || bounds[0].trim().isBlank() || bounds[1].trim().isBlank()) {
      throw new IllegalArgumentException("Ungueltiger Bereich: " + part);
    }

    int start = Integer.parseInt(bounds[0].trim());
    int end = Integer.parseInt(bounds[1].trim());
    int step = start <= end ? 1 : -1;
    for (int value = start; value != end + step; value += step) {
      numbers.add(value);
    }
  }
}
