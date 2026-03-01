package de.zettelkastenfx.notes.controller;

import de.zettelkastenfx.notes.editor.NoteEditorPane;
import javafx.application.Platform;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

public class ZettelWindowController {

  private final Stage stage;
  private final ZettelWindowView view;
  private boolean titleEditing;

  private String titleTextBeforeEdit = "";

  public ZettelWindowController(Stage stage, ZettelWindowView view) {
    this.stage = stage;
    this.view = view;

    wireEvents();
  }

  public void applyInitialState() {
    // Startlayout: drei Bereiche ungefähr gleich
    Platform.runLater(() -> view.getWorkAreaSplitPane().setDividerPositions(0.33, 0.66));
  }

  private void wireEvents() {
    NoteEditorPane editor = view.getNoteEditorPane();

    wireTitleEditing(editor);
    wireGlobalTitleCommitHandlers(editor);
    wireBodyEditingAndContextMenu(editor);
    wireGlobalBodyDeactivateHandlers(editor);

    // Toolbar (oben): Dummies, später echte Funktionen
    view.getToolButtonOne().setOnAction(event -> System.out.println("Tool 1"));
    view.getToolButtonTwo().setOnAction(event -> System.out.println("Tool 2"));
    view.getToolButtonThree().setOnAction(event -> System.out.println("Tool 3"));

    // Menü (oben): "Datei -> Schließen" schließt nur dieses Fenster
    view.getMenuFile().getItems().getLast().setOnAction(event -> stage.close());

    // Toolbar (oben): Dummies, später echte Funktionen (Formatierung, Export, etc.)
    view.getToolButtonOne().setOnAction(event -> System.out.println("Tool 1"));
    view.getToolButtonTwo().setOnAction(event -> System.out.println("Tool 2"));
    view.getToolButtonThree().setOnAction(event -> System.out.println("Tool 3"));

    // Menü (oben): vorerst Dummies
    view.getMenuFile().getItems().getFirst().setOnAction(event -> System.out.println("Neu (kommt später)"));
    view.getMenuFile().getItems().getLast().setOnAction(event -> stage.close());
  }

  private void wireTitleEditing(NoteEditorPane editor) {
    editor.getTitleEditorArea().setEditable(false);
    editor.getTitleEditorArea().setFocusTraversable(false);
    editor.getTitleEditorArea().setMouseTransparent(true);

    editor.getTitleStack().setOnMouseClicked(event -> beginTitleEdit(editor));

    editor.getTitleEditorArea().focusedProperty().addListener((obs, oldFocused, newFocused) -> {
      if (!newFocused) {
        commitTitle(editor);
      }
    });

    editor.getTitleEditorArea().setOnKeyPressed(event -> {
      if (event.getCode() == KeyCode.ENTER) {
        commitTitle(editor);
        event.consume();
      } else if (event.getCode() == KeyCode.ESCAPE) {
        cancelTitle(editor);
        event.consume();
      }
    });
  }

  private void beginTitleEdit(NoteEditorPane editor) {
    if (titleEditing) {
      editor.getTitleEditorArea().requestFocus();
      return;
    }

    titleEditing = true;
    titleTextBeforeEdit = editor.getTitleEditorArea().getText();

    editor.getTitleEditorArea().setMouseTransparent(false);
    editor.getTitleEditorArea().setFocusTraversable(true);
    editor.getTitleEditorArea().setEditable(true);

    editor.getTitleEditorArea().requestFocus();
    editor.getTitleEditorArea().positionCaret(editor.getTitleEditorArea().getText().length());
  }

  private void commitTitle(NoteEditorPane editor) {
    if (!titleEditing) {
      return;
    }
    titleEditing = false;

    String normalized = editor.getTitleEditorArea().getText() == null ? "" : editor.getTitleEditorArea().getText().trim();
    if (normalized.isEmpty()) {
      editor.getTitleEditorArea().clear(); // Prompt "Überschrift" erscheint wieder
    }

    editor.getTitleEditorArea().setEditable(false);
    editor.getTitleEditorArea().setMouseTransparent(true);
    editor.getTitleEditorArea().setFocusTraversable(false);
  }

  private void cancelTitle(NoteEditorPane editor) {
    if (!titleEditing) {
      return;
    }
    titleEditing = false;

    editor.getTitleEditorArea().setText(titleTextBeforeEdit);

    editor.getTitleEditorArea().setEditable(false);
    editor.getTitleEditorArea().setMouseTransparent(true);
    editor.getTitleEditorArea().setFocusTraversable(false);
  }

  private void wireBodyEditingAndContextMenu(NoteEditorPane editor) {
    editor.getBodyArea().setEditable(false);

    final var lastMouseScreenX = new double[] { 0.0 };
    final var lastMouseScreenY = new double[] { 0.0 };

    editor.getBodyArea().addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
      if (!editor.getBodyContainer().getStyleClass().contains("note-body-active")) {
        editor.getBodyContainer().getStyleClass().add("note-body-active");
      }

      editor.getBodyArea().setEditable(true);
      editor.getBodyArea().requestFocus();

      lastMouseScreenX[0] = event.getScreenX();
      lastMouseScreenY[0] = event.getScreenY();
    });

    editor.getBodyArea().addEventHandler(MouseEvent.MOUSE_MOVED, event -> {
      lastMouseScreenX[0] = event.getScreenX();
      lastMouseScreenY[0] = event.getScreenY();
    });

    editor.getBodyArea().focusedProperty().addListener((obs, oldFocused, newFocused) -> {
      if (newFocused) {
        if (!editor.getBodyContainer().getStyleClass().contains("note-body-active")) {
          editor.getBodyContainer().getStyleClass().add("note-body-active");
        }
        editor.getBodyArea().setEditable(true);
      } else {
        editor.getBodyContainer().getStyleClass().remove("note-body-active");
        editor.getBodyArea().setEditable(false);
        editor.getFormattingContextMenu().hide();
      }
    });

    editor.getBodyArea().setOnContextMenuRequested(event -> {
      boolean hasSelection = editor.getBodyArea().getSelection() != null
                                 && editor.getBodyArea().getSelection().getLength() > 0;

      if (hasSelection) {
        editor.getFormattingContextMenu().show(editor.getBodyArea(), event.getScreenX(), event.getScreenY());
      } else {
        editor.getFormattingContextMenu().hide();
      }

      event.consume();
    });

    editor.getBodyArea().addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
      if (event.isPrimaryButtonDown()) {
        editor.getFormattingContextMenu().hide();
      }
    });
  }

  private void wireGlobalBodyDeactivateHandlers(NoteEditorPane editor) {
    stage.focusedProperty().addListener((obs, oldFocused, newFocused) -> {
      if (!newFocused) {
        deactivateBody(editor);
        if (stage.getScene() != null) {
          stage.getScene().getRoot().requestFocus();
        }
      }
    });

    stage.sceneProperty().addListener((obs, oldScene, newScene) -> {
      if (newScene == null) {
        return;
      }

      newScene.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
        if (!editor.getBodyArea().isEditable()) {
          return;
        }

        Object target = event.getTarget();
        if (target instanceof javafx.scene.Node clickedNode) {
          if (!isNodeInside(clickedNode, editor.getBodyContainer())) {
            deactivateBody(editor);
            newScene.getRoot().requestFocus();
          }
        } else {
          deactivateBody(editor);
          newScene.getRoot().requestFocus();
        }
      });
    });
  }

  private void wireGlobalTitleCommitHandlers(NoteEditorPane editor) {
    stage.focusedProperty().addListener((obs, oldFocused, newFocused) -> {
      if (!newFocused) {
        commitTitle(editor);
        if (stage.getScene() != null) {
          stage.getScene().getRoot().requestFocus();
        }
      }
    });

    stage.sceneProperty().addListener((obs, oldScene, newScene) -> {
      if (newScene == null) {
        return;
      }

      newScene.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
        if (!titleEditing) {
          return;
        }

        Object target = event.getTarget();
        if (target instanceof javafx.scene.Node clickedNode) {
          if (!isNodeInside(clickedNode, editor.getTitleStack())) {
            commitTitle(editor);
            newScene.getRoot().requestFocus();
          }
        } else {
          commitTitle(editor);
          newScene.getRoot().requestFocus();
        }
      });
    });
  }

  private boolean isNodeInside(javafx.scene.Node node, javafx.scene.Node container) {
    javafx.scene.Node current = node;
    while (current != null) {
      if (current == container) {
        return true;
      }
      current = current.getParent();
    }
    return false;
  }

  private void deactivateBody(NoteEditorPane editor) {
    editor.getBodyContainer().getStyleClass().remove("note-body-active");
    editor.getBodyArea().setEditable(false);
    editor.getFormattingContextMenu().hide();
  }
}