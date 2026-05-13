package de.zettelkastenfx.notes.editor.media;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueft die Bilddatei-Verwaltung des Zetteleditors.
 */
class EditorImageAssetServiceTest {

  @TempDir
  Path tempDir;

  /**
   * Legt den Bildordner neben der Datenbank im App-Datenordner an.
   *
   * @throws Exception bei Dateisystemfehlern
   */
  @Test
  void createsImageDirectoryNextToDatabase() throws Exception {
    String previousHome = System.getProperty("user.home");
    System.setProperty("user.home", tempDir.toString());
    try {
      Path imageDirectory = EditorImageAssetService.ensureImageDirectory();

      assertTrue(Files.isDirectory(imageDirectory));
      assertEquals(tempDir.resolve(".zettelkastenfx").resolve("image"), imageDirectory);
    } finally {
      System.setProperty("user.home", previousHome);
    }
  }

  /**
   * Kopiert externe Bilder eindeutig in den Bildordner und erzeugt lokale Verweise.
   *
   * @throws Exception bei Dateisystemfehlern
   */
  @Test
  void copiesExternalImageAndCreatesLocalReference() throws Exception {
    String previousHome = System.getProperty("user.home");
    System.setProperty("user.home", tempDir.toString());
    try {
      Path source = tempDir.resolve("quelle.png");
      Files.writeString(source, "png");

      Path copied = EditorImageAssetService.copyIntoImageDirectory(source);

      assertTrue(Files.exists(source));
      assertTrue(Files.exists(copied));
      assertTrue(EditorImageAssetService.isInsideImageDirectory(copied));
      assertEquals("[[image:image/quelle.png]]", EditorImageAssetService.referenceText(copied));
    } finally {
      System.setProperty("user.home", previousHome);
    }
  }

  /**
   * Verschiebt externe Bilder in den Bildordner.
   *
   * @throws Exception bei Dateisystemfehlern
   */
  @Test
  void movesExternalImageIntoImageDirectory() throws Exception {
    String previousHome = System.getProperty("user.home");
    System.setProperty("user.home", tempDir.toString());
    try {
      Path source = tempDir.resolve("quelle.jpg");
      Files.writeString(source, "jpg");

      Path moved = EditorImageAssetService.moveIntoImageDirectory(source);

      assertFalse(Files.exists(source));
      assertTrue(Files.exists(moved));
      assertEquals("[[image:image/quelle.jpg]]", EditorImageAssetService.referenceText(moved));
    } finally {
      System.setProperty("user.home", previousHome);
    }
  }

  /**
   * Loest lokale Bildverweise wieder zum Bildordner auf.
   *
   * @throws Exception bei Dateisystemfehlern
   */
  @Test
  void resolvesLocalImageReferenceToImageDirectory() throws Exception {
    String previousHome = System.getProperty("user.home");
    System.setProperty("user.home", tempDir.toString());
    try {
      Path imageDirectory = EditorImageAssetService.ensureImageDirectory();

      Path resolved = EditorImageAssetService.resolveReferencePath("[[image:image/bild.png]]");

      assertEquals(imageDirectory.resolve("bild.png"), resolved);
      assertTrue(EditorImageAssetService.isImageReferenceParagraph("[[image:image/bild.png]]"));
    } finally {
      System.setProperty("user.home", previousHome);
    }
  }
}
