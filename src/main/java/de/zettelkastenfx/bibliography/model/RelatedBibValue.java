package de.zettelkastenfx.bibliography.model;

/**
 * Repräsentiert einen Verweis auf einen anderen bibliographischen Eintrag,
 * z. B. {@code related_journal} oder {@code related_collection}.
 *
 * @param bibtexName technischer Feldname
 * @param relatedEntryId Ziel-ID des verknüpften Eintrags
 * @param relatedMediaTypeId Medientyp-ID des Zielobjekts
 * @param displayValue Anzeigetext des Zielobjekts
 */
public record RelatedBibValue(
    String bibtexName,
    Integer relatedEntryId,
    Integer relatedMediaTypeId,
    String displayValue
) implements BibValue {

  /**
   * Erzeugt einen Related-Wert mit bereinigten Textfeldern.
   *
   * @param bibtexName technischer Feldname
   * @param relatedEntryId Ziel-ID des verknüpften Eintrags
   * @param relatedMediaTypeId Medientyp-ID des Zielobjekts
   * @param displayValue Anzeigetext des Zielobjekts
   */
  public RelatedBibValue {
    bibtexName = sanitize(bibtexName);
    displayValue = sanitize(displayValue);
  }

  @Override
  public BibValueType valueType() {
    return BibValueType.RELATED;
  }

  /**
   * Liefert als generischen Nutzwert die Ziel-ID des verknüpften Eintrags.
   *
   * @return Ziel-ID des verknüpften Eintrags
   */
  @Override
  public Object value() {
    return relatedEntryId;
  }

  private static String sanitize(String value) {
    return value == null ? "" : value.trim();
  }
}