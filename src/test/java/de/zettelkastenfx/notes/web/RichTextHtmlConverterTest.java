package de.zettelkastenfx.notes.web;

import de.zettelkastenfx.notes.editor.table.EditorTableService;
import org.fxmisc.richtext.model.ReadOnlyStyledDocument;
import org.fxmisc.richtext.model.SegmentOps;
import org.fxmisc.richtext.model.StyledDocument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueft die Umcodierung von RichTextFX-Zetteln in HTML-Body-Fragmente.
 */
class RichTextHtmlConverterTest {

  /**
   * Wandelt Absatz- und Inline-Stile in Klassen und escapt Textinhalt.
   */
  @Test
  void convertsParagraphAndInlineStylesToClassBasedHtml() {
    StyledDocument<String, String, String> document =
        ReadOnlyStyledDocument.fromString(
                                  "Kontext",
                                  "-fx-text-alignment: justify;-fx-padding: 3 0 0 0;",
                                  "-fx-font-style: italic;-fx-fill: #112233;",
                                  SegmentOps.styledTextOps()
                              )
                              .concat(ReadOnlyStyledDocument.fromString(
                                  " < & >",
                                  "-fx-text-alignment: justify;-fx-padding: 3 0 0 0;",
                                  "-fx-font-weight: bold;-fx-underline: true;-rtfx-background-color: #eeeeee;",
                                  SegmentOps.styledTextOps()
                              ));

    String html = new RichTextHtmlConverter().toHtmlBody(document);
    System.out.println(html);

    assertEquals(
        "<p class=\"j\"><span class=\"i\" style=\"color: #112233;\">Kontext</span>"
            + "<span class=\"b u\" style=\"background-color: #eeeeee;\"> &lt; &amp; &gt;</span></p>",
        html
    );
  }

  /**
   * Erkennt Bullet- und geordnete Listen nach den Regeln der Alt-Codierung.
   */
  @Test
  void convertsLegacyListPrefixesToHtmlLists() {
    StyledDocument<String, String, String> document = ReadOnlyStyledDocument.fromString(
        "• Alpha\n• Beta\nNormal\n1. Eins\n2. Zwei\nNachlauf\n9. Einzel",
        "-fx-text-alignment: justify;",
        "",
        SegmentOps.styledTextOps()
    );

    String html = new RichTextHtmlConverter().toHtmlBody(document);
    System.out.println(html);

    assertTrue(html.contains("<ul><li>Alpha</li><li>Beta</li></ul>"));
    assertTrue(html.contains("<ol><li>Eins</li><li>Zwei</li></ol>"));
    assertTrue(html.contains("<p class=\"j\">9. Einzel</p>"));
  }

  /**
   * Wandelt Bild- und Tabellenmarker in echte HTML-Elemente.
   */
  @Test
  void convertsImageAndTableReferencesToHtmlElements() {
    var table = EditorTableService.resolveReference(EditorTableService.referenceText(2, 2))
        .withCell(0, 1, "A < B");
    StyledDocument<String, String, String> document = ReadOnlyStyledDocument.fromString(
        "[[image:image/bild.png]]\n" + EditorTableService.referenceText(table),
        "",
        "",
        SegmentOps.styledTextOps()
    );

    String html = new RichTextHtmlConverter().toHtmlBody(document);
    System.out.println(html);

    assertTrue(html.contains("<img src=\"image/bild.png\">"));
    assertTrue(html.contains("<table><tbody><tr><td></td><td>A &lt; B</td></tr>"));
    assertTrue(html.contains("<td></td>"));
  }

  /**
   * Erzeugt h1/h2 fuer bestehende Heading-Stile.
   */
  @Test
  void convertsKnownHeadingStylesToHeadingTags() {
    StyledDocument<String, String, String> document = ReadOnlyStyledDocument.fromString(
        "Titel",
        "-fx-font-weight: bold;-fx-font-size: 20px;",
        "",
        SegmentOps.styledTextOps()
    );

    String html = new RichTextHtmlConverter().toHtmlBody(document);
    System.out.println(html);

    assertEquals("<h1>Titel</h1>", html);
  }
}
