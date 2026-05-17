package de.zettelkastenfx.notes.editor.web;

import de.zettelkastenfx.fx.config.ExternalStylesheetService;
import de.zettelkastenfx.notes.editor.NoteEditorPane;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Prueft editorinterne HTML/CSS-Hilfen fuer die WebView-Anzeige.
 */
class TinyMceEditorPaneTest {

  @TempDir
  Path tempDir;

  /**
   * Startet die JavaFX-Plattform fuer WebView-nahe Tests.
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
   * Body-Regeln muessen auch auf TinyMCEs Body-Klasse und den Fallback-Editor wirken.
   *
   * @throws Exception bei Reflection-Fehlern
   */
  @Test
  void effectiveEditorCssMirrorsBodyRuleToTinyMceAndFallback() throws Exception {
    Method method = TinyMceEditorPane.class.getDeclaredMethod("effectiveEditorCss", String.class);
    method.setAccessible(true);

    String css = (String) method.invoke(null, "body {\n  font-size: 14px;\n}\np { margin: 0; }");

    assertTrue(css.contains("body {"));
    assertTrue(css.contains(".mce-content-body, #fallbackEditor {font-size: 14px;"));
  }

  /**
   * Wenn eine externe CSS-Datei vorhanden ist, verwendet der Editor diese statt der internen Ressource.
   *
   * @throws Exception bei Reflection- oder Dateisystemfehlern
   */
  @Test
  void loadEditorCssPrefersExistingExternalStylesheet() throws Exception {
    Path stylesheet = ExternalStylesheetService.ensureExternalStylesheet(tempDir);
    Files.writeString(stylesheet, "body { font-size: 19px; }\n.external-only { color: #123456; }");
    Method method = TinyMceEditorPane.class.getDeclaredMethod("loadEditorCss");
    method.setAccessible(true);

    String css = (String) method.invoke(null);

    assertTrue(css.contains("font-size: 19px;"), css);
    assertTrue(css.contains(".external-only"), css);
  }

  /**
   * Erkennt Alt+X beim ersten Tastendruck auch dann, wenn der Fokus im HTML-Quelltextfeld liegt.
   *
   * @throws Exception wenn JavaFX oder WebView nicht rechtzeitig reagieren
   */
  @Test
  void sourceAreaAltXShortcutReturnsToRichViewOnFirstKeyPress() throws Exception {
    AtomicReference<TinyMceEditorPane> editorRef = new AtomicReference<>();
    AtomicReference<Stage> stageRef = new AtomicReference<>();
    runOnFxThread(() -> {
      TinyMceEditorPane editor = new TinyMceEditorPane();
      Stage stage = new Stage();
      stage.setScene(new Scene(editor, 640, 420));
      stage.show();
      editorRef.set(editor);
      stageRef.set(stage);
    });
    TinyMceEditorPane editor = editorRef.get();
    assertNotNull(editor);

    try {
      waitUntilFx(() -> Boolean.TRUE.equals(editor.getWebView().getEngine().executeScript(
          "Boolean(window.zkfxSetContent && window.zkfxUseFallback)"
      )));
      runOnFxThread(() -> editor.getWebView().getEngine().executeScript("window.zkfxUseFallback();"));
      runOnFxThread(() -> editor.setHtmlContent("<p>Alpha</p>"));
      waitUntilFx(() -> editor.getHtmlContent().contains("Alpha"));

      runOnFxThread(editor::toggleSourceMode);
      waitUntilFx(editor::isSourceMode);
      Thread.sleep(300);
      runOnFxThread(() -> editor.getWebView().getEngine().executeScript("""
          (() => {
            const source = document.getElementById('sourceArea');
            source.value = '<p>Beta</p>';
            source.focus();
            source.dispatchEvent(new KeyboardEvent('keydown', {
              key: 'x',
              code: 'KeyX',
              altKey: true,
              bubbles: true,
              cancelable: true
            }));
            source.dispatchEvent(new KeyboardEvent('keyup', {
              key: 'x',
              code: 'KeyX',
              altKey: true,
              bubbles: true,
              cancelable: true
            }));
          })();
          """));

      waitUntilFx(() -> !editor.isSourceMode());
      AtomicReference<String> htmlRef = new AtomicReference<>();
      runOnFxThread(() -> htmlRef.set(editor.getHtmlContent()));
      assertTrue(htmlRef.get().contains("<p>Beta</p>"), htmlRef.get());
    } finally {
      runOnFxThread(() -> stageRef.get().close());
    }
  }

  /**
   * Der Rechtsklick darf die vorher gespeicherte mehrabsätzige Auswahl nicht überschreiben.
   *
   * @throws Exception bei Dateifehlern
   */
  @Test
  void contextMenuHandlerDoesNotOverwriteSavedEditorSelection() throws Exception {
    String source = Files.readString(Path.of(
        "src/main/java/de/zettelkastenfx/notes/editor/web/TinyMceEditorPane.java"
    ));

    int contextMenuIndex = source.indexOf("editor.on('contextmenu'");
    int callbackIndex = source.indexOf("window.javaBridge.contextMenuRequested", contextMenuIndex);
    String contextMenuBlock = source.substring(contextMenuIndex, callbackIndex);

    assertFalse(contextMenuBlock.contains("zkfxSaveSelectionBookmark"));
  }

  /**
   * Formatiert eine Auswahl ueber mehrere TinyMCE-Absaetze und prueft das Ergebnis.
   *
   * @throws Exception wenn JavaFX oder TinyMCE nicht rechtzeitig reagieren
   */
  @Test
  void paragraphClassCommandAppliesClassToEverySelectedParagraph() throws Exception {
    AtomicReference<TinyMceEditorPane> editorRef = new AtomicReference<>();
    AtomicReference<Stage> stageRef = new AtomicReference<>();
    runOnFxThread(() -> {
      TinyMceEditorPane editor = new TinyMceEditorPane();
      Stage stage = new Stage();
      stage.setScene(new Scene(editor, 640, 420));
      stage.show();
      editorRef.set(editor);
      stageRef.set(stage);
    });
    TinyMceEditorPane editor = editorRef.get();
    assertNotNull(editor);

    try {
      waitUntilFx(() -> Boolean.TRUE.equals(editor.getWebView().getEngine().executeScript(
          "Boolean(window.zkfxSetContent && window.zkfxUseFallback)"
      )));
      runOnFxThread(() -> editor.getWebView().getEngine().executeScript("window.zkfxUseFallback();"));

      runOnFxThread(() -> editor.setHtmlContent("<p>Alpha</p><p>Beta</p><p>Gamma</p>"));
      waitUntilFx(() -> editor.getHtmlContent().contains("Gamma"));

      runOnFxThread(() -> editor.getWebView().getEngine().executeScript("""
          if (window.zkfxEditor && !window.zkfxFallbackMode) {
            const body = window.zkfxEditor.getBody();
            const paragraphs = body.querySelectorAll('p');
            const range = body.ownerDocument.createRange();
            range.setStart(paragraphs[0].firstChild, 0);
            range.setEnd(paragraphs[2].firstChild, paragraphs[2].firstChild.nodeValue.length);
            window.zkfxEditor.selection.setRng(range);
            window.zkfxSaveSelection();
          } else {
            const fallback = document.getElementById('fallbackEditor');
            const paragraphs = fallback.querySelectorAll('p');
            const range = document.createRange();
            range.setStart(paragraphs[0].firstChild, 0);
            range.setEnd(paragraphs[2].firstChild, paragraphs[2].firstChild.nodeValue.length);
            const selection = window.getSelection();
            selection.removeAllRanges();
            selection.addRange(range);
          }
          """));
      runOnFxThread(() -> editor.executeCommand("align", "justify"));

      AtomicReference<String> htmlRef = new AtomicReference<>();
      runOnFxThread(() -> htmlRef.set(editor.getHtmlContent()));
      String html = htmlRef.get();

      assertTrue(html.contains("<p class=\"j\">Alpha</p>"), html);
      assertTrue(html.contains("<p class=\"j\">Beta</p>"), html);
      assertTrue(html.contains("<p class=\"j\">Gamma</p>"), html);
    } finally {
      runOnFxThread(() -> stageRef.get().close());
    }
  }

  /**
   * Entfernt Hintergrundfarbe im Ergebnis, wenn im Fallback transparent gewaehlt wird.
   *
   * @throws Exception wenn JavaFX oder WebView nicht rechtzeitig reagieren
   */
  @Test
  void transparentBackgroundRemovesBackgroundStyleFromSelection() throws Exception {
    AtomicReference<TinyMceEditorPane> editorRef = new AtomicReference<>();
    AtomicReference<Stage> stageRef = new AtomicReference<>();
    runOnFxThread(() -> {
      TinyMceEditorPane editor = new TinyMceEditorPane();
      Stage stage = new Stage();
      stage.setScene(new Scene(editor, 640, 420));
      stage.show();
      editorRef.set(editor);
      stageRef.set(stage);
    });
    TinyMceEditorPane editor = editorRef.get();
    assertNotNull(editor);

    try {
      waitUntilFx(() -> Boolean.TRUE.equals(editor.getWebView().getEngine().executeScript(
          "Boolean(window.zkfxSetContent && window.zkfxUseFallback)"
      )));
      runOnFxThread(() -> editor.getWebView().getEngine().executeScript("window.zkfxUseFallback();"));
      runOnFxThread(() -> editor.setHtmlContent("<p><span style=\"background-color: #fdd835;\">Alpha</span></p>"));
      waitUntilFx(() -> editor.getHtmlContent().contains("background-color"));

      runOnFxThread(() -> editor.getWebView().getEngine().executeScript("""
          const fallback = document.getElementById('fallbackEditor');
          const text = fallback.querySelector('span').firstChild;
          const range = document.createRange();
          range.setStart(text, 0);
          range.setEnd(text, text.nodeValue.length);
          const selection = window.getSelection();
          selection.removeAllRanges();
          selection.addRange(range);
          window.__zkfxSelectionDebug = String(selection.rangeCount) + ':' + selection.toString();
          """));
      runOnFxThread(() -> editor.executeCommand("backgroundColor", "transparent"));

      AtomicReference<String> htmlRef = new AtomicReference<>();
      runOnFxThread(() -> htmlRef.set(editor.getHtmlContent()));
      String html = htmlRef.get();

      AtomicReference<String> debugRef = new AtomicReference<>();
      runOnFxThread(() -> debugRef.set(String.valueOf(editor.getWebView().getEngine().executeScript("window.__zkfxSelectionDebug || ''"))));

      assertTrue(debugRef.get().contains("Alpha"), debugRef.get());
      assertFalse(html.contains("background-color"), html);
      assertTrue(html.contains("Alpha"), html);
    } finally {
      runOnFxThread(() -> stageRef.get().close());
    }
  }

  /**
   * Setzt Schriftfamilien als style-Attribut auf ein span-Tag.
   *
   * @throws Exception wenn JavaFX oder WebView nicht rechtzeitig reagieren
   */
  @Test
  void fontFamilyCommandWritesSpanStyle() throws Exception {
    AtomicReference<TinyMceEditorPane> editorRef = new AtomicReference<>();
    AtomicReference<Stage> stageRef = new AtomicReference<>();
    runOnFxThread(() -> {
      TinyMceEditorPane editor = new TinyMceEditorPane();
      Stage stage = new Stage();
      stage.setScene(new Scene(editor, 640, 420));
      stage.show();
      editorRef.set(editor);
      stageRef.set(stage);
    });
    TinyMceEditorPane editor = editorRef.get();
    assertNotNull(editor);

    try {
      waitUntilFx(() -> Boolean.TRUE.equals(editor.getWebView().getEngine().executeScript(
          "Boolean(window.zkfxSetContent && window.zkfxUseFallback)"
      )));
      runOnFxThread(() -> editor.getWebView().getEngine().executeScript("window.zkfxUseFallback();"));
      runOnFxThread(() -> editor.setHtmlContent("<p>Alpha Beta</p>"));
      waitUntilFx(() -> editor.getHtmlContent().contains("Beta"));

      selectFallbackTextRange(editor, "p", 0, 0);
      runOnFxThread(() -> editor.executeCommand("fontFamily", "Times New Roman"));

      AtomicReference<String> htmlRef = new AtomicReference<>();
      runOnFxThread(() -> htmlRef.set(editor.getHtmlContent()));
      String html = htmlRef.get();

      assertTrue(html.contains("<span"), html);
      assertTrue(html.contains("font-family"), html);
      assertTrue(html.contains("Times New Roman"), html);
      assertTrue(html.contains("Alpha Beta"), html);
    } finally {
      runOnFxThread(() -> stageRef.get().close());
    }
  }

  /**
   * Fuegt ueber den Tabellenbefehl eine echte HTML-Tabelle mit der gewaehlten Groesse ein.
   *
   * @throws Exception wenn JavaFX oder WebView nicht rechtzeitig reagieren
   */
  @Test
  void insertTableCommandCreatesHtmlTableCells() throws Exception {
    AtomicReference<TinyMceEditorPane> editorRef = new AtomicReference<>();
    AtomicReference<Stage> stageRef = new AtomicReference<>();
    runOnFxThread(() -> {
      TinyMceEditorPane editor = new TinyMceEditorPane();
      Stage stage = new Stage();
      stage.setScene(new Scene(editor, 640, 420));
      stage.show();
      editorRef.set(editor);
      stageRef.set(stage);
    });
    TinyMceEditorPane editor = editorRef.get();
    assertNotNull(editor);

    try {
      waitUntilFx(() -> Boolean.TRUE.equals(editor.getWebView().getEngine().executeScript(
          "Boolean(window.zkfxSetContent && window.zkfxUseFallback)"
      )));
      runOnFxThread(() -> editor.getWebView().getEngine().executeScript("window.zkfxUseFallback();"));
      runOnFxThread(() -> editor.setHtmlContent("<p>Alpha</p>"));
      waitUntilFx(() -> editor.getHtmlContent().contains("Alpha"));

      runOnFxThread(editor::requestEditorFocus);
      runOnFxThread(() -> editor.insertTable(2, 3));

      AtomicReference<String> htmlRef = new AtomicReference<>();
      runOnFxThread(() -> htmlRef.set(editor.getHtmlContent()));
      String html = htmlRef.get();

      assertTrue(html.contains("<table"), html);
      assertTrue(html.contains("<tbody>"), html);
      assertTrue(html.contains("<tr>"), html);
      assertEquals(6, countOccurrences(html, "<td"), html);
      assertTrue(html.contains("<p><br></p>"), html);
      assertFalse(html.contains("<p><table"), html);
    } finally {
      runOnFxThread(() -> stageRef.get().close());
    }
  }

  /**
   * Erkennt den Cursor in einer HTML-Tabellenzelle, normalisiert feste Breiten und zeigt am Zellrand den Resize-Cursor.
   *
   * @throws Exception wenn JavaFX oder WebView nicht rechtzeitig reagieren
   */
  @Test
  void tableContextAndFixedCellLayoutAreReportedFromHtmlTable() throws Exception {
    AtomicReference<TinyMceEditorPane> editorRef = new AtomicReference<>();
    AtomicReference<Stage> stageRef = new AtomicReference<>();
    AtomicReference<Boolean> tableContextRef = new AtomicReference<>(false);
    runOnFxThread(() -> {
      TinyMceEditorPane editor = new TinyMceEditorPane();
      editor.setOnTableContextChanged(tableContextRef::set);
      Stage stage = new Stage();
      stage.setScene(new Scene(editor, 640, 420));
      stage.show();
      editorRef.set(editor);
      stageRef.set(stage);
    });
    TinyMceEditorPane editor = editorRef.get();
    assertNotNull(editor);

    try {
      waitUntilFx(() -> Boolean.TRUE.equals(editor.getWebView().getEngine().executeScript(
          "Boolean(window.zkfxSetContent && window.zkfxUseFallback)"
      )));
      runOnFxThread(() -> editor.getWebView().getEngine().executeScript("window.zkfxUseFallback();"));
      runOnFxThread(() -> editor.setHtmlContent("<table><tbody><tr><td>Alpha</td><td>Beta</td></tr></tbody></table>"));
      waitUntilFx(() -> editor.getHtmlContent().contains("table-layout"));

      runOnFxThread(() -> editor.getWebView().getEngine().executeScript("""
          (() => {
            const fallback = document.getElementById('fallbackEditor');
            const cell = fallback.querySelector('td');
            const text = cell.firstChild;
            const range = document.createRange();
            range.setStart(text, 0);
            range.setEnd(text, text.nodeValue.length);
            const selection = window.getSelection();
            selection.removeAllRanges();
            selection.addRange(range);
            fallback.dispatchEvent(new KeyboardEvent('keyup', { bubbles: true }));
            const rect = cell.getBoundingClientRect();
            cell.dispatchEvent(new MouseEvent('mousemove', {
              clientX: rect.right - 1,
              clientY: rect.top + 3,
              bubbles: true
            }));
          })();
          """));

      waitUntilFx(() -> Boolean.TRUE.equals(tableContextRef.get()));
      AtomicReference<String> cursorRef = new AtomicReference<>();
      runOnFxThread(() -> cursorRef.set(String.valueOf(editor.getWebView().getEngine().executeScript(
          "document.getElementById('fallbackEditor').style.cursor"
      ))));
      assertEquals("col-resize", cursorRef.get());

      runOnFxThread(() -> editor.executeCommand("tableAction", "INSERT_COLUMN_RIGHT"));
      AtomicReference<String> htmlRef = new AtomicReference<>();
      runOnFxThread(() -> htmlRef.set(editor.getHtmlContent()));
      String html = htmlRef.get();

      assertEquals(3, countOccurrences(html, "<td"), html);
      assertTrue(html.contains("table-layout"), html);
      assertTrue(html.contains("width"), html);

      runOnFxThread(() -> editor.getWebView().getEngine().executeScript("""
          (() => {
            const fallback = document.getElementById('fallbackEditor');
            const cell = fallback.querySelector('td');
            const rect = cell.getBoundingClientRect();
            cell.dispatchEvent(new MouseEvent('mousedown', {
              clientX: rect.right - 1,
              clientY: rect.top + 3,
              bubbles: true,
              cancelable: true
            }));
            cell.dispatchEvent(new MouseEvent('mousemove', {
              clientX: rect.right + 60,
              clientY: rect.top + 3,
              bubbles: true,
              cancelable: true
            }));
            cell.dispatchEvent(new MouseEvent('mouseup', {
              clientX: rect.right + 60,
              clientY: rect.top + 3,
              bubbles: true,
              cancelable: true
            }));
          })();
          """));

      runOnFxThread(() -> htmlRef.set(editor.getHtmlContent()));
      html = htmlRef.get();
      assertTrue(html.contains("style=\"width:"), html);
      assertTrue(html.contains("width: 43.") || html.contains("width: 42.") || html.contains("width: 44."), html);
      assertTrue(html.contains("width: 23.") || html.contains("width: 22.") || html.contains("width: 24."), html);
    } finally {
      runOnFxThread(() -> stageRef.get().close());
    }
  }

  /**
   * Wandelt mehrere normale Absaetze im Fallback zu einer echten ungeordneten Liste um.
   *
   * @throws Exception wenn JavaFX oder WebView nicht rechtzeitig reagieren
   */
  @Test
  void bulletCommandConvertsSelectedParagraphsToListItems() throws Exception {
    AtomicReference<TinyMceEditorPane> editorRef = new AtomicReference<>();
    AtomicReference<Stage> stageRef = new AtomicReference<>();
    runOnFxThread(() -> {
      TinyMceEditorPane editor = new TinyMceEditorPane();
      Stage stage = new Stage();
      stage.setScene(new Scene(editor, 640, 420));
      stage.show();
      editorRef.set(editor);
      stageRef.set(stage);
    });
    TinyMceEditorPane editor = editorRef.get();
    assertNotNull(editor);

    try {
      waitUntilFx(() -> Boolean.TRUE.equals(editor.getWebView().getEngine().executeScript(
          "Boolean(window.zkfxSetContent && window.zkfxUseFallback)"
      )));
      runOnFxThread(() -> editor.getWebView().getEngine().executeScript("window.zkfxUseFallback();"));
      runOnFxThread(() -> editor.setHtmlContent("<p>Alpha</p><p>Beta</p><p>Gamma</p>"));
      waitUntilFx(() -> editor.getHtmlContent().contains("Gamma"));

      selectFallbackTextRange(editor, "p", 0, 2);
      runOnFxThread(() -> editor.executeCommand("bullets", null));

      AtomicReference<String> htmlRef = new AtomicReference<>();
      runOnFxThread(() -> htmlRef.set(editor.getHtmlContent()));
      String html = htmlRef.get();

      assertTrue(html.contains("<ul>"), html);
      assertTrue(html.contains("<li>Alpha</li>"), html);
      assertTrue(html.contains("<li>Beta</li>"), html);
      assertTrue(html.contains("<li>Gamma</li>"), html);
      assertFalse(html.contains("<p>Alpha</p>"), html);
    } finally {
      runOnFxThread(() -> stageRef.get().close());
    }
  }

  /**
   * Wandelt eine bestehende geordnete Liste bei erneutem Listenbefehl wieder in normale Absaetze um.
   *
   * @throws Exception wenn JavaFX oder WebView nicht rechtzeitig reagieren
   */
  @Test
  void numbersCommandConvertsSelectedListItemsBackToParagraphs() throws Exception {
    AtomicReference<TinyMceEditorPane> editorRef = new AtomicReference<>();
    AtomicReference<Stage> stageRef = new AtomicReference<>();
    runOnFxThread(() -> {
      TinyMceEditorPane editor = new TinyMceEditorPane();
      Stage stage = new Stage();
      stage.setScene(new Scene(editor, 640, 420));
      stage.show();
      editorRef.set(editor);
      stageRef.set(stage);
    });
    TinyMceEditorPane editor = editorRef.get();
    assertNotNull(editor);

    try {
      waitUntilFx(() -> Boolean.TRUE.equals(editor.getWebView().getEngine().executeScript(
          "Boolean(window.zkfxSetContent && window.zkfxUseFallback)"
      )));
      runOnFxThread(() -> editor.getWebView().getEngine().executeScript("window.zkfxUseFallback();"));
      runOnFxThread(() -> editor.setHtmlContent("<ol><li>Alpha</li><li>Beta</li><li>Gamma</li></ol>"));
      waitUntilFx(() -> editor.getHtmlContent().contains("Gamma"));

      selectFallbackTextRange(editor, "li", 0, 2);
      runOnFxThread(() -> editor.executeCommand("numbers", null));

      AtomicReference<String> htmlRef = new AtomicReference<>();
      runOnFxThread(() -> htmlRef.set(editor.getHtmlContent()));
      String html = htmlRef.get();

      assertFalse(html.contains("<ol>"), html);
      assertFalse(html.contains("<li>"), html);
      assertTrue(html.contains("<p>Alpha</p>"), html);
      assertTrue(html.contains("<p>Beta</p>"), html);
      assertTrue(html.contains("<p>Gamma</p>"), html);
    } finally {
      runOnFxThread(() -> stageRef.get().close());
    }
  }

  /**
   * Loescht Formatierung strukturell, ohne Bilder oder Tabellen aus dem Zettel zu entfernen.
   *
   * @throws Exception wenn JavaFX oder WebView nicht rechtzeitig reagieren
   */
  @Test
  void clearCommandRemovesFormattingButKeepsImagesAndTables() throws Exception {
    AtomicReference<TinyMceEditorPane> editorRef = new AtomicReference<>();
    AtomicReference<Stage> stageRef = new AtomicReference<>();
    runOnFxThread(() -> {
      TinyMceEditorPane editor = new TinyMceEditorPane();
      Stage stage = new Stage();
      stage.setScene(new Scene(editor, 640, 420));
      stage.show();
      editorRef.set(editor);
      stageRef.set(stage);
    });
    TinyMceEditorPane editor = editorRef.get();
    assertNotNull(editor);

    try {
      waitUntilFx(() -> Boolean.TRUE.equals(editor.getWebView().getEngine().executeScript(
          "Boolean(window.zkfxSetContent && window.zkfxUseFallback)"
      )));
      runOnFxThread(() -> editor.getWebView().getEngine().executeScript("window.zkfxUseFallback();"));
      runOnFxThread(() -> editor.setHtmlContent("""
          <h1 class="j" style="color: red;"><span style="background-color: yellow;">Titel</span></h1>
          <p class="q" data-test="x"><span style="color: blue;"><sub>Alpha</sub></span></p>
          <ul><li><span>Beta</span></li></ul>
          <table><tbody><tr><td>Zelle</td></tr></tbody></table>
          <p><img src="image/bild.png"></p>
          """));
      waitUntilFx(() -> editor.getHtmlContent().contains("Beta"));

      runOnFxThread(() -> editor.getWebView().getEngine().executeScript("""
          const fallback = document.getElementById('fallbackEditor');
          const start = fallback.querySelector('h1 span').firstChild;
          const end = fallback.querySelector('li span').firstChild;
          const range = document.createRange();
          range.setStart(start, 0);
          range.setEnd(end, end.nodeValue.length);
          const selection = window.getSelection();
          selection.removeAllRanges();
          selection.addRange(range);
          """));
      runOnFxThread(() -> editor.executeCommand("clear", null));

      AtomicReference<String> htmlRef = new AtomicReference<>();
      runOnFxThread(() -> htmlRef.set(editor.getHtmlContent()));
      String html = htmlRef.get();

      assertFalse(html.contains("<h1"), html);
      assertFalse(html.contains("<ul"), html);
      assertFalse(html.contains("<li"), html);
      assertFalse(html.contains("<span"), html);
      assertFalse(html.contains("<sub"), html);
      assertFalse(html.contains("class="), html);
      assertFalse(html.contains("<p style="), html);
      assertFalse(html.contains("<p class="), html);
      assertFalse(html.contains("data-test"), html);
      assertTrue(html.contains("<p>Titel</p>"), html);
      assertTrue(html.contains("<p>Alpha</p>"), html);
      assertTrue(html.contains("<p>Beta</p>"), html);
      assertTrue(html.contains("<table"), html);
      assertTrue(html.contains("<img src=\"image/bild.png\">"), html);
    } finally {
      runOnFxThread(() -> stageRef.get().close());
    }
  }

  /**
   * Markiert ein angeklicktes Bild temporaer und entfernt es mit der Entf-Taste,
   * ohne die Markierungsklasse im gespeicherten HTML zu persistieren.
   *
   * @throws Exception wenn JavaFX oder WebView nicht rechtzeitig reagieren
   */
  @Test
  void clickedImageIsSelectedAndDeleteRemovesItWithoutPersistingSelectionClass() throws Exception {
    AtomicReference<TinyMceEditorPane> editorRef = new AtomicReference<>();
    AtomicReference<Stage> stageRef = new AtomicReference<>();
    runOnFxThread(() -> {
      TinyMceEditorPane editor = new TinyMceEditorPane();
      Stage stage = new Stage();
      stage.setScene(new Scene(editor, 640, 420));
      stage.show();
      editorRef.set(editor);
      stageRef.set(stage);
    });
    TinyMceEditorPane editor = editorRef.get();
    assertNotNull(editor);

    try {
      waitUntilFx(() -> Boolean.TRUE.equals(editor.getWebView().getEngine().executeScript(
          "Boolean(window.zkfxSetContent && window.zkfxUseFallback)"
      )));
      runOnFxThread(() -> editor.getWebView().getEngine().executeScript("window.zkfxUseFallback();"));
      runOnFxThread(() -> editor.setHtmlContent("<p>Alpha</p><p><img src=\"image/bild.png\"></p><p>Beta</p>"));
      waitUntilFx(() -> editor.getHtmlContent().contains("image/bild.png"));

      runOnFxThread(() -> editor.getWebView().getEngine().executeScript("""
          const image = document.querySelector('#fallbackEditor img');
          image.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
          """));

      AtomicReference<Boolean> selectedRef = new AtomicReference<>();
      AtomicReference<String> serializedRef = new AtomicReference<>();
      runOnFxThread(() -> {
        selectedRef.set(Boolean.TRUE.equals(editor.getWebView().getEngine().executeScript(
            "document.querySelector('#fallbackEditor img').classList.contains('zkfx-selected-image')"
        )));
        serializedRef.set(editor.getHtmlContent());
      });

      assertTrue(selectedRef.get());
      assertFalse(serializedRef.get().contains("zkfx-selected-image"), serializedRef.get());

      runOnFxThread(() -> editor.getWebView().getEngine().executeScript("""
          document.getElementById('fallbackEditor').dispatchEvent(new KeyboardEvent('keydown', {
            key: 'Delete',
            keyCode: 46,
            which: 46,
            bubbles: true,
            cancelable: true
          }));
          """));

      AtomicReference<String> htmlRef = new AtomicReference<>();
      runOnFxThread(() -> htmlRef.set(editor.getHtmlContent()));
      String html = htmlRef.get();

      assertFalse(html.contains("<img"), html);
      assertTrue(html.contains("<p>Alpha</p>"), html);
      assertTrue(html.contains("<p>Beta</p>"), html);
    } finally {
      runOnFxThread(() -> stageRef.get().close());
    }
  }

  /**
   * H1 setzt ein echtes h1-Tag und wandelt es bei erneutem Befehl wieder in p zurueck.
   *
   * @throws Exception wenn JavaFX oder WebView nicht rechtzeitig reagieren
   */
  @Test
  void h1CommandTogglesParagraphTag() throws Exception {
    AtomicReference<TinyMceEditorPane> editorRef = new AtomicReference<>();
    AtomicReference<Stage> stageRef = new AtomicReference<>();
    runOnFxThread(() -> {
      TinyMceEditorPane editor = new TinyMceEditorPane();
      Stage stage = new Stage();
      stage.setScene(new Scene(editor, 640, 420));
      stage.show();
      editorRef.set(editor);
      stageRef.set(stage);
    });
    TinyMceEditorPane editor = editorRef.get();
    assertNotNull(editor);

    try {
      waitUntilFx(() -> Boolean.TRUE.equals(editor.getWebView().getEngine().executeScript(
          "Boolean(window.zkfxSetContent && window.zkfxUseFallback)"
      )));
      runOnFxThread(() -> editor.getWebView().getEngine().executeScript("window.zkfxUseFallback();"));
      runOnFxThread(() -> editor.setHtmlContent("<p>Alpha</p>"));
      waitUntilFx(() -> editor.getHtmlContent().contains("Alpha"));

      selectFallbackTextRange(editor, "p", 0, 0);
      runOnFxThread(() -> editor.executeCommand("h1", null));
      AtomicReference<String> htmlRef = new AtomicReference<>();
      runOnFxThread(() -> htmlRef.set(editor.getHtmlContent()));
      assertTrue(htmlRef.get().contains("<h1>Alpha</h1>"), htmlRef.get());

      selectFallbackTextRange(editor, "h1", 0, 0);
      runOnFxThread(() -> editor.executeCommand("h1", null));
      runOnFxThread(() -> htmlRef.set(editor.getHtmlContent()));
      assertTrue(htmlRef.get().contains("<p>Alpha</p>"), htmlRef.get());
      assertFalse(htmlRef.get().contains("<h1>"), htmlRef.get());
    } finally {
      runOnFxThread(() -> stageRef.get().close());
    }
  }

  /**
   * Subscript und Superscript erzeugen echte sub- bzw. sup-Tags.
   *
   * @throws Exception wenn JavaFX oder WebView nicht rechtzeitig reagieren
   */
  @Test
  void subAndSupCommandsUseDedicatedInlineTags() throws Exception {
    AtomicReference<TinyMceEditorPane> editorRef = new AtomicReference<>();
    AtomicReference<Stage> stageRef = new AtomicReference<>();
    runOnFxThread(() -> {
      TinyMceEditorPane editor = new TinyMceEditorPane();
      Stage stage = new Stage();
      stage.setScene(new Scene(editor, 640, 420));
      stage.show();
      editorRef.set(editor);
      stageRef.set(stage);
    });
    TinyMceEditorPane editor = editorRef.get();
    assertNotNull(editor);

    try {
      waitUntilFx(() -> Boolean.TRUE.equals(editor.getWebView().getEngine().executeScript(
          "Boolean(window.zkfxSetContent && window.zkfxUseFallback)"
      )));
      runOnFxThread(() -> editor.getWebView().getEngine().executeScript("window.zkfxUseFallback();"));
      runOnFxThread(() -> editor.setHtmlContent("<p>Alpha Beta</p>"));
      waitUntilFx(() -> editor.getHtmlContent().contains("Beta"));

      selectFallbackTextRange(editor, "p", 0, 0);
      runOnFxThread(() -> editor.executeCommand("sub", null));
      AtomicReference<String> htmlRef = new AtomicReference<>();
      runOnFxThread(() -> htmlRef.set(editor.getHtmlContent()));
      assertTrue(htmlRef.get().contains("<sub>Alpha Beta</sub>"), htmlRef.get());

      runOnFxThread(() -> editor.setHtmlContent("<p>Gamma Delta</p>"));
      waitUntilFx(() -> editor.getHtmlContent().contains("Delta"));
      selectFallbackTextRange(editor, "p", 0, 0);
      runOnFxThread(() -> editor.executeCommand("sup", null));
      runOnFxThread(() -> htmlRef.set(editor.getHtmlContent()));
      assertTrue(htmlRef.get().contains("<sup>Gamma Delta</sup>"), htmlRef.get());
    } finally {
      runOnFxThread(() -> stageRef.get().close());
    }
  }

  /**
   * Inline-Formatierungen werden als span-Klassen gespeichert, nicht als native HTML-Format-Tags.
   *
   * @throws Exception wenn JavaFX oder WebView nicht rechtzeitig reagieren
   */
  @Test
  void inlineFormattingCommandsWriteConfiguredCssClasses() throws Exception {
    AtomicReference<TinyMceEditorPane> editorRef = new AtomicReference<>();
    AtomicReference<Stage> stageRef = new AtomicReference<>();
    runOnFxThread(() -> {
      TinyMceEditorPane editor = new TinyMceEditorPane();
      Stage stage = new Stage();
      stage.setScene(new Scene(editor, 640, 420));
      stage.show();
      editorRef.set(editor);
      stageRef.set(stage);
    });
    TinyMceEditorPane editor = editorRef.get();
    assertNotNull(editor);

    try {
      waitUntilFx(() -> Boolean.TRUE.equals(editor.getWebView().getEngine().executeScript(
          "Boolean(window.zkfxSetContent && window.zkfxUseFallback)"
      )));
      runOnFxThread(() -> editor.getWebView().getEngine().executeScript("window.zkfxUseFallback();"));

      assertInlineClassCommand(editor, "bold", "b");
      assertInlineClassCommand(editor, "italic", "i");
      assertInlineClassCommand(editor, "underline", "u");
      assertInlineClassCommand(editor, "strike", "x");
    } finally {
      runOnFxThread(() -> stageRef.get().close());
    }
  }

  /**
   * TinyMCE registriert die Inline-Formate als span-Klassen aus der Editor-CSS.
   *
   * @throws Exception bei Dateifehlern
   */
  @Test
  void tinyMceInlineFormatsUseConfiguredCssClasses() throws Exception {
    String source = Files.readString(Path.of(
        "src/main/java/de/zettelkastenfx/notes/editor/web/TinyMceEditorPane.java"
    ));

    assertTrue(source.contains("editor.formatter.register('zkBold'"), source);
    assertTrue(source.contains("classes: 'b'"), source);
    assertTrue(source.contains("editor.formatter.register('zkItalic'"), source);
    assertTrue(source.contains("classes: 'i'"), source);
    assertTrue(source.contains("editor.formatter.register('zkUnderline'"), source);
    assertTrue(source.contains("classes: 'u'"), source);
    assertTrue(source.contains("editor.formatter.register('zkStrike'"), source);
    assertTrue(source.contains("classes: 'x'"), source);
    assertFalse(source.contains("editor.addShortcut("), source);
    assertFalse(source.contains("execCommand('Bold'"), source);
    assertFalse(source.contains("execCommand('Italic'"), source);
    assertFalse(source.contains("execCommand('Underline'"), source);
    assertFalse(source.contains("execCommand('Strikethrough'"), source);
  }

  /**
   * NoteEditorPane leitet Strg+D ueber denselben Formatbefehlspfad wie das Formatierfenster.
   *
   * @throws Exception wenn JavaFX oder WebView nicht rechtzeitig reagieren
   */
  @Test
  void noteEditorPaneCtrlDShortcutUsesSharedFormatCommandPath() throws Exception {
    AtomicReference<NoteEditorPane> editorRef = new AtomicReference<>();
    AtomicReference<Stage> stageRef = new AtomicReference<>();
    runOnFxThread(() -> {
      NoteEditorPane editor = new NoteEditorPane();
      Stage stage = new Stage();
      stage.setScene(new Scene(editor, 640, 420));
      stage.show();
      editorRef.set(editor);
      stageRef.set(stage);
    });
    NoteEditorPane editor = editorRef.get();
    assertNotNull(editor);

    try {
      TinyMceEditorPane webEditor = editor.getWebBodyEditor();
      waitUntilFx(() -> Boolean.TRUE.equals(webEditor.getWebView().getEngine().executeScript(
          "Boolean(window.zkfxSetContent && window.zkfxUseFallback)"
      )));
      runOnFxThread(() -> webEditor.getWebView().getEngine().executeScript("window.zkfxUseFallback();"));
      runOnFxThread(() -> editor.setBodyHtml("<p>Alpha</p>"));
      waitUntilFx(() -> editor.getBodyHtml().contains("Alpha"));

      selectFallbackTextRange(webEditor, "p", 0, 0);
      runOnFxThread(() -> webEditor.getWebView().fireEvent(new KeyEvent(
          KeyEvent.KEY_PRESSED,
          "",
          "",
          KeyCode.D,
          false,
          true,
          false,
          false
      )));

      AtomicReference<String> htmlRef = new AtomicReference<>();
      AtomicReference<String> selectionRef = new AtomicReference<>();
      runOnFxThread(() -> htmlRef.set(editor.getBodyHtml()));
      runOnFxThread(() -> selectionRef.set(String.valueOf(webEditor.getWebView().getEngine().executeScript(
          "window.getSelection().toString()"
      ))));
      String html = htmlRef.get();

      assertTrue(html.contains("<span class=\"x\">Alpha</span>"), html);
      assertEquals("Alpha", selectionRef.get());
    } finally {
      runOnFxThread(() -> stageRef.get().close());
    }
  }

  /**
   * Ein Absatzformat wirkt bei gesetztem Cursor auf den aktuellen Absatz.
   *
   * @throws Exception wenn JavaFX oder WebView nicht rechtzeitig reagieren
   */
  @Test
  void paragraphFormattingAppliesToCurrentParagraphWhenSelectionIsCollapsed() throws Exception {
    AtomicReference<TinyMceEditorPane> editorRef = new AtomicReference<>();
    AtomicReference<Stage> stageRef = new AtomicReference<>();
    runOnFxThread(() -> {
      TinyMceEditorPane editor = new TinyMceEditorPane();
      Stage stage = new Stage();
      stage.setScene(new Scene(editor, 640, 420));
      stage.show();
      editorRef.set(editor);
      stageRef.set(stage);
    });
    TinyMceEditorPane editor = editorRef.get();
    assertNotNull(editor);

    try {
      waitUntilFx(() -> Boolean.TRUE.equals(editor.getWebView().getEngine().executeScript(
          "Boolean(window.zkfxSetContent && window.zkfxUseFallback)"
      )));
      runOnFxThread(() -> editor.getWebView().getEngine().executeScript("window.zkfxUseFallback();"));
      runOnFxThread(() -> editor.setHtmlContent("<p>Alpha</p><p>Beta</p>"));
      waitUntilFx(() -> editor.getHtmlContent().contains("Beta"));

      runOnFxThread(() -> editor.getWebView().getEngine().executeScript("""
          const fallback = document.getElementById('fallbackEditor');
          const text = fallback.querySelectorAll('p')[1].firstChild;
          const range = document.createRange();
          range.setStart(text, 2);
          range.collapse(true);
          const selection = window.getSelection();
          selection.removeAllRanges();
          selection.addRange(range);
          """));
      runOnFxThread(() -> editor.executeCommand("align", "right"));

      AtomicReference<String> htmlRef = new AtomicReference<>();
      AtomicReference<String> cursorRef = new AtomicReference<>();
      runOnFxThread(() -> htmlRef.set(editor.getHtmlContent()));
      runOnFxThread(() -> cursorRef.set(String.valueOf(editor.getWebView().getEngine().executeScript("""
          (() => {
            const selection = window.getSelection();
            if (!selection || selection.rangeCount === 0) return '';
            const range = selection.getRangeAt(0);
            return range.collapsed + ':' + range.startContainer.nodeValue + ':' + range.startOffset;
          })();
          """))));
      String html = htmlRef.get();

      assertTrue(html.contains("<p>Alpha</p>"), html);
      assertTrue(html.contains("<p class=\"r\">Beta</p>"), html);
      assertEquals("true:Beta:2", cursorRef.get());
    } finally {
      runOnFxThread(() -> stageRef.get().close());
    }
  }

  /**
   * Strg+M zentriert den aktuellen Absatz, ohne an der Cursorposition Text einzufuegen.
   *
   * @throws Exception wenn JavaFX oder WebView nicht rechtzeitig reagieren
   */
  @Test
  void ctrlMCenterShortcutDoesNotInsertBlankAtCursor() throws Exception {
    AtomicReference<NoteEditorPane> editorRef = new AtomicReference<>();
    AtomicReference<Stage> stageRef = new AtomicReference<>();
    runOnFxThread(() -> {
      NoteEditorPane editor = new NoteEditorPane();
      Stage stage = new Stage();
      stage.setScene(new Scene(editor, 640, 420));
      stage.show();
      editorRef.set(editor);
      stageRef.set(stage);
    });
    NoteEditorPane editor = editorRef.get();
    assertNotNull(editor);

    try {
      TinyMceEditorPane webEditor = editor.getWebBodyEditor();
      waitUntilFx(() -> Boolean.TRUE.equals(webEditor.getWebView().getEngine().executeScript(
          "Boolean(window.zkfxSetContent && window.zkfxUseFallback)"
      )));
      runOnFxThread(() -> webEditor.getWebView().getEngine().executeScript("window.zkfxUseFallback();"));
      runOnFxThread(() -> editor.setBodyHtml("<p>Alpha</p>"));
      waitUntilFx(() -> editor.getBodyHtml().contains("Alpha"));

      runOnFxThread(() -> webEditor.getWebView().getEngine().executeScript("""
          const text = document.querySelector('#fallbackEditor p').firstChild;
          const range = document.createRange();
          range.setStart(text, 2);
          range.collapse(true);
          const selection = window.getSelection();
          selection.removeAllRanges();
          selection.addRange(range);
          """));
      runOnFxThread(() -> webEditor.getWebView().fireEvent(new KeyEvent(
          KeyEvent.KEY_PRESSED,
          "",
          "",
          KeyCode.M,
          false,
          true,
          false,
          false
      )));
      runOnFxThread(() -> {
        KeyEvent typedEvent = new KeyEvent(
            KeyEvent.KEY_TYPED,
            "\r",
            "",
            KeyCode.UNDEFINED,
            false,
            true,
            false,
            false
        );
        webEditor.getWebView().fireEvent(typedEvent);
      });
      AtomicReference<Boolean> domShortcutPreventedRef = new AtomicReference<>();
      runOnFxThread(() -> domShortcutPreventedRef.set(Boolean.TRUE.equals(
          webEditor.getWebView().getEngine().executeScript("""
              (() => {
                const fallback = document.getElementById('fallbackEditor');
                const down = new KeyboardEvent('keydown', {
                  key: '', code: 'KeyM', ctrlKey: true, bubbles: true, cancelable: true
                });
                const press = new KeyboardEvent('keypress', {
                  key: 'Enter', keyCode: 13, which: 13, charCode: 13,
                  ctrlKey: true, bubbles: true, cancelable: true
                });
                const input = typeof InputEvent === 'function'
                    ? new InputEvent('beforeinput', {
                        inputType: 'insertParagraph', bubbles: true, cancelable: true
                      })
                    : new Event('beforeinput', { bubbles: true, cancelable: true });
                fallback.dispatchEvent(down);
                fallback.dispatchEvent(press);
                fallback.dispatchEvent(input);
                return down.defaultPrevented && press.defaultPrevented && input.defaultPrevented;
              })();
              """))));

      AtomicReference<String> htmlRef = new AtomicReference<>();
      AtomicReference<String> cursorRef = new AtomicReference<>();
      runOnFxThread(() -> htmlRef.set(editor.getBodyHtml()));
      runOnFxThread(() -> cursorRef.set(fallbackCursorSummary(webEditor)));

      assertTrue(htmlRef.get().contains("<p class=\"m\">Alpha</p>"), htmlRef.get());
      assertFalse(htmlRef.get().contains("Al pha"), htmlRef.get());
      assertFalse(htmlRef.get().contains("<p class=\"m\">Al</p><p>pha</p>"), htmlRef.get());
      assertTrue(domShortcutPreventedRef.get(), "Strg+M muss auch im DOM als nativer Shortcut verhindert werden");
      assertEquals("true:Alpha:2", cursorRef.get());
    } finally {
      runOnFxThread(() -> stageRef.get().close());
    }
  }

  /**
   * H1 und H2 behalten bei kollabierter Auswahl die Cursorposition im Text.
   *
   * @throws Exception wenn JavaFX oder WebView nicht rechtzeitig reagieren
   */
  @Test
  void headingCommandsKeepCollapsedCursorOffset() throws Exception {
    AtomicReference<TinyMceEditorPane> editorRef = new AtomicReference<>();
    AtomicReference<Stage> stageRef = new AtomicReference<>();
    runOnFxThread(() -> {
      TinyMceEditorPane editor = new TinyMceEditorPane();
      Stage stage = new Stage();
      stage.setScene(new Scene(editor, 640, 420));
      stage.show();
      editorRef.set(editor);
      stageRef.set(stage);
    });
    TinyMceEditorPane editor = editorRef.get();
    assertNotNull(editor);

    try {
      waitUntilFx(() -> Boolean.TRUE.equals(editor.getWebView().getEngine().executeScript(
          "Boolean(window.zkfxSetContent && window.zkfxUseFallback)"
      )));
      runOnFxThread(() -> editor.getWebView().getEngine().executeScript("window.zkfxUseFallback();"));

      assertHeadingKeepsCursor(editor, "h1", "<h1>Alpha</h1>");
      assertHeadingKeepsCursor(editor, "h2", "<h2>Alpha</h2>");
    } finally {
      runOnFxThread(() -> stageRef.get().close());
    }
  }

  /**
   * Eine Teilauswahl ueber mehrere Absaetze formatiert jeden beruehrten Absatz.
   *
   * @throws Exception wenn JavaFX oder WebView nicht rechtzeitig reagieren
   */
  @Test
  void paragraphFormattingAppliesToEveryPartlySelectedParagraph() throws Exception {
    AtomicReference<TinyMceEditorPane> editorRef = new AtomicReference<>();
    AtomicReference<Stage> stageRef = new AtomicReference<>();
    runOnFxThread(() -> {
      TinyMceEditorPane editor = new TinyMceEditorPane();
      Stage stage = new Stage();
      stage.setScene(new Scene(editor, 640, 420));
      stage.show();
      editorRef.set(editor);
      stageRef.set(stage);
    });
    TinyMceEditorPane editor = editorRef.get();
    assertNotNull(editor);

    try {
      waitUntilFx(() -> Boolean.TRUE.equals(editor.getWebView().getEngine().executeScript(
          "Boolean(window.zkfxSetContent && window.zkfxUseFallback)"
      )));
      runOnFxThread(() -> editor.getWebView().getEngine().executeScript("window.zkfxUseFallback();"));
      runOnFxThread(() -> editor.setHtmlContent("<p>Alpha</p><p>Beta</p><p>Gamma</p>"));
      waitUntilFx(() -> editor.getHtmlContent().contains("Gamma"));

      runOnFxThread(() -> editor.getWebView().getEngine().executeScript("""
          const fallback = document.getElementById('fallbackEditor');
          const paragraphs = fallback.querySelectorAll('p');
          const range = document.createRange();
          range.setStart(paragraphs[0].firstChild, 2);
          range.setEnd(paragraphs[1].firstChild, 1);
          const selection = window.getSelection();
          selection.removeAllRanges();
          selection.addRange(range);
          """));
      runOnFxThread(() -> editor.executeCommand("citation", null));

      AtomicReference<String> htmlRef = new AtomicReference<>();
      runOnFxThread(() -> htmlRef.set(editor.getHtmlContent()));
      String html = htmlRef.get();

      assertTrue(html.contains("<p class=\"q\">Alpha</p>"), html);
      assertTrue(html.contains("<p class=\"q\">Beta</p>"), html);
      assertTrue(html.contains("<p>Gamma</p>"), html);
    } finally {
      runOnFxThread(() -> stageRef.get().close());
    }
  }

  /**
   * Alt+S uebergibt nur die erste Zeile der aktuellen Textauswahl an Java.
   *
   * @throws Exception wenn JavaFX oder WebView nicht rechtzeitig reagieren
   */
  @Test
  void altSTransfersSelectedTextUntilFirstLineBreak() throws Exception {
    AtomicReference<NoteEditorPane> editorRef = new AtomicReference<>();
    AtomicReference<Stage> stageRef = new AtomicReference<>();
    AtomicReference<String> transferredTextRef = new AtomicReference<>();
    runOnFxThread(() -> {
      NoteEditorPane editor = new NoteEditorPane();
      editor.getWebBodyEditor().setOnKeyFilterTextRequested(transferredTextRef::set);
      Stage stage = new Stage();
      stage.setScene(new Scene(editor, 640, 420));
      stage.show();
      editorRef.set(editor);
      stageRef.set(stage);
    });
    NoteEditorPane editor = editorRef.get();
    assertNotNull(editor);

    try {
      TinyMceEditorPane webEditor = editor.getWebBodyEditor();
      waitUntilFx(() -> Boolean.TRUE.equals(webEditor.getWebView().getEngine().executeScript(
          "Boolean(window.zkfxSetContent && window.zkfxUseFallback)"
      )));
      runOnFxThread(() -> webEditor.getWebView().getEngine().executeScript("window.zkfxUseFallback();"));
      runOnFxThread(() -> editor.setBodyHtml("<p>Alpha</p><p>Beta</p>"));
      waitUntilFx(() -> editor.getBodyHtml().contains("Beta"));

      selectFallbackTextRange(webEditor, "p", 0, 1);
      runOnFxThread(() -> webEditor.getWebView().fireEvent(new KeyEvent(
          KeyEvent.KEY_PRESSED,
          "",
          "",
          KeyCode.S,
          false,
          false,
          true,
          false
      )));

      waitUntilFx(() -> "Alpha".equals(transferredTextRef.get()));
    } finally {
      runOnFxThread(() -> stageRef.get().close());
    }
  }

  /**
   * Per Strg+V eingefuegter Klartext wird absatzweise von mehrfachen Leerzeichen und Randzeichen bereinigt.
   *
   * @throws Exception wenn JavaFX oder WebView nicht rechtzeitig reagieren
   */
  @Test
  void fallbackPasteCleansPlainTextParagraphs() throws Exception {
    AtomicReference<TinyMceEditorPane> editorRef = new AtomicReference<>();
    AtomicReference<Stage> stageRef = new AtomicReference<>();
    runOnFxThread(() -> {
      TinyMceEditorPane editor = new TinyMceEditorPane();
      Stage stage = new Stage();
      stage.setScene(new Scene(editor, 640, 420));
      stage.show();
      editorRef.set(editor);
      stageRef.set(stage);
    });
    TinyMceEditorPane editor = editorRef.get();
    assertNotNull(editor);

    try {
      waitUntilFx(() -> Boolean.TRUE.equals(editor.getWebView().getEngine().executeScript(
          "Boolean(window.zkfxSetContent && window.zkfxUseFallback)"
      )));
      runOnFxThread(() -> editor.getWebView().getEngine().executeScript("window.zkfxUseFallback();"));
      runOnFxThread(() -> editor.setHtmlContent("<p><br></p>"));
      waitUntilFx(() -> editor.getHtmlContent().contains("<p><br></p>"));

      runOnFxThread(() -> editor.getWebView().getEngine().executeScript("""
          (() => {
            const fallback = document.getElementById('fallbackEditor');
            fallback.focus();
            const range = document.createRange();
            range.selectNodeContents(fallback);
            const selection = window.getSelection();
            selection.removeAllRanges();
            selection.addRange(range);
            const event = new Event('paste', { bubbles: true, cancelable: true });
            Object.defineProperty(event, 'clipboardData', {
              value: {
                getData: function(type) {
                  if (type === 'text/html') return '';
                  if (type === 'text/plain') return '  Alpha   Beta  \\n\\t Gamma    Delta ' + String.fromCharCode(8203);
                  return '';
                }
              }
            });
            fallback.dispatchEvent(event);
          })();
          """));

      AtomicReference<String> htmlRef = new AtomicReference<>();
      runOnFxThread(() -> htmlRef.set(editor.getHtmlContent()));

      assertTrue(htmlRef.get().contains("<p>Alpha Beta</p>"), htmlRef.get());
      assertTrue(htmlRef.get().contains("<p>Gamma Delta</p>"), htmlRef.get());
      assertFalse(htmlRef.get().contains("Alpha   Beta"), htmlRef.get());
      assertFalse(htmlRef.get().contains("Gamma    Delta"), htmlRef.get());
    } finally {
      runOnFxThread(() -> stageRef.get().close());
    }
  }

  /**
   * HTML-Paste wird innerhalb vorhandener Absaetze bereinigt, ohne die Absatzstruktur zu verlieren.
   *
   * @throws Exception wenn JavaFX oder WebView nicht rechtzeitig reagieren
   */
  @Test
  void pasteCleanupKeepsHtmlParagraphsAndTrimsEdges() throws Exception {
    AtomicReference<TinyMceEditorPane> editorRef = new AtomicReference<>();
    AtomicReference<Stage> stageRef = new AtomicReference<>();
    runOnFxThread(() -> {
      TinyMceEditorPane editor = new TinyMceEditorPane();
      Stage stage = new Stage();
      stage.setScene(new Scene(editor, 640, 420));
      stage.show();
      editorRef.set(editor);
      stageRef.set(stage);
    });
    TinyMceEditorPane editor = editorRef.get();
    assertNotNull(editor);

    try {
      waitUntilFx(() -> Boolean.TRUE.equals(editor.getWebView().getEngine().executeScript(
          "Boolean(window.zkfxSetContent && window.zkfxUseFallback)"
      )));
      runOnFxThread(() -> editor.getWebView().getEngine().executeScript("window.zkfxUseFallback();"));

      AtomicReference<String> cleanedRef = new AtomicReference<>();
      runOnFxThread(() -> cleanedRef.set(String.valueOf(editor.getWebView().getEngine().executeScript("""
          window.zkfxCleanPastedHtml(
            '<p>&nbsp; Alpha   <span>Beta&nbsp;</span></p>'
            + '<p>' + String.fromCharCode(8203) + ' Gamma    Delta ' + String.fromCharCode(8203) + '</p>'
          )
          """))));

      assertEquals("<p>Alpha <span>Beta</span></p><p>Gamma Delta</p>", cleanedRef.get());
    } finally {
      runOnFxThread(() -> stageRef.get().close());
    }
  }

  private static void waitUntilFx(FxBooleanSupplier condition) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
    while (System.nanoTime() < deadline) {
      AtomicReference<Boolean> result = new AtomicReference<>(false);
      runOnFxThread(() -> result.set(condition.getAsBoolean()));
      if (Boolean.TRUE.equals(result.get())) {
        return;
      }
      Thread.sleep(100);
    }
    fail("Bedingung wurde nicht rechtzeitig erfuellt.");
  }

  private static void runOnFxThread(FxRunnable runnable) throws Exception {
    if (Platform.isFxApplicationThread()) {
      runnable.run();
      return;
    }
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Platform.runLater(() -> {
      try {
        runnable.run();
      } catch (Throwable throwable) {
        failure.set(throwable);
      } finally {
        latch.countDown();
      }
    });
    if (!latch.await(10, TimeUnit.SECONDS)) {
      fail("JavaFX-Aufgabe wurde nicht rechtzeitig beendet.");
    }
    if (failure.get() != null) {
      throw new AssertionError(failure.get());
    }
  }

  private static void selectFallbackTextRange(TinyMceEditorPane editor, String selector, int start, int end) throws Exception {
    runOnFxThread(() -> editor.getWebView().getEngine().executeScript("""
        (() => {
        const fallback = document.getElementById('fallbackEditor');
        const blocks = fallback.querySelectorAll('__SELECTOR__');
        const range = document.createRange();
        range.setStart(blocks[__START__].firstChild, 0);
        range.setEnd(blocks[__END__].firstChild, blocks[__END__].firstChild.nodeValue.length);
        const selection = window.getSelection();
        selection.removeAllRanges();
        selection.addRange(range);
        })();
        """
        .replace("__SELECTOR__", selector)
        .replace("__START__", Integer.toString(start))
        .replace("__END__", Integer.toString(end))));
  }

  private static String fallbackCursorSummary(TinyMceEditorPane editor) {
    return String.valueOf(editor.getWebView().getEngine().executeScript("""
        (() => {
          const selection = window.getSelection();
          if (!selection || selection.rangeCount === 0) return '';
          const range = selection.getRangeAt(0);
          return range.collapsed + ':' + range.startContainer.nodeValue + ':' + range.startOffset;
        })();
        """));
  }

  private static void assertHeadingKeepsCursor(TinyMceEditorPane editor, String command, String expectedHtml) throws Exception {
    runOnFxThread(() -> editor.setHtmlContent("<p>Alpha</p>"));
    waitUntilFx(() -> editor.getHtmlContent().contains("Alpha"));
    runOnFxThread(() -> editor.getWebView().getEngine().executeScript("""
        (() => {
        const text = document.querySelector('#fallbackEditor p').firstChild;
        const range = document.createRange();
        range.setStart(text, 2);
        range.collapse(true);
        const selection = window.getSelection();
        selection.removeAllRanges();
        selection.addRange(range);
        })();
        """));
    runOnFxThread(() -> editor.executeCommand(command, null));

    AtomicReference<String> htmlRef = new AtomicReference<>();
    AtomicReference<String> cursorRef = new AtomicReference<>();
    runOnFxThread(() -> htmlRef.set(editor.getHtmlContent()));
    runOnFxThread(() -> cursorRef.set(fallbackCursorSummary(editor)));

    assertTrue(htmlRef.get().contains(expectedHtml), htmlRef.get());
    assertEquals("true:Alpha:2", cursorRef.get());
  }

  private static int countOccurrences(String text, String needle) {
    int count = 0;
    int index = 0;
    while ((index = text.indexOf(needle, index)) >= 0) {
      count++;
      index += needle.length();
    }
    return count;
  }

  private static void assertInlineClassCommand(TinyMceEditorPane editor, String command, String cssClass) throws Exception {
    runOnFxThread(() -> editor.setHtmlContent("<p>Alpha</p>"));
    waitUntilFx(() -> editor.getHtmlContent().contains("Alpha"));
    selectFallbackTextRange(editor, "p", 0, 0);
    runOnFxThread(() -> editor.executeCommand(command, null));

    AtomicReference<String> htmlRef = new AtomicReference<>();
    runOnFxThread(() -> htmlRef.set(editor.getHtmlContent()));
    String html = htmlRef.get();

    assertTrue(html.contains("<span class=\"" + cssClass + "\">Alpha</span>"), html);
    assertFalse(html.contains("<b>"), html);
    assertFalse(html.contains("<strong>"), html);
    assertFalse(html.contains("<i>"), html);
    assertFalse(html.contains("<em>"), html);
    assertFalse(html.contains("<u>"), html);
    assertFalse(html.contains("<strike>"), html);
    assertFalse(html.contains("<s>"), html);
  }

  @FunctionalInterface
  private interface FxRunnable {
    void run() throws Exception;
  }

  @FunctionalInterface
  private interface FxBooleanSupplier {
    boolean getAsBoolean() throws Exception;
  }
}
