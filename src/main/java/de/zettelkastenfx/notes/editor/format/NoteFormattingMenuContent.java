package de.zettelkastenfx.notes.editor.format;

import de.zettelkastenfx.base.BaseIcon;
import javafx.collections.FXCollections;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.stage.Popup;

import java.util.List;
import java.util.Locale;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Formatierungsleiste fuer den WebView/TinyMCE-Zetteleditor.
 */
public final class NoteFormattingMenuContent {

  private static final String MIXED_CLASS = "mixed";

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

  private Runnable onFormattingApplied = () -> {};
  private Consumer<Point2D> onImageDropPopupRequested = _ -> {};
  private Consumer<TableAction> onTableActionRequested = _ -> {};
  private BiFunction<String, String, Boolean> onFormatCommandRequested = (_, _) -> false;
  private BaseIcon currentAlignIcon = BaseIcon.TEXT_ALIGN_LEFT;
  private boolean suppressNextImageAction;
  private boolean syncing;

  public NoteFormattingMenuContent() {
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
    backgroundColorMenu = colorMenu("Hintergrundfarbe", backgroundIndicator, true,
        color -> command("backgroundColor", toCssColor(color)));

    textIndicator = new Rectangle(14, 14, Color.rgb(40, 40, 40));
    textColorMenu = colorMenu("Schriftfarbe", textIndicator, false,
        color -> command("textColor", toCssColor(color)));

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

  public void setOnFormattingApplied(Runnable onFormattingApplied) {
    this.onFormattingApplied = onFormattingApplied == null ? () -> {} : onFormattingApplied;
  }

  public void setOnImageDropPopupRequested(Consumer<Point2D> onImageDropPopupRequested) {
    this.onImageDropPopupRequested = onImageDropPopupRequested == null ? _ -> {} : onImageDropPopupRequested;
  }

  public void setOnTableActionRequested(Consumer<TableAction> onTableActionRequested) {
    this.onTableActionRequested = onTableActionRequested == null ? _ -> {} : onTableActionRequested;
  }

  public void setOnFormatCommandRequested(BiFunction<String, String, Boolean> onFormatCommandRequested) {
    this.onFormatCommandRequested = onFormatCommandRequested == null ? (_, _) -> false : onFormatCommandRequested;
  }

  public void setTableControlsVisible(boolean visible) {
    tableRow.setVisible(visible);
    tableRow.setManaged(visible);
  }

  public void syncFromSelection() {
    syncing = true;
    try {
      setMixedClass(bold, false);
      setMixedClass(italic, false);
      setMixedClass(underline, false);
      setMixedClass(strike, false);
      setMixedClass(sub, false);
      setMixedClass(sup, false);
      setMixedClass(h1, false);
      setMixedClass(h2, false);
      setMixedClass(alignMenu, false);
    } finally {
      syncing = false;
    }
  }

  private HBox buildRow1() {
    HBox row = new HBox(
        clearFormatting, bold, italic, underline, strike, spacer(), sub, sup, spacer(),
        h1, h2, insertCitation, separator, image, table
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

  private void wireActions() {
    clearFormatting.setOnAction(_ -> {
      command("clear");
      resetToggles();
    });
    bold.setOnAction(_ -> {
      if (!syncing) command("bold");
    });
    italic.setOnAction(_ -> {
      if (!syncing) command("italic");
    });
    underline.setOnAction(_ -> {
      if (!syncing) command("underline");
    });
    strike.setOnAction(_ -> {
      if (!syncing) command("strike");
    });
    sup.setOnAction(_ -> {
      if (!syncing) command("sup");
    });
    sub.setOnAction(_ -> {
      if (!syncing) command("sub");
    });
    headings.selectedToggleProperty().addListener((_, _, now) -> {
      if (syncing) return;
      setMixedClass(h1, false);
      setMixedClass(h2, false);
      if (now == h1) {
        command("h1");
      } else if (now == h2) {
        command("h2");
      } else {
        command("paragraph");
      }
    });
    insertCitation.setOnAction(_ -> command("citation"));
    separator.setOnAction(_ -> command("separator"));
    indent.setOnAction(_ -> command("indent"));
    outdent.setOnAction(_ -> command("outdent"));
    bullets.setOnAction(_ -> command("bullets"));
    numbers.setOnAction(_ -> command("numbers"));
    image.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
      if (event.getButton() == MouseButton.PRIMARY && event.isControlDown()) {
        suppressNextImageAction = true;
        requestImageDropPopup();
        event.consume();
      }
    });
    image.setOnAction(_ -> {
      if (suppressNextImageAction) {
        suppressNextImageAction = false;
        return;
      }
      command("image");
    });
    table.setOnAction(_ -> showTableSelectionPopup());
    tableColumnInsertLeft.setOnAction(_ -> requestTableAction(TableAction.INSERT_COLUMN_LEFT));
    tableColumnInsertRight.setOnAction(_ -> requestTableAction(TableAction.INSERT_COLUMN_RIGHT));
    tableRowInsertAbove.setOnAction(_ -> requestTableAction(TableAction.INSERT_ROW_ABOVE));
    tableRowInsertBelow.setOnAction(_ -> requestTableAction(TableAction.INSERT_ROW_BELOW));
    tableRowDelete.setOnAction(_ -> requestTableAction(TableAction.DELETE_ROW));
    tableColumnDelete.setOnAction(_ -> requestTableAction(TableAction.DELETE_COLUMN));

    fontFamily.setOnAction(_ -> {
      if (syncing) return;
      String family = fontFamily.getValue();
      if (family != null && !family.isBlank()) {
        command("fontFamily", family.replace("'", ""));
      }
    });
  }

  private Button iconButton(BaseIcon icon) {
    Button button = icon.button();
    button.getStyleClass().add("formatting-button");
    button.setFocusTraversable(false);
    return button;
  }

  private ToggleButton toggleButton(BaseIcon icon) {
    ToggleButton button = new ToggleButton();
    button.getStyleClass().addAll("zettel-toolbar-button", "formatting-button");
    button.setGraphic(icon.imageView());
    button.setTooltip(new Tooltip(icon.tooltip()));
    button.setFocusTraversable(false);
    return button;
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

  private MenuItem alignItem(MenuButton host, BaseIcon icon, String value) {
    MenuItem item = new MenuItem(icon.tooltip(), icon.imageView());
    item.setOnAction(_ -> {
      currentAlignIcon = icon;
      host.setGraphic(icon.imageView());
      command("align", value);
    });
    return item;
  }

  private MenuButton colorMenu(String tooltip,
                               Rectangle indicator,
                               boolean includeTransparent,
                               Consumer<Color> apply) {
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
      swatchButton.setOnAction(_ -> {
        indicator.setFill(swatch);
        apply.accept(swatch);
        menu.hide();
      });
      swatchPane.getChildren().add(swatchButton);
    }

    ColorPicker picker = new ColorPicker((Color) indicator.getFill());
    picker.getStyleClass().add("note-formatting-color-picker");
    picker.setOnAction(_ -> {
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
          command("insertTable", selectedRows[0] + "x" + selectedColumns[0]);
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

  private void requestImageDropPopup() {
    Bounds bounds = image.localToScreen(image.getBoundsInLocal());
    if (bounds != null) {
      onImageDropPopupRequested.accept(new Point2D(bounds.getMinX(), bounds.getMaxY() + 4));
    }
  }

  private void requestTableAction(TableAction action) {
    onTableActionRequested.accept(action);
  }

  private void command(String command) {
    command(command, null);
  }

  private void command(String command, String value) {
    boolean handled = Boolean.TRUE.equals(onFormatCommandRequested.apply(command, value));
    if (handled) {
      onFormattingApplied.run();
    }
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

  private void setMixedClass(Control control, boolean mixed) {
    control.getStyleClass().remove(MIXED_CLASS);
    if (mixed) {
      control.getStyleClass().add(MIXED_CLASS);
    }
  }

  private Region spacer() {
    Region region = new Region();
    region.setMinWidth(6);
    region.setPrefWidth(6);
    return region;
  }

  private static String toCssColor(Color color) {
    if (color == null || color.getOpacity() <= 0.0) {
      return "transparent";
    }
    int r = clampColor(color.getRed());
    int g = clampColor(color.getGreen());
    int b = clampColor(color.getBlue());
    if (color.getOpacity() >= 0.999) {
      return String.format(Locale.ROOT, "#%02x%02x%02x", r, g, b);
    }
    return String.format(
        Locale.ROOT,
        "rgba(%d,%d,%d,%.3f)",
        r,
        g,
        b,
        Math.max(0.0, Math.min(1.0, color.getOpacity()))
    );
  }

  private static int clampColor(double component) {
    return (int) Math.round(Math.max(0.0, Math.min(1.0, component)) * 255.0);
  }

  public enum TableAction {
    INSERT_COLUMN_LEFT,
    INSERT_COLUMN_RIGHT,
    INSERT_ROW_ABOVE,
    INSERT_ROW_BELOW,
    DELETE_ROW,
    DELETE_COLUMN
  }
}
