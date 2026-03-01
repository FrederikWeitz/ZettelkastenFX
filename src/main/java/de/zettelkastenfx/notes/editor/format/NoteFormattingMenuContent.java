package de.zettelkastenfx.notes.editor.format;

import de.zettelkastenfx.base.BaseIcon;
import javafx.collections.FXCollections;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import org.fxmisc.richtext.InlineCssTextArea;
import org.fxmisc.richtext.model.StyleSpan;
import org.fxmisc.richtext.model.StyleSpans;

import java.util.List;
import java.util.regex.Pattern;

public final class NoteFormattingMenuContent {

  private final InlineCssTextArea area;

  private final VBox root;

  private final Button clearFormatting;

  private final ToggleButton bold;
  private final ToggleButton italic;
  private final ToggleButton underline;
  private final ToggleButton strike;

  private final ToggleButton sub;
  private final ToggleButton sup;

  private final ToggleButton h1;
  private final ToggleButton h2;
  private final ToggleGroup headings;

  private final Button indent;
  private final Button outdent;
  private final Button bullets;
  private final Button numbers;

  private final MenuButton backgroundColorMenu;
  private final Rectangle backgroundIndicator;

  private final MenuButton textColorMenu;
  private final Rectangle textIndicator;

  private final MenuButton alignMenu;

  private final ComboBox<String> fontFamily;

  private BaseIcon currentAlignIcon = BaseIcon.TEXT_ALIGN_LEFT;

  private enum HeadingKind {NONE, H1, H2, MIXED}
  private static final String MIXED_CLASS = "mixed";
  private boolean syncing;

  public NoteFormattingMenuContent(InlineCssTextArea area) {
    this.area = area;

    root = new VBox();
    root.getStyleClass().add("note-formatting-root");
    root.setSpacing(6);
    root.setPadding(new Insets(2));

    clearFormatting = iconButton(BaseIcon.CLEAR_FORMATTING);

    bold = toggleButton(BaseIcon.TEXT_BOLD);
    italic = toggleButton(BaseIcon.TEXT_ITALIC);
    underline = toggleButton(BaseIcon.TEXT_UNDERLINE);
    strike = toggleButton(BaseIcon.TEXT_STRIKETHROUGH);

    sub = toggleButton(BaseIcon.TEXT_SUBSCRIPT);
    sup = toggleButton(BaseIcon.TEXT_SUPERSCRIPT);

    headings = new ToggleGroup();
    h1 = toggleButton(BaseIcon.TEXT_HEADING_1);
    h2 = toggleButton(BaseIcon.TEXT_HEADING_2);
    h1.setToggleGroup(headings);
    h2.setToggleGroup(headings);

    indent = iconButton(BaseIcon.TEXT_INDENT);
    outdent = iconButton(BaseIcon.TEXT_INDENT_REMOVE);
    bullets = iconButton(BaseIcon.TEXT_LIST_BULLETS);
    numbers = iconButton(BaseIcon.TEXT_LIST_NUMBERS);

    backgroundIndicator = new Rectangle(14, 14, Color.TRANSPARENT);
    backgroundColorMenu = colorMenu("Hintergrundfarbe", backgroundIndicator, color ->
                                                                                 InlineCssStyleUtil.setSelectionProperty(area, "-rtfx-background-color", toCssColor(color))
    );

    textIndicator = new Rectangle(14, 14, Color.rgb(40, 40, 40));
    textColorMenu = colorMenu("Schriftfarbe", textIndicator, color ->
                                                                 InlineCssStyleUtil.setSelectionProperty(area, "-fx-fill", toCssColor(color))
    );

    alignMenu = buildAlignmentMenu();

    fontFamily = new ComboBox<>(FXCollections.observableArrayList(Font.getFamilies()));
    fontFamily.getStyleClass().add("note-formatting-combo");
    fontFamily.setVisibleRowCount(12);
    fontFamily.setPrefWidth(180);
    fontFamily.setValue(Font.getDefault().getFamily());

    wireActions();
    root.getChildren().addAll(buildRow1(), buildRow2());
  }

  public Node getRoot() {
    return root;
  }

  private BaseIcon alignIconForValue(String value) {
    if (value == null) {
      return BaseIcon.TEXT_ALIGN_LEFT;
    }
    return switch (value.toLowerCase()) {
      case "center" -> BaseIcon.TEXT_ALIGN_CENTER;
      case "right" -> BaseIcon.TEXT_ALIGN_RIGHT;
      case "justify" -> BaseIcon.TEXT_ALIGN_JUSTIFY;
      default -> BaseIcon.TEXT_ALIGN_LEFT;
    };
  }

  private MenuItem alignItem(MenuButton host, BaseIcon icon, String value) {
    MenuItem item = new MenuItem(icon.tooltip(), icon.imageView());
    item.setOnAction(e -> {
      currentAlignIcon = icon;
      host.setGraphic(icon.imageView());
      InlineCssStyleUtil.setParagraphPropertyForSelection(area, "-fx-text-alignment", value);
    });
    return item;
  }

  private void applyTriState(ToggleButton btn, TriState state) {
    btn.setSelected(state == TriState.ON);
    setMixedClass(btn, state == TriState.MIXED);
  }

  private MenuButton buildAlignmentMenu() {
    MenuButton menu = new MenuButton();
    menu.getStyleClass().addAll("note-formatting-menu", "note-formatting-dropdown");
    menu.setGraphic(currentAlignIcon.imageView());
    menu.setTooltip(new Tooltip("Ausrichtung"));

    menu.getItems().addAll(
        alignItem(menu, BaseIcon.TEXT_ALIGN_LEFT, "left"),
        alignItem(menu, BaseIcon.TEXT_ALIGN_CENTER, "center"),
        alignItem(menu, BaseIcon.TEXT_ALIGN_RIGHT, "right"),
        alignItem(menu, BaseIcon.TEXT_ALIGN_JUSTIFY, "justify")
    );
    return menu;
  }

  private HBox buildRow1() {
    HBox row = new HBox(clearFormatting, bold, italic, underline, strike, spacer(), sub, sup, spacer(), h1, h2);
    row.getStyleClass().add("note-formatting-row");
    row.setAlignment(Pos.CENTER_LEFT);
    row.setSpacing(4);
    return row;
  }

  private HBox buildRow2() {
    HBox row = new HBox(
        indent, outdent, spacer(),
        bullets, numbers, spacer(),
        backgroundColorMenu, textColorMenu, spacer(),
        alignMenu, spacer(),
        fontFamily
    );
    row.getStyleClass().add("note-formatting-row");
    row.setAlignment(Pos.CENTER_LEFT);
    row.setSpacing(4);
    return row;
  }

  private MenuButton colorMenu(String tooltip, Rectangle indicator, java.util.function.Consumer<Color> apply) {
    MenuButton menu = new MenuButton();
    menu.getStyleClass().addAll("note-formatting-menu", "note-formatting-dropdown");
    menu.setTooltip(new Tooltip(tooltip));
    menu.setGraphic(indicator);

    List<Color> swatches = List.of(
        Color.TRANSPARENT,
        Color.web("#1f1f1f"), Color.web("#7a0f0f"), Color.web("#1b5fbf"),
        Color.web("#2e7d32"), Color.web("#f57c00"), Color.web("#fdd835"),
        Color.web("#e0e0e0"), Color.web("#ffffff")
    );

    FlowPane swatchPane = new FlowPane();
    swatchPane.getStyleClass().add("note-formatting-swatches");
    swatchPane.setHgap(6);
    swatchPane.setVgap(6);

    for (Color swatch : swatches) {
      Button swatchButton = new Button();
      swatchButton.getStyleClass().add("note-formatting-swatch");
      swatchButton.setGraphic(new Rectangle(14, 14, swatch));
      swatchButton.setOnAction(e -> {
        indicator.setFill(swatch);
        apply.accept(swatch);
        menu.hide();
      });
      swatchPane.getChildren().add(swatchButton);
    }

    ColorPicker picker = new ColorPicker((Color) indicator.getFill());
    picker.getStyleClass().add("note-formatting-color-picker");
    picker.setOnAction(e -> {
      Color chosen = picker.getValue();
      if (chosen != null) {
        indicator.setFill(chosen);
        apply.accept(chosen);
      }
      menu.hide();
    });

    VBox content = new VBox(swatchPane, new Separator(), picker);
    content.getStyleClass().add("note-formatting-color-menu-content");
    content.setSpacing(8);
    content.setPadding(new Insets(8));

    menu.getItems().add(new CustomMenuItem(content, false));
    return menu;
  }

  private int[] getParagraphRangeFromUtil() {
    // kleine Brücke: gleiche Logik wie Util (Selektion -> Absatzrange)
    var sel = area.getSelection();
    if (sel == null || sel.getLength() == 0) {
      int p = area.getCurrentParagraph();
      return new int[] { p, p };
    }
    int start = area.offsetToPosition(sel.getStart(), org.fxmisc.richtext.model.TwoDimensional.Bias.Forward).getMajor();
    int end = area.offsetToPosition(sel.getEnd(), org.fxmisc.richtext.model.TwoDimensional.Bias.Backward).getMajor();
    return new int[] { Math.min(start, end), Math.max(start, end) };
  }

  private HeadingKind headingFromStyle(String paragraphStyle) {
    boolean bold = paragraphStyle.contains("-fx-font-weight: bold");
    if (!bold) {
      return HeadingKind.NONE;
    }
    if (paragraphStyle.contains("-fx-font-size: 20px")) {
      return HeadingKind.H1;
    }
    if (paragraphStyle.contains("-fx-font-size: 16px")) {
      return HeadingKind.H2;
    }
    return HeadingKind.NONE;
  }

  private Button iconButton(BaseIcon icon) {
    Button button = icon.button();
    button.getStyleClass().add("formatting-button");
    return button;
  }

  private void resetToggles() {
    bold.setSelected(false);
    italic.setSelected(false);
    underline.setSelected(false);
    strike.setSelected(false);
    sub.setSelected(false);
    sup.setSelected(false);
    h1.setSelected(false);
    h2.setSelected(false);
  }

  private boolean selectionAllSpansContain(String property, String value) {
    var sel = area.getSelection();
    if (sel == null || sel.getLength() == 0) {
      return false;
    }

    StyleSpans<String> spans = area.getStyleSpans(sel.getStart(), sel.getEnd());
    Pattern pattern = Pattern.compile(Pattern.quote(property) + "\\s*:\\s*" + Pattern.quote(value), Pattern.CASE_INSENSITIVE);

    return spans.stream().allMatch(span -> pattern.matcher(span.getStyle() == null ? "" : span.getStyle()).find());
  }

  private boolean selectionAnySpansContain(String prop, String value) {
    IndexRange sel = area.getSelection();
    int start = sel.getStart();
    int end = sel.getEnd();

    if (start == end) {
      // caret: Style an Cursorposition prüfen
      if (area.getLength() == 0) return false;
      int pos = Math.min(start, area.getLength() - 1);
      String style = InlineCssStyleUtil.normalize(area.getStyleAtPosition(start));
      String v = InlineCssStyleUtil.extractCssProperty(style, prop);
      return v != null && v.equalsIgnoreCase(value);
    }

    StyleSpans<String> spans = area.getStyleSpans(start, end);
    for (StyleSpan<String> span : spans) {
      String style = InlineCssStyleUtil.normalize(span.getStyle());
      String v = InlineCssStyleUtil.extractCssProperty(style, prop);
      if (v != null && v.equalsIgnoreCase(value)) return true;
    }
    return false;
  }

  private void setMixedClass(Control c, boolean mixed) {
    c.getStyleClass().remove(MIXED_CLASS);
    if (mixed) c.getStyleClass().add(MIXED_CLASS);
  }

  private Region spacer() {
    Region region = new Region();
    region.setMinWidth(6);
    region.setPrefWidth(6);
    return region;
  }

  private String stripQuotes(String value) {
    if (value == null) {
      return null;
    }
    return value.replace("'", "").replace("\"", "");
  }

  public void syncFromSelection() {
    syncing = true;
    try {
      applyTriState(bold, InlineCssStyleUtil.selectionInlinePropertyTriState(area, "-fx-font-weight", "bold"));
      applyTriState(italic, InlineCssStyleUtil.selectionInlinePropertyTriState(area, "-fx-font-style", "italic"));
      applyTriState(underline, InlineCssStyleUtil.selectionInlinePropertyTriState(area, "-fx-underline", "true"));
      applyTriState(strike, InlineCssStyleUtil.selectionInlinePropertyTriState(area, "-fx-strikethrough", "true"));

      applyTriState(sup, InlineCssStyleUtil.selectionSuperscriptTriState(area));
      applyTriState(sub, InlineCssStyleUtil.selectionSubscriptTriState(area));

      // Heading als TriState (statt uniformHeadingKind)
      applyTriState(h1, InlineCssStyleUtil.selectionHeadingTriState(area, 20));
      applyTriState(h2, InlineCssStyleUtil.selectionHeadingTriState(area, 16));

      // Align: wenn du weiterhin uniformParagraphPropertyValue nutzt:
      String align = uniformParagraphPropertyValue("-fx-text-alignment");
      currentAlignIcon = alignIconForValue(align);
      alignMenu.setGraphic(currentAlignIcon.imageView());
      setMixedClass(alignMenu, align == null);

      // Font-Family: wie gehabt, aber Action-Loop verhindern
      String family = uniformInlinePropertyValue("-fx-font-family");
      if (family == null) {
        fontFamily.setValue(null);
        fontFamily.setPromptText("Gemischt");
      } else {
        fontFamily.setValue(stripQuotes(family));
        fontFamily.setPromptText(null);
      }
    } finally {
      syncing = false;
    }
  }

  private TriState triStateFromSelection(String prop, String value) {
    boolean all = selectionAllSpansContain(prop, value);
    boolean any = selectionAnySpansContain(prop, value);
    if (all) return TriState.ON;
    if (any) return TriState.MIXED;
    return TriState.OFF;
  }

  private String toCssColor(Color color) {
    if (color == null) {
      return "transparent";
    }
    int r = (int) Math.round(color.getRed() * 255);
    int g = (int) Math.round(color.getGreen() * 255);
    int b = (int) Math.round(color.getBlue() * 255);
    double a = color.getOpacity();
    if (a >= 0.999) {
      return String.format("#%02x%02x%02x", r, g, b);
    }
    return String.format("rgba(%d,%d,%d,%.3f)", r, g, b, a);
  }

  private ToggleButton toggleButton(BaseIcon icon) {
    ToggleButton button = new ToggleButton();
    button.getStyleClass().addAll("zettel-toolbar-button", "formatting-button");
    button.setGraphic(icon.imageView());
    button.setTooltip(new Tooltip(icon.tooltip()));
    return button;
  }

  private HeadingKind uniformHeadingKind() {
    int[] range = getParagraphRangeFromUtil();

    HeadingKind first = null;
    for (int p = range[0]; p <= range[1]; p++) {
      String style = area.getParagraph(p).getParagraphStyle();
      style = style == null ? "" : style;

      HeadingKind kind = headingFromStyle(style);

      if (first == null) {
        first = kind;
      } else if (first != kind) {
        return HeadingKind.MIXED;
      }
    }

    return first == null ? HeadingKind.NONE : first;
  }

  private String uniformInlinePropertyValue(String property) {
    var sel = area.getSelection();
    if (sel == null || sel.getLength() == 0) {
      return null;
    }

    StyleSpans<String> spans = area.getStyleSpans(sel.getStart(), sel.getEnd());
    Pattern pattern = Pattern.compile(Pattern.quote(property) + "\\s*:\\s*([^;]+)", Pattern.CASE_INSENSITIVE);

    String first = null;
    for (var span : spans) {
      String style = span.getStyle() == null ? "" : span.getStyle();
      var m = pattern.matcher(style);
      String value = m.find() ? m.group(1).trim() : null;

      if (first == null) {
        first = value;
      } else if ((first == null && value != null) || (first != null && !first.equals(value))) {
        return null;
      }
    }
    return first;
  }

  private String uniformParagraphPropertyValue(String property) {
    // nutzt Absatzrange aus Util, damit wir exakt dieselben Regeln haben wie bei Align/H1/H2
    int[] range = getParagraphRangeFromUtil();
    Pattern pattern = Pattern.compile(Pattern.quote(property) + "\\s*:\\s*([^;]+)", Pattern.CASE_INSENSITIVE);

    String first = null;
    for (int p = range[0]; p <= range[1]; p++) {
      String style = area.getParagraph(p).getParagraphStyle();
      style = style == null ? "" : style;

      var m = pattern.matcher(style);
      String value = m.find() ? m.group(1).trim() : null;

      if (first == null) {
        first = value;
      } else if ((first == null && value != null) || (first != null && !first.equals(value))) {
        return null;
      }
    }
    return first;
  }

  private void wireActions() {
    clearFormatting.setOnAction(e -> {
      InlineCssStyleUtil.clearSelectionFormatting(area);
      resetToggles();
      syncFromSelection();
    });

    bold.setOnAction(e -> {
      if (syncing) return;
      setMixedClass(bold, false);
      InlineCssStyleUtil.toggleSelectionProperty(area, "-fx-font-weight", "bold", bold.isSelected());
      syncFromSelection();
    });
    italic.setOnAction(e -> {
      if (syncing) return;
      setMixedClass(italic, false);
      InlineCssStyleUtil.toggleSelectionProperty(area, "-fx-font-style", "italic", italic.isSelected());
      syncFromSelection();
    });
    underline.setOnAction(e -> {
      if (syncing) return;
      setMixedClass(underline, false);
      InlineCssStyleUtil.toggleSelectionProperty(area, "-fx-underline", "true", underline.isSelected());
      syncFromSelection();
    });
    strike.setOnAction(e -> {
      if (syncing) return;
      setMixedClass(strike, false);
      InlineCssStyleUtil.toggleSelectionProperty(area, "-fx-strikethrough", "true", strike.isSelected());
      syncFromSelection();
    });

    sup.setOnAction(e -> {
      if (syncing) return;
      setMixedClass(sup, false);
      InlineCssStyleUtil.toggleSelectionSuperscript(area, sup.isSelected());
      syncFromSelection();
    });
    sub.setOnAction(e -> {
      if (syncing) return;
      setMixedClass(sub, false);
      InlineCssStyleUtil.toggleSelectionSubscript(area, sub.isSelected());
      syncFromSelection();
    });

    headings.selectedToggleProperty().addListener((obs, old, now) -> {
      if (syncing) return;

      // mixed marker beim user-change löschen
      setMixedClass(h1, false);
      setMixedClass(h2, false);

      if (now == h1) InlineCssStyleUtil.applyHeadingForSelection(area, 20);
      else if (now == h2) InlineCssStyleUtil.applyHeadingForSelection(area, 16);
      else InlineCssStyleUtil.clearHeadingForSelection(area);

      syncFromSelection();
    });

    indent.setOnAction(e -> InlineCssStyleUtil.adjustParagraphIndentForSelection(area, 20));
    outdent.setOnAction(e -> InlineCssStyleUtil.adjustParagraphIndentForSelection(area, -20));

    bullets.setOnAction(e -> InlineCssStyleUtil.toggleSelectionBullets(area));
    numbers.setOnAction(e -> InlineCssStyleUtil.toggleSelectionNumbers(area));

    fontFamily.setOnAction(e -> {
      if (syncing) return;
      String family = fontFamily.getValue();
      if (family != null && !family.isBlank()) {
        InlineCssStyleUtil.setSelectionProperty(area, "-fx-font-family", "'" + family.replace("'", "") + "'");
        syncFromSelection();
      }
    });
  }
}