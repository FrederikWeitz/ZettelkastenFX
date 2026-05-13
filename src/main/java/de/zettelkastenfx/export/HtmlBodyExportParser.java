package de.zettelkastenfx.export;

import de.zettelkastenfx.export.model.ExportCssStyleUtil;
import de.zettelkastenfx.export.model.ExportParagraph;
import de.zettelkastenfx.export.model.ExportParagraphStyle;
import de.zettelkastenfx.export.model.ExportTable;
import de.zettelkastenfx.export.model.ExportTextRun;
import de.zettelkastenfx.export.model.ExportTextStyle;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Wandelt gespeicherte HTML-Body-Fragmente in das neutrale Exportmodell um.
 */
final class HtmlBodyExportParser {

  /**
   * Parst ein HTML-Body-Fragment.
   *
   * @param html gespeicherter Zettelrumpf
   * @return exportierbare Absaetze
   */
  List<ExportParagraph> parse(String html) {
    if (html == null || html.isBlank()) {
      return List.of();
    }
    List<ExportParagraph> paragraphs = new ArrayList<>();
    for (Node node : Jsoup.parseBodyFragment(html).body().childNodes()) {
      appendBlock(node, paragraphs);
    }
    return List.copyOf(paragraphs);
  }

  private void appendBlock(Node node, List<ExportParagraph> paragraphs) {
    if (node instanceof TextNode textNode) {
      if (!textNode.text().isBlank()) {
        paragraphs.add(ExportParagraph.plain(textNode.text()));
      }
      return;
    }
    if (!(node instanceof Element element)) {
      return;
    }

    String tag = element.normalName();
    switch (tag) {
      case "p", "div", "h1", "h2" -> paragraphs.add(paragraphOrSoleImage(element));
      case "ul" -> appendList(element, paragraphs, false);
      case "ol" -> appendList(element, paragraphs, true);
      case "table" -> paragraphs.add(table(element));
      case "img" -> paragraphs.add(image(element));
      case "br" -> paragraphs.add(ExportParagraph.plain(""));
      default -> {
        if (element.children().stream().anyMatch(child -> isBlock(child.normalName()))) {
          for (Node child : element.childNodes()) {
            appendBlock(child, paragraphs);
          }
        } else {
          paragraphs.add(paragraph(element));
        }
      }
    }
  }

  /**
   * Wandelt einen normalen Block um oder erkennt einen von TinyMCE erzeugten Bildabsatz.
   *
   * @param element Blockelement
   * @return Exportabsatz
   */
  private ExportParagraph paragraphOrSoleImage(Element element) {
    Element image = soleImage(element);
    return image == null ? paragraph(element) : image(image);
  }

  private ExportParagraph paragraph(Element element) {
    List<ExportTextRun> runs = new ArrayList<>();
    appendInlineChildren(element, InlineStyle.PLAIN, runs);
    if (runs.isEmpty()) {
      runs.add(new ExportTextRun("", ExportTextStyle.PLAIN));
    }
    return new ExportParagraph(runs, paragraphStyle(element));
  }

  private void appendList(Element list, List<ExportParagraph> paragraphs, boolean ordered) {
    int number = 1;
    InlineStyle listInlineStyle = InlineStyle.PLAIN.apply(list);
    for (Element item : list.children()) {
      if (!"li".equals(item.normalName())) {
        continue;
      }
      List<ExportTextRun> runs = new ArrayList<>();
      appendInlineChildren(item, listInlineStyle, runs);
      if (runs.isEmpty()) {
        runs.add(new ExportTextRun("", ExportTextStyle.PLAIN));
      }
      paragraphs.add(new ExportParagraph(runs, listStyle(ordered, number)));
      number++;
    }
  }

  private ExportParagraph table(Element tableElement) {
    List<List<String>> cells = new ArrayList<>();
    int columns = 0;
    for (Element row : tableElement.select("> tbody > tr, > tr")) {
      List<String> rowCells = new ArrayList<>();
      for (Element cell : row.select("> td, > th")) {
        rowCells.add(cell.wholeText().strip());
      }
      if (!rowCells.isEmpty()) {
        columns = Math.max(columns, rowCells.size());
        cells.add(List.copyOf(rowCells));
      }
    }
    int rows = Math.max(1, cells.size());
    columns = Math.max(1, columns);
    return ExportParagraph.table(new ExportTable(rows, columns, cells, columnWeights(tableElement, columns)));
  }

  private List<Integer> columnWeights(Element tableElement, int columns) {
    List<Integer> weights = weightsFromColumnElements(tableElement, columns);
    return weights.isEmpty() ? weightsFromFirstRow(tableElement, columns) : weights;
  }

  private List<Integer> weightsFromColumnElements(Element tableElement, int columns) {
    List<Integer> weights = new ArrayList<>();
    for (Element column : tableElement.select("> colgroup > col, > col")) {
      Integer width = widthWeight(column);
      weights.add(width == null ? 1 : width);
      if (weights.size() >= columns) {
        break;
      }
    }
    return weights.size() == columns ? List.copyOf(weights) : List.of();
  }

  private List<Integer> weightsFromFirstRow(Element tableElement, int columns) {
    Element firstRow = tableElement.selectFirst("> tbody > tr, > tr");
    if (firstRow == null) {
      return List.of();
    }
    List<Integer> weights = new ArrayList<>();
    for (Element cell : firstRow.select("> td, > th")) {
      Integer width = widthWeight(cell);
      weights.add(width == null ? 1 : width);
      if (weights.size() >= columns) {
        break;
      }
    }
    return weights.size() == columns && weights.stream().anyMatch(width -> width > 1)
        ? List.copyOf(weights)
        : List.of();
  }

  private Integer widthWeight(Element element) {
    Integer styleWidth = numericCssWidth(ExportCssStyleUtil.cssValue(element.attr("style"), "width"));
    if (styleWidth != null) {
      return styleWidth;
    }
    return numericCssWidth(element.attr("width"));
  }

  private Integer numericCssWidth(String rawWidth) {
    if (rawWidth == null || rawWidth.isBlank()) {
      return null;
    }
    String normalized = ExportCssStyleUtil.stripQuotes(rawWidth).toLowerCase(Locale.ROOT).trim();
    if (normalized.endsWith("%") || normalized.endsWith("px")) {
      normalized = normalized.substring(0, normalized.length() - (normalized.endsWith("px") ? 2 : 1)).trim();
    }
    try {
      return Math.max(1, (int) Math.round(Double.parseDouble(normalized)));
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private ExportParagraph image(Element image) {
    String src = image.attr("src");
    if (src == null || src.isBlank()) {
      return ExportParagraph.plain(imageFallbackText(image, ""));
    }
    try {
      URI uri = URI.create(src);
      if ("file".equalsIgnoreCase(uri.getScheme())) {
        return ExportParagraph.image(Path.of(uri));
      }
      if (uri.getScheme() != null) {
        return ExportParagraph.plain(imageFallbackText(image, src));
      }
    } catch (RuntimeException ignored) {
      // Lokale Editorbilder werden meistens als normaler Dateipfad gespeichert.
    }
    try {
      return ExportParagraph.image(Path.of(src));
    } catch (RuntimeException ignored) {
      return ExportParagraph.plain(imageFallbackText(image, src));
    }
  }

  /**
   * Liefert den Ersatztext fuer Bildquellen, die nicht als lokale Dateien exportierbar sind.
   *
   * @param image Bildelement
   * @param fallbackSrc urspruengliche Bildquelle
   * @return Alt-Text oder Bildquelle
   */
  private String imageFallbackText(Element image, String fallbackSrc) {
    String fallback = image.hasAttr("alt") ? image.attr("alt") : fallbackSrc;
    return fallback == null ? "" : fallback;
  }

  /**
   * Sucht ein einzelnes Bild, wenn der Block abgesehen von Leerraum und Zeilenumbruechen nur dieses Bild enthaelt.
   *
   * @param element Blockelement
   * @return Bild-Element oder {@code null}
   */
  private Element soleImage(Element element) {
    List<Element> images = element.select("img");
    if (images.size() != 1) {
      return null;
    }
    Element image = images.getFirst();
    return containsOnlyImage(element, image) ? image : null;
  }

  /**
   * Prueft rekursiv, ob ein DOM-Knoten nur das erwartete Bild und neutrale Huellen enthaelt.
   *
   * @param node zu pruefender DOM-Knoten
   * @param image erwartetes Bild
   * @return {@code true}, wenn keine exportrelevanten Textinhalte danebenstehen
   */
  private boolean containsOnlyImage(Node node, Element image) {
    if (node instanceof TextNode textNode) {
      return textNode.text().replace('\u00a0', ' ').isBlank();
    }
    if (!(node instanceof Element element)) {
      return true;
    }
    String tag = element.normalName();
    if ("img".equals(tag)) {
      return element == image;
    }
    if ("br".equals(tag)) {
      return true;
    }
    for (Node child : element.childNodes()) {
      if (!containsOnlyImage(child, image)) {
        return false;
      }
    }
    return true;
  }

  private void appendInlineChildren(Element element, InlineStyle inherited, List<ExportTextRun> runs) {
    InlineStyle current = inherited.apply(element);
    for (Node child : element.childNodes()) {
      appendInline(child, current, runs);
    }
  }

  private void appendInline(Node node, InlineStyle style, List<ExportTextRun> runs) {
    if (node instanceof TextNode textNode) {
      addRun(runs, textNode.text(), style.toExportStyle());
      return;
    }
    if (!(node instanceof Element element)) {
      return;
    }
    if ("br".equals(element.normalName())) {
      addRun(runs, "\n", style.toExportStyle());
      return;
    }
    if ("img".equals(element.normalName())) {
      return;
    }
    appendInlineChildren(element, style, runs);
  }

  private void addRun(List<ExportTextRun> runs, String text, ExportTextStyle style) {
    if (text == null || text.isEmpty()) {
      return;
    }
    if (!runs.isEmpty() && runs.getLast().style().equals(style)) {
      ExportTextRun previous = runs.removeLast();
      runs.add(new ExportTextRun(previous.text() + text, style));
      return;
    }
    runs.add(new ExportTextRun(text, style));
  }

  private ExportParagraphStyle paragraphStyle(Element element) {
    String tag = element.normalName();
    String style = element.attr("style");
    String alignment = alignmentFromClasses(element);
    if (alignment == null) {
      alignment = normalizeAlignment(ExportCssStyleUtil.cssValue(style, "text-align"));
    }

    int indent = classNames(element).contains("q") ? 12 : 0;
    Integer paddingLeft = firstPixel(style, "padding-left", "margin-left");
    if (paddingLeft != null) {
      indent = paddingLeft;
    } else {
      int shorthandLeft = paddingPixels(style, 3);
      if (shorthandLeft > 0) {
        indent = shorthandLeft;
      }
    }

    int spacing = classNames(element).contains("q") || classNames(element).contains("s") ? 3 : 0;
    Integer paddingTop = firstPixel(style, "padding-top", "margin-top");
    if (paddingTop != null) {
      spacing = paddingTop;
    } else {
      int shorthandTop = paddingPixels(style, 0);
      if (shorthandTop > 0) {
        spacing = shorthandTop;
      }
    }

    boolean bold = classNames(element).contains("s")
        || "h1".equals(tag)
        || "h2".equals(tag)
        || isBold(ExportCssStyleUtil.cssValue(style, "font-weight"));
    Integer fontSize = switch (tag) {
      case "h1" -> 20;
      case "h2" -> 16;
      default -> ExportCssStyleUtil.pixelValue(style, "font-size");
    };
    String background = classNames(element).contains("q")
        ? "#eeeeee"
        : ExportCssStyleUtil.cssValue(style, "background-color");
    Integer headingLevel = switch (tag) {
      case "h1" -> 1;
      case "h2" -> 2;
      default -> null;
    };
    return new ExportParagraphStyle(alignment, indent, spacing, fontSize, bold, background, headingLevel, null, 0);
  }

  private ExportParagraphStyle listStyle(boolean ordered, int number) {
    return new ExportParagraphStyle(null, 0, 0, null, false, null, null, ordered ? "ol" : "ul", number);
  }

  private String alignmentFromClasses(Element element) {
    List<String> classes = classNames(element);
    if (classes.contains("r")) {
      return "right";
    }
    if (classes.contains("m")) {
      return "center";
    }
    if (classes.contains("j")) {
      return "justify";
    }
    if (classes.contains("l")) {
      return "left";
    }
    return null;
  }

  private List<String> classNames(Element element) {
    return element.classNames().stream().map(name -> name.toLowerCase(Locale.ROOT)).toList();
  }

  private Integer firstPixel(String style, String firstProperty, String secondProperty) {
    Integer first = ExportCssStyleUtil.pixelValue(style, firstProperty);
    return first != null ? first : ExportCssStyleUtil.pixelValue(style, secondProperty);
  }

  private int paddingPixels(String css, int index) {
    String value = ExportCssStyleUtil.cssValue(css, "padding");
    if (value == null) {
      return 0;
    }
    String[] parts = value.split("\\s+");
    if (parts.length == 1) {
      return parsePixel(parts[0]);
    }
    if (parts.length == 2) {
      return parsePixel(parts[index == 0 || index == 2 ? 0 : 1]);
    }
    if (parts.length == 3) {
      return parsePixel(parts[index == 3 ? 1 : index]);
    }
    return index < parts.length ? parsePixel(parts[index]) : 0;
  }

  private int parsePixel(String raw) {
    if (raw == null) {
      return 0;
    }
    try {
      return Math.max(0, Integer.parseInt(raw.toLowerCase(Locale.ROOT).replace("px", "").trim()));
    } catch (NumberFormatException ignored) {
      return 0;
    }
  }

  private boolean isBlock(String tag) {
    return switch (tag) {
      case "p", "div", "h1", "h2", "ul", "ol", "table", "img" -> true;
      default -> false;
    };
  }

  private String normalizeAlignment(String value) {
    String normalized = ExportCssStyleUtil.stripQuotes(value);
    if (normalized == null || normalized.isBlank()) {
      return null;
    }
    return switch (normalized.toLowerCase(Locale.ROOT)) {
      case "center", "right", "justify", "left" -> normalized.toLowerCase(Locale.ROOT);
      default -> null;
    };
  }

  private boolean isBold(String value) {
    if (value == null) {
      return false;
    }
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    return "bold".equals(normalized) || "700".equals(normalized) || "800".equals(normalized) || "900".equals(normalized);
  }

  private record InlineStyle(
      boolean bold,
      boolean italic,
      boolean underline,
      boolean strikethrough,
      String fontFamily,
      String textColor,
      String backgroundColor,
      boolean subscript,
      boolean superscript
  ) {

    private static final InlineStyle PLAIN = new InlineStyle(false, false, false, false, null, null, null, false, false);

    private InlineStyle apply(Element element) {
      String tag = element.normalName();
      List<String> classes = element.classNames().stream().map(name -> name.toLowerCase(Locale.ROOT)).toList();
      String style = element.attr("style");
      boolean nextBold = bold || classes.contains("b") || "b".equals(tag) || "strong".equals(tag)
          || isBoldValue(ExportCssStyleUtil.cssValue(style, "font-weight"));
      boolean nextItalic = italic || classes.contains("i") || "i".equals(tag) || "em".equals(tag)
          || "italic".equalsIgnoreCase(ExportCssStyleUtil.stripQuotes(ExportCssStyleUtil.cssValue(style, "font-style")));
      String decoration = valueOr(
          ExportCssStyleUtil.cssValue(style, "text-decoration"),
          ExportCssStyleUtil.cssValue(style, "text-decoration-line")
      );
      boolean nextUnderline = underline || classes.contains("u") || "u".equals(tag)
          || containsCssWord(decoration, "underline");
      boolean nextStrike = strikethrough || classes.contains("x") || "s".equals(tag) || "strike".equals(tag) || "del".equals(tag)
          || containsCssWord(decoration, "line-through");
      String cssFont = valueOr(element.attr("face"), ExportCssStyleUtil.cssValue(style, "font-family"));
      String nextFont = valueOr(fontFamily, cssFont);
      String color = valueOr(
          valueOr(element.attr("color"), ExportCssStyleUtil.cssValue(style, "-webkit-text-fill-color")),
          ExportCssStyleUtil.cssValue(style, "color")
      );
      String nextColor = valueOr(textColor, color);
      String background = ExportCssStyleUtil.cssValue(style, "background-color");
      String nextBackground = valueOr(backgroundColor, background);
      String verticalAlign = ExportCssStyleUtil.stripQuotes(ExportCssStyleUtil.cssValue(style, "vertical-align"));
      boolean nextSubscript = subscript || "sub".equals(tag) || "sub".equalsIgnoreCase(verticalAlign);
      boolean nextSuperscript = superscript || "sup".equals(tag) || "super".equalsIgnoreCase(verticalAlign);
      if (nextSubscript && nextSuperscript) {
        nextSuperscript = "sup".equals(tag) || "super".equalsIgnoreCase(verticalAlign);
        nextSubscript = !nextSuperscript;
      }
      return new InlineStyle(
          nextBold,
          nextItalic,
          nextUnderline,
          nextStrike,
          nextFont,
          nextColor,
          nextBackground,
          nextSubscript,
          nextSuperscript
      );
    }

    private ExportTextStyle toExportStyle() {
      return new ExportTextStyle(
          bold,
          italic,
          underline,
          strikethrough,
          fontFamily,
          textColor,
          backgroundColor,
          subscript,
          superscript
      );
    }

    private static String valueOr(String previous, String next) {
      return next == null || next.isBlank() ? previous : next;
    }

    private static boolean containsCssWord(String value, String word) {
      return value != null && value.toLowerCase(Locale.ROOT).contains(word);
    }

    private static boolean isBoldValue(String value) {
      if (value == null) {
        return false;
      }
      String normalized = value.trim().toLowerCase(Locale.ROOT);
      return "bold".equals(normalized) || "700".equals(normalized) || "800".equals(normalized) || "900".equals(normalized);
    }
  }
}
