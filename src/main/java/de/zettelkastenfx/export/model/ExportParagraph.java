package de.zettelkastenfx.export.model;

import java.nio.file.Path;
import java.util.List;

/**
 * Beschreibt einen Absatz des formatierten Zettelinhalts.
 *
 * @param runs formatierte Textbereiche des Absatzes
 * @param style Absatzformatierung
 * @param imagePath Bilddatei oder {@code null}
 * @param table Tabelle oder {@code null}
 */
public record ExportParagraph(List<ExportTextRun> runs, ExportParagraphStyle style, Path imagePath, ExportTable table) {

  /**
   * Erzeugt einen Exportabsatz ohne Absatzformatierung.
   *
   * @param runs formatierte Textbereiche
   */
  public ExportParagraph(List<ExportTextRun> runs) {
    this(runs, ExportParagraphStyle.PLAIN, null, null);
  }

  /**
   * Erzeugt einen Exportabsatz ohne Bild.
   *
   * @param runs formatierte Textbereiche
   * @param style Absatzformatierung
   */
  public ExportParagraph(List<ExportTextRun> runs, ExportParagraphStyle style) {
    this(runs, style, null, null);
  }

  /**
   * Erzeugt einen Exportabsatz mit defensiver Run-Liste.
   *
   * @param runs formatierte Textbereiche
   * @param style Absatzformatierung
   * @param imagePath Bilddatei oder {@code null}
   * @param table Tabelle oder {@code null}
   */
  public ExportParagraph {
    runs = runs == null ? List.of() : List.copyOf(runs);
    style = style == null ? ExportParagraphStyle.PLAIN : style;
    imagePath = imagePath == null ? null : imagePath.toAbsolutePath().normalize();
  }

  /**
   * Erzeugt einen einfachen Absatz ohne Inline-Formatierung.
   *
   * @param text Absatztext
   * @return Absatz mit einem unformatierten Run
   */
  public static ExportParagraph plain(String text) {
    return new ExportParagraph(List.of(new ExportTextRun(text, ExportTextStyle.PLAIN)));
  }

  /**
   * Erzeugt einen Bildabsatz.
   *
   * @param imagePath Bilddatei
   * @return Bildabsatz
   */
  public static ExportParagraph image(Path imagePath) {
    return new ExportParagraph(List.of(), ExportParagraphStyle.PLAIN, imagePath, null);
  }

  /**
   * Erzeugt einen Tabellenabsatz.
   *
   * @param rows Zeilenanzahl
   * @param columns Spaltenanzahl
   * @return Tabellenabsatz
   */
  public static ExportParagraph table(int rows, int columns) {
    return new ExportParagraph(List.of(), ExportParagraphStyle.PLAIN, null, new ExportTable(rows, columns));
  }

  /**
   * Erzeugt einen Tabellenabsatz.
   *
   * @param table Tabelle
   * @return Tabellenabsatz
   */
  public static ExportParagraph table(ExportTable table) {
    return new ExportParagraph(List.of(), ExportParagraphStyle.PLAIN, null, table);
  }

  /**
   * Prueft, ob dieser Absatz ein Bildabsatz ist.
   *
   * @return {@code true}, wenn ein Bildpfad vorhanden ist
   */
  public boolean isImage() {
    return imagePath != null;
  }

  /**
   * Prueft, ob dieser Absatz ein Tabellenabsatz ist.
   *
   * @return {@code true}, wenn eine Tabelle vorhanden ist
   */
  public boolean isTable() {
    return table != null;
  }

  /**
   * Liefert den reinen Absatztext.
   *
   * @return zusammengesetzter Klartext
   */
  public String plainText() {
    StringBuilder text = new StringBuilder();
    for (ExportTextRun run : runs) {
      text.append(run.text());
    }
    return text.toString();
  }
}
