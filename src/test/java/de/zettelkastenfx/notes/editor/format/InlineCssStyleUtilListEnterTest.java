package de.zettelkastenfx.notes.editor.format;

import javafx.application.Platform;
import org.fxmisc.richtext.InlineCssTextArea;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Prueft Enter-Verhalten innerhalb von Textlisten.
 */
class InlineCssStyleUtilListEnterTest {

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
   * Fuehrt Aufzaehlungslisten beim Druecken von Enter fort.
   *
   * @throws Exception wenn der JavaFX-Testcode fehlschlaegt
   */
  @Test
  void enterContinuesBulletList() throws Exception {
    runOnFxThread(() -> {
      InlineCssTextArea area = new InlineCssTextArea();
      area.replaceText("\u2022 Alpha");
      area.moveTo(area.getLength());

      assertTrue(InlineCssStyleUtil.handleListEnter(area));

      assertEquals("\u2022 Alpha\n\u2022 ", area.getText());
    });
  }

  /**
   * Fuegt nummerierte Listenpunkte ein und nummeriert nachfolgende Punkte neu.
   *
   * @throws Exception wenn der JavaFX-Testcode fehlschlaegt
   */
  @Test
  void enterInMiddleOfNumberedListRenumbersFollowingItems() throws Exception {
    runOnFxThread(() -> {
      InlineCssTextArea area = new InlineCssTextArea();
      area.replaceText("1. Alpha\n2. Beta\n3. Gamma");
      area.moveTo("1. Alpha".length());

      assertTrue(InlineCssStyleUtil.handleListEnter(area));

      assertEquals("1. Alpha\n2. \n3. Beta\n4. Gamma", area.getText());
      assertEquals("1. Alpha\n2. ".length(), area.getCaretPosition());
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
