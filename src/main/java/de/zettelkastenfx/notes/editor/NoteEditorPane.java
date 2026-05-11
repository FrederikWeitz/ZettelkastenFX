package de.zettelkastenfx.notes.editor;

import de.zettelkastenfx.base.BaseIcon;
import de.zettelkastenfx.base.util.DateUtil;
import de.zettelkastenfx.notes.editor.format.NoteFormattingMenuContent;
import de.zettelkastenfx.notes.editor.format.RichTextSourceCodec;
import de.zettelkastenfx.notes.editor.media.EditorImageAssetService;
import de.zettelkastenfx.notes.editor.table.EditorTableService;
import de.zettelkastenfx.notes.model.NoteReferenceInfo;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.stage.Popup;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.InlineCssTextArea;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

public class NoteEditorPane extends VBox {

  private static final String HIDDEN_IMAGE_REFERENCE_STYLE = "-fx-fill: transparent;-fx-font-size: 1px;";
  private static final double HIDDEN_IMAGE_REFERENCE_FONT_SIZE = 1.0;
  private static final double IMAGE_PREVIEW_HORIZONTAL_PADDING = 24.0;
  private static final String SELECTED_IMAGE_STYLE_CLASS = "selected";
  private static final double TABLE_CELL_WIDTH = 72.0;
  private static final double TABLE_CELL_HEIGHT = 28.0;
  private static final double TABLE_PREVIEW_HORIZONTAL_PADDING = 24.0;

  private final StackPane titleStack;
  private final TextArea titleEditorArea;

  private final InlineCssTextArea bodyArea;
  private final VirtualizedScrollPane<InlineCssTextArea> bodyScrollPane;

  private final ContextMenu formattingContextMenu;
  private final HBox formattingBarContainer;
  private final Region formattingDragHandle;

  private final StackPane bodyContainer;
  private final NoteFormattingMenuContent formattingMenuContent;

  // HeaderBar
  private final BorderPane headerBar;

  private final Label lblHeaderNoteNo;
  private final Label lblHeaderCreatedAt;
  private final Button btnHeaderPrev;
  private final Button btnHeaderNext;
  private final Button btnHeaderInfo;

  // Info-Popup
  private final Popup infoPopup = new Popup();
  private final VBox infoPopupRoot = new VBox(8);

  private final Popup imageDropPopup = new Popup();
  private final VBox imageDropRoot = new VBox(8);
  private final ObservableList<Path> pendingImageDropFiles = FXCollections.observableArrayList();
  private Integer selectedImageParagraph;
  private Integer activeTableParagraph;
  private Integer activeTableRow;
  private Integer activeTableColumn;
  private InlineCssTextArea activeTableCellArea;
  private boolean bodySourceMode;

  private IntConsumer onNavigateToNote = id -> {};
  private List<NoteReferenceInfo> infoOutgoing = List.of();
  private List<NoteReferenceInfo> infoIncoming = List.of();
  private List<String> infoProjects = List.of();

  // Fenster für die bibliographischen Angaben
  private final VBox bibliographyHost;

  public NoteEditorPane() {
    getStyleClass().add("note-editor-pane");
    setSpacing(0);

    headerBar = new BorderPane();
    headerBar.getStyleClass().add("note-header-bar");

    var left = new HBox(6);
    left.getStyleClass().add("note-header-left");
    var lblLeftTitle = new Label("Zettel");
    lblLeftTitle.getStyleClass().add("note-header-title");
    lblHeaderNoteNo = new Label("—");
    lblHeaderNoteNo.getStyleClass().add("note-header-value");
    left.getChildren().addAll(lblLeftTitle, lblHeaderNoteNo);

    var center = new HBox(6);
    center.getStyleClass().add("note-header-center");
    var lblCenterTitle = new Label("Erstellt");
    lblCenterTitle.getStyleClass().add("note-header-title");
    lblHeaderCreatedAt = new Label("—");
    lblHeaderCreatedAt.getStyleClass().add("note-header-value");
    center.getChildren().addAll(lblCenterTitle, lblHeaderCreatedAt);

    var right = new HBox();
    right.getStyleClass().add("note-header-right");
    btnHeaderInfo = BaseIcon.INFORMATION.button();
    btnHeaderInfo.getStyleClass().addAll("icon-button", "note-info-button");
    btnHeaderInfo.setFocusTraversable(false);

    // Zettelwechsel in der HeaderBar
    btnHeaderPrev = BaseIcon.BULLET_ARROW_LEFT.button(null);
    btnHeaderNext = BaseIcon.BULLET_ARROW_RIGHT.button(null);
    btnHeaderPrev.getStyleClass().add("note-header-nav");
    btnHeaderPrev.setFocusTraversable(false);
    btnHeaderNext.getStyleClass().add("note-header-nav");
    btnHeaderNext.setFocusTraversable(false);

    HBox.setMargin(btnHeaderInfo, new Insets(0, 0, 0, 10)); // 10px Abstand links

    right.getChildren().addAll(btnHeaderPrev, btnHeaderNext, btnHeaderInfo);

    headerBar.setLeft(left);
    headerBar.setCenter(center);
    headerBar.setRight(right);

    // Info-Popup in der HeaderBar
    infoPopupRoot.getStyleClass().add("note-info-popup");
    infoPopup.getContent().add(infoPopupRoot);
    infoPopup.setAutoHide(false);

    // Popup soll verschwinden, wenn man es mit der Maus verlässt
    infoPopupRoot.setOnMouseExited(e -> infoPopup.hide());

    // Toggle beim Klick
    btnHeaderInfo.setOnAction(e -> {
      if (infoPopup.isShowing()) infoPopup.hide();
      else showInfoPopup();
    });

    titleEditorArea = new TextArea();
    titleEditorArea.getStyleClass().add("note-title-area");
    titleEditorArea.setPromptText("Überschrift");
    titleEditorArea.setWrapText(false);
    titleEditorArea.setPrefRowCount(1);

    final double titleRowHeight = 24;
    titleEditorArea.setMinHeight(titleRowHeight);
    titleEditorArea.setPrefHeight(titleRowHeight);
    titleEditorArea.setMaxHeight(titleRowHeight);
    titleEditorArea.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

    titleEditorArea.setTextFormatter(new javafx.scene.control.TextFormatter<String>(change -> {
      String inserted = change.getText();
      if (inserted != null && inserted.contains("\n")) {
        change.setText(inserted.replace("\n", " "));
      }
      if (inserted != null && inserted.contains("\r")) {
        change.setText(change.getText().replace("\r", ""));
      }
      return change;
    }));

    titleStack = new StackPane(titleEditorArea);
    titleStack.getStyleClass().add("note-title-stack");
    StackPane.setAlignment(titleEditorArea, Pos.TOP_LEFT);

    bodyArea = new InlineCssTextArea();
    bodyArea.getStyleClass().add("note-body-area");
    bodyArea.setWrapText(true);
    bodyArea.setParagraphGraphicFactory(this::structuralGraphicForParagraph);
    bodyArea.widthProperty().addListener((_, _, _) -> refreshImageReferences());
    installImageDropPopup();
    installImageSelectionHandlers();

    bodyScrollPane = new VirtualizedScrollPane<>(bodyArea);
    bodyScrollPane.getStyleClass().add("note-body-scroll");

    bodyContainer = new StackPane(bodyScrollPane);
    bodyContainer.getStyleClass().add("note-body-container");

    bibliographyHost = new VBox();
    bibliographyHost.getStyleClass().add("note-bibliography-host");

    // initial unsichtbar
    bibliographyHost.setVisible(false);
    bibliographyHost.setManaged(false);

    // HIER einhängen: direkt unterhalb des Body-Bereichs (unter dem Eingabetext)
    VBox.setVgrow(bodyContainer, Priority.ALWAYS);

    formattingBarContainer = new HBox();
    formattingBarContainer.getStyleClass().add("note-formatting-bar");
    formattingBarContainer.setMinHeight(Region.USE_COMPUTED_SIZE);
    formattingBarContainer.setPrefHeight(Region.USE_COMPUTED_SIZE);

    formattingMenuContent = new NoteFormattingMenuContent(bodyArea);
    formattingMenuContent.setOnImageDropPopupRequested(this::showImageDropPopup);
    formattingMenuContent.setOnTableActionRequested(this::handleTableAction);
    formattingMenuContent.setOnFormattingApplied(this::persistActiveTableCell);
    formattingDragHandle = new Region();
    formattingDragHandle.getStyleClass().add("note-formatting-drag-handle");
    formattingDragHandle.setCursor(Cursor.HAND);
    formattingContextMenu = new ContextMenu();
    installFormattingDragHandle();
    formattingBarContainer.getChildren().setAll(formattingDragHandle, formattingMenuContent.getRoot());

    CustomMenuItem formattingMenuItem = new CustomMenuItem(formattingBarContainer, false);
    formattingContextMenu.getItems().setAll(formattingMenuItem);
    formattingContextMenu.getStyleClass().add("note-formatting-context-menu");

    formattingContextMenu.setOnShowing(e -> formattingMenuContent.syncFromSelection());

    getChildren().addAll(headerBar, titleStack, bodyContainer, bibliographyHost);
  }

  /**
   * Erstellt das Popup fuer Strg-Klick auf Bildverweise.
   */
  private void installImageDropPopup() {
    imageDropPopup.setAutoHide(false);
    imageDropRoot.getStyleClass().add("note-image-drop-popup");

    Label hint = new Label("Bilder hierher ziehen");
    hint.getStyleClass().add("note-image-drop-hint");

    ListView<Path> imageList = new ListView<>(pendingImageDropFiles);
    imageList.getStyleClass().add("note-image-drop-list");
    imageList.setPrefSize(360, 160);
    imageList.setCellFactory(_ -> new ListCell<>() {
      @Override
      protected void updateItem(Path item, boolean empty) {
        super.updateItem(item, empty);
        setText(empty || item == null ? null : item.getFileName() + "  -  " + item.getParent());
      }
    });

    Button copy = new Button("Kopieren");
    Button move = new Button("Verschieben");
    Button cancel = new Button("Abbrechen");
    copy.setOnAction(_ -> transferPendingImages(false));
    move.setOnAction(_ -> transferPendingImages(true));
    cancel.setOnAction(_ -> cancelPendingImages());

    HBox actions = new HBox(8, move, copy, cancel);
    actions.setAlignment(Pos.CENTER_RIGHT);

    imageDropRoot.getChildren().setAll(hint, imageList, actions);
    imageDropRoot.setPadding(new Insets(10));
    imageDropPopup.getContent().setAll(imageDropRoot);

    imageDropRoot.setOnDragOver(event -> {
      if (event.getDragboard().hasFiles() && containsSupportedImage(event.getDragboard().getFiles())) {
        event.acceptTransferModes(TransferMode.COPY);
      }
      event.consume();
    });
    imageDropRoot.setOnDragDropped(event -> {
      addDroppedImages(event.getDragboard().getFiles());
      event.setDropCompleted(true);
      event.consume();
    });
  }

  /**
   * Aktiviert Markieren und Loeschen von Bildabsaetzen im Editor.
   */
  private void installImageSelectionHandlers() {
    bodyArea.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
      if (event.getButton() != MouseButton.PRIMARY) {
        return;
      }

      Integer imageParagraph = imageParagraphAt(event.getX(), event.getY());
      if (imageParagraph == null) {
        clearSelectedImageParagraph();
        clearActiveTableCell();
        return;
      }

      selectImageParagraph(imageParagraph);
      event.consume();
    });

    bodyArea.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
      if (selectedImageParagraph == null) {
        return;
      }
      if (event.getCode() == KeyCode.DELETE || event.getCode() == KeyCode.BACK_SPACE) {
        deleteSelectedImageParagraph();
        event.consume();
      }
    });
  }

  /**
   * Oeffnet das Drag-and-drop-Bildpopup an der angeforderten Bildschirmposition.
   *
   * @param screenPosition Bildschirmposition
   */
  private void showImageDropPopup(Point2D screenPosition) {
    if (screenPosition == null) {
      return;
    }
    if (!imageDropPopup.isShowing()) {
      imageDropPopup.show(bodyArea, screenPosition.getX(), screenPosition.getY());
    } else {
      imageDropPopup.setX(screenPosition.getX());
      imageDropPopup.setY(screenPosition.getY());
    }
  }

  /**
   * Liefert den Container des Titelfeldes.
   *
   * @return Titel-Stack
   */
  public StackPane getTitleStack() {
    return titleStack;
  }

  /**
   * Liefert das Titelfeld.
   *
   * @return Titel-Editor
   */
  public TextArea getTitleEditorArea() {
    return titleEditorArea;
  }

  /**
   * Liefert den Rich-Text-Inhaltsbereich.
   *
   * @return Body-Editor
   */
  public InlineCssTextArea getBodyArea() {
    return bodyArea;
  }

  /**
   * Liefert das Kontextmenü für Formatierungen.
   *
   * @return Formatierungsmenü
   */
  public ContextMenu getFormattingContextMenu() {
    return formattingContextMenu;
  }

  /**
   * Schaltet zwischen normaler RichText-Anzeige und editierbarer Quelltextanzeige um.
   */
  public void toggleBodySourceMode() {
    if (bodySourceMode) {
      commitBodySourceViewIfActive();
      return;
    }

    persistActiveTableCell();
    clearSelectedImageParagraph();
    clearActiveTableCell();
    formattingContextMenu.hide();
    bodyArea.setParagraphGraphicFactory(null);
    String source = RichTextSourceCodec.encode(bodyArea);
    bodyArea.clear();
    bodyArea.replaceText(source);
    bodyArea.setStyle(0, bodyArea.getLength(), "");
    bodySourceMode = true;
    bodyArea.requestFocus();
  }

  /**
   * Wendet eine aktive Quelltextansicht auf den RichText-Editor an.
   */
  public void commitBodySourceViewIfActive() {
    if (!bodySourceMode) {
      return;
    }
    String source = bodyArea.getText();
    bodyArea.setParagraphGraphicFactory(this::structuralGraphicForParagraph);
    RichTextSourceCodec.decodeInto(bodyArea, source);
    bodySourceMode = false;
    refreshImageReferences();
    bodyArea.requestFocus();
  }

  /**
   * Verlaesst die Quelltextansicht ohne den angezeigten Quelltext anzuwenden.
   */
  public void discardBodySourceView() {
    if (!bodySourceMode) {
      return;
    }
    bodySourceMode = false;
    bodyArea.setParagraphGraphicFactory(this::structuralGraphicForParagraph);
  }

  /**
   * Gibt an, ob der Body gerade als RichText-Quelltext angezeigt wird.
   *
   * @return {@code true}, wenn die Quelltextansicht aktiv ist
   */
  public boolean isBodySourceMode() {
    return bodySourceMode;
  }

  /**
   * Bereitet das gemeinsame Formatierungsmenue fuer normalen Fliesstext vor.
   */
  public void prepareBodyFormattingContext() {
    clearActiveTableCell();
    formattingMenuContent.syncFromSelection();
  }

  /**
   * Aktiviert das Verschieben des Formatierungsmenues ueber den linken Griff.
   */
  private void installFormattingDragHandle() {
    final double[] dragOffset = new double[2];

    formattingDragHandle.setOnMousePressed(event -> {
      dragOffset[0] = event.getScreenX() - formattingContextMenu.getX();
      dragOffset[1] = event.getScreenY() - formattingContextMenu.getY();
      event.consume();
    });

    formattingDragHandle.setOnMouseDragged(event -> {
      formattingContextMenu.setX(event.getScreenX() - dragOffset[0]);
      formattingContextMenu.setY(event.getScreenY() - dragOffset[1]);
      event.consume();
    });
  }

  /**
   * Liefert den Container des Body-Bereichs.
   *
   * @return Body-Container
   */
  public StackPane getBodyContainer() {
    return bodyContainer;
  }

  /**
   * Liefert die Kopfzeile des Editors.
   *
   * @return Header-Bar
   */
  public BorderPane getHeaderBar() {
    return headerBar;
  }

  /**
   * Liefert den Host für bibliographische Angaben.
   *
   * @return Bibliographie-Host
   */
  public VBox getBibliographyHost() {
    return bibliographyHost;
  }

  /**
   * Aktualisiert die visuelle Darstellung von Bildverweisen im Editor.
   */
  public void refreshImageReferences() {
    if (bodySourceMode) {
      return;
    }
    for (int p = 0; p < bodyArea.getParagraphs().size(); p++) {
      String text = bodyArea.getParagraph(p).getText();
      if (!EditorImageAssetService.isImageReferenceParagraph(text)
          && !EditorTableService.isTableReferenceParagraph(text)) {
        continue;
      }
      int start = bodyArea.getAbsolutePosition(p, 0);
      bodyArea.setStyle(start, start + text.length(), HIDDEN_IMAGE_REFERENCE_STYLE);
      bodyArea.setParagraphStyle(p, structuralParagraphStyle(text));
    }
    refreshSelectedImageGraphic();
  }

  /**
   * Fuegt per Drag-and-drop uebergebene Bilddateien zur Popup-Liste hinzu.
   *
   * @param files uebergebene Dateien
   */
  private void addDroppedImages(List<java.io.File> files) {
    if (files == null || files.isEmpty()) {
      return;
    }
    for (java.io.File file : files) {
      if (file == null) {
        continue;
      }
      Path path = file.toPath();
      if (Files.isRegularFile(path) && EditorImageAssetService.isSupportedImageFile(path)) {
        pendingImageDropFiles.add(path.toAbsolutePath().normalize());
      }
    }
  }

  /**
   * Prueft, ob eine Dateiliste mindestens ein unterstuetztes Bild enthaelt.
   *
   * @param files Dateiliste
   * @return {@code true}, wenn ein Bild enthalten ist
   */
  private boolean containsSupportedImage(List<java.io.File> files) {
    if (files == null || files.isEmpty()) {
      return false;
    }
    for (java.io.File file : files) {
      if (file != null && EditorImageAssetService.isSupportedImageFile(file.toPath())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Kopiert oder verschiebt alle im Popup gesammelten Bilder in den Bildordner.
   *
   * @param move {@code true} fuer Verschieben, sonst Kopieren
   */
  private void transferPendingImages(boolean move) {
    List<Path> files = new ArrayList<>(pendingImageDropFiles);
    for (Path file : files) {
      if (move) {
        EditorImageAssetService.moveIntoImageDirectory(file);
      } else {
        EditorImageAssetService.copyIntoImageDirectory(file);
      }
    }
    pendingImageDropFiles.clear();
    imageDropPopup.hide();
  }

  /**
   * Schliesst das Bildpopup und verwirft nur die zwischengespeicherten Dateipfade.
   */
  private void cancelPendingImages() {
    pendingImageDropFiles.clear();
    imageDropPopup.hide();
  }

  public void setHeaderNoteNumber(Integer noteId) {
    lblHeaderNoteNo.setText(noteId == null ? "—" : Integer.toString(noteId));
  }

  /**
   * Baut eine Bildvorschau fuer Bildverweis-Absaetze.
   *
   * @param paragraphIndex Absatzindex
   * @return Bildvorschau oder leerer Platzhalter
   */
  private Node structuralGraphicForParagraph(int paragraphIndex) {
    if (paragraphIndex < 0 || paragraphIndex >= bodyArea.getParagraphs().size()) {
      return new Region();
    }

    String text = bodyArea.getParagraph(paragraphIndex).getText();
    if (EditorTableService.isTableReferenceParagraph(text)) {
      return tableGraphicForParagraph(text);
    }
    Path imagePath = EditorImageAssetService.resolveReferencePath(text);
    if (imagePath == null || !Files.isRegularFile(imagePath)) {
      return new Region();
    }

    Image image = new Image(imagePath.toUri().toString());
    ImageView imageView = new ImageView(image);
    imageView.getStyleClass().add("note-inline-image-preview");
    if (selectedImageParagraph != null && selectedImageParagraph == paragraphIndex) {
      imageView.getStyleClass().add(SELECTED_IMAGE_STYLE_CLASS);
    }
    imageView.setPreserveRatio(true);
    imageView.fitWidthProperty().bind(Bindings.createDoubleBinding(
        () -> {
          double imageWidth = image.getWidth();
          double editorWidth = Math.max(1.0, bodyArea.getWidth() - IMAGE_PREVIEW_HORIZONTAL_PADDING);
          return imageWidth <= 0.0 ? editorWidth : Math.min(imageWidth, editorWidth);
        },
        image.widthProperty(),
        bodyArea.widthProperty()
    ));
    imageView.setSmooth(true);
    return imageView;
  }

  /**
   * Baut eine Tabellenvorschau fuer Tabellenverweis-Absaetze.
   *
   * @param referenceText gespeicherter Tabellenverweis
   * @return Tabellengitter oder leerer Platzhalter
   */
  private Node tableGraphicForParagraph(String referenceText) {
    var tableSize = EditorTableService.resolveReference(referenceText);
    if (tableSize == null) {
      return new Region();
    }

    GridPane table = new GridPane();
    table.getStyleClass().add("note-inline-table-preview");
    double availableWidth = Math.max(TABLE_CELL_WIDTH, bodyArea.getWidth() - TABLE_PREVIEW_HORIZONTAL_PADDING);
    double cellWidth = Math.max(24.0, availableWidth / tableSize.columns());
    for (int row = 0; row < tableSize.rows(); row++) {
      for (int column = 0; column < tableSize.columns(); column++) {
        InlineCssTextArea cell = new InlineCssTextArea();
        cell.getStyleClass().add("note-inline-table-cell");
        cell.setWrapText(true);
        EditorTableService.decodeCellInto(cell, tableSize.cell(row, column));
        cell.setMinSize(cellWidth, TABLE_CELL_HEIGHT);
        cell.setPrefSize(cellWidth, TABLE_CELL_HEIGHT);
        cell.setMaxWidth(Double.MAX_VALUE);
        int cellRow = row;
        int cellColumn = column;
        cell.focusedProperty().addListener((_, _, focused) -> {
          if (focused) {
            activateTableCell(referenceText, cellRow, cellColumn, cell);
          } else if (cell == activeTableCellArea) {
            persistTableCell(referenceText, cellRow, cellColumn, EditorTableService.encodeCell(cell));
          }
        });
        cell.addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> {
          activateTableCell(referenceText, cellRow, cellColumn, cell);
          showFormattingContextMenu(cell, event.getScreenX(), event.getScreenY());
          event.consume();
        });
        table.add(cell, column, row);
      }
    }
    return table;
  }

  /**
   * Oeffnet das gemeinsame Formatierungsmenue an der angegebenen Bildschirmposition.
   *
   * @param anchor Ankerknoten
   * @param screenX X-Position auf dem Bildschirm
   * @param screenY Y-Position auf dem Bildschirm
   */
  private void showFormattingContextMenu(Node anchor, double screenX, double screenY) {
    if (anchor == null) {
      return;
    }
    if (formattingContextMenu.isShowing()) {
      formattingContextMenu.hide();
    }
    formattingMenuContent.syncFromSelection();
    formattingContextMenu.show(anchor, screenX, screenY);
  }

  /**
   * Merkt sich die aktive Tabellenzelle fuer Tabellenbefehle.
   *
   * @param referenceText Tabellenmarker
   * @param row Zeilenindex
   * @param column Spaltenindex
   */
  private void activateTableCell(String referenceText, int row, int column) {
    activateTableCell(referenceText, row, column, null);
  }

  /**
   * Merkt sich die aktive Tabellenzelle fuer Tabellenbefehle und Formatierungen.
   *
   * @param referenceText Tabellenmarker
   * @param row Zeilenindex
   * @param column Spaltenindex
   * @param cellArea aktiver Zelleditor
   */
  private void activateTableCell(String referenceText, int row, int column, InlineCssTextArea cellArea) {
    int paragraph = tableParagraphIndex(referenceText);
    if (paragraph < 0) {
      clearActiveTableCell();
      return;
    }
    activeTableParagraph = paragraph;
    activeTableRow = row;
    activeTableColumn = column;
    activeTableCellArea = cellArea;
    if (cellArea != null) {
      formattingMenuContent.setFormattingArea(cellArea);
    }
    formattingMenuContent.setTableControlsVisible(true);
  }

  /**
   * Loescht den aktiven Tabellenkontext.
   */
  private void clearActiveTableCell() {
    activeTableParagraph = null;
    activeTableRow = null;
    activeTableColumn = null;
    activeTableCellArea = null;
    formattingMenuContent.setFormattingArea(bodyArea);
    formattingMenuContent.setTableControlsVisible(false);
  }

  /**
   * Speichert die aktive Tabellenzelle nach Formatieraenderungen.
   */
  private void persistActiveTableCell() {
    if (activeTableCellArea != null
        && activeTableParagraph != null
        && activeTableRow != null
        && activeTableColumn != null
        && activeTableParagraph >= 0
        && activeTableParagraph < bodyArea.getParagraphs().size()) {
      persistTableCell(
          bodyArea.getParagraph(activeTableParagraph).getText(),
          activeTableRow,
          activeTableColumn,
          EditorTableService.encodeCell(activeTableCellArea)
      );
    }
  }

  /**
   * Fuehrt eine Tabellenaktion an der aktiven Tabellenzelle aus.
   *
   * @param action Tabellenaktion
   */
  private void handleTableAction(NoteFormattingMenuContent.TableAction action) {
    if (activeTableParagraph == null
        || activeTableRow == null
        || activeTableColumn == null
        || activeTableParagraph < 0
        || activeTableParagraph >= bodyArea.getParagraphs().size()) {
      return;
    }
    persistActiveTableCell();
    String referenceText = bodyArea.getParagraph(activeTableParagraph).getText();
    var tableData = EditorTableService.resolveReference(referenceText);
    if (tableData == null) {
      clearActiveTableCell();
      return;
    }

    var updated = switch (action) {
      case INSERT_COLUMN_LEFT -> tableData.insertColumn(activeTableColumn);
      case INSERT_COLUMN_RIGHT -> tableData.insertColumn(activeTableColumn + 1);
      case INSERT_ROW_ABOVE -> tableData.insertRow(activeTableRow);
      case INSERT_ROW_BELOW -> tableData.insertRow(activeTableRow + 1);
      case DELETE_ROW -> tableData.deleteRow(activeTableRow);
      case DELETE_COLUMN -> tableData.deleteColumn(activeTableColumn);
    };

    if (updated == null) {
      deleteTableParagraph(activeTableParagraph);
      clearActiveTableCell();
      return;
    }

    replaceTableReference(activeTableParagraph, EditorTableService.referenceText(updated));
    if (action == NoteFormattingMenuContent.TableAction.INSERT_COLUMN_RIGHT) {
      activeTableColumn++;
    } else if (action == NoteFormattingMenuContent.TableAction.INSERT_ROW_BELOW) {
      activeTableRow++;
    } else if (action == NoteFormattingMenuContent.TableAction.DELETE_ROW) {
      activeTableRow = Math.min(activeTableRow, updated.rows() - 1);
    } else if (action == NoteFormattingMenuContent.TableAction.DELETE_COLUMN) {
      activeTableColumn = Math.min(activeTableColumn, updated.columns() - 1);
    }
    bodyArea.recreateParagraphGraphic(activeTableParagraph);
    rebindActiveTableCellArea();
  }

  /**
   * Bindet den Formatierungsbereich nach einer neu aufgebauten Tabelle an die neue Zellinstanz.
   */
  private void rebindActiveTableCellArea() {
    if (activeTableParagraph == null || activeTableRow == null || activeTableColumn == null) {
      clearActiveTableCell();
      return;
    }
    Node graphic = bodyArea.getParagraphGraphic(activeTableParagraph);
    if (!(graphic instanceof GridPane table)) {
      activeTableCellArea = null;
      formattingMenuContent.setFormattingArea(bodyArea);
      return;
    }
    var tableData = EditorTableService.resolveReference(bodyArea.getParagraph(activeTableParagraph).getText());
    if (tableData == null) {
      clearActiveTableCell();
      return;
    }
    int index = activeTableRow * tableData.columns() + activeTableColumn;
    if (index < 0 || index >= table.getChildren().size()
        || !(table.getChildren().get(index) instanceof InlineCssTextArea cell)) {
      activeTableCellArea = null;
      formattingMenuContent.setFormattingArea(bodyArea);
      return;
    }
    activeTableCellArea = cell;
    formattingMenuContent.setFormattingArea(cell);
    formattingMenuContent.setTableControlsVisible(true);
  }

  /**
   * Speichert den Inhalt einer bearbeiteten Tabellenzelle im versteckten Tabellenmarker.
   *
   * @param referenceText bisheriger Tabellenmarker
   * @param row Zeilenindex
   * @param column Spaltenindex
   * @param value Zelltext
   */
  private void persistTableCell(String referenceText, int row, int column, String value) {
    int paragraph = tableParagraphIndex(referenceText);
    String currentReference = referenceText;
    if (paragraph < 0
        && activeTableParagraph != null
        && activeTableParagraph >= 0
        && activeTableParagraph < bodyArea.getParagraphs().size()) {
      paragraph = activeTableParagraph;
      currentReference = bodyArea.getParagraph(paragraph).getText();
    }

    var tableData = EditorTableService.resolveReference(currentReference);
    if (tableData == null || row < 0 || column < 0 || row >= tableData.rows() || column >= tableData.columns()) {
      return;
    }
    String updatedReference = EditorTableService.referenceText(tableData.withCell(row, column, value));
    if (paragraph < 0) {
      return;
    }
    replaceTableReference(paragraph, updatedReference);
  }

  /**
   * Findet den Absatzindex eines Tabellenmarkers.
   *
   * @param referenceText Tabellenmarker
   * @return Absatzindex oder -1
   */
  private int tableParagraphIndex(String referenceText) {
    for (int paragraph = 0; paragraph < bodyArea.getParagraphs().size(); paragraph++) {
      if (referenceText.equals(bodyArea.getParagraph(paragraph).getText())) {
        return paragraph;
      }
    }
    return -1;
  }

  /**
   * Ersetzt den versteckten Tabellenmarker eines Absatzes.
   *
   * @param paragraph Absatzindex
   * @param updatedReference neuer Tabellenmarker
   */
  private void replaceTableReference(int paragraph, String updatedReference) {
    int start = bodyArea.getAbsolutePosition(paragraph, 0);
    int end = start + bodyArea.getParagraph(paragraph).getText().length();
    bodyArea.replaceText(start, end, updatedReference);
    bodyArea.setStyle(start, start + updatedReference.length(), HIDDEN_IMAGE_REFERENCE_STYLE);
    bodyArea.setParagraphStyle(paragraph, structuralParagraphStyle(updatedReference));
  }

  /**
   * Loescht einen Tabellenabsatz aus dem Editor.
   *
   * @param paragraph Absatzindex
   */
  private void deleteTableParagraph(int paragraph) {
    if (paragraph < 0 || paragraph >= bodyArea.getParagraphs().size()) {
      return;
    }
    int start = bodyArea.getAbsolutePosition(paragraph, 0);
    int end;
    if (bodyArea.getParagraphs().size() == 1) {
      end = bodyArea.getLength();
    } else if (paragraph < bodyArea.getParagraphs().size() - 1) {
      end = bodyArea.getAbsolutePosition(paragraph + 1, 0);
    } else {
      start = Math.max(0, start - 1);
      end = bodyArea.getLength();
    }
    bodyArea.replaceText(start, end, "");
    bodyArea.moveTo(Math.min(start, bodyArea.getLength()));
  }

  /**
   * Baut den Absatzstil, der die Bildhoehe im normalen Textfluss reserviert.
   *
   * @param referenceText gespeicherter Bildverweis
   * @return Absatz-CSS fuer den Bildabsatz
   */
  private String structuralParagraphStyle(String referenceText) {
    if (EditorTableService.isTableReferenceParagraph(referenceText)) {
      var tableSize = EditorTableService.resolveReference(referenceText);
      double displayHeight = tableSize == null ? HIDDEN_IMAGE_REFERENCE_FONT_SIZE : tableSize.rows() * TABLE_CELL_HEIGHT;
      double bottomPadding = Math.max(0.0, displayHeight - HIDDEN_IMAGE_REFERENCE_FONT_SIZE);
      return "-fx-padding: 0 0 " + Math.ceil(bottomPadding) + "px 0;";
    }

    Path imagePath = EditorImageAssetService.resolveReferencePath(referenceText);
    if (imagePath == null || !Files.isRegularFile(imagePath)) {
      return "-fx-padding: 0;";
    }

    Image image = new Image(imagePath.toUri().toString());
    double displayHeight = displayedImageHeight(image);
    double bottomPadding = Math.max(0.0, displayHeight - HIDDEN_IMAGE_REFERENCE_FONT_SIZE);
    return "-fx-padding: 0 0 " + Math.ceil(bottomPadding) + "px 0;";
  }

  /**
   * Berechnet die sichtbare Bildhoehe unter Beruecksichtigung der Editorbreite.
   *
   * @param image Bild
   * @return sichtbare Hoehe
   */
  private double displayedImageHeight(Image image) {
    double imageWidth = image.getWidth();
    double imageHeight = image.getHeight();
    if (imageWidth <= 0.0 || imageHeight <= 0.0) {
      return HIDDEN_IMAGE_REFERENCE_FONT_SIZE;
    }

    double availableWidth = Math.max(1.0, bodyArea.getWidth() - IMAGE_PREVIEW_HORIZONTAL_PADDING);
    double displayWidth = Math.min(imageWidth, availableWidth);
    return imageHeight * (displayWidth / imageWidth);
  }

  /**
   * Entfernt die sichtbare Bildauswahl.
   */
  private void clearSelectedImageParagraph() {
    if (selectedImageParagraph == null) {
      return;
    }
    int oldParagraph = selectedImageParagraph;
    selectedImageParagraph = null;
    bodyArea.recreateParagraphGraphic(oldParagraph);
  }

  /**
   * Loescht den aktuell markierten Bildabsatz ohne Formatuebertrag auf den Folgeabsatz.
   */
  private void deleteSelectedImageParagraph() {
    if (selectedImageParagraph == null
            || selectedImageParagraph < 0
            || selectedImageParagraph >= bodyArea.getParagraphs().size()) {
      clearSelectedImageParagraph();
      return;
    }

    int paragraph = selectedImageParagraph;
    int start = bodyArea.getAbsolutePosition(paragraph, 0);
    int end;
    if (bodyArea.getParagraphs().size() == 1) {
      end = bodyArea.getLength();
    } else if (paragraph < bodyArea.getParagraphs().size() - 1) {
      end = bodyArea.getAbsolutePosition(paragraph + 1, 0);
    } else {
      start = Math.max(0, start - 1);
      end = bodyArea.getLength();
    }

    selectedImageParagraph = null;
    bodyArea.replaceText(start, end, "");
    int caret = Math.min(start, bodyArea.getLength());
    bodyArea.moveTo(caret);
    int paragraphToNormalize = Math.min(bodyArea.getCurrentParagraph(), bodyArea.getParagraphs().size() - 1);
    if (paragraphToNormalize >= 0) {
      bodyArea.setParagraphStyle(paragraphToNormalize, "");
    }
    refreshImageReferences();
  }

  /**
   * Ermittelt den Bildabsatz an einer lokalen Editorposition.
   *
   * @param x lokale X-Position
   * @param y lokale Y-Position
   * @return Bildabsatz oder {@code null}
   */
  private Integer imageParagraphAt(double x, double y) {
    int insertionIndex = bodyArea.hit(x, y).getInsertionIndex();
    int paragraph = bodyArea.offsetToPosition(insertionIndex, org.fxmisc.richtext.model.TwoDimensional.Bias.Forward)
                            .getMajor();
    if (paragraph < 0 || paragraph >= bodyArea.getParagraphs().size()) {
      return null;
    }
    return EditorImageAssetService.isImageReferenceParagraph(bodyArea.getParagraph(paragraph).getText())
               ? paragraph
               : null;
  }

  /**
   * Aktualisiert die Grafik des markierten Bildabsatzes.
   */
  private void refreshSelectedImageGraphic() {
    if (selectedImageParagraph != null
            && selectedImageParagraph >= 0
            && selectedImageParagraph < bodyArea.getParagraphs().size()) {
      bodyArea.recreateParagraphGraphic(selectedImageParagraph);
    }
  }

  /**
   * Markiert einen Bildabsatz sichtbar.
   *
   * @param paragraph Absatzindex
   */
  private void selectImageParagraph(int paragraph) {
    Integer previous = selectedImageParagraph;
    selectedImageParagraph = paragraph;
    if (previous != null && previous != paragraph) {
      bodyArea.recreateParagraphGraphic(previous);
    }
    bodyArea.recreateParagraphGraphic(paragraph);
    bodyArea.moveTo(bodyArea.getAbsolutePosition(paragraph, 0));
  }

  public void setHeaderCreatedAt(Instant createdAt) {
    if (createdAt == null) {
      lblHeaderCreatedAt.setText("—");
      return;
    }
    var dt = LocalDateTime.ofInstant(createdAt, java.time.ZoneId.systemDefault());
    lblHeaderCreatedAt.setText(DateUtil.formatHeaderDate(dt)); // später hübscher formatieren
  }

  public void setBibliographyVisible(boolean visible) {
    bibliographyHost.setVisible(visible);
    bibliographyHost.setManaged(visible);
  }

  /**
   * Setzt die anzuzeigenden Daten für das Info-Popup.
   * Ist das Popup gerade geöffnet, wird sein Inhalt sofort neu aufgebaut
   * und neu unter dem Info-Button positioniert.
   *
   * @param outgoing ausgehende Referenzen des aktuellen Zettels
   * @param incoming eingehende Referenzen auf den aktuellen Zettel
   * @param projects zugeordnete Projekte
   */
  public void setInfoData(List<NoteReferenceInfo> outgoing,
                          List<NoteReferenceInfo> incoming,
                          List<String> projects) {
    this.infoOutgoing = outgoing == null ? List.of() : List.copyOf(outgoing);
    this.infoIncoming = incoming == null ? List.of() : List.copyOf(incoming);
    this.infoProjects = projects == null ? List.of() : List.copyOf(projects);

    if (infoPopup.isShowing()) {
      refreshOpenInfoPopup();
    }
  }

  public void setOnNavigateToNote(IntConsumer onNavigateToNote) {
    this.onNavigateToNote = (onNavigateToNote == null) ? id -> {} : onNavigateToNote;
  }

  private void showInfoPopup() {
    rebuildInfoPopupContent();

    var b = btnHeaderInfo.localToScreen(btnHeaderInfo.getBoundsInLocal());
    if (b == null) return;

    // rechts unter dem Button anzeigen
    infoPopup.show(btnHeaderInfo, b.getMaxX() - 260, b.getMaxY() + 6);

    // danach exakt zentrieren (Width ist erst nach dem Show/Layout verfügbar)
    javafx.application.Platform.runLater(() -> {
      double w = infoPopupRoot.getWidth();
      if (w <= 0) w = infoPopupRoot.prefWidth(-1);

      double centerX = (b.getMinX() + b.getMaxX()) / 2.0;
      double x = centerX - w / 2.0;

      infoPopup.setX(x);
      infoPopup.setY(b.getMaxY() + 6);
    });
  }

  /**
   * Aktualisiert ein bereits geöffnetes Info-Popup und positioniert es
   * erneut unterhalb des Info-Buttons.
   */
  private void refreshOpenInfoPopup() {
    if (!infoPopup.isShowing()) {
      return;
    }

    rebuildInfoPopupContent();

    var b = btnHeaderInfo.localToScreen(btnHeaderInfo.getBoundsInLocal());
    if (b == null) {
      return;
    }

    javafx.application.Platform.runLater(() -> {
      double w = infoPopupRoot.getWidth();
      if (w <= 0) {
        w = infoPopupRoot.prefWidth(-1);
      }

      double centerX = (b.getMinX() + b.getMaxX()) / 2.0;
      double x = centerX - w / 2.0;

      infoPopup.setX(x);
      infoPopup.setY(b.getMaxY() + 6);
    });
  }

  /**
   * Baut den Inhalt des Info-Popups vollständig neu auf.
   * Angezeigt werden Wortzahl, ausgehende Referenzen, eingehende Referenzen
   * und zugeordnete Projekte.
   */
  private void rebuildInfoPopupContent() {
    infoPopupRoot.getChildren().clear();

    int wc = computeWordCount();
    infoPopupRoot.getChildren().add(sectionLine("Wörter", Integer.toString(wc)));
    infoPopupRoot.getChildren().add(sectionReferenceLinks("Folgezettel", infoOutgoing));
    infoPopupRoot.getChildren().add(sectionReferenceLinks("Verweist auf diesen Zettel", infoIncoming));

    if (infoProjects != null && !infoProjects.isEmpty()) {
      infoPopupRoot.getChildren().add(sectionTexts("Projekte", infoProjects));
    }
  }

  private int computeWordCount() {
    String t = getBodyArea().getText(); // BodyArea existiert bei dir bereits als Getter
    if (t == null) return 0;
    t = t.trim();
    if (t.isEmpty()) return 0;
    return t.split("\\s+").length;
  }

  private Node sectionLine(String title, String value) {
    var row = new HBox(8);
    row.getStyleClass().add("note-info-row");

    var a = new Label(title + ":");
    a.getStyleClass().add("note-info-key");
    var b = new Label(value);
    b.getStyleClass().add("note-info-value");

    row.getChildren().addAll(a, b);
    return row;
  }

  /**
   * Baut einen Abschnitt mit klickbaren Zettelreferenzen für das Info-Popup.
   * Die Einträge selbst zeigen die Zettelnummer, der Tooltip liefert
   * Referenztyp und Überschrift.
   *
   * @param title Abschnittsüberschrift
   * @param references anzuzeigende Referenzen
   * @return UI-Knoten für den Abschnitt
   */
  private Node sectionReferenceLinks(String title, List<NoteReferenceInfo> references) {
    var box = new VBox(4);
    box.getStyleClass().add("note-info-section");

    var head = new Label(title);
    head.getStyleClass().add("note-info-section-title");
    box.getChildren().add(head);

    if (references == null || references.isEmpty()) {
//      var empty = new Label("—");
//      empty.getStyleClass().add("note-info-muted");
      box.getChildren().clear();
      return box;
    }

    var flow = new FlowPane(6, 6);

    for (NoteReferenceInfo reference : references) {
      if (reference == null) {
        continue;
      }

      var link = new Hyperlink(Integer.toString(reference.noteId()));
      link.getStyleClass().add("note-info-link");
      link.setTooltip(new Tooltip(reference.tooltipText()));
      link.setOnAction(e -> {
        infoPopup.hide();
        onNavigateToNote.accept(reference.noteId());
      });

      flow.getChildren().add(link);
    }

    box.getChildren().add(flow);
    return box;
  }

  private Node sectionTexts(String title, List<String> items) {
    var box = new VBox(4);
    box.getStyleClass().add("note-info-section");

    var head = new Label(title);
    head.getStyleClass().add("note-info-section-title");
    box.getChildren().add(head);

    if (items == null || items.isEmpty()) {
      var empty = new Label("—");
      empty.getStyleClass().add("note-info-muted");
      box.getChildren().add(empty);
      return box;
    }

    for (String s : items) {
      if (s == null || s.isBlank()) continue;
      var lbl = new Label(s);
      lbl.getStyleClass().add("note-info-value");
      box.getChildren().add(lbl);
    }
    return box;
  }

  public void setOnHeaderPrev(Runnable r) {
    btnHeaderPrev.setOnAction(e -> { if (r != null) r.run(); });
  }

  public void setOnHeaderNext(Runnable r) {
    btnHeaderNext.setOnAction(e -> { if (r != null) r.run(); });
  }
}
