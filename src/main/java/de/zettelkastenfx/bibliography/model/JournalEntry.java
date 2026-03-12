package de.zettelkastenfx.bibliography.model;

import lombok.Getter;

/**
 * Domänenobjekt für bibliographische Angaben vom Typ Zeitschrift.
 * Die Klasse ergänzt den Basiseintrag um journalspezifische Metadaten.
 */
public class JournalEntry extends BibliographyEntry {

  /** Untertitel der Zeitschrift. */
  @Getter private String subtitle = "";

  /** ISSN der Zeitschrift. */
  @Getter private String issn = "";

  /** Verlag oder herausgebende Institution. */
  @Getter private String publisher = "";

  /** Erscheinungsort. */
  @Getter private String place = "";

  /** Startjahr der Zeitschrift. */
  @Getter private String startYear = "";

  /** Endjahr der Zeitschrift, falls eingestellt. */
  @Getter private String endYear = "";

  /** Freies Notizfeld. */
  @Getter private String note = "";

  /**
   * Erzeugt einen leeren Zeitschrifteneintrag.
   */
  public JournalEntry() {
    super();
  }

  /**
   * Setzt den Untertitel.
   *
   * @param subtitle neuer Untertitel; {@code null} wird als leerer String behandelt
   */
  public void setSubtitle(String subtitle) {
    this.subtitle = subtitle == null ? "" : subtitle.trim();
  }

  /**
   * Setzt die ISSN.
   *
   * @param issn neue ISSN; {@code null} wird als leerer String behandelt
   */
  public void setIssn(String issn) {
    this.issn = issn == null ? "" : issn.trim();
  }

  /**
   * Setzt den Verlag oder die herausgebende Institution.
   *
   * @param publisher neuer Verlag; {@code null} wird als leerer String behandelt
   */
  public void setPublisher(String publisher) {
    this.publisher = publisher == null ? "" : publisher.trim();
  }

  /**
   * Setzt den Erscheinungsort.
   *
   * @param place neuer Erscheinungsort; {@code null} wird als leerer String behandelt
   */
  public void setPlace(String place) {
    this.place = place == null ? "" : place.trim();
  }

  /**
   * Setzt das Startjahr.
   *
   * @param startYear neues Startjahr; {@code null} wird als leerer String behandelt
   */
  public void setStartYear(String startYear) {
    this.startYear = startYear == null ? "" : startYear.trim();
  }

  /**
   * Setzt das Endjahr.
   *
   * @param endYear neues Endjahr; {@code null} wird als leerer String behandelt
   */
  public void setEndYear(String endYear) {
    this.endYear = endYear == null ? "" : endYear.trim();
  }

  /**
   * Setzt eine freie Notiz.
   *
   * @param note neue Notiz; {@code null} wird als leerer String behandelt
   */
  public void setNote(String note) {
    this.note = note == null ? "" : note.trim();
  }
}
