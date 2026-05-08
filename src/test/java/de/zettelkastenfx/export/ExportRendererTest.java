package de.zettelkastenfx.export;

import de.zettelkastenfx.export.model.ExportBibliographyEntry;
import de.zettelkastenfx.export.model.ExportBibliographyField;
import de.zettelkastenfx.export.model.ExportDocument;
import de.zettelkastenfx.export.model.ExportNote;
import de.zettelkastenfx.export.render.ExportTextRenderer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueft die fachliche Textstruktur des Exports.
 */
class ExportRendererTest {

  /**
   * Prueft Kopfzeile, Inhalt, Schlagwoerter und Inline-Bibliographie.
   */
  @Test
  void rendersSelectedNotePartsInRequiredOrder() {
    ExportSelection selection = new ExportSelection(
        true,
        true,
        true,
        true,
        BibliographyExportLevel.IDENTIFY,
        BibliographyPlacement.AFTER_EACH_NOTE,
        true
    );
    ExportBibliographyEntry bibliography = new ExportBibliographyEntry(
        7,
        List.of(new ExportBibliographyField("Titel", "Quelle"))
    );
    ExportDocument document = new ExportDocument(
        List.of(new ExportNote(3, "Ueberschrift", "Inhalt", List.of("alpha"), bibliography)),
        List.of(),
        selection
    );

    String rendered = new ExportTextRenderer().render(document);

    assertTrue(rendered.contains("3: Ueberschrift"));
    assertTrue(rendered.contains("Inhalt"));
    assertTrue(rendered.contains("Stichwörter:"));
    assertTrue(rendered.contains("\talpha"));
    assertTrue(rendered.contains("Titel: Quelle"));
  }

  /**
   * Prueft, dass der Doppelpunkt entfaellt, wenn nur der Titel ausgewaehlt ist.
   */
  @Test
  void omitsColonWhenOnlyTitleIsSelected() {
    ExportSelection selection = new ExportSelection(
        false,
        true,
        false,
        false,
        BibliographyExportLevel.NONE,
        BibliographyPlacement.AFTER_EACH_NOTE,
        false
    );
    ExportDocument document = new ExportDocument(
        List.of(new ExportNote(3, "Nur Titel", "", List.of(), null)),
        List.of(),
        selection
    );

    assertEquals("Nur Titel" + System.lineSeparator(), new ExportTextRenderer().render(document));
  }
}
