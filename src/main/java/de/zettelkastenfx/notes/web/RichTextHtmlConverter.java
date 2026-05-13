package de.zettelkastenfx.notes.web;

import de.zettelkastenfx.notes.editor.media.EditorImageAssetService;
import de.zettelkastenfx.notes.editor.table.EditorTableService;
import de.zettelkastenfx.persistence.InlineCssRtfxBlobCodec;
import org.fxmisc.richtext.model.Paragraph;
import org.fxmisc.richtext.model.StyledDocument;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Wandelt das bisherige RichTextFX-Format in gespeicherte HTML-Body-Fragmente um.
 */
public final class RichTextHtmlConverter {

  private static final String RTFX_CELL_PREFIX = "{rtfx}";
  private static final String BULLET_PREFIX = "\u2022 ";
  private static final Pattern NUMBER_PREFIX_PATTERN = Pattern.compile("^(\\d+)\\.\\s+");
  private static final String CITATION_PADDING = "3 2 3 12";
  private static final String CITATION_BACKGROUND = "#eeeeee";
  private static final String SEPARATOR_PADDING = "3 0 0 0";

  /**
   * Wandelt ein RichTextFX-Dokument in ein HTML-Body-Fragment um.
   *
   * @param document RichTextFX-Dokument
   * @return HTML-Body-Fragment
   */
  public String toHtmlBody(StyledDocument<String, String, String> document) {
    if (document == null || document.getParagraphs().isEmpty()) {
      return "";
    }
    List<Block> blocks = document.getParagraphs().stream()
        .map(this::toBlock)
        .toList();
    return renderBlocks(blocks);
  }

  /**
   * Wandelt einen einfachen Text als HTML-Body-Fragment um.
   *
   * @param text Klartext
   * @return HTML-Body-Fragment
   */
  public String plainTextToHtmlBody(String text) {
    if (text == null || text.isEmpty()) {
      return "";
    }
    String[] lines = text.split("\\R", -1);
    List<Block> blocks = new ArrayList<>();
    for (String line : lines) {
      blocks.add(new Block("p", List.of(), List.of(new Segment(List.of(), "", escapeText(line))), line, false, null));
    }
    return renderBlocks(blocks);
  }

  private Block toBlock(Paragraph<String, String, String> paragraph) {
    String text = paragraph.getText();
    if (EditorImageAssetService.isImageReferenceParagraph(text)) {
      return new Block("img", List.of(), List.of(), text, false, text);
    }
    if (EditorTableService.isTableReferenceParagraph(text)) {
      return new Block("table", List.of(), List.of(), text, false, text);
    }

    StyleMap paragraphStyle = StyleMap.parse(paragraph.getParagraphStyle());
    String tag = headingTag(paragraphStyle);
    List<String> classes = paragraphClasses(paragraphStyle);
    List<Segment> segments = new ArrayList<>();
    for (var styledSegment : paragraph.getStyledSegments()) {
      String segmentText = styledSegment.getSegment() == null ? "" : styledSegment.getSegment();
      if (segmentText.isEmpty()) {
        continue;
      }
      segments.add(toSegment(styledSegment.getStyle(), segmentText));
    }
    if (segments.isEmpty()) {
      segments.add(new Segment(List.of(), "", ""));
    }
    return new Block(tag, classes, List.copyOf(segments), text, false, null);
  }

  private Segment toSegment(String styleText, String text) {
    StyleMap style = StyleMap.parse(styleText);
    List<String> classes = new ArrayList<>();
    if ("bold".equalsIgnoreCase(style.value("-fx-font-weight"))) {
      classes.add("b");
    }
    if ("italic".equalsIgnoreCase(style.value("-fx-font-style"))) {
      classes.add("i");
    }
    if (truthy(style.value("-fx-underline"))) {
      classes.add("u");
    }
    if (truthy(style.value("-fx-strikethrough"))) {
      classes.add("x");
    }

    List<String> inlineStyles = new ArrayList<>();
    String color = style.value("-fx-fill");
    if (color != null && !color.isBlank() && !"transparent".equalsIgnoreCase(color)) {
      inlineStyles.add("color: " + color);
    }
    String background = style.value("-rtfx-background-color");
    if (background != null && !background.isBlank() && !"transparent".equalsIgnoreCase(background)) {
      inlineStyles.add("background-color: " + background);
    }
    return new Segment(List.copyOf(classes), String.join("; ", inlineStyles), escapeText(text));
  }

  private String renderBlocks(List<Block> blocks) {
    StringBuilder html = new StringBuilder();
    int index = 0;
    while (index < blocks.size()) {
      Block block = blocks.get(index);
      if (block.imageReference() != null) {
        appendLine(html, renderImage(block.imageReference()));
        index++;
      } else if (block.tableReference() != null) {
        appendLine(html, renderTable(block.tableReference()));
        index++;
      } else if (isBullet(block)) {
        int end = index;
        while (end < blocks.size() && isBullet(blocks.get(end))) {
          end++;
        }
        appendLine(html, renderList("ul", blocks.subList(index, end), true));
        index = end;
      } else if (isNumberedListStart(blocks, index)) {
        int end = index;
        while (end < blocks.size() && numberedPrefixLength(blocks.get(end).plainText()) > 0) {
          end++;
        }
        appendLine(html, renderList("ol", blocks.subList(index, end), false));
        index = end;
      } else {
        appendLine(html, renderParagraph(block));
        index++;
      }
    }
    if (html.length() > 0 && html.charAt(html.length() - 1) == '\n') {
      html.setLength(html.length() - 1);
    }
    return html.toString();
  }

  private String renderParagraph(Block block) {
    return "<" + block.tag() + attributes(block.classes(), "") + ">"
        + renderSegments(block.segments())
        + "</" + block.tag() + ">";
  }

  private String renderImage(String referenceText) {
    String trimmed = referenceText == null ? "" : referenceText.trim();
    String path = trimmed.substring("[[image:".length(), trimmed.length() - 2);
    return "<img src=\"" + escapeAttribute(path) + "\">";
  }

  private String renderTable(String referenceText) {
    EditorTableService.TableData table = EditorTableService.resolveReference(referenceText);
    if (table == null) {
      return "<table><tbody></tbody></table>";
    }
    StringBuilder html = new StringBuilder("<table><tbody>");
    for (int row = 0; row < table.rows(); row++) {
      html.append("<tr>");
      for (int column = 0; column < table.columns(); column++) {
        html.append("<td>")
            .append(renderCell(table.cell(row, column)))
            .append("</td>");
      }
      html.append("</tr>");
    }
    html.append("</tbody></table>");
    return html.toString();
  }

  private String renderCell(String cellValue) {
    if (cellValue == null || cellValue.isEmpty()) {
      return "";
    }
    if (cellValue.startsWith(RTFX_CELL_PREFIX)) {
      try {
        byte[] bytes = Base64.getUrlDecoder().decode(cellValue.substring(RTFX_CELL_PREFIX.length()));
        return toHtmlBody(InlineCssRtfxBlobCodec.decodeFromGzipToStyledDocument(bytes));
      } catch (RuntimeException ignored) {
        return "";
      }
    }
    return escapeText(cellValue);
  }

  private String renderList(String tag, List<Block> blocks, boolean bullet) {
    StringBuilder html = new StringBuilder("<").append(tag).append(">");
    for (Block block : blocks) {
      int prefixLength = bullet ? BULLET_PREFIX.length() : numberedPrefixLength(block.plainText());
      html.append("<li>")
          .append(renderSegments(stripPrefix(block.segments(), prefixLength)))
          .append("</li>");
    }
    html.append("</").append(tag).append(">");
    return html.toString();
  }

  private List<Segment> stripPrefix(List<Segment> segments, int prefixLength) {
    List<Segment> stripped = new ArrayList<>();
    int remaining = Math.max(0, prefixLength);
    for (Segment segment : segments) {
      String text = segment.escapedText();
      if (remaining <= 0) {
        stripped.add(segment);
        continue;
      }
      String plain = unescapeTextForPrefixLength(text);
      if (remaining >= plain.length()) {
        remaining -= plain.length();
        continue;
      }
      stripped.add(new Segment(segment.classes(), segment.inlineStyle(), escapeText(plain.substring(remaining))));
      remaining = 0;
    }
    if (stripped.isEmpty()) {
      stripped.add(new Segment(List.of(), "", ""));
    }
    return List.copyOf(stripped);
  }

  private String renderSegments(List<Segment> segments) {
    StringBuilder html = new StringBuilder();
    for (Segment segment : segments) {
      if (segment.classes().isEmpty() && segment.inlineStyle().isBlank()) {
        html.append(segment.escapedText());
      } else {
        html.append("<span")
            .append(attributes(segment.classes(), segment.inlineStyle()))
            .append(">")
            .append(segment.escapedText())
            .append("</span>");
      }
    }
    return html.toString();
  }

  private String attributes(List<String> classes, String style) {
    StringBuilder attributes = new StringBuilder();
    if (classes != null && !classes.isEmpty()) {
      attributes.append(" class=\"").append(String.join(" ", classes)).append("\"");
    }
    if (style != null && !style.isBlank()) {
      attributes.append(" style=\"").append(escapeAttribute(style.endsWith(";") ? style : style + ";")).append("\"");
    }
    return attributes.toString();
  }

  private List<String> paragraphClasses(StyleMap style) {
    List<String> classes = new ArrayList<>();
    String alignment = style.value("-fx-text-alignment");
    if (alignment != null) {
      String normalized = alignment.toLowerCase(Locale.ROOT);
      if (normalized.equals("left")) {
        classes.add("l");
      } else if (normalized.equals("right")) {
        classes.add("r");
      } else if (normalized.equals("center")) {
        classes.add("m");
      } else if (normalized.equals("justify") || normalized.equals("justified")) {
        classes.add("j");
      }
    }
    if (isCitation(style)) {
      classes.add("q");
    } else if (isSeparator(style)) {
      classes.add("s");
    }
    return List.copyOf(classes);
  }

  private String headingTag(StyleMap style) {
    if (!"bold".equalsIgnoreCase(style.value("-fx-font-weight"))) {
      return "p";
    }
    String size = style.value("-fx-font-size");
    if ("20px".equalsIgnoreCase(size)) {
      return "h1";
    }
    if ("16px".equalsIgnoreCase(size)) {
      return "h2";
    }
    return "p";
  }

  private boolean isCitation(StyleMap style) {
    return CITATION_PADDING.equals(style.value("-fx-padding"))
        && CITATION_BACKGROUND.equalsIgnoreCase(style.value("-fx-background-color"));
  }

  private boolean isSeparator(StyleMap style) {
    return "bold".equalsIgnoreCase(style.value("-fx-font-weight"))
        && SEPARATOR_PADDING.equals(style.value("-fx-padding"))
        && style.value("-fx-font-size") == null;
  }

  private boolean isBullet(Block block) {
    return block.plainText().startsWith(BULLET_PREFIX);
  }

  private boolean isNumberedListStart(List<Block> blocks, int index) {
    return index + 1 < blocks.size()
        && numberedPrefixLength(blocks.get(index).plainText()) > 0
        && numberedPrefixLength(blocks.get(index + 1).plainText()) > 0;
  }

  private int numberedPrefixLength(String text) {
    var matcher = NUMBER_PREFIX_PATTERN.matcher(text == null ? "" : text);
    return matcher.find() ? matcher.group(0).length() : 0;
  }

  private boolean truthy(String value) {
    return value != null && ("true".equalsIgnoreCase(value) || "underline".equalsIgnoreCase(value));
  }

  private void appendLine(StringBuilder html, String line) {
    if (html.length() > 0) {
      html.append('\n');
    }
    html.append(line);
  }

  private String escapeText(String text) {
    if (text == null || text.isEmpty()) {
      return "";
    }
    return text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;");
  }

  private String escapeAttribute(String text) {
    return escapeText(text)
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }

  private String unescapeTextForPrefixLength(String text) {
    return text
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&");
  }

  private record Block(
      String tag,
      List<String> classes,
      List<Segment> segments,
      String plainText,
      boolean structural,
      String structuralReference
  ) {
    private String imageReference() {
      return structuralReference != null && EditorImageAssetService.isImageReferenceParagraph(structuralReference)
          ? structuralReference
          : null;
    }

    private String tableReference() {
      return structuralReference != null && EditorTableService.isTableReferenceParagraph(structuralReference)
          ? structuralReference
          : null;
    }
  }

  private record Segment(List<String> classes, String inlineStyle, String escapedText) {}

  private record StyleMap(java.util.Map<String, String> values) {
    private static StyleMap parse(String styleText) {
      java.util.Map<String, String> values = new java.util.LinkedHashMap<>();
      if (styleText != null) {
        for (String part : styleText.split(";")) {
          int colon = part.indexOf(':');
          if (colon > 0) {
            values.put(part.substring(0, colon).trim().toLowerCase(Locale.ROOT), part.substring(colon + 1).trim());
          }
        }
      }
      return new StyleMap(values);
    }

    private String value(String property) {
      return values.get(property.toLowerCase(Locale.ROOT));
    }
  }
}
