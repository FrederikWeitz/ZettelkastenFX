package de.zettelkastenfx.export.model;

import java.util.List;

/**
 * Beschreibt einen Absatz des formatierten Zettelinhalts.
 *
 * @param runs formatierte Textbereiche des Absatzes
 * @param style Absatzformatierung
 */
public record ExportParagraph(List<ExportTextRun> runs, ExportParagraphStyle style) {

  /**
   * Erzeugt einen Exportabsatz ohne Absatzformatierung.
   *
   * @param runs formatierte Textbereiche
   */
  public ExportParagraph(List<ExportTextRun> runs) {
    this(runs, ExportParagraphStyle.PLAIN);
  }

  /**
   * Erzeugt einen Exportabsatz mit defensiver Run-Liste.
   *
   * @param runs formatierte Textbereiche
   * @param style Absatzformatierung
   */
  public ExportParagraph {
    runs = runs == null ? List.of() : List.copyOf(runs);
    style = style == null ? ExportParagraphStyle.PLAIN : style;
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
