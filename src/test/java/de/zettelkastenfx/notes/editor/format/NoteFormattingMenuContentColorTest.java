package de.zettelkastenfx.notes.editor.format;

import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Prueft die CSS-Farbserialisierung des Formatierungsmenues.
 */
class NoteFormattingMenuContentColorTest {

  /**
   * Voll transparente JavaFX-Farbe muss als Loeschsignal "transparent" serialisiert werden.
   *
   * @throws Exception bei Reflection-Fehlern
   */
  @Test
  void transparentColorSerializesAsTransparentKeyword() throws Exception {
    Method method = NoteFormattingMenuContent.class.getDeclaredMethod("toCssColor", Color.class);
    method.setAccessible(true);

    assertEquals("transparent", method.invoke(null, Color.TRANSPARENT));
  }
}
