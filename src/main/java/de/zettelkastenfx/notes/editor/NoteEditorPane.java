package de.zettelkastenfx.notes.editor;

import de.zettelkastenfx.base.BaseIcon;
import de.zettelkastenfx.base.util.DateUtil;
import de.zettelkastenfx.notes.editor.format.NoteFormattingMenuContent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Popup;
import lombok.Getter;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.InlineCssTextArea;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.IntConsumer;

public class NoteEditorPane extends VBox {

  @Getter
  private final StackPane titleStack;
  @Getter
  private final TextArea titleEditorArea;

  @Getter
  private final InlineCssTextArea bodyArea;
  private final VirtualizedScrollPane<InlineCssTextArea> bodyScrollPane;

  @Getter
  private final ContextMenu formattingContextMenu;
  private final HBox formattingBarContainer;

  @Getter
  private final StackPane bodyContainer;
  private final NoteFormattingMenuContent formattingMenuContent;

  // HeaderBar
  @Getter
  private final BorderPane headerBar;

  private final Label lblHeaderNoteNo;
  private final Label lblHeaderCreatedAt;
  private final Button btnHeaderPrev;
  private final Button btnHeaderNext;
  private final Button btnHeaderInfo;

  // Info-Popup
  private final Popup infoPopup = new javafx.stage.Popup();
  private final VBox infoPopupRoot = new javafx.scene.layout.VBox(8);

  private IntConsumer onNavigateToNote = id -> {};
  private List<Integer> infoOutgoing = java.util.List.of();
  private List<Integer> infoIncoming = java.util.List.of();
  private List<String> infoProjects = java.util.List.of();

  // Fenster für die bibliographischen Angaben
  @Getter
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
    btnHeaderInfo = new Button("i");
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
    formattingBarContainer.getChildren().setAll(formattingMenuContent.getRoot());

    CustomMenuItem formattingMenuItem = new CustomMenuItem(formattingBarContainer, false);
    formattingContextMenu = new ContextMenu(formattingMenuItem);

    formattingContextMenu.setOnShowing(e -> formattingMenuContent.syncFromSelection());

    getChildren().addAll(headerBar, titleStack, bodyContainer, bibliographyHost);
  }

  public void setHeaderNoteNumber(Integer noteId) {
    lblHeaderNoteNo.setText(noteId == null ? "—" : Integer.toString(noteId));
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

  public void setInfoData(List<Integer> outgoing,
                          List<Integer> incoming,
                          List<String> projects) {
    this.infoOutgoing = outgoing == null ? java.util.List.of() : java.util.List.copyOf(outgoing);
    this.infoIncoming = incoming == null ? java.util.List.of() : java.util.List.copyOf(incoming);
    this.infoProjects = projects == null ? java.util.List.of() : java.util.List.copyOf(projects);
  }

  public void setOnNavigateToNote(java.util.function.IntConsumer onNavigateToNote) {
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

  private void rebuildInfoPopupContent() {
    infoPopupRoot.getChildren().clear();

    int wc = computeWordCount();
    infoPopupRoot.getChildren().add(sectionLine("Wörter", Integer.toString(wc)));

    if (infoOutgoing != null && !infoOutgoing.isEmpty()) {
      infoPopupRoot.getChildren().add(sectionLinks("Folgezettel", infoOutgoing));
    }

    if (infoIncoming != null && !infoIncoming.isEmpty()) {
      infoPopupRoot.getChildren().add(sectionLinks("Verweist auf diesen Zettel", infoIncoming));
    }

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

  private Node sectionLinks(String title, List<Integer> ids) {
    var box = new VBox(4);
    box.getStyleClass().add("note-info-section");

    var head = new Label(title);
    head.getStyleClass().add("note-info-section-title");
    box.getChildren().add(head);

    if (ids == null || ids.isEmpty()) {
      var empty = new Label("—");
      empty.getStyleClass().add("note-info-muted");
      box.getChildren().add(empty);
      return box;
    }

    var flow = new FlowPane(6, 6);
    for (Integer id : ids) {
      if (id == null) continue;
      var link = new Hyperlink(Integer.toString(id));
      link.getStyleClass().add("note-info-link");
      link.setOnAction(e -> {
        infoPopup.hide();
        onNavigateToNote.accept(id);
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
