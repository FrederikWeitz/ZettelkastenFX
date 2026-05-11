package de.zettelkastenfx.notes.controller;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Prueft die JavaFX-Integration der Arbeitsflaechenbreiten im Zettelfenster.
 */
class ZettelWindowViewWorkAreaTest {

  private static final double EPSILON = 0.000_001;
  private static final double LAYOUT_TOLERANCE = 1.0;

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
   * Fixiert beim Einklappen die SplitPane-Breite auf Zettelanzeige, Stichwortanzeige und Teiler.
   *
   * @throws Exception wenn die Ausfuehrung auf dem JavaFX-Thread fehlschlaegt
   */
  @Test
  void collapsingLocksSplitPaneToRememberedNoteAndKeywordWidth() throws Exception {
    runOnFxThread(() -> {
      ZettelWindowView view = new ZettelWindowView();
      view.getNoteEditorPane().resize(420.0, 600.0);
      view.getKeywordsTablePane().resize(280.0, 600.0);

      view.setServiceAreaCollapsed(true);

      SplitPane splitPane = view.getWorkAreaSplitPane();
      assertEquals(2, splitPane.getItems().size());
      assertEquals(708.0, splitPane.getMinWidth(), EPSILON);
      assertEquals(708.0, splitPane.getPrefWidth(), EPSILON);
      assertEquals(708.0, splitPane.getMaxWidth(), EPSILON);
    });
  }

  /**
   * Gibt beim Wiederaufklappen die SplitPane-Breitenfixierung wieder frei.
   *
   * @throws Exception wenn die Ausfuehrung auf dem JavaFX-Thread fehlschlaegt
   */
  @Test
  void reopeningReleasesSplitPaneWidthLockAndRestoresServiceArea() throws Exception {
    runOnFxThread(() -> {
      ZettelWindowView view = new ZettelWindowView();
      view.getNoteEditorPane().resize(420.0, 600.0);
      view.getKeywordsTablePane().resize(280.0, 600.0);

      view.setServiceAreaCollapsed(true);
      view.setServiceAreaCollapsed(false);

      SplitPane splitPane = view.getWorkAreaSplitPane();
      assertEquals(3, splitPane.getItems().size());
      assertEquals(Region.USE_COMPUTED_SIZE, splitPane.getMinWidth(), EPSILON);
      assertEquals(Region.USE_COMPUTED_SIZE, splitPane.getPrefWidth(), EPSILON);
      assertEquals(Double.MAX_VALUE, splitPane.getMaxWidth(), EPSILON);
    });
  }

  /**
   * Laesst die SplitPane ohne gemessene Primaerbreiten unfixiert.
   *
   * @throws Exception wenn die Ausfuehrung auf dem JavaFX-Thread fehlschlaegt
   */
  @Test
  void collapsingWithoutMeasuredPrimaryWidthsDoesNotApplyArtificialLock() throws Exception {
    runOnFxThread(() -> {
      ZettelWindowView view = new ZettelWindowView();

      view.setServiceAreaCollapsed(true);

      SplitPane splitPane = view.getWorkAreaSplitPane();
      assertEquals(2, splitPane.getItems().size());
      assertEquals(Region.USE_COMPUTED_SIZE, splitPane.getMinWidth(), EPSILON);
      assertEquals(Region.USE_COMPUTED_SIZE, splitPane.getPrefWidth(), EPSILON);
      assertEquals(Double.MAX_VALUE, splitPane.getMaxWidth(), EPSILON);
    });
  }

  /**
   * Prueft das erwartete sichtbare Verhalten beim Controller-aehnlichen Einklappen.
   *
   * @throws Exception wenn die Ausfuehrung auf dem JavaFX-Thread fehlschlaegt
   */
  @Test
  void controllerLikeCollapseKeepsVisiblePanelAndKeywordTableWidths() throws Exception {
    WorkAreaTestWindow window = openWindowAndSetDividers();

    try {
      WorkAreaMeasurements before = measureAfterLayout(window);
      runOnFxThread(() -> collapseLikeController(window));
      waitForFxEvents();
      WorkAreaMeasurements after = measureAfterLayout(window);

      assertWorkAreaWidthsUnchanged(before, after);
    } finally {
      closeWindow(window);
    }
  }

  /**
   * Prueft das erwartete sichtbare Verhalten ueber einen kompletten Ein-/Ausklapp-Zyklus.
   *
   * @throws Exception wenn die Ausfuehrung auf dem JavaFX-Thread fehlschlaegt
   */
  @Test
  void controllerLikeCollapseAndReopenKeepsVisiblePanelAndKeywordTableWidths() throws Exception {
    WorkAreaTestWindow window = openWindowAndSetDividers();

    try {
      WorkAreaMeasurements before = measureAfterLayout(window);
      runOnFxThread(() -> {
        collapseLikeController(window);
        reopenLikeController(window);
      });
      waitForFxEvents();
      WorkAreaMeasurements after = measureAfterLayout(window);

      assertWorkAreaWidthsUnchanged(before, after);
    } finally {
      closeWindow(window);
    }
  }

  /**
   * Prueft, dass wiederholtes Toggeln keine Drift in der Stichwortanzeige erzeugt.
   *
   * @throws Exception wenn die Ausfuehrung auf dem JavaFX-Thread fehlschlaegt
   */
  @Test
  void repeatedControllerLikeTogglesDoNotWidenKeywordPanelOrTable() throws Exception {
    WorkAreaTestWindow window = openWindowAndSetDividers();

    try {
      WorkAreaMeasurements before = measureAfterLayout(window);
      for (int cycle = 0; cycle < 3; cycle++) {
        runOnFxThread(() -> {
          collapseLikeController(window);
          reopenLikeController(window);
        });
        waitForFxEvents();
      }
      WorkAreaMeasurements after = measureAfterLayout(window);

      assertWorkAreaWidthsUnchanged(before, after);
    } finally {
      closeWindow(window);
    }
  }

  /**
   * Oeffnet ein echtes Testfenster und setzt reproduzierbare SplitPane-Positionen.
   *
   * @return Testfenster mit View und Stage
   * @throws Exception wenn die Ausfuehrung auf dem JavaFX-Thread fehlschlaegt
   */
  private static WorkAreaTestWindow openWindowAndSetDividers() throws Exception {
    WorkAreaTestWindow window = callOnFxThread(() -> {
      ZettelWindowView view = new ZettelWindowView();
      Stage stage = new Stage();
      stage.setMinWidth(640.0);
      stage.setScene(new Scene(view.getRoot(), 1280.0, 720.0));
      stage.show();
      forceLayout(view);
      view.getWorkAreaSplitPane().setDividerPositions(0.45, 0.70);
      forceLayout(view);
      return new WorkAreaTestWindow(stage, view);
    });
    waitForFxEvents();
    return window;
  }

  /**
   * Misst die Arbeitsflaeche nach abgeschlossenen JavaFX-Layoutlaeufen.
   *
   * @param window Testfenster
   * @return gemessene Breiten
   * @throws Exception wenn die Ausfuehrung auf dem JavaFX-Thread fehlschlaegt
   */
  private static WorkAreaMeasurements measureAfterLayout(WorkAreaTestWindow window) throws Exception {
    waitForFxEvents();
    return callOnFxThread(() -> {
      forceLayout(window.view);
      return measure(window.view);
    });
  }

  /**
   * Simuliert die Reihenfolge des Controller-Toggles beim Einklappen.
   *
   * @param window Testfenster
   */
  private static void collapseLikeController(WorkAreaTestWindow window) {
    ZettelWindowView view = window.view;
    view.rememberCurrentWorkAreaWidths();
    double widthDelta = view.getServiceAreaToggleWidthDelta();
    view.setServiceAreaCollapsed(true);
    window.stage.setWidth(Math.max(window.stage.getMinWidth(), window.stage.getWidth() - widthDelta));
    view.restoreRememberedWorkAreaWidthsAfterLayout();
    forceLayout(view);
  }

  /**
   * Simuliert die Reihenfolge des Controller-Toggles beim Wiederaufklappen.
   *
   * @param window Testfenster
   */
  private static void reopenLikeController(WorkAreaTestWindow window) {
    ZettelWindowView view = window.view;
    view.rememberCurrentWorkAreaWidths();
    double widthDelta = view.getServiceAreaToggleWidthDelta();
    window.stage.setWidth(window.stage.getWidth() + widthDelta);
    view.setServiceAreaCollapsed(false);
    view.restoreRememberedWorkAreaWidthsAfterLayout();
    forceLayout(view);
  }

  /**
   * Erzwingt CSS- und Layoutberechnung fuer die relevanten View-Knoten.
   *
   * @param view Zettelfenster-View
   */
  private static void forceLayout(ZettelWindowView view) {
    view.getRoot().applyCss();
    view.getRoot().layout();
    view.getWorkAreaSplitPane().applyCss();
    view.getWorkAreaSplitPane().layout();
    view.getKeywordsTablePane().applyCss();
    view.getKeywordsTablePane().layout();
  }

  /**
   * Erfasst die tatsaechlich gelayouteten Breiten der Arbeitsflaeche.
   *
   * @param view Zettelfenster-View
   * @return Messwerte der aeusseren Panels und inneren Stichworttabelle
   */
  private static WorkAreaMeasurements measure(ZettelWindowView view) {
    TableView<?> keywordTable = findKeywordTable(view);
    TableColumn<?, ?> keywordColumn = keywordTable.getColumns().get(0);
    TableColumn<?, ?> countColumn = keywordTable.getColumns().get(1);
    return new WorkAreaMeasurements(
        view.getNoteEditorPane().getWidth(),
        view.getKeywordsTablePane().getWidth(),
        keywordTable.getWidth(),
        keywordColumn.getWidth(),
        countColumn.getWidth()
    );
  }

  /**
   * Findet die innere Stichworttabelle des Stichwortpanels.
   *
   * @param view Zettelfenster-View
   * @return TableView der Stichwortliste
   */
  private static TableView<?> findKeywordTable(ZettelWindowView view) {
    Node tableNode = view.getKeywordsTablePane().lookup(".keywords-table");
    assertTrue(tableNode instanceof TableView<?>, "Die innere Stichworttabelle wurde nicht gefunden.");
    return (TableView<?>) tableNode;
  }

  /**
   * Vergleicht die sichtbaren Breiten der relevanten Arbeitsflaechen-Komponenten.
   *
   * @param before Messwerte vor dem Toggle
   * @param after Messwerte nach dem Toggle
   */
  private static void assertWorkAreaWidthsUnchanged(WorkAreaMeasurements before, WorkAreaMeasurements after) {
    assertAll(
        () -> assertWidthUnchanged("Zettelanzeige", before.notePaneWidth, after.notePaneWidth),
        () -> assertWidthUnchanged("Stichwort-Panel", before.keywordPaneWidth, after.keywordPaneWidth),
        () -> assertWidthUnchanged("Stichwort-Tabelle", before.keywordTableWidth, after.keywordTableWidth),
        () -> assertWidthUnchanged("Stichwort-Spalte", before.keywordColumnWidth, after.keywordColumnWidth),
        () -> assertWidthUnchanged("Anzahl-Spalte", before.countColumnWidth, after.countColumnWidth)
    );
  }

  /**
   * Vergleicht zwei Breiten mit Layout-Toleranz.
   *
   * @param label Bezeichnung des Messwerts
   * @param expected erwartete Breite
   * @param actual tatsaechliche Breite
   */
  private static void assertWidthUnchanged(String label, double expected, double actual) {
    assertEquals(expected, actual, LAYOUT_TOLERANCE, label + " hat die Breite veraendert.");
  }

  /**
   * Schliesst das Testfenster.
   *
   * @param window Testfenster
   * @throws Exception wenn die Ausfuehrung auf dem JavaFX-Thread fehlschlaegt
   */
  private static void closeWindow(WorkAreaTestWindow window) throws Exception {
    runOnFxThread(() -> window.stage.close());
  }

  /**
   * Wartet auf mehrere JavaFX-Eventzyklen, damit verschachtelte runLater-Aufrufe verarbeitet sind.
   *
   * @throws Exception wenn der JavaFX-Thread nicht antwortet
   */
  private static void waitForFxEvents() throws Exception {
    for (int i = 0; i < 4; i++) {
      runOnFxThread(() -> {});
    }
  }

  /**
   * Fuehrt Testcode synchron auf dem JavaFX-Thread aus.
   *
   * @param action auszufuehrender Testcode
   * @throws Exception wenn der Testcode fehlschlaegt oder der JavaFX-Thread nicht antwortet
   */
  private static void runOnFxThread(FxAction action) throws Exception {
    callOnFxThread(() -> {
      action.run();
      return null;
    });
  }

  /**
   * Fuehrt Testcode synchron auf dem JavaFX-Thread aus und liefert ein Ergebnis.
   *
   * @param action auszufuehrender Testcode
   * @param <T> Ergebnistyp
   * @return Ergebnis des Testcodes
   * @throws Exception wenn der Testcode fehlschlaegt oder der JavaFX-Thread nicht antwortet
   */
  private static <T> T callOnFxThread(FxSupplier<T> action) throws Exception {
    if (Platform.isFxApplicationThread()) {
      return action.get();
    }

    CountDownLatch actionLatch = new CountDownLatch(1);
    AtomicReference<T> result = new AtomicReference<>();
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Platform.runLater(() -> {
      try {
        result.set(action.get());
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
    return result.get();
  }

  /**
   * Beschreibt ein geoeffnetes Testfenster fuer die Arbeitsflaeche.
   */
  private static final class WorkAreaTestWindow {
    private final Stage stage;
    private final ZettelWindowView view;

    /**
     * Erzeugt den Testfenster-Kontext.
     *
     * @param stage JavaFX-Stage
     * @param view Zettelfenster-View
     */
    private WorkAreaTestWindow(Stage stage, ZettelWindowView view) {
      this.stage = stage;
      this.view = view;
    }
  }

  /**
   * Enthaelt gemessene Breiten der aeusseren Panels und der inneren Stichworttabelle.
   */
  private static final class WorkAreaMeasurements {
    private final double notePaneWidth;
    private final double keywordPaneWidth;
    private final double keywordTableWidth;
    private final double keywordColumnWidth;
    private final double countColumnWidth;

    /**
     * Erzeugt eine Messwertsammlung.
     *
     * @param notePaneWidth Breite der Zettelanzeige
     * @param keywordPaneWidth Breite des Stichwortpanels
     * @param keywordTableWidth Breite der inneren Stichworttabelle
     * @param keywordColumnWidth Breite der Stichwortspalte
     * @param countColumnWidth Breite der Anzahlspalte
     */
    private WorkAreaMeasurements(
        double notePaneWidth,
        double keywordPaneWidth,
        double keywordTableWidth,
        double keywordColumnWidth,
        double countColumnWidth
    ) {
      this.notePaneWidth = notePaneWidth;
      this.keywordPaneWidth = keywordPaneWidth;
      this.keywordTableWidth = keywordTableWidth;
      this.keywordColumnWidth = keywordColumnWidth;
      this.countColumnWidth = countColumnWidth;
    }
  }

  /**
   * Beschreibt eine Aktion, die auf dem JavaFX-Thread ausgefuehrt wird.
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

  /**
   * Beschreibt eine Aktion mit Rueckgabewert, die auf dem JavaFX-Thread ausgefuehrt wird.
   *
   * @param <T> Ergebnistyp
   */
  @FunctionalInterface
  private interface FxSupplier<T> {

    /**
     * Fuehrt die Aktion aus.
     *
     * @return Ergebnis der Aktion
     * @throws Exception wenn die Aktion fehlschlaegt
     */
    T get() throws Exception;
  }
}
