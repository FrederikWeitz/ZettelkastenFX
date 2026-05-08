package de.zettelkastenfx.bibliography.ui.field;

import de.zettelkastenfx.bibliography.model.BibValue;
import de.zettelkastenfx.bibliography.model.RelatedBibValue;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * Bibliographische Feldkomponente für Related-Felder.
 * Die Komponente zeigt einen Textwert an, delegiert die Verwaltung der
 * eigentlichen Related-Ziel-ID aber an die umgebende Shell.
 */
public class RelatedBibliographyFieldComponent extends AbstractTextBibliographyFieldComponent {

  private final BiFunction<String, String, Integer> relatedEntryIdReader;
  private final RelatedEntryWriter relatedEntryWriter;

  /**
   * Erzeugt eine Related-Feldkomponente.
   *
   * @param request Erzeugungsanfrage
   * @param relatedEntryIdReader liest die Related-Ziel-ID für Medientyp und Feldname
   * @param relatedEntryWriter schreibt Related-Ziel-ID und Anzeigetext zurück
   */
  public RelatedBibliographyFieldComponent(
      BibliographyFieldRequest request,
      BiFunction<String, String, Integer> relatedEntryIdReader,
      RelatedEntryWriter relatedEntryWriter
  ) {
    super(request);
    this.relatedEntryIdReader = Objects.requireNonNull(relatedEntryIdReader, "relatedEntryIdReader");
    this.relatedEntryWriter = Objects.requireNonNull(relatedEntryWriter, "relatedEntryWriter");
  }

  @Override
  public Collection<BibValue> readValues() {
    Integer relatedEntryId = relatedEntryIdReader.apply(mediaTypeBibName(), bibtexName());

    if (relatedEntryId == null || relatedEntryId <= 0) {
      return List.of();
    }

    return List.of(new RelatedBibValue(
        bibtexName(),
        relatedEntryId,
        null,
        fieldView().getText()
    ));
  }

  @Override
  protected BibValue readSingleValue() {
    Integer relatedEntryId = relatedEntryIdReader.apply(mediaTypeBibName(), bibtexName());

    if (relatedEntryId == null || relatedEntryId <= 0) {
      return null;
    }

    return new RelatedBibValue(
        bibtexName(),
        relatedEntryId,
        null,
        fieldView().getText()
    );
  }

  @Override
  protected void writeSingleValue(BibValue value) {
    if (!(value instanceof RelatedBibValue relatedValue)) {
      return;
    }

    relatedEntryWriter.writeRelatedEntry(
        mediaTypeBibName(),
        bibtexName(),
        relatedValue.relatedEntryId(),
        relatedValue.displayValue()
    );
  }

  @Override
  public void clear() {
    relatedEntryWriter.writeRelatedEntry(mediaTypeBibName(), bibtexName(), null, "");
  }

  @Override
  public boolean isEmpty() {
    Integer relatedEntryId = relatedEntryIdReader.apply(mediaTypeBibName(), bibtexName());
    return relatedEntryId == null || relatedEntryId <= 0;
  }

  /**
   * Schreibt eine Related-Ziel-ID samt Anzeigetext zurück.
   */
  @FunctionalInterface
  public interface RelatedEntryWriter {

    /**
     * Schreibt den Related-Zustand eines Feldes.
     *
     * @param mediaTypeBibName technischer Medientypname
     * @param bibtexName technischer BibTeX-Feldname
     * @param entryId Related-Ziel-ID oder {@code null}
     * @param displayText sichtbarer Feldtext
     */
    void writeRelatedEntry(String mediaTypeBibName,
                           String bibtexName,
                           Integer entryId,
                           String displayText);
  }
}