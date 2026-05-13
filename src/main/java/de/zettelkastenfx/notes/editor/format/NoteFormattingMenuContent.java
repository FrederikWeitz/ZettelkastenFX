package de.zettelkastenfx.notes.editor.format;

import de.zettelkastenfx.base.BaseIcon;
import de.zettelkastenfx.notes.editor.media.EditorImageAssetService;
import de.zettelkastenfx.notes.editor.table.EditorTableService;
import javafx.collections.FXCollections;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.stage.Popup;
import javafx.stage.Window;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.fxmisc.richtext.InlineCssTextArea;
import org.fxmisc.richtext.model.StyleSpan;
import org.fxmisc.richtext.model.StyleSpans;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public final class NoteFormattingMenuContent {

  private static final String HIDDEN_IMAGE_REFERENCE_STYLE = "-fx-fill: transparent;-fx-font-size: 1px;";

  private final InlineCssTextArea area;
  private InlineCssTextArea formattingArea;
  private Runnable onFormattingApplied;

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
  private final Button insertCitation;
  private final Button separator;
  private final Button image;
  private final Button table;
  private final Button tableColumnInsertLeft;
  private final Button tableColumnInsertRight;
  private final Button tableRowInsertAbove;
  private final Button tableRowInsertBelow;
  private final Button tableRowDelete;
  private final Button tableColumnDelete;
  private final HBox tableRow;

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
  private Consumer<Point2D> onImageDropPopupRequested;
  private Consumer<TableAction> onTableActionRequested;
  private FormatCommandHandler onFormatCommandRequested;
  private boolean suppressNextImageAction;

  private enum HeadingKind {NONE, H1, H2, MIXED}
  private static final String MIXED_CLASS = "mixed";
  private boolean syncing;

  public enum TableAction {
    INSERT_COLUMN_LEFT,
    INSERT_COLUMN_RIGHT,
    INSERT_ROW_ABOVE,
    INSERT_ROW_BELOW,
    DELETE_ROW,
    DELETE_COLUMN
  }

  @FunctionalInterface
  public interface FormatCommandHandler {
    boolean handle(String command, String value);
  }

  public NoteFormattingMenuContent(InlineCssTextArea area) {
    this.area = area;
    this.formattingArea = area;

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

    insertCitation = iconButton(BaseIcon.INSERT_CITATION);
    separator = iconButton(BaseIcon.SEPARATOR);
    image = iconButton(BaseIcon.IMAGE);
    table = iconButton(BaseIcon.TABLE);
    tableColumnInsertLeft = iconButton(BaseIcon.TABLE_COLUMNS_INSERT_LEFT);
    tableColumnInsertRight = iconButton(BaseIcon.TABLE_COLUMNS_INSERT_RIGHT);
    tableRowInsertAbove = iconButton(BaseIcon.TABLE_ROWS_INSERT_ABOVE_WORD);
    tableRowInsertBelow = iconButton(BaseIcon.TABLE_ROWS_INSERT_BELOW_WORD);
    tableRowDelete = iconButton(BaseIcon.TABLE_ROW_DELETE);
    tableColumnDelete = iconButton(BaseIcon.TABLE_COLUMN_DELETE);

    indent = iconButton(BaseIcon.TEXT_INDENT);
    outdent = iconButton(BaseIcon.TEXT_INDENT_REMOVE);
    bullets = iconButton(BaseIcon.TEXT_LIST_BULLETS);
    numbers = iconButton(BaseIcon.TEXT_LIST_NUMBERS);

    backgroundIndicator = new Rectangle(14, 14, Color.TRANSPARENT);
    backgroundColorMenu = colorMenu("Hintergrundfarbe", backgroundIndicator, true, color -> {
      if (requestFormatCommand("backgroundColor", toCssColor(color))) {
        return;
      }
      InlineCssStyleUtil.setSelectionProperty(targetArea(), "-rtfx-background-color", toCssColor(color));
      afterFormattingApplied();
    });

    textIndicator = new Rectangle(14, 14, Color.rgb(40, 40, 40));
    textColorMenu = colorMenu("Schriftfarbe", textIndicator, false, color -> {
      if (requestFormatCommand("textColor", toCssColor(color))) {
        return;
      }
      InlineCssStyleUtil.setSelectionProperty(targetArea(), "-fx-fill", toCssColor(color));
      afterFormattingApplied();
    });

    alignMenu = buildAlignmentMenu();

    fontFamily = new ComboBox<>(FXCollections.observableArrayList(Font.getFamilies()));
    fontFamily.getStyleClass().add("note-formatting-combo");
    fontFamily.setVisibleRowCount(12);
    fontFamily.setPrefWidth(180);
    fontFamily.setValue(Font.getDefault().getFamily());

    tableRow = buildTableRow();
    setTableControlsVisible(false);
    wireActions();
    root.getChildren().addAll(buildRow1(), buildRow2(), tableRow);
  }

  public Node getRoot() {
    return root;
  }

  /**
   * Setzt den Editorbereich, auf den Formatierbefehle angewendet werden.
   *
   * @param formattingArea aktiver Editorbereich oder {@code null} fuer den Haupteditor
   */
  public void setFormattingArea(InlineCssTextArea formattingArea) {
    this.formattingArea = formattingArea == null ? area : formattingArea;
  }

  /**
   * Setzt einen Callback nach erfolgreichen Formatieraenderungen.
   *
   * @param onFormattingApplied Callback
   */
  public void setOnFormattingApplied(Runnable onFormattingApplied) {
    this.onFormattingApplied = onFormattingApplied;
  }

  private InlineCssTextArea targetArea() {
    return formattingArea == null ? area : formattingArea;
  }

  private void afterFormattingApplied() {
    if (onFormattingApplied != null) {
      onFormattingApplied.run();
    }
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
      if (requestFormatCommand("align", value)) {
        return;
      }
      InlineCssStyleUtil.setParagraphPropertyForSelection(targetArea(), "-fx-text-alignment", value);
      afterFormattingApplied();
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
    HBox row = new HBox(
        clearFormatting, bold, italic, underline, strike, spacer(), sub, sup, spacer(), h1, h2, insertCitation, separator, image, table
    );
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

  private HBox buildTableRow() {
    HBox row = new HBox(
        tableColumnInsertLeft,
        tableColumnInsertRight,
        spacer(),
        tableRowInsertAbove,
        tableRowInsertBelow,
        spacer(),
        tableRowDelete,
        tableColumnDelete
    );
    row.getStyleClass().add("note-formatting-row");
    row.setAlignment(Pos.CENTER_LEFT);
    row.setSpacing(4);
    return row;
  }

  private MenuButton colorMenu(String tooltip,
                               Rectangle indicator,
                               boolean includeTransparent,
                               java.util.function.Consumer<Color> apply) {
    MenuButton menu = new MenuButton();
    menu.getStyleClass().addAll("note-formatting-menu", "note-formatting-dropdown");
    menu.setTooltip(new Tooltip(tooltip));
    menu.setGraphic(indicator);

    List<Color> swatches = includeTransparent
        ? List.of(
            Color.TRANSPARENT,
            Color.web("#000000"), Color.web("#7a0f0f"), Color.web("#1b5fbf"),
            Color.web("#2e7d32"), Color.web("#f57c00"), Color.web("#fdd835"),
            Color.web("#e0e0e0"), Color.web("#ffffff")
        )
        : List.of(
            Color.web("#000000"), Color.web("#7a0f0f"), Color.web("#1b5fbf"),
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
    InlineCssTextArea area = targetArea();
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
    InlineCssTextArea area = targetArea();
    var sel = area.getSelection();
    if (sel == null || sel.getLength() == 0) {
      return false;
    }

    StyleSpans<String> spans = area.getStyleSpans(sel.getStart(), sel.getEnd());
    Pattern pattern = Pattern.compile(Pattern.quote(property) + "\\s*:\\s*" + Pattern.quote(value), Pattern.CASE_INSENSITIVE);

    return spans.stream().allMatch(span -> pattern.matcher(span.getStyle() == null ? "" : span.getStyle()).find());
  }

  private boolean selectionAnySpansContain(String prop, String value) {
    InlineCssTextArea area = targetArea();
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
    InlineCssTextArea area = targetArea();
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

  private static String toCssColor(Color color) {
    if (color == null) {
      return "transparent";
    }
    int r = (int) Math.round(color.getRed() * 255);
    int g = (int) Math.round(color.getGreen() * 255);
    int b = (int) Math.round(color.getBlue() * 255);
    double a = color.getOpacity();
    if (a <= 0.001) {
      return "transparent";
    }
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
    InlineCssTextArea area = targetArea();
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
    InlineCssTextArea area = targetArea();
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
    InlineCssTextArea area = targetArea();
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
      if (requestFormatCommand("clear", null)) {
        return;
      }
      InlineCssStyleUtil.clearSelectionFormatting(targetArea());
      resetToggles();
      syncFromSelection();
      afterFormattingApplied();
    });

    bold.setOnAction(e -> {
      if (syncing) return;
      if (requestFormatCommand("bold", null)) return;
      setMixedClass(bold, false);
      InlineCssStyleUtil.toggleSelectionProperty(targetArea(), "-fx-font-weight", "bold", bold.isSelected());
      syncFromSelection();
      afterFormattingApplied();
    });
    italic.setOnAction(e -> {
      if (syncing) return;
      if (requestFormatCommand("italic", null)) return;
      setMixedClass(italic, false);
      InlineCssStyleUtil.toggleSelectionProperty(targetArea(), "-fx-font-style", "italic", italic.isSelected());
      syncFromSelection();
      afterFormattingApplied();
    });
    underline.setOnAction(e -> {
      if (syncing) return;
      if (requestFormatCommand("underline", null)) return;
      setMixedClass(underline, false);
      InlineCssStyleUtil.toggleSelectionProperty(targetArea(), "-fx-underline", "true", underline.isSelected());
      syncFromSelection();
      afterFormattingApplied();
    });
    strike.setOnAction(e -> {
      if (syncing) return;
      if (requestFormatCommand("strike", null)) return;
      setMixedClass(strike, false);
      InlineCssStyleUtil.toggleSelectionProperty(targetArea(), "-fx-strikethrough", "true", strike.isSelected());
      syncFromSelection();
      afterFormattingApplied();
    });

    sup.setOnAction(e -> {
      if (syncing) return;
      if (requestFormatCommand("sup", null)) return;
      setMixedClass(sup, false);
      InlineCssStyleUtil.toggleSelectionSuperscript(targetArea(), sup.isSelected());
      syncFromSelection();
      afterFormattingApplied();
    });
    sub.setOnAction(e -> {
      if (syncing) return;
      if (requestFormatCommand("sub", null)) return;
      setMixedClass(sub, false);
      InlineCssStyleUtil.toggleSelectionSubscript(targetArea(), sub.isSelected());
      syncFromSelection();
      afterFormattingApplied();
    });

    headings.selectedToggleProperty().addListener((obs, old, now) -> {
      if (syncing) return;

      // mixed marker beim user-change löschen
      setMixedClass(h1, false);
      setMixedClass(h2, false);

      if (now == h1 && requestFormatCommand("h1", null)) return;
      else if (now == h2 && requestFormatCommand("h2", null)) return;
      else if (now == null && requestFormatCommand("paragraph", null)) return;

      if (now == h1) InlineCssStyleUtil.applyHeadingForSelection(targetArea(), 20);
      else if (now == h2) InlineCssStyleUtil.applyHeadingForSelection(targetArea(), 16);
      else InlineCssStyleUtil.clearHeadingForSelection(targetArea());

      syncFromSelection();
      afterFormattingApplied();
    });

    indent.setOnAction(e -> {
      if (requestFormatCommand("indent", null)) return;
      InlineCssStyleUtil.adjustParagraphIndentForSelection(targetArea(), 20);
      afterFormattingApplied();
    });
    outdent.setOnAction(e -> {
      if (requestFormatCommand("outdent", null)) return;
      InlineCssStyleUtil.adjustParagraphIndentForSelection(targetArea(), -20);
      afterFormattingApplied();
    });

    bullets.setOnAction(e -> {
      if (requestFormatCommand("bullets", null)) return;
      InlineCssStyleUtil.toggleSelectionBullets(targetArea());
      afterFormattingApplied();
    });
    numbers.setOnAction(e -> {
      if (requestFormatCommand("numbers", null)) return;
      InlineCssStyleUtil.toggleSelectionNumbers(targetArea());
      afterFormattingApplied();
    });
    insertCitation.setOnAction(e -> {
      if (requestFormatCommand("citation", null)) return;
      InlineCssStyleUtil.applyCitationForSelection(targetArea());
      syncFromSelection();
      afterFormattingApplied();
    });
    separator.setOnAction(e -> {
      if (requestFormatCommand("separator", null)) return;
      InlineCssStyleUtil.applySeparatorForSelection(targetArea());
      syncFromSelection();
      afterFormattingApplied();
    });
    image.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
      if (event.getButton() == MouseButton.PRIMARY && event.isControlDown()) {
        suppressNextImageAction = true;
        requestImageDropPopup();
        event.consume();
      }
    });
    image.setOnAction(e -> {
      if (suppressNextImageAction) {
        suppressNextImageAction = false;
        return;
      }
      if (!requestFormatCommand("image", null)) {
        insertImageReference();
      }
    });
    table.setOnAction(e -> showTableSelectionPopup());
    tableColumnInsertLeft.setOnAction(e -> requestTableAction(TableAction.INSERT_COLUMN_LEFT));
    tableColumnInsertRight.setOnAction(e -> requestTableAction(TableAction.INSERT_COLUMN_RIGHT));
    tableRowInsertAbove.setOnAction(e -> requestTableAction(TableAction.INSERT_ROW_ABOVE));
    tableRowInsertBelow.setOnAction(e -> requestTableAction(TableAction.INSERT_ROW_BELOW));
    tableRowDelete.setOnAction(e -> requestTableAction(TableAction.DELETE_ROW));
    tableColumnDelete.setOnAction(e -> requestTableAction(TableAction.DELETE_COLUMN));

    fontFamily.setOnAction(e -> {
      if (syncing) return;
      String family = fontFamily.getValue();
      if (family != null && !family.isBlank()) {
        if (requestFormatCommand("fontFamily", family.replace("'", ""))) return;
        InlineCssStyleUtil.setSelectionProperty(targetArea(), "-fx-font-family", "'" + family.replace("'", "") + "'");
        syncFromSelection();
        afterFormattingApplied();
      }
    });
  }

  /**
   * Setzt den Handler fuer das Drag-and-drop-Bildpopup.
   *
   * @param onImageDropPopupRequested Handler mit Bildschirmposition fuer das Popup
   */
  public void setOnImageDropPopupRequested(Consumer<Point2D> onImageDropPopupRequested) {
    this.onImageDropPopupRequested = onImageDropPopupRequested;
  }

  /**
   * Setzt den Handler fuer Tabellenaktionen.
   *
   * @param onTableActionRequested Handler fuer Tabellenaktionen
   */
  public void setOnTableActionRequested(Consumer<TableAction> onTableActionRequested) {
    this.onTableActionRequested = onTableActionRequested;
  }

  /**
   * Setzt einen Handler fuer TinyMCE-Formatierbefehle.
   *
   * @param onFormatCommandRequested Handler oder {@code null} fuer RichTextFX
   */
  public void setOnFormatCommandRequested(FormatCommandHandler onFormatCommandRequested) {
    this.onFormatCommandRequested = onFormatCommandRequested;
  }

  /**
   * Schaltet die Tabellenbefehle im Formatierungsmenue sichtbar.
   *
   * @param visible {@code true}, wenn eine Tabellenzelle aktiv ist
   */
  public void setTableControlsVisible(boolean visible) {
    tableRow.setVisible(visible);
    tableRow.setManaged(visible);
  }

  /**
   * Fordert eine Tabellenaktion an.
   *
   * @param action Aktion
   */
  private void requestTableAction(TableAction action) {
    if (onTableActionRequested != null) {
      onTableActionRequested.accept(action);
    }
  }

  private boolean requestFormatCommand(String command, String value) {
    if (onFormatCommandRequested == null) {
      return false;
    }
    boolean handled = onFormatCommandRequested.handle(command, value);
    if (handled) {
      afterFormattingApplied();
    }
    return handled;
  }

  /**
   * Fordert das Drag-and-drop-Bildpopup an der Position des Bildbuttons an.
   */
  private void requestImageDropPopup() {
    if (onImageDropPopupRequested == null) {
      return;
    }

    Bounds bounds = image.localToScreen(image.getBoundsInLocal());
    if (bounds == null) {
      return;
    }
    onImageDropPopupRequested.accept(new Point2D(bounds.getMinX(), bounds.getMaxY() + 4));
  }

  /**
   * Oeffnet die Bildauswahl und fuegt den externen Bildverweis ein.
   */
  private void insertImageReference() {
    FileChooser chooser = new FileChooser();
    chooser.setTitle("Bild auswählen");
    chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
        "Bilddateien",
        "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.webp", "*.tif", "*.tiff"
    ));

    File selected = chooser.showOpenDialog(ownerWindow());
    if (selected == null) {
      return;
    }

    try {
      java.nio.file.Path imagePath = selected.toPath();
      if (!EditorImageAssetService.isSupportedImageFile(imagePath)) {
        showImageError("Dieses Bildformat wird nicht unterstützt.");
        return;
      }

      if (EditorImageAssetService.isInsideImageDirectory(imagePath)) {
        insertImageReference(imagePath);
        return;
      }

      java.nio.file.Path resolved = chooseExternalImageHandling(imagePath);
      if (resolved != null) {
        insertImageReference(resolved);
      }
    } catch (RuntimeException exception) {
      showImageError(exception.getMessage());
    }
  }

  /**
   * Fuegt einen Bildverweis an der aktuellen Cursorposition ein.
   *
   * @param imagePath Bildpfad
   */
  private void insertImageReference(java.nio.file.Path imagePath) {
    String reference = EditorImageAssetService.referenceParagraphText(imagePath);
    String insertion = paragraphInsertionText(reference);
    area.replaceSelection(insertion);
    styleInsertedImageReference(insertion, reference);
    area.requestFocus();
  }

  /**
   * Formatiert den eingefuegten Bildverweis fuer die Bildvorschau transparent.
   *
   * @param insertion eingefuegter Text inklusive eventueller Umbrueche
   * @param reference eingefuegter Bildverweis
   */
  private void styleInsertedImageReference(String insertion, String reference) {
    styleInsertedStructuralReference(insertion, reference);
  }

  /**
   * Formatiert den eingefuegten Strukturmarker transparent.
   *
   * @param insertion eingefuegter Text inklusive eventueller Umbrueche
   * @param reference eingefuegter Strukturmarker
   */
  private void styleInsertedStructuralReference(String insertion, String reference) {
    int relativeStart = insertion.indexOf(reference);
    if (relativeStart < 0) {
      return;
    }
    int start = Math.max(0, area.getCaretPosition() - insertion.length() + relativeStart);
    area.setStyle(start, start + reference.length(), HIDDEN_IMAGE_REFERENCE_STYLE);
    int paragraph = area.offsetToPosition(start, org.fxmisc.richtext.model.TwoDimensional.Bias.Forward).getMajor();
    area.setParagraphStyle(paragraph, "-fx-padding: 0;");
  }

  /**
   * Baut den Bildverweis als eigenen Absatz.
   *
   * @param reference Bildverweis
   * @return einzufuegender Text
   */
  private String paragraphInsertionText(String reference) {
    String text = area.getText();
    int caret = area.getCaretPosition();
    boolean needsLeadingBreak = caret > 0 && text.charAt(caret - 1) != '\n';
    boolean needsTrailingBreak = caret < text.length() && text.charAt(caret) != '\n';

    StringBuilder insertion = new StringBuilder();
    if (needsLeadingBreak) {
      insertion.append('\n');
    }
    insertion.append(reference);
    if (needsTrailingBreak) {
      insertion.append('\n');
    }
    return insertion.toString();
  }

  /**
   * Oeffnet die Rasterauswahl fuer eine neue Tabelle.
   */
  private void showTableSelectionPopup() {
    Popup popup = new Popup();
    popup.setAutoHide(true);
    GridPane grid = new GridPane();
    grid.getStyleClass().add("note-table-selection-popup");
    grid.setHgap(2);
    grid.setVgap(2);
    grid.setPadding(new Insets(8));

    Label sizeLabel = new Label("1 x 1");
    sizeLabel.getStyleClass().add("note-table-selection-size");
    GridPane.setColumnSpan(sizeLabel, 6);
    grid.add(sizeLabel, 0, 6);

    Region[][] cells = new Region[6][6];
    int[] selectedRows = {1};
    int[] selectedColumns = {1};
    boolean[] tableInserted = {false};
    for (int row = 0; row < 6; row++) {
      for (int column = 0; column < 6; column++) {
        Region cell = new Region();
        cell.getStyleClass().add("note-table-selection-cell");
        cell.setMinSize(18, 18);
        cell.setPrefSize(18, 18);
        int rows = row + 1;
        int columns = column + 1;
        cell.setOnMouseEntered(_ -> updateTableSelection(cells, sizeLabel, selectedRows, selectedColumns, rows, columns));
        cell.setOnMouseDragged(_ -> updateTableSelection(cells, sizeLabel, selectedRows, selectedColumns, rows, columns));
        Runnable insertSelectedTable = () -> {
          if (tableInserted[0]) {
            return;
          }
          tableInserted[0] = true;
          if (!requestFormatCommand("insertTable", selectedRows[0] + "x" + selectedColumns[0])) {
            insertTableReference(selectedRows[0], selectedColumns[0]);
          }
          popup.hide();
        };
        cell.setOnMouseReleased(_ -> insertSelectedTable.run());
        cell.setOnMouseClicked(_ -> insertSelectedTable.run());
        cells[row][column] = cell;
        grid.add(cell, column, row);
      }
    }
    updateTableSelection(cells, sizeLabel, selectedRows, selectedColumns, 1, 1);
    popup.getContent().setAll(grid);

    Bounds bounds = table.localToScreen(table.getBoundsInLocal());
    if (bounds != null) {
      popup.show(table, bounds.getMinX(), bounds.getMaxY() + 4);
    }
  }

  /**
   * Aktualisiert die sichtbare Auswahl im Tabellenraster.
   *
   * @param cells Rasterzellen
   * @param sizeLabel Groessenanzeige
   * @param selectedRows aktuelle Zeilenreferenz
   * @param selectedColumns aktuelle Spaltenreferenz
   * @param rows neue Zeilenanzahl
   * @param columns neue Spaltenanzahl
   */
  private void updateTableSelection(Region[][] cells,
                                    Label sizeLabel,
                                    int[] selectedRows,
                                    int[] selectedColumns,
                                    int rows,
                                    int columns) {
    selectedRows[0] = rows;
    selectedColumns[0] = columns;
    sizeLabel.setText(rows + " x " + columns);
    for (int row = 0; row < cells.length; row++) {
      for (int column = 0; column < cells[row].length; column++) {
        cells[row][column].getStyleClass().remove("selected");
        if (row < rows && column < columns) {
          cells[row][column].getStyleClass().add("selected");
        }
      }
    }
  }

  /**
   * Fuegt einen Tabellenverweis an der aktuellen Cursorposition ein.
   *
   * @param rows Zeilenanzahl
   * @param columns Spaltenanzahl
   */
  private void insertTableReference(int rows, int columns) {
    String reference = EditorTableService.referenceText(rows, columns);
    String insertion = paragraphInsertionText(reference);
    area.replaceSelection(insertion);
    styleInsertedStructuralReference(insertion, reference);
    area.requestFocus();
  }

  /**
   * Fragt ab, wie eine externe Bilddatei behandelt werden soll.
   *
   * @param imagePath ausgewaehlte Bilddatei
   * @return Bildpfad fuer den Zettel oder {@code null}, wenn abgebrochen wurde
   */
  private java.nio.file.Path chooseExternalImageHandling(java.nio.file.Path imagePath) {
    ButtonType keep = new ButtonType("Übernehmen", ButtonBar.ButtonData.OTHER);
    ButtonType move = new ButtonType("Verschieben", ButtonBar.ButtonData.OTHER);
    ButtonType copy = new ButtonType("Kopieren", ButtonBar.ButtonData.OTHER);
    ButtonType cancel = new ButtonType("Abbrechen", ButtonBar.ButtonData.CANCEL_CLOSE);

    Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "", keep, move, copy, cancel);
    alert.initOwner(ownerWindow());
    alert.setTitle("Bild einfügen");
    alert.setHeaderText("Bild liegt nicht im lokalen Bildordner");
    alert.setContentText("Soll die Datei nur referenziert oder in den Bildordner übernommen werden?");

    return alert.showAndWait()
                .map(choice -> {
                  if (choice == keep) {
                    return imagePath;
                  }
                  if (choice == copy) {
                    return EditorImageAssetService.copyIntoImageDirectory(imagePath);
                  }
                  if (choice == move) {
                    return EditorImageAssetService.moveIntoImageDirectory(imagePath);
                  }
                  return null;
                })
                .orElse(null);
  }

  /**
   * Zeigt einen Fehler beim Bildeinfuegen an.
   *
   * @param message Fehlermeldung
   */
  private void showImageError(String message) {
    Alert alert = new Alert(Alert.AlertType.ERROR);
    alert.initOwner(ownerWindow());
    alert.setTitle("Bild einfügen");
    alert.setHeaderText("Bild konnte nicht eingefügt werden");
    alert.setContentText(message == null || message.isBlank() ? "Unbekannter Fehler" : message);
    alert.showAndWait();
  }

  /**
   * Liefert das Fenster fuer Dialoge des Formatierungsmenues.
   *
   * @return Besitzerfenster oder {@code null}
   */
  private Window ownerWindow() {
    return root.getScene() == null ? null : root.getScene().getWindow();
  }
}
