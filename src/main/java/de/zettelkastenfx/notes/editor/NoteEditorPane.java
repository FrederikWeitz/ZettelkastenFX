package de.zettelkastenfx.notes.editor;

import de.zettelkastenfx.notes.editor.format.NoteFormattingMenuContent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.*;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.InlineCssTextArea;

public class NoteEditorPane extends VBox {

  private final HBox headerBar;
  private final Label headerLabel;

  private final StackPane titleStack;
  private final TextArea titleEditorArea;

  private final InlineCssTextArea bodyArea;
  private final VirtualizedScrollPane<InlineCssTextArea> bodyScrollPane;

  private final ContextMenu formattingContextMenu;
  private final HBox formattingBarContainer;

  private final StackPane bodyContainer;
  private final NoteFormattingMenuContent formattingMenuContent;

  public NoteEditorPane() {
    getStyleClass().add("note-editor-pane");
    setSpacing(0);

    headerLabel = new Label("Zettel");
    headerLabel.getStyleClass().add("note-header-label");

    headerBar = new HBox(headerLabel);
    headerBar.getStyleClass().add("note-header-bar");
    headerBar.setAlignment(Pos.CENTER_LEFT);
    headerBar.setPadding(new Insets(6, 10, 6, 10));

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

    getChildren().addAll(headerBar, titleStack, bodyContainer);
  }

  public HBox getHeaderBar() {
    return headerBar;
  }

  public TextArea getTitleEditorArea() {
    return titleEditorArea;
  }

  public InlineCssTextArea getBodyArea() {
    return bodyArea;
  }

  public StackPane getBodyContainer() {
    return bodyContainer;
  }

  public StackPane getTitleStack() {
    return titleStack;
  }

  public ContextMenu getFormattingContextMenu() {
    return formattingContextMenu;
  }
}
