package de.zettelkastenfx.notes.editor.format;

import javafx.application.Platform;
import org.fxmisc.richtext.InlineCssTextArea;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Prueft die Absatzformatierung fuer Zitate im Editor.
 */
class InlineCssStyleUtilCitationTest {

  /**
   * Startet die JavaFX-Plattform fuer RichTextFX-nahe Tests.
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
   * Formatiert einen Absatz als Zitat und behaelt nur die Ausrichtung.
   *
   * @throws Exception wenn der JavaFX-Testcode fehlschlaegt
   */
  @Test
  void citationFormattingKeepsAlignmentAndClearsOtherParagraphMarks() throws Exception {
    runOnFxThread(() -> {
      InlineCssTextArea area = new InlineCssTextArea();
      area.replaceText("1. Alpha");
      area.setParagraphStyle(
          0,
          "-fx-text-alignment: right;-fx-font-weight: bold;-fx-font-size: 20px;-fx-padding: 0 0 0 40;"
      );
      area.moveTo(area.getLength());

      InlineCssStyleUtil.applyCitationForSelection(area);

      String style = area.getParagraph(0).getParagraphStyle();
      assertEquals("Alpha", area.getText());
      assertEquals("right", InlineCssStyleUtil.readCssProperty(style, "-fx-text-alignment"));
      assertEquals("3 2 3 12", InlineCssStyleUtil.readCssProperty(style, "-fx-padding"));
      assertEquals("#eeeeee", InlineCssStyleUtil.readCssProperty(style, "-fx-background-color"));
      assertNull(InlineCssStyleUtil.readCssProperty(style, "-fx-font-weight"));
      assertNull(InlineCssStyleUtil.readCssProperty(style, "-fx-font-size"));
    });
  }

  /**
   * Formatiert einen Absatz als Separator und behaelt nur die Ausrichtung.
   *
   * @throws Exception wenn der JavaFX-Testcode fehlschlaegt
   */
  @Test
  void separatorFormattingKeepsAlignmentAndClearsOtherParagraphMarks() throws Exception {
    runOnFxThread(() -> {
      InlineCssTextArea area = new InlineCssTextArea();
      area.replaceText("\u2022 Linie");
      area.setParagraphStyle(
          0,
          "-fx-text-alignment: center;-fx-font-size: 20px;-fx-padding: 0 0 0 40;-fx-background-color: #eeeeee;"
      );
      area.moveTo(area.getLength());

      InlineCssStyleUtil.applySeparatorForSelection(area);

      String style = area.getParagraph(0).getParagraphStyle();
      assertEquals("Linie", area.getText());
      assertEquals("center", InlineCssStyleUtil.readCssProperty(style, "-fx-text-alignment"));
      assertEquals("3 0 0 0", InlineCssStyleUtil.readCssProperty(style, "-fx-padding"));
      assertEquals("bold", InlineCssStyleUtil.readCssProperty(style, "-fx-font-weight"));
      assertNull(InlineCssStyleUtil.readCssProperty(style, "-fx-font-size"));
      assertNull(InlineCssStyleUtil.readCssProperty(style, "-fx-background-color"));
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
