package de.zettelkastenfx.export.model;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Liest die fuer den Export relevanten Werte aus Inline-CSS des Editors.
 */
final class ExportCssStyleUtil {

  private ExportCssStyleUtil() {
  }

  /**
   * Prueft einen CSS-Eigenschaftswert tolerant gegen Leerzeichen und Quotes.
   *
   * @param css CSS-Text
   * @param property Eigenschaftsname
   * @param expected erwarteter Wert
   * @return {@code true}, wenn der Wert vorhanden ist
   */
  static boolean cssValueEquals(String css, String property, String expected) {
    String actual = cssValue(css, property);
    return actual != null && stripQuotes(actual).equalsIgnoreCase(stripQuotes(expected));
  }

  /**
   * Liest einen CSS-Eigenschaftswert.
   *
   * @param css CSS-Text
   * @param property Eigenschaftsname
   * @return Eigenschaftswert oder {@code null}
   */
  static String cssValue(String css, String property) {
    String normalized = css == null ? "" : css;
    var matcher = Pattern.compile(Pattern.quote(property) + "\\s*:\\s*([^;]+)", Pattern.CASE_INSENSITIVE)
                         .matcher(normalized);
    return matcher.find() ? matcher.group(1).trim() : null;
  }

  /**
   * Liest einen Pixelwert aus einer CSS-Eigenschaft.
   *
   * @param css CSS-Text
   * @param property Eigenschaftsname
   * @return Pixelwert oder {@code null}
   */
  static Integer pixelValue(String css, String property) {
    String value = cssValue(css, property);
    if (value == null) {
      return null;
    }
    String cleaned = value.toLowerCase(Locale.ROOT).replace("px", "").trim();
    try {
      return Integer.parseInt(cleaned);
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  /**
   * Liest den linken Padding-Wert aus einer CSS-Padding-Deklaration.
   *
   * @param css CSS-Text
   * @return linker Einzug in Pixeln
   */
  static int leftPaddingPixels(String css) {
    String value = cssValue(css, "-fx-padding");
    if (value == null) {
      return 0;
    }
    String[] parts = value.split("\\s+");
    if (parts.length != 4) {
      return 0;
    }
    String left = parts[3].toLowerCase(Locale.ROOT).replace("px", "").trim();
    try {
      return Math.max(0, Integer.parseInt(left));
    } catch (NumberFormatException ignored) {
      return 0;
    }
  }

  /**
   * Normalisiert eine CSS-Farbe auf sechsstellige Hex-Schreibweise.
   *
   * @param raw Farbe aus CSS
   * @return Hex-Farbe mit fuehrendem # oder {@code null}
   */
  static String normalizeColor(String raw) {
    if (raw == null) {
      return null;
    }
    String value = stripQuotes(raw).trim().toLowerCase(Locale.ROOT);
    if (value.isBlank() || "transparent".equals(value)) {
      return null;
    }
    if (value.matches("#[0-9a-f]{6}")) {
      return value;
    }
    if (value.matches("#[0-9a-f]{3}")) {
      return "#" + value.charAt(1) + value.charAt(1)
          + value.charAt(2) + value.charAt(2)
          + value.charAt(3) + value.charAt(3);
    }
    var rgb = Pattern.compile("rgba?\\(([^)]+)\\)").matcher(value);
    if (rgb.find()) {
      String[] parts = rgb.group(1).split(",");
      if (parts.length >= 3) {
        try {
          int r = clampColor(Integer.parseInt(parts[0].trim()));
          int g = clampColor(Integer.parseInt(parts[1].trim()));
          int b = clampColor(Integer.parseInt(parts[2].trim()));
          return String.format("#%02x%02x%02x", r, g, b);
        } catch (NumberFormatException ignored) {
          return null;
        }
      }
    }
    return null;
  }

  /**
   * Entfernt einfache und doppelte Quotes.
   *
   * @param value Rohwert
   * @return Wert ohne Quotes
   */
  static String stripQuotes(String value) {
    return value == null ? null : value.replace("'", "").replace("\"", "").trim();
  }

  private static int clampColor(int value) {
    return Math.max(0, Math.min(255, value));
  }
}
