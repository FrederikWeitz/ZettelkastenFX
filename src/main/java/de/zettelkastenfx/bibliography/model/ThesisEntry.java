package de.zettelkastenfx.bibliography.model;

import lombok.Getter;

/**
 * Domänenobjekt für bibliographische Angaben vom Typ Thesis.
 * Die Klasse beschreibt Hochschulschriften wie Bachelorarbeiten,
 * Masterarbeiten, Dissertationen oder Habilitationen.
 */
public class ThesisEntry extends BibliographyEntry {

  @Getter private String subtitle = "";

  /** Typ der Hochschulschrift. */
  @Getter private ThesisType thesisType = ThesisType.DISSERTATION;

  /** Hochschule oder Universität. */
  @Getter private String university = "";

  /** Ort der Hochschule oder des Erscheinens. */
  @Getter private String place = "";

  /** Erscheinungs- oder Abschlussjahr. */
  @Getter private String year = "";

  /** Betreuer oder betreuende Person. */
  @Getter private String advisor = "";

  /** Optionaler Reihentitel, falls die Arbeit in einer Schriftenreihe erscheint. */
  @Getter private String series = "";

  /** Freies Notizfeld. */
  @Getter private String note = "";

  /**
   * Erzeugt einen leeren Thesis-Eintrag.
   */
  public ThesisEntry() {
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
   * Setzt den Typ der Hochschulschrift.
   *
   * @param thesisType neuer Thesis-Typ; {@code null} wird als {@link ThesisType#DISSERTATION} behandelt
   */
  public void setThesisType(ThesisType thesisType) {
    this.thesisType = thesisType == null ? ThesisType.DISSERTATION : thesisType;
  }

  /**
   * Setzt die Hochschule oder Universität.
   *
   * @param university neue Hochschule; {@code null} wird als leerer String behandelt
   */
  public void setUniversity(String university) {
    this.university = university == null ? "" : university.trim();
  }

  /**
   * Setzt den Ort.
   *
   * @param place neuer Ort; {@code null} wird als leerer String behandelt
   */
  public void setPlace(String place) {
    this.place = place == null ? "" : place.trim();
  }

  /**
   * Setzt das Jahr.
   *
   * @param year neues Jahr; {@code null} wird als leerer String behandelt
   */
  public void setYear(String year) {
    this.year = year == null ? "" : year.trim();
  }

  /**
   * Setzt den Betreuer.
   *
   * @param advisor neuer Betreuer; {@code null} wird als leerer String behandelt
   */
  public void setAdvisor(String advisor) {
    this.advisor = advisor == null ? "" : advisor.trim();
  }

  /**
   * Setzt einen optionalen Reihentitel.
   *
   * @param series neuer Reihentitel; {@code null} wird als leerer String behandelt
   */
  public void setSeries(String series) {
    this.series = series == null ? "" : series.trim();
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