package de.zettelkastenfx.export.model;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Liest die fuer den Export relevanten Werte aus Inline-CSS des Editors.
 */
public final class ExportCssStyleUtil {

  private static final Map<String, String> NAMED_COLORS = Map.ofEntries(
      Map.entry("black", "#000000"),
      Map.entry("white", "#ffffff"),
      Map.entry("red", "#ff0000"),
      Map.entry("green", "#008000"),
      Map.entry("blue", "#0000ff"),
      Map.entry("yellow", "#ffff00"),
      Map.entry("gray", "#808080"),
      Map.entry("grey", "#808080"),
      Map.entry("silver", "#c0c0c0"),
      Map.entry("maroon", "#800000"),
      Map.entry("purple", "#800080"),
      Map.entry("fuchsia", "#ff00ff"),
      Map.entry("magenta", "#ff00ff"),
      Map.entry("lime", "#00ff00"),
      Map.entry("olive", "#808000"),
      Map.entry("navy", "#000080"),
      Map.entry("teal", "#008080"),
      Map.entry("aqua", "#00ffff"),
      Map.entry("cyan", "#00ffff"),
      Map.entry("orange", "#ffa500")
  );

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
  public static boolean cssValueEquals(String css, String property, String expected) {
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
  public static String cssValue(String css, String property) {
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
  public static Integer pixelValue(String css, String property) {
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
  public static int leftPaddingPixels(String css) {
    return paddingPixels(css, 3);
  }

  /**
   * Liest den oberen Padding-Wert aus einer CSS-Padding-Deklaration.
   *
   * @param css CSS-Text
   * @return oberer Abstand in Pixeln
   */
  public static int topPaddingPixels(String css) {
    return paddingPixels(css, 0);
  }

  /**
   * Liest einen Padding-Wert aus einer CSS-Padding-Deklaration.
   *
   * @param css CSS-Text
   * @param index Position im vierteiligen Padding-Wert
   * @return Padding in Pixeln
   */
  private static int paddingPixels(String css, int index) {
    String value = cssValue(css, "-fx-padding");
    if (value == null) {
      return 0;
    }
    String[] parts = value.split("\\s+");
    if (parts.length != 4 || index < 0 || index >= parts.length) {
      return 0;
    }
    String part = parts[index].toLowerCase(Locale.ROOT).replace("px", "").trim();
    try {
      return Math.max(0, Integer.parseInt(part));
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
  public static String normalizeColor(String raw) {
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
    if (NAMED_COLORS.containsKey(value)) {
      return NAMED_COLORS.get(value);
    }
    var rgb = Pattern.compile("rgba?\\(([^)]+)\\)").matcher(value);
    if (rgb.find()) {
      String rgbValue = rgb.group(1).trim();
      String[] parts = rgbValue.contains(",")
          ? rgbValue.split(",")
          : rgbValue.replace("/", " ").split("\\s+");
      if (parts.length >= 3) {
        try {
          int r = parseColorComponent(parts[0]);
          int g = parseColorComponent(parts[1]);
          int b = parseColorComponent(parts[2]);
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
  public static String stripQuotes(String value) {
    return value == null ? null : value.replace("'", "").replace("\"", "").trim();
  }

  private static int clampColor(int value) {
    return Math.max(0, Math.min(255, value));
  }

  private static int parseColorComponent(String raw) {
    String value = raw == null ? "" : raw.trim();
    if (value.endsWith("%")) {
      double percent = Double.parseDouble(value.substring(0, value.length() - 1).trim());
      return clampColor((int) Math.round(255.0 * percent / 100.0));
    }
    return clampColor((int) Math.round(Double.parseDouble(value)));
  }
}
