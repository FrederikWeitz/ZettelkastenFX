package de.zettelkastenfx.notes.editor.web;

import de.zettelkastenfx.fx.config.ExternalStylesheetService;
import javafx.application.Platform;
import javafx.scene.Scene;
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

  private static int countOccurrences(String text, String needle) {
    int count = 0;
    int index = 0;
    while ((index = text.indexOf(needle, index)) >= 0) {
      count++;
      index += needle.length();
    }
    return count;
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
