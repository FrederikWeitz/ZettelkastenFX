package de.zettelkastenfx.notes.editor.format;

import javafx.scene.control.IndexRange;
import org.fxmisc.richtext.InlineCssTextArea;
import org.fxmisc.richtext.model.StyleSpan;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.fxmisc.richtext.model.TwoDimensional;

import java.util.Map;
import java.util.Objects;
import java.util.function.IntConsumer;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

public final class InlineCssStyleUtil {

  private static final java.util.Map<Character, Character> SUPER = java.util.Map.ofEntries(
      java.util.Map.entry('0', '⁰'), java.util.Map.entry('1', '¹'), java.util.Map.entry('2', '²'),
      java.util.Map.entry('3', '³'), java.util.Map.entry('4', '⁴'), java.util.Map.entry('5', '⁵'),
      java.util.Map.entry('6', '⁶'), java.util.Map.entry('7', '⁷'), java.util.Map.entry('8', '⁸'),
      java.util.Map.entry('9', '⁹'), java.util.Map.entry('+', '⁺'), java.util.Map.entry('-', '⁻'),
      java.util.Map.entry('=', '⁼'), java.util.Map.entry('(', '⁽'), java.util.Map.entry(')', '⁾'),
      java.util.Map.entry('n', 'ⁿ'), java.util.Map.entry('i', 'ᶦ')
  );

  private static final java.util.Map<Character, Character> SUB = java.util.Map.ofEntries(
      java.util.Map.entry('0', '₀'), java.util.Map.entry('1', '₁'), java.util.Map.entry('2', '₂'),
      java.util.Map.entry('3', '₃'), java.util.Map.entry('4', '₄'), java.util.Map.entry('5', '₅'),
      java.util.Map.entry('6', '₆'), java.util.Map.entry('7', '₇'), java.util.Map.entry('8', '₈'),
      java.util.Map.entry('9', '₉'), java.util.Map.entry('+', '₊'), java.util.Map.entry('-', '₋'),
      java.util.Map.entry('=', '₌'), java.util.Map.entry('(', '₍'), java.util.Map.entry(')', '₎'),
      java.util.Map.entry('n', 'ₙ'), java.util.Map.entry('i', 'ᵢ')
  );

  private static final java.util.Map<Character, Character> SUPER_REV = reverseMap(SUPER);
  private static final java.util.Map<Character, Character> SUB_REV = reverseMap(SUB);

  private InlineCssStyleUtil() {
  }

  public static void adjustParagraphIndentForSelection(InlineCssTextArea area, int deltaPixels) {
    Objects.requireNonNull(area, "area");

    forEachSelectedParagraph(area, p -> {
      String current = normalize(area.getParagraph(p).getParagraphStyle());
      int currentIndent = extractLeftPaddingPixels(current);
      int nextIndent = Math.max(0, currentIndent + deltaPixels);
      String updated = upsertCssProperty(current, "-fx-padding", "0 0 0 " + nextIndent);
      area.setParagraphStyle(p, updated);
    });
  }

  public static void applyHeadingForSelection(InlineCssTextArea area, int fontSizePx) {
    Objects.requireNonNull(area, "area");

    forEachSelectedParagraph(area, p -> {
      String current = normalize(area.getParagraph(p).getParagraphStyle());
      String updated = current;
      updated = upsertCssProperty(updated, "-fx-font-weight", "bold");
      updated = upsertCssProperty(updated, "-fx-font-size", fontSizePx + "px");
      area.setParagraphStyle(p, updated);
    });
  }

  public static void clearHeadingForSelection(InlineCssTextArea area) {
    Objects.requireNonNull(area, "area");

    forEachSelectedParagraph(area, p -> {
      String current = normalize(area.getParagraph(p).getParagraphStyle());
      String updated = current;
      updated = removeCssProperty(updated, "-fx-font-size");
      updated = removeCssProperty(updated, "-fx-font-weight");
      area.setParagraphStyle(p, updated);
    });
  }

  public static void clearSelectionFormatting(InlineCssTextArea area) {
    Objects.requireNonNull(area, "area");

    IndexRange selection = area.getSelection();
    if (selection == null || selection.getLength() == 0) {
      return;
    }

    // 1) Inline-Styles in der Selektion entfernen
    clearSelectionStyles(area);

    // 2) Absatz-Formatierungen für alle betroffenen Absätze zurücksetzen
    int startParagraph = area.offsetToPosition(selection.getStart(), TwoDimensional.Bias.Forward).getMajor();
    int endParagraph = area.offsetToPosition(selection.getEnd(), TwoDimensional.Bias.Backward).getMajor();

    for (int paragraphIndex = endParagraph; paragraphIndex >= startParagraph; paragraphIndex--) {
      String current = normalize(area.getParagraph(paragraphIndex).getParagraphStyle());

      String updated = current;
      updated = removeCssProperty(updated, "-fx-font-size");
      updated = removeCssProperty(updated, "-fx-font-weight");
      updated = removeCssProperty(updated, "-fx-text-alignment");
      updated = removeCssProperty(updated, "-fx-padding");

      area.setParagraphStyle(paragraphIndex, updated);

      // Optionaler Reset unserer "Listen" (die bei uns als Text-Präfix eingefügt werden)
      removeListPrefixIfPresent(area, paragraphIndex);
    }
  }

  public static void clearSelectionStyles(InlineCssTextArea area) {
    Objects.requireNonNull(area, "area");

    IndexRange selection = area.getSelection();
    if (selection == null || selection.getLength() == 0) {
      return;
    }

    // Setzt für die gesamte Selektion den Inline-Style auf leer => "Clear Formatting"
    StyleSpansBuilder<String> builder = new StyleSpansBuilder<>();
    builder.add("", selection.getLength());
    area.setStyleSpans(selection.getStart(), builder.create());
  }

  public static TriState selectionHeadingTriState(InlineCssTextArea area, int fontSizePx) {
    Objects.requireNonNull(area, "area");
    int[] range = selectedParagraphRange(area);

    boolean any = false;
    boolean all = true;

    String sizeExpected = fontSizePx + "px";

    for (int p = range[0]; p <= range[1]; p++) {
      String ps = normalize(area.getParagraph(p).getParagraphStyle());
      boolean isHeading =
          cssValueEquals(ps, "-fx-font-size", sizeExpected) &&
              cssValueEquals(ps, "-fx-font-weight", "bold");

      any |= isHeading;
      all &= isHeading;

      if (any && !all) return TriState.MIXED;
    }

    if (all) return TriState.ON;
    if (any) return TriState.MIXED;
    return TriState.OFF;
  }

  public static TriState selectionInlinePropertyTriState(
      InlineCssTextArea area, String cssProperty, String onValue
  ) {
    Objects.requireNonNull(area, "area");
    Objects.requireNonNull(cssProperty, "cssProperty");
    Objects.requireNonNull(onValue, "onValue");

    IndexRange sel = area.getSelection();
    if (sel == null || sel.getLength() == 0) {
      String styleAtCaret = normalize(area.getStyleAtPosition(area.getCaretPosition()));
      return cssValueEquals(styleAtCaret, cssProperty, onValue) ? TriState.ON : TriState.OFF;
    }

    boolean any = false;
    boolean all = true;

    StyleSpans<String> spans = area.getStyleSpans(sel.getStart(), sel.getEnd());
    for (StyleSpan<String> span : spans) {
      String style = normalize(span.getStyle());
      boolean has = cssValueEquals(style, cssProperty, onValue);
      any |= has;
      all &= has;
      if (any && !all) return TriState.MIXED; // early exit
    }

    if (all) return TriState.ON;
    if (any) return TriState.MIXED;
    return TriState.OFF;
  }

  public static TriState selectionSuperscriptTriState(InlineCssTextArea area) {
    return selectionUnicodeState(area, SUPER, SUPER_REV);
  }

  public static TriState selectionSubscriptTriState(InlineCssTextArea area) {
    return selectionUnicodeState(area, SUB, SUB_REV);
  }

  private static TriState selectionUnicodeState(
      InlineCssTextArea area,
      java.util.Map<Character, Character> forward,
      java.util.Map<Character, Character> reverse
  ) {
    IndexRange sel = area.getSelection();
    if (sel == null || sel.getLength() == 0) return TriState.OFF;

    String text = area.getText(sel.getStart(), sel.getEnd());

    int baseCount = 0;   // Zeichen, die man transformieren könnte (forward-key)
    int mappedCount = 0; // Zeichen, die bereits transformiert sind (reverse-key)

    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (reverse.containsKey(c)) mappedCount++;
      else if (forward.containsKey(c)) baseCount++;
    }

    if (baseCount == 0 && mappedCount == 0) return TriState.OFF; // nichts Relevantes
    if (mappedCount > 0 && baseCount == 0) return TriState.ON;   // vollständig transformiert
    if (mappedCount == 0 && baseCount > 0) return TriState.OFF;  // vollständig normal
    return TriState.MIXED;
  }

  public static boolean selectionLooksSuperscript(InlineCssTextArea area) {
    return selectionLooksLikeMapped(area, SUPER, SUPER_REV);
  }

  public static boolean selectionLooksSubscript(InlineCssTextArea area) {
    return selectionLooksLikeMapped(area, SUB, SUB_REV);
  }

  public static void setParagraphPropertyForSelection(InlineCssTextArea area, String cssProperty, String value) {
    Objects.requireNonNull(area, "area");
    Objects.requireNonNull(cssProperty, "cssProperty");
    Objects.requireNonNull(value, "value");

    forEachSelectedParagraph(area, p -> {
      String current = normalize(area.getParagraph(p).getParagraphStyle());
      String updated = upsertCssProperty(current, cssProperty, value);
      area.setParagraphStyle(p, updated);
    });
  }

  public static void setSelectionProperty(InlineCssTextArea area, String cssProperty, String value) {
    Objects.requireNonNull(area, "area");
    Objects.requireNonNull(cssProperty, "cssProperty");
    Objects.requireNonNull(value, "value");

    IndexRange selection = area.getSelection();
    if (selection == null || selection.getLength() == 0) {
      return;
    }

    applyToSelection(area, selection, style -> upsertCssProperty(style, cssProperty, value));
  }

  public static void toggleSelectionBullets(InlineCssTextArea area) {
    Objects.requireNonNull(area, "area");

    int[] range = selectedParagraphRange(area);
    boolean allAreBullets = true;

    for (int p = range[0]; p <= range[1]; p++) {
      String text = area.getParagraph(p).getText();
      if (!text.startsWith("• ")) {
        allAreBullets = false;
        break;
      }
    }

    if (allAreBullets) {
      for (int p = range[1]; p >= range[0]; p--) {
        removeBulletPrefix(area, p);
      }
    } else {
      for (int p = range[0]; p <= range[1]; p++) {
        removeAnyListPrefix(area, p);
        insertPrefix(area, p, "• ");
      }
    }
  }

  public static void toggleSelectionNumbers(InlineCssTextArea area) {
    Objects.requireNonNull(area, "area");

    int[] range = selectedParagraphRange(area);
    boolean allAreNumbers = true;

    for (int p = range[0]; p <= range[1]; p++) {
      String text = area.getParagraph(p).getText();
      if (!text.matches("^\\d+\\.\\s.*")) {
        allAreNumbers = false;
        break;
      }
    }

    if (allAreNumbers) {
      for (int p = range[1]; p >= range[0]; p--) {
        removeNumberPrefix(area, p);
      }
    } else {
      int counter = 1;
      for (int p = range[0]; p <= range[1]; p++) {
        removeAnyListPrefix(area, p);
        insertPrefix(area, p, counter + ". ");
        counter++;
      }
    }
  }

  public static void toggleSelectionSuperscript(InlineCssTextArea area, boolean enabled) {
    toggleSelectionUnicodeTransform(area, enabled ? SUPER : SUPER_REV);
  }

  public static void toggleSelectionSubscript(InlineCssTextArea area, boolean enabled) {
    toggleSelectionUnicodeTransform(area, enabled ? SUB : SUB_REV);
  }

  public static void toggleSelectionProperty(InlineCssTextArea area, String cssProperty, String onValue, boolean enabled) {
    Objects.requireNonNull(area, "area");
    Objects.requireNonNull(cssProperty, "cssProperty");
    Objects.requireNonNull(onValue, "onValue");

    IndexRange selection = area.getSelection();
    if (selection == null || selection.getLength() == 0) {
      return;
    }

    applyToSelection(area, selection, style -> enabled
                                                   ? upsertCssProperty(style, cssProperty, onValue)
                                                   : removeCssProperty(style, cssProperty)
    );
  }

  private static void applyToSelection(InlineCssTextArea area, IndexRange selection, UnaryOperator<String> styleTransform) {
    StyleSpans<String> spans = area.getStyleSpans(selection.getStart(), selection.getEnd());

    StyleSpansBuilder<String> builder = new StyleSpansBuilder<>();
    spans.forEach(styleSpan -> builder.add(styleTransform.apply(normalize(styleSpan.getStyle())), styleSpan.getLength()));

    area.setStyleSpans(selection.getStart(), builder.create());
  }

  private static int extractLeftPaddingPixels(String paragraphStyle) {
    Pattern padding = Pattern.compile("-fx-padding\\s*:\\s*([^;]+);?", Pattern.CASE_INSENSITIVE);
    var matcher = padding.matcher(paragraphStyle);
    if (!matcher.find()) {
      return 0;
    }

    String value = matcher.group(1).trim();
    String[] parts = value.split("\\s+");
    if (parts.length != 4) {
      return 0;
    }

    try {
      return Integer.parseInt(parts[3]);
    } catch (NumberFormatException ignored) {
      return 0;
    }
  }

  private static void forEachSelectedParagraph(InlineCssTextArea area, IntConsumer consumer) {
    IndexRange sel = area.getSelection();
    if (sel == null || sel.getLength() == 0) {
      int p = area.getCurrentParagraph();
      consumer.accept(p);
      return;
    }

    int start = sel.getStart();
    int end = sel.getEnd();

    int startPar = area.offsetToPosition(start, TwoDimensional.Bias.Forward).getMajor();
    int endPar   = area.offsetToPosition(end,   TwoDimensional.Bias.Backward).getMajor();

    for (int p = startPar; p <= endPar; p++) {
      consumer.accept(p);
    }
  }

  static String extractCssProperty(String style, String property) {
    String normalized = normalize(style);
    Pattern pattern = Pattern.compile(
        Pattern.quote(property) + "\\s*:\\s*([^;]+);?",
        Pattern.CASE_INSENSITIVE
    );
    var m = pattern.matcher(normalized);
    return m.find() ? m.group(1).trim() : null;
  }

  private static void insertPrefix(InlineCssTextArea area, int paragraphIndex, String prefix) {
    int start = area.getAbsolutePosition(paragraphIndex, 0);
    area.insertText(start, prefix);
  }

  static String normalize(String style) {
    return style == null ? "" : style;
  }

  private static void removeAnyListPrefix(InlineCssTextArea area, int paragraphIndex) {
    removeBulletPrefix(area, paragraphIndex);
    removeNumberPrefix(area, paragraphIndex);
  }

  private static void removeBulletPrefix(InlineCssTextArea area, int paragraphIndex) {
    String text = area.getParagraph(paragraphIndex).getText();
    if (!text.startsWith("• ")) {
      return;
    }
    int start = area.getAbsolutePosition(paragraphIndex, 0);
    area.deleteText(start, start + 2);
  }

  private static String removeCssProperty(String style, String property) {
    String normalized = normalize(style);
    Pattern pattern = Pattern.compile(Pattern.quote(property) + "\\s*:\\s*[^;]*;?", Pattern.CASE_INSENSITIVE);
    return pattern.matcher(normalized).replaceAll("").trim();
  }

  private static void removeListPrefixIfPresent(InlineCssTextArea area, int paragraphIndex) {
    String text = area.getParagraph(paragraphIndex).getText();
    int paragraphStart = area.getAbsolutePosition(paragraphIndex, 0);

    if (text.startsWith("• ")) {
      area.deleteText(paragraphStart, paragraphStart + 2);
      return;
    }

    var matcher = Pattern.compile("^\\d+\\.\\s").matcher(text);
    if (matcher.find()) {
      area.deleteText(paragraphStart, paragraphStart + matcher.group(0).length());
    }
  }

  static String readCssProperty(String style, String property) {
    String normalized = normalize(style);
    Pattern pattern = Pattern.compile(Pattern.quote(property) + "\\s*:\\s*([^;]+);?", Pattern.CASE_INSENSITIVE);
    var m = pattern.matcher(normalized);
    if (!m.find()) return null;
    return m.group(1).trim();
  }

  static boolean cssValueEquals(String style, String property, String expectedValue) {
    String v = readCssProperty(style, property);
    if (v == null) return false;
    // leichte Normalisierung (Quotes + Whitespace)
    String a = v.replace("'", "").replace("\"", "").trim();
    String b = expectedValue.replace("'", "").replace("\"", "").trim();
    return a.equalsIgnoreCase(b);
  }

  private static void removeNumberPrefix(InlineCssTextArea area, int paragraphIndex) {
    String text = area.getParagraph(paragraphIndex).getText();
    var matcher = Pattern.compile("^\\d+\\.\\s").matcher(text);
    if (!matcher.find()) {
      return;
    }
    int start = area.getAbsolutePosition(paragraphIndex, 0);
    area.deleteText(start, start + matcher.group(0).length());
  }

  private static Map<Character, Character> reverseMap(java.util.Map<Character, Character> forward) {
    java.util.Map<Character, Character> reverse = new java.util.HashMap<>();
    forward.forEach((k, v) -> reverse.put(v, k));
    return java.util.Collections.unmodifiableMap(reverse);
  }

  private static int[] selectedParagraphRange(InlineCssTextArea area) {
    IndexRange selection = area.getSelection();

    if (selection == null || selection.getLength() == 0) {
      int p = area.getCurrentParagraph();
      return new int[] { p, p };
    }

    int start = area.offsetToPosition(selection.getStart(), TwoDimensional.Bias.Forward).getMajor();
    int end = area.offsetToPosition(selection.getEnd(), TwoDimensional.Bias.Backward).getMajor();
    return new int[] { Math.min(start, end), Math.max(start, end) };
  }

  private static boolean selectionLooksLikeMapped(InlineCssTextArea area,
                                                  java.util.Map<Character, Character> forward,
                                                  java.util.Map<Character, Character> reverse) {
    IndexRange sel = area.getSelection();
    if (sel == null || sel.getLength() == 0) {
      return false;
    }
    String text = area.getText(sel.getStart(), sel.getEnd());
    boolean anyMapped = false;

    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);

      if (reverse.containsKey(c)) {
        anyMapped = true;
        continue;
      }
      if (forward.containsKey(c)) {
        return false;
      }
    }
    return anyMapped;
  }

  private static void toggleSelectionUnicodeTransform(InlineCssTextArea area, java.util.Map<Character, Character> map) {
    Objects.requireNonNull(area, "area");
    Objects.requireNonNull(map, "map");

    IndexRange sel = area.getSelection();
    if (sel == null || sel.getLength() == 0) {
      return;
    }

    var spans = area.getStyleSpans(sel.getStart(), sel.getEnd());
    String original = area.getText(sel.getStart(), sel.getEnd());

    StringBuilder sb = new StringBuilder(original.length());
    for (int i = 0; i < original.length(); i++) {
      char c = original.charAt(i);
      sb.append(map.getOrDefault(c, c));
    }

    area.replaceText(sel.getStart(), sel.getEnd(), sb.toString());
    area.setStyleSpans(sel.getStart(), spans);
  }

  private static String upsertCssProperty(String style, String property, String value) {
    String cleaned = removeCssProperty(style, property).trim();
    if (!cleaned.isEmpty() && !cleaned.endsWith(";")) {
      cleaned = cleaned + ";";
    }
    return cleaned + property + ": " + value + ";";
  }
}