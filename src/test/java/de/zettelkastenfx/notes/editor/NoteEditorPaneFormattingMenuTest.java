package de.zettelkastenfx.notes.editor;

import javafx.application.Platform;
import javafx.scene.Cursor;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Control;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import de.zettelkastenfx.notes.keywords.KeywordsTablePane;
import de.zettelkastenfx.notes.model.Note;
import org.fxmisc.richtext.InlineCssTextArea;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Prueft die Struktur des Formatierungsmenues im Zetteleditor.
 */
class NoteEditorPaneFormattingMenuTest {

  @TempDir
  Path tempDir;

  /**
   * Startet die JavaFX-Plattform fuer View-nahe Tests.
   *
   * @throws InterruptedException wenn der Testthread unterbrochen wird
   */
  @BeforeAll
  static void startJavaFxToolkit() throws InterruptedException {
    CountDownLatch startupLatch = new CountDownLatch(1);
    try {
      Platform.startup(() -> {
        Platform.setImplicitExit(false);
        startupLatch.countDown();
      });
      if (!startupLatch.await(5, TimeUnit.SECONDS)) {
        fail("JavaFX-Plattform wurde nicht rechtzeitig gestartet.");
      }
    } catch (IllegalStateException alreadyStarted) {
      Platform.setImplicitExit(false);
    }
  }

  /**
   * Sichert den linken Griff des Formatierungsmenues als Drag-Zone ab.
   *
   * @throws Exception wenn der JavaFX-Testcode fehlschlaegt
   */
  @Test
  void formattingMenuStartsWithDragHandle() throws Exception {
    runOnFxThread(() -> {
      NoteEditorPane editor = new NoteEditorPane();
      ContextMenu menu = editor.getFormattingContextMenu();
      CustomMenuItem item = (CustomMenuItem) menu.getItems().getFirst();
      HBox bar = (HBox) item.getContent();
      Region dragHandle = (Region) bar.getChildren().getFirst();

      assertAll(
          () -> assertTrue(menu.getStyleClass().contains("note-formatting-context-menu")),
          () -> assertTrue(bar.getStyleClass().contains("note-formatting-bar")),
          () -> assertTrue(dragHandle.getStyleClass().contains("note-formatting-drag-handle")),
          () -> assertEquals(Cursor.HAND, dragHandle.getCursor())
      );
    });
  }

  /**
   * Sichert die Position des Zitatbuttons direkt nach den Ueberschriften ab.
   *
   * @throws Exception wenn der JavaFX-Testcode fehlschlaegt
   */
  @Test
  void formattingMenuPlacesCitationButtonAfterHeadings() throws Exception {
    runOnFxThread(() -> {
      NoteEditorPane editor = new NoteEditorPane();
      ContextMenu menu = editor.getFormattingContextMenu();
      CustomMenuItem item = (CustomMenuItem) menu.getItems().getFirst();
      HBox bar = (HBox) item.getContent();
      VBox contentRoot = (VBox) bar.getChildren().get(1);
      HBox firstRow = (HBox) contentRoot.getChildren().getFirst();
      Tooltip tooltip = ((Control) firstRow.getChildren().get(11)).getTooltip();

      assertNotNull(tooltip);
      assertEquals("Zitat einfügen", tooltip.getText());
    });
  }

  /**
   * Sichert die Position des Separatorbuttons direkt nach dem Zitatbutton ab.
   *
   * @throws Exception wenn der JavaFX-Testcode fehlschlaegt
   */
  @Test
  void formattingMenuPlacesSeparatorButtonAfterCitation() throws Exception {
    runOnFxThread(() -> {
      NoteEditorPane editor = new NoteEditorPane();
      ContextMenu menu = editor.getFormattingContextMenu();
      CustomMenuItem item = (CustomMenuItem) menu.getItems().getFirst();
      HBox bar = (HBox) item.getContent();
      VBox contentRoot = (VBox) bar.getChildren().get(1);
      HBox firstRow = (HBox) contentRoot.getChildren().getFirst();
      Tooltip tooltip = ((Control) firstRow.getChildren().get(12)).getTooltip();

      assertNotNull(tooltip);
      assertEquals("Zwischenüberschrift", tooltip.getText());
    });
  }

  /**
   * Sichert die Position des Bildbuttons direkt nach dem Separatorbutton ab.
   *
   * @throws Exception wenn der JavaFX-Testcode fehlschlaegt
   */
  @Test
  void formattingMenuPlacesImageButtonAfterSeparator() throws Exception {
    runOnFxThread(() -> {
      NoteEditorPane editor = new NoteEditorPane();
      ContextMenu menu = editor.getFormattingContextMenu();
      CustomMenuItem item = (CustomMenuItem) menu.getItems().getFirst();
      HBox bar = (HBox) item.getContent();
      VBox contentRoot = (VBox) bar.getChildren().get(1);
      HBox firstRow = (HBox) contentRoot.getChildren().getFirst();
      Tooltip tooltip = ((Control) firstRow.getChildren().get(13)).getTooltip();

      assertNotNull(tooltip);
      assertEquals("Bild einfügen", tooltip.getText());
    });
  }

  /**
   * Sichert die Position des Tabellenbuttons direkt nach dem Bildbutton ab.
   *
   * @throws Exception wenn der JavaFX-Testcode fehlschlaegt
   */
  @Test
  void formattingMenuPlacesTableButtonAfterImage() throws Exception {
    runOnFxThread(() -> {
      NoteEditorPane editor = new NoteEditorPane();
      ContextMenu menu = editor.getFormattingContextMenu();
      CustomMenuItem item = (CustomMenuItem) menu.getItems().getFirst();
      HBox bar = (HBox) item.getContent();
      VBox contentRoot = (VBox) bar.getChildren().get(1);
      HBox firstRow = (HBox) contentRoot.getChildren().getFirst();
      Tooltip tooltip = ((Control) firstRow.getChildren().get(14)).getTooltip();

      assertNotNull(tooltip);
      assertEquals("Tabelle einfügen", tooltip.getText());
    });
  }

  /**
   * Reserviert fuer Bildverweis-Absaetze die sichtbare Bildhoehe im Textfluss.
   *
   * @throws Exception wenn der JavaFX-Testcode fehlschlaegt
   */
  @Test
  void imageReferenceParagraphReservesImageHeightInTextFlow() throws Exception {
    String previousHome = System.getProperty("user.home");
    System.setProperty("user.home", tempDir.toString());
    try {
      Path imageDirectory = tempDir.resolve(".zettelkastenfx").resolve("image");
      Files.createDirectories(imageDirectory);
      Files.write(
          imageDirectory.resolve("bild.png"),
          Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAFElEQVR42mP8z8AARLJgYGBgAAAOAQICBHFNWQAAAABJRU5ErkJggg==")
      );

      runOnFxThread(() -> {
        NoteEditorPane editor = new NoteEditorPane();
        editor.getBodyArea().replaceText("[[image:image/bild.png]]");
        editor.getBodyArea().resize(220.0, 120.0);

        editor.refreshImageReferences();

        String paragraphStyle = editor.getBodyArea().getParagraph(0).getParagraphStyle();
        assertTrue(paragraphStyle.contains("-fx-padding: 0 0 "));
        assertTrue(paragraphStyle.contains("px 0;"));
        assertTrue(editor.getBodyArea().getStyleAtPosition(0).contains("-fx-font-size: 1px"));
      });
    } finally {
      System.setProperty("user.home", previousHome);
    }
  }

  /**
   * Sichert die Grundregeln des Drag-and-drop-Bildpopups ab.
   *
   * @throws Exception wenn der JavaFX-Testcode fehlschlaegt
   */
  @Test
  void imageDropPopupKeepsApplicationFocusAndOffersCancelLast() throws Exception {
    runOnFxThread(() -> {
      NoteEditorPane editor = new NoteEditorPane();
      Popup popup = (Popup) privateField(editor, "imageDropPopup");
      VBox root = (VBox) privateField(editor, "imageDropRoot");
      HBox actions = (HBox) root.getChildren().getLast();

      assertEquals(false, popup.isAutoHide());
      assertEquals(3, actions.getChildren().size());
      assertEquals("Kopieren", ((javafx.scene.control.Button) actions.getChildren().get(1)).getText());
      assertEquals("Abbrechen", ((javafx.scene.control.Button) actions.getChildren().getLast()).getText());
    });
  }

  /**
   * Loescht einen Bildabsatz ohne Bildformat auf den folgenden Textabsatz zu uebertragen.
   *
   * @throws Exception wenn der JavaFX-Testcode fehlschlaegt
   */
  @Test
  void deletingSelectedImageParagraphDoesNotDeformFollowingTextParagraph() throws Exception {
    String previousHome = System.getProperty("user.home");
    System.setProperty("user.home", tempDir.toString());
    try {
      Path imageDirectory = tempDir.resolve(".zettelkastenfx").resolve("image");
      Files.createDirectories(imageDirectory);
      Files.write(
          imageDirectory.resolve("bild.png"),
          Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAFElEQVR42mP8z8AARLJgYGBgAAAOAQICBHFNWQAAAABJRU5ErkJggg==")
      );

      runOnFxThread(() -> {
        NoteEditorPane editor = new NoteEditorPane();
        editor.getBodyArea().replaceText("[[image:image/bild.png]]\nNormaler Text");
        editor.getBodyArea().resize(220.0, 120.0);
        editor.refreshImageReferences();

        setPrivateField(editor, "selectedImageParagraph", 0);
        invokePrivate(editor, "deleteSelectedImageParagraph");

        assertEquals("Normaler Text", editor.getBodyArea().getText());
        assertEquals("", editor.getBodyArea().getParagraph(0).getParagraphStyle());
      });
    } finally {
      System.setProperty("user.home", previousHome);
    }
  }

  /**
   * Fuegt einen Tabellenmarker als eigenen Absatz ein und blendet ihn fuer die Vorschau aus.
   *
   * @throws Exception wenn der JavaFX-Testcode fehlschlaegt
   */
  @Test
  void insertsTableReferenceAsHiddenParagraph() throws Exception {
    runOnFxThread(() -> {
      NoteEditorPane editor = new NoteEditorPane();
      Object formattingContent = privateField(editor, "formattingMenuContent");

      invokePrivate(formattingContent, "insertTableReference", 3, 4);
      editor.refreshImageReferences();

      assertTrue(editor.getBodyArea().getText().startsWith("[[table:3x4:"));
      assertTrue(editor.getBodyArea().getStyleAtPosition(0).contains("-fx-font-size: 1px"));
      assertTrue(editor.getBodyArea().getParagraph(0).getParagraphStyle().contains("-fx-padding: 0 0 "));
    });
  }

  /**
   * Speichert bearbeitete Zelltexte in den Tabellenmarker zurueck.
   *
   * @throws Exception wenn der JavaFX-Testcode fehlschlaegt
   */
  @Test
  void editableTableCellPersistsContentInReference() throws Exception {
    runOnFxThread(() -> {
      NoteEditorPane editor = new NoteEditorPane();
      Object formattingContent = privateField(editor, "formattingMenuContent");
      invokePrivate(formattingContent, "insertTableReference", 2, 2);
      editor.getBodyArea().resize(420.0, 160.0);
      editor.refreshImageReferences();

      var graphic = editor.getBodyArea().getParagraphGraphic(0);
      InlineCssTextArea cell = (InlineCssTextArea) ((javafx.scene.layout.GridPane) graphic).getChildren().get(1);
      cell.replaceText("Alpha");
      invokePrivate(editor, "persistTableCell", editor.getBodyArea().getParagraph(0).getText(), 0, 1, "Alpha");

      assertTrue(editor.getBodyArea().getParagraph(0).getText().startsWith("[[table:2x2:"));
      assertEquals(
          "Alpha",
          de.zettelkastenfx.notes.editor.table.EditorTableService
              .resolveReference(editor.getBodyArea().getParagraph(0).getText())
              .cell(0, 1)
      );
    });
  }

  /**
   * Sichert, dass Tabellenzellen das gemeinsame Formatierungsmenue statt des Standard-TextArea-Menues nutzen.
   *
   * @throws Exception wenn der JavaFX-Testcode fehlschlaegt
   */
  @Test
  void tableCellUsesSharedFormattingMenuOnContextRequest() throws Exception {
    runOnFxThread(() -> {
      NoteEditorPane editor = new NoteEditorPane();
      Object formattingContent = privateField(editor, "formattingMenuContent");
      invokePrivate(formattingContent, "insertTableReference", 2, 2);
      editor.getBodyArea().resize(420.0, 160.0);
      editor.refreshImageReferences();

      var graphic = editor.getBodyArea().getParagraphGraphic(0);
      InlineCssTextArea cell = (InlineCssTextArea) ((javafx.scene.layout.GridPane) graphic).getChildren().get(0);

      assertNotNull(cell.getEventDispatcher());
      invokePrivate(editor, "activateTableCell", editor.getBodyArea().getParagraph(0).getText(), 0, 0);
      assertTrue(((HBox) privateField(formattingContent, "tableRow")).isVisible());
      assertEquals(1, editor.getFormattingContextMenu().getItems().size());
    });
  }

  /**
   * Formatiert markierten Text in einer Tabellenzelle ueber das gemeinsame Formatierungsmenue.
   *
   * @throws Exception wenn der JavaFX-Testcode fehlschlaegt
   */
  @Test
  void formattingMenuAppliesInlineStyleToActiveTableCell() throws Exception {
    runOnFxThread(() -> {
      NoteEditorPane editor = new NoteEditorPane();
      Object formattingContent = privateField(editor, "formattingMenuContent");
      invokePrivate(formattingContent, "insertTableReference", 1, 1);
      editor.getBodyArea().resize(420.0, 160.0);
      editor.refreshImageReferences();

      var graphic = editor.getBodyArea().getParagraphGraphic(0);
      InlineCssTextArea cell = (InlineCssTextArea) ((javafx.scene.layout.GridPane) graphic).getChildren().getFirst();
      cell.replaceText("Alpha");
      cell.selectRange(0, 5);
      invokePrivate(editor, "activateTableCell", editor.getBodyArea().getParagraph(0).getText(), 0, 0, cell);
      invokePrivate(formattingContent, "setFormattingArea", cell);

      HBox bar = (HBox) ((CustomMenuItem) editor.getFormattingContextMenu().getItems().getFirst()).getContent();
      VBox contentRoot = (VBox) bar.getChildren().get(1);
      HBox firstRow = (HBox) contentRoot.getChildren().getFirst();
      ((javafx.scene.control.ToggleButton) firstRow.getChildren().get(1)).fire();

      assertTrue(cell.getStyleAtPosition(0).contains("-fx-font-weight"));
      assertTrue(
          de.zettelkastenfx.notes.editor.table.EditorTableService
              .resolveReference(editor.getBodyArea().getParagraph(0).getText())
              .cell(0, 0)
              .startsWith("{rtfx}")
      );
    });
  }

  /**
   * Setzt den Tabellenkontext zurueck, wenn das Formatierungsmenue fuer normalen Text vorbereitet wird.
   *
   * @throws Exception wenn der JavaFX-Testcode fehlschlaegt
   */
  @Test
  void bodyFormattingContextClearsActiveTableFormattingTarget() throws Exception {
    runOnFxThread(() -> {
      NoteEditorPane editor = new NoteEditorPane();
      Object formattingContent = privateField(editor, "formattingMenuContent");
      invokePrivate(formattingContent, "insertTableReference", 1, 1);
      editor.getBodyArea().resize(420.0, 160.0);
      editor.refreshImageReferences();

      var graphic = editor.getBodyArea().getParagraphGraphic(0);
      InlineCssTextArea cell = (InlineCssTextArea) ((javafx.scene.layout.GridPane) graphic).getChildren().getFirst();
      invokePrivate(editor, "activateTableCell", editor.getBodyArea().getParagraph(0).getText(), 0, 0, cell);
      assertTrue(((HBox) privateField(formattingContent, "tableRow")).isVisible());

      editor.prepareBodyFormattingContext();

      assertFalse(((HBox) privateField(formattingContent, "tableRow")).isVisible());
    });
  }

  /**
   * Zeigt RichText-Formatbefehle als editierbaren Quelltext und wendet Aenderungen wieder an.
   *
   * @throws Exception wenn der JavaFX-Testcode fehlschlaegt
   */
  @Test
  void altSourceModeShowsAndAppliesInlineStyles() throws Exception {
    runOnFxThread(() -> {
      NoteEditorPane editor = new NoteEditorPane();
      editor.getBodyArea().replaceText("Alpha");
      editor.getBodyArea().setStyle(0, 5, "-fx-font-weight: bold;");

      editor.toggleBodySourceMode();

      assertTrue(editor.isBodySourceMode());
      assertTrue(editor.getBodyArea().getText().contains("@S -fx-font-weight: bold;|Alpha"));

      editor.getBodyArea().replaceText(
          editor.getBodyArea().getText().replace("-fx-font-weight: bold;", "-fx-font-style: italic;")
      );
      editor.toggleBodySourceMode();

      assertEquals("Alpha", editor.getBodyArea().getText());
      assertTrue(editor.getBodyArea().getStyleAtPosition(0).contains("-fx-font-style: italic"));
    });
  }

  /**
   * Speichert aus aktiver Quelltextansicht den daraus rekonstruierten RichText-Inhalt.
   *
   * @throws Exception wenn der JavaFX-Testcode fehlschlaegt
   */
  @Test
  void noteCaptureCommitsActiveSourceModeBeforeEncoding() throws Exception {
    runOnFxThread(() -> {
      NoteEditorPane editor = new NoteEditorPane();
      editor.getTitleEditorArea().setText("Titel");
      editor.getBodyArea().replaceText("Alpha");
      editor.getBodyArea().setStyle(0, 5, "-fx-font-weight: bold;");
      editor.toggleBodySourceMode();
      editor.getBodyArea().replaceText(
          editor.getBodyArea().getText().replace("-fx-font-weight: bold;", "-fx-font-style: italic;")
      );

      Note note = new Note();
      note.captureFrom(editor, new KeywordsTablePane());

      assertFalse(editor.isBodySourceMode());
      assertEquals("Alpha", editor.getBodyArea().getText());
      assertTrue(editor.getBodyArea().getStyleAtPosition(0).contains("-fx-font-style: italic"));
    });
  }

  /**
   * Sichert aktive Zellinhalte vor Tabellenumbauten und bindet den Formatierungsbereich neu.
   *
   * @throws Exception wenn der JavaFX-Testcode fehlschlaegt
   */
  @Test
  void tableActionPersistsAndRebindsActiveCell() throws Exception {
    runOnFxThread(() -> {
      NoteEditorPane editor = new NoteEditorPane();
      Object formattingContent = privateField(editor, "formattingMenuContent");
      invokePrivate(formattingContent, "insertTableReference", 1, 1);
      editor.getBodyArea().resize(420.0, 160.0);
      editor.refreshImageReferences();

      var graphic = editor.getBodyArea().getParagraphGraphic(0);
      InlineCssTextArea cell = (InlineCssTextArea) ((javafx.scene.layout.GridPane) graphic).getChildren().getFirst();
      cell.replaceText("Alpha");
      invokePrivate(editor, "activateTableCell", editor.getBodyArea().getParagraph(0).getText(), 0, 0, cell);

      invokePrivate(editor, "handleTableAction", de.zettelkastenfx.notes.editor.format.NoteFormattingMenuContent.TableAction.INSERT_COLUMN_RIGHT);

      var updated = de.zettelkastenfx.notes.editor.table.EditorTableService
          .resolveReference(editor.getBodyArea().getParagraph(0).getText());
      assertEquals("Alpha", de.zettelkastenfx.notes.editor.table.EditorTableService.plainCellText(updated.cell(0, 0)));

      var updatedGraphic = editor.getBodyArea().getParagraphGraphic(0);
      InlineCssTextArea reboundCell = (InlineCssTextArea) ((javafx.scene.layout.GridPane) updatedGraphic).getChildren().get(1);
      reboundCell.replaceText("Beta");
      reboundCell.selectRange(0, 4);

      HBox bar = (HBox) ((CustomMenuItem) editor.getFormattingContextMenu().getItems().getFirst()).getContent();
      VBox contentRoot = (VBox) bar.getChildren().get(1);
      HBox firstRow = (HBox) contentRoot.getChildren().getFirst();
      ((javafx.scene.control.ToggleButton) firstRow.getChildren().get(1)).fire();

      assertTrue(reboundCell.getStyleAtPosition(0).contains("-fx-font-weight"));
    });
  }

  /**
   * Fuehrt Tabellenbefehle relativ zur aktiven Zelle aus.
   *
   * @throws Exception wenn der JavaFX-Testcode fehlschlaegt
   */
  @Test
  void tableCommandsModifyActiveTableStructure() throws Exception {
    runOnFxThread(() -> {
      NoteEditorPane editor = new NoteEditorPane();
      Object formattingContent = privateField(editor, "formattingMenuContent");
      invokePrivate(formattingContent, "insertTableReference", 2, 2);
      invokePrivate(editor, "activateTableCell", editor.getBodyArea().getParagraph(0).getText(), 0, 1);
      invokePrivate(editor, "persistTableCell", editor.getBodyArea().getParagraph(0).getText(), 0, 1, "Alpha");

      invokePrivate(editor, "handleTableAction", de.zettelkastenfx.notes.editor.format.NoteFormattingMenuContent.TableAction.INSERT_COLUMN_LEFT);
      var afterColumnInsert = de.zettelkastenfx.notes.editor.table.EditorTableService
          .resolveReference(editor.getBodyArea().getParagraph(0).getText());
      assertEquals(3, afterColumnInsert.columns());
      assertEquals("Alpha", afterColumnInsert.cell(0, 2));

      invokePrivate(editor, "handleTableAction", de.zettelkastenfx.notes.editor.format.NoteFormattingMenuContent.TableAction.INSERT_ROW_BELOW);
      var afterRowInsert = de.zettelkastenfx.notes.editor.table.EditorTableService
          .resolveReference(editor.getBodyArea().getParagraph(0).getText());
      assertEquals(3, afterRowInsert.rows());
      assertEquals("Alpha", afterRowInsert.cell(0, 2));

      invokePrivate(editor, "handleTableAction", de.zettelkastenfx.notes.editor.format.NoteFormattingMenuContent.TableAction.DELETE_COLUMN);
      invokePrivate(editor, "handleTableAction", de.zettelkastenfx.notes.editor.format.NoteFormattingMenuContent.TableAction.DELETE_ROW);
      var afterDeletes = de.zettelkastenfx.notes.editor.table.EditorTableService
          .resolveReference(editor.getBodyArea().getParagraph(0).getText());
      assertEquals(2, afterDeletes.rows());
      assertEquals(2, afterDeletes.columns());
      assertEquals("Alpha", afterDeletes.cell(0, 1));
    });
  }

  /**
   * Fuehrt Testcode synchron auf dem JavaFX-Thread aus.
   *
   * @param action auszufuehrender Testcode
   * @throws Exception wenn der Testcode fehlschlaegt oder der JavaFX-Thread nicht antwortet
   */
  private static void runOnFxThread(FxAction action) throws Exception {
    if (Platform.isFxApplicationThread()) {
      action.run();
      return;
    }

    CountDownLatch actionLatch = new CountDownLatch(1);
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Platform.runLater(() -> {
      try {
        action.run();
      } catch (Throwable throwable) {
        failure.set(throwable);
      } finally {
        actionLatch.countDown();
      }
    });

    if (!actionLatch.await(5, TimeUnit.SECONDS)) {
      fail("JavaFX-Testcode wurde nicht rechtzeitig ausgefuehrt.");
    }
    if (failure.get() instanceof Exception exception) {
      throw exception;
    }
    if (failure.get() instanceof Error error) {
      throw error;
    }
    if (failure.get() != null) {
      throw new AssertionError(failure.get());
    }
  }

  /**
   * Liest ein privates Testfeld.
   *
   * @param target Zielobjekt
   * @param fieldName Feldname
   * @return Feldwert
   * @throws Exception wenn Reflection fehlschlaegt
   */
  private static Object privateField(Object target, String fieldName) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    return field.get(target);
  }

  /**
   * Ruft eine private Methode ohne Parameter auf.
   *
   * @param target Zielobjekt
   * @param methodName Methodenname
   * @throws Exception wenn Reflection fehlschlaegt
   */
  private static void invokePrivate(Object target, String methodName) throws Exception {
    var method = target.getClass().getDeclaredMethod(methodName);
    method.setAccessible(true);
    method.invoke(target);
  }

  /**
   * Ruft eine private Methode mit zwei int-Parametern auf.
   *
   * @param target Zielobjekt
   * @param methodName Methodenname
   * @param first erster Wert
   * @param second zweiter Wert
   * @throws Exception wenn Reflection fehlschlaegt
   */
  private static void invokePrivate(Object target, String methodName, int first, int second) throws Exception {
    var method = target.getClass().getDeclaredMethod(methodName, int.class, int.class);
    method.setAccessible(true);
    method.invoke(target, first, second);
  }

  /**
   * Ruft eine private Methode mit Tabellenzellparametern auf.
   *
   * @param target Zielobjekt
   * @param methodName Methodenname
   * @param reference Tabellenmarker
   * @param row Zeilenindex
   * @param column Spaltenindex
   * @param value Zelltext
   * @throws Exception wenn Reflection fehlschlaegt
   */
  private static void invokePrivate(Object target,
                                    String methodName,
                                    String reference,
                                    int row,
                                    int column,
                                    String value) throws Exception {
    var method = target.getClass().getDeclaredMethod(methodName, String.class, int.class, int.class, String.class);
    method.setAccessible(true);
    method.invoke(target, reference, row, column, value);
  }

  /**
   * Ruft eine private Methode mit Tabellenmarker und Zellkoordinaten auf.
   *
   * @param target Zielobjekt
   * @param methodName Methodenname
   * @param reference Tabellenmarker
   * @param row Zeilenindex
   * @param column Spaltenindex
   * @throws Exception wenn Reflection fehlschlaegt
   */
  private static void invokePrivate(Object target,
                                    String methodName,
                                    String reference,
                                    int row,
                                    int column) throws Exception {
    var method = target.getClass().getDeclaredMethod(methodName, String.class, int.class, int.class);
    method.setAccessible(true);
    method.invoke(target, reference, row, column);
  }

  /**
   * Ruft eine private Methode mit Tabellenmarker, Zellkoordinaten und Zelleditor auf.
   *
   * @param target Zielobjekt
   * @param methodName Methodenname
   * @param reference Tabellenmarker
   * @param row Zeilenindex
   * @param column Spaltenindex
   * @param cellArea Zelleditor
   * @throws Exception wenn Reflection fehlschlaegt
   */
  private static void invokePrivate(Object target,
                                    String methodName,
                                    String reference,
                                    int row,
                                    int column,
                                    InlineCssTextArea cellArea) throws Exception {
    var method = target.getClass().getDeclaredMethod(
        methodName,
        String.class,
        int.class,
        int.class,
        InlineCssTextArea.class
    );
    method.setAccessible(true);
    method.invoke(target, reference, row, column, cellArea);
  }

  /**
   * Ruft eine private Methode mit einem InlineCssTextArea-Parameter auf.
   *
   * @param target Zielobjekt
   * @param methodName Methodenname
   * @param cellArea Zelleditor
   * @throws Exception wenn Reflection fehlschlaegt
   */
  private static void invokePrivate(Object target, String methodName, InlineCssTextArea cellArea) throws Exception {
    var method = target.getClass().getDeclaredMethod(methodName, InlineCssTextArea.class);
    method.setAccessible(true);
    method.invoke(target, cellArea);
  }

  /**
   * Ruft eine private Tabellenaktion auf.
   *
   * @param target Zielobjekt
   * @param methodName Methodenname
   * @param action Tabellenaktion
   * @throws Exception wenn Reflection fehlschlaegt
   */
  private static void invokePrivate(Object target,
                                    String methodName,
                                    de.zettelkastenfx.notes.editor.format.NoteFormattingMenuContent.TableAction action) throws Exception {
    var method = target.getClass().getDeclaredMethod(
        methodName,
        de.zettelkastenfx.notes.editor.format.NoteFormattingMenuContent.TableAction.class
    );
    method.setAccessible(true);
    method.invoke(target, action);
  }

  /**
   * Setzt ein privates Testfeld.
   *
   * @param target Zielobjekt
   * @param fieldName Feldname
   * @param value neuer Wert
   * @throws Exception wenn Reflection fehlschlaegt
   */
  private static void setPrivateField(Object target, String fieldName, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }

  /**
   * Beschreibt eine Aktion auf dem JavaFX-Thread.
   */
  @FunctionalInterface
  private interface FxAction {

    /**
     * Fuehrt die Aktion aus.
     *
     * @throws Exception wenn die Aktion fehlschlaegt
     */
    void run() throws Exception;
  }
}
