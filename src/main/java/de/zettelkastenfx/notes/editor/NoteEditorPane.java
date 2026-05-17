package de.zettelkastenfx.notes.editor;

import de.zettelkastenfx.base.BaseIcon;
import de.zettelkastenfx.base.util.DateUtil;
import de.zettelkastenfx.notes.editor.format.NoteFormattingMenuContent;
import de.zettelkastenfx.notes.editor.media.EditorImageAssetService;
import de.zettelkastenfx.notes.editor.web.TinyMceEditorPane;
import de.zettelkastenfx.notes.model.NoteReferenceInfo;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.FileChooser;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.IntConsumer;

public class NoteEditorPane extends VBox {

  private final StackPane titleStack;
  private final TextArea titleEditorArea;
  private final TinyMceEditorPane webBodyEditor;
  private final ContextMenu formattingContextMenu;
  private final HBox formattingBarContainer;
  private final Region formattingDragHandle;
  private final StackPane bodyContainer;
  private final NoteFormattingMenuContent formattingMenuContent;
  private final BorderPane headerBar;
  private final Label lblHeaderNoteNo;
  private final Label lblHeaderCreatedAt;
  private final Button btnHeaderPrev;
  private final Button btnHeaderNext;
  private final Button btnHeaderInfo;
  private final Popup infoPopup = new Popup();
  private final VBox infoPopupRoot = new VBox(8);
  private final VBox bibliographyHost;

  private boolean bodySourceMode;
  private IntConsumer onNavigateToNote = _ -> {};
  private List<NoteReferenceInfo> infoOutgoing = List.of();
  private List<NoteReferenceInfo> infoIncoming = List.of();
  private List<String> infoProjects = List.of();

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
    btnHeaderPrev = BaseIcon.BULLET_ARROW_LEFT.button(null);
    btnHeaderNext = BaseIcon.BULLET_ARROW_RIGHT.button(null);
    btnHeaderPrev.getStyleClass().add("note-header-nav");
    btnHeaderNext.getStyleClass().add("note-header-nav");
    btnHeaderPrev.setFocusTraversable(false);
    btnHeaderNext.setFocusTraversable(false);
    HBox.setMargin(btnHeaderInfo, new Insets(0, 0, 0, 10));
    right.getChildren().addAll(btnHeaderPrev, btnHeaderNext, btnHeaderInfo);

    headerBar.setLeft(left);
    headerBar.setCenter(center);
    headerBar.setRight(right);

    infoPopupRoot.getStyleClass().add("note-info-popup");
    infoPopup.getContent().add(infoPopupRoot);
    infoPopup.setAutoHide(false);
    infoPopupRoot.setOnMouseExited(_ -> infoPopup.hide());
    btnHeaderInfo.setOnAction(_ -> {
      if (infoPopup.isShowing()) {
        infoPopup.hide();
      } else {
        showInfoPopup();
      }
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
    titleEditorArea.setTextFormatter(new TextFormatter<String>(change -> {
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

    webBodyEditor = new TinyMceEditorPane();
    webBodyEditor.setOnContentChanged(this::refreshOpenInfoPopup);
    webBodyEditor.setOnSourceModeChanged(sourceMode -> bodySourceMode = sourceMode);

    bodyContainer = new StackPane(webBodyEditor);
    bodyContainer.getStyleClass().add("note-body-container");
    VBox.setVgrow(bodyContainer, Priority.ALWAYS);

    bibliographyHost = new VBox();
    bibliographyHost.getStyleClass().add("note-bibliography-host");
    bibliographyHost.setVisible(false);
    bibliographyHost.setManaged(false);

    formattingBarContainer = new HBox();
    formattingBarContainer.getStyleClass().add("note-formatting-bar");
    formattingBarContainer.setMinHeight(Region.USE_COMPUTED_SIZE);
    formattingBarContainer.setPrefHeight(Region.USE_COMPUTED_SIZE);

    formattingMenuContent = new NoteFormattingMenuContent();
    webBodyEditor.setOnTableContextChanged(formattingMenuContent::setTableControlsVisible);
    formattingMenuContent.setOnTableActionRequested(this::handleTableAction);
    formattingMenuContent.setOnFormatCommandRequested(this::handleWebFormatCommand);
    formattingDragHandle = new Region();
    formattingDragHandle.getStyleClass().add("note-formatting-drag-handle");
    formattingDragHandle.setCursor(Cursor.HAND);
    formattingContextMenu = new ContextMenu();
    installFormattingDragHandle();
    formattingBarContainer.getChildren().setAll(formattingDragHandle, formattingMenuContent.getRoot());
    formattingContextMenu.getItems().setAll(new CustomMenuItem(formattingBarContainer, false));
    formattingContextMenu.getStyleClass().add("note-formatting-context-menu");
    formattingContextMenu.setOnShowing(_ -> formattingMenuContent.syncFromSelection());

    getChildren().addAll(headerBar, titleStack, bodyContainer, bibliographyHost);
  }

  public TinyMceEditorPane getWebBodyEditor() {
    return webBodyEditor;
  }

  public StackPane getTitleStack() {
    return titleStack;
  }

  public TextArea getTitleEditorArea() {
    return titleEditorArea;
  }

  public ContextMenu getFormattingContextMenu() {
    return formattingContextMenu;
  }

  public void toggleBodySourceMode() {
    webBodyEditor.toggleSourceMode();
    bodySourceMode = webBodyEditor.isSourceMode();
  }

  public void commitBodySourceViewIfActive() {
    webBodyEditor.commitSourceViewIfActive();
    bodySourceMode = false;
  }

  public void discardBodySourceView() {
    bodySourceMode = false;
  }

  public boolean isBodySourceMode() {
    return bodySourceMode || webBodyEditor.isSourceMode();
  }

  public void prepareBodyFormattingContext() {
    formattingMenuContent.setTableControlsVisible(webBodyEditor.isTableContextActive());
    formattingMenuContent.syncFromSelection();
  }

  public void setBodyHtml(String html) {
    webBodyEditor.setHtmlContent(html);
  }

  public String getBodyHtml() {
    return webBodyEditor.getHtmlContent();
  }

  public String getBodyPlainText() {
    return webBodyEditor.getPlainText();
  }

  public void requestBodyEditorFocus() {
    webBodyEditor.requestEditorFocus();
  }

  public StackPane getBodyContainer() {
    return bodyContainer;
  }

  public BorderPane getHeaderBar() {
    return headerBar;
  }

  public VBox getBibliographyHost() {
    return bibliographyHost;
  }

  public void refreshImageReferences() {
    // Bilder werden im WebView direkt als HTML-Bilder gerendert.
  }

  private boolean handleWebFormatCommand(String command, String value) {
    if ("image".equals(command)) {
      insertImageViaFileChooser();
      return true;
    }
    if ("insertTable".equals(command)) {
      String[] parts = value == null ? new String[] {"1", "1"} : value.split("x", 2);
      int rows = parsePositiveInt(parts.length > 0 ? parts[0] : "1", 1);
      int columns = parsePositiveInt(parts.length > 1 ? parts[1] : "1", 1);
      webBodyEditor.insertTable(rows, columns);
      return true;
    }
    webBodyEditor.executeCommand(command, value);
    return true;
  }

  private void handleTableAction(NoteFormattingMenuContent.TableAction action) {
    if (webBodyEditor.isTableContextActive()) {
      webBodyEditor.executeCommand("tableAction", action.name());
    }
  }

  private int parsePositiveInt(String value, int fallback) {
    try {
      return Math.max(1, Integer.parseInt(value));
    } catch (RuntimeException ex) {
      return fallback;
    }
  }

  private void insertImageViaFileChooser() {
    FileChooser chooser = new FileChooser();
    chooser.setTitle("Bild auswählen");
    chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
        "Bilddateien",
        "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.webp", "*.tif", "*.tiff"
    ));
    java.io.File selected = chooser.showOpenDialog(getScene() == null ? null : getScene().getWindow());
    if (selected == null) {
      return;
    }
    Path imagePath = selected.toPath().toAbsolutePath().normalize();
    if (EditorImageAssetService.isSupportedImageFile(imagePath)) {
      webBodyEditor.insertImage(imagePath.toUri().toString());
    }
  }

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

  public void setHeaderNoteNumber(Integer noteId) {
    lblHeaderNoteNo.setText(noteId == null || noteId <= 0 ? "—" : Integer.toString(noteId));
  }

  public void setHeaderCreatedAt(Instant createdAt) {
    if (createdAt == null) {
      lblHeaderCreatedAt.setText("—");
      return;
    }
    var dt = LocalDateTime.ofInstant(createdAt, java.time.ZoneId.systemDefault());
    lblHeaderCreatedAt.setText(DateUtil.formatHeaderDate(dt));
  }

  public void setBibliographyVisible(boolean visible) {
    bibliographyHost.setVisible(visible);
    bibliographyHost.setManaged(visible);
  }

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
    this.onNavigateToNote = onNavigateToNote == null ? _ -> {} : onNavigateToNote;
  }

  private void showInfoPopup() {
    rebuildInfoPopupContent();
    var b = btnHeaderInfo.localToScreen(btnHeaderInfo.getBoundsInLocal());
    if (b == null) {
      return;
    }
    infoPopup.show(btnHeaderInfo, b.getMaxX() - 260, b.getMaxY() + 6);
    javafx.application.Platform.runLater(() -> {
      double w = infoPopupRoot.getWidth();
      if (w <= 0) {
        w = infoPopupRoot.prefWidth(-1);
      }
      double centerX = (b.getMinX() + b.getMaxX()) / 2.0;
      infoPopup.setX(centerX - w / 2.0);
      infoPopup.setY(b.getMaxY() + 6);
    });
  }

  private void refreshOpenInfoPopup() {
    if (!infoPopup.isShowing()) {
      return;
    }
    rebuildInfoPopupContent();
  }

  private void rebuildInfoPopupContent() {
    infoPopupRoot.getChildren().clear();
    infoPopupRoot.getChildren().add(sectionLine("Wörter", Integer.toString(computeWordCount())));
    infoPopupRoot.getChildren().add(sectionReferenceLinks("Folgezettel", infoOutgoing));
    infoPopupRoot.getChildren().add(sectionReferenceLinks("Verweist auf diesen Zettel", infoIncoming));
    if (infoProjects != null && !infoProjects.isEmpty()) {
      infoPopupRoot.getChildren().add(sectionTexts("Projekte", infoProjects));
    }
  }

  private int computeWordCount() {
    String text = getBodyPlainText();
    if (text == null || text.isBlank()) {
      return 0;
    }
    return text.trim().split("\\s+").length;
  }

  private Node sectionLine(String title, String value) {
    var row = new HBox(8);
    row.getStyleClass().add("note-info-row");
    var key = new Label(title + ":");
    key.getStyleClass().add("note-info-key");
    var label = new Label(value);
    label.getStyleClass().add("note-info-value");
    row.getChildren().addAll(key, label);
    return row;
  }

  private Node sectionReferenceLinks(String title, List<NoteReferenceInfo> references) {
    var box = new VBox(4);
    box.getStyleClass().add("note-info-section");
    var head = new Label(title);
    head.getStyleClass().add("note-info-section-title");
    box.getChildren().add(head);
    if (references == null || references.isEmpty()) {
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
      link.setOnAction(_ -> {
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
    for (String item : items) {
      if (item == null || item.isBlank()) {
        continue;
      }
      var label = new Label(item);
      label.getStyleClass().add("note-info-value");
      box.getChildren().add(label);
    }
    return box;
  }

  public void setOnHeaderPrev(Runnable r) {
    btnHeaderPrev.setOnAction(_ -> {
      if (r != null) {
        r.run();
      }
    });
  }

  public void setOnHeaderNext(Runnable r) {
    btnHeaderNext.setOnAction(_ -> {
      if (r != null) {
        r.run();
      }
    });
  }
}
