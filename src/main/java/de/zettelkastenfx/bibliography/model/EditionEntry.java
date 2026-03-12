package de.zettelkastenfx.bibliography.model;

import lombok.Getter;

/**
 * Domänenobjekt für bibliographische Angaben vom Typ Edition.
 * Gemeint ist hier eine herausgegebene Ausgabe oder Edition,
 * die optional zu einer Reihe gehören kann.
 */
public class EditionEntry extends BibliographyEntry {

  /** Verlag oder herausgebende Institution. */
  @Getter private String publisher = "";

  /** Erscheinungsort. */
  @Getter private String place = "";

  /** Erscheinungsjahr. */
  @Getter private String year = "";

  /** ISBN der Edition. */
  @Getter private String isbn = "";

  /** Auflage. */
  @Getter private String run = "";

  /** Optionaler Reihentitel. */
  @Getter private String series = "";

  /** Bandangabe innerhalb einer Reihe. */
  @Getter private String volume = "";

  /**
   * Erzeugt einen leeren Editions-Eintrag.
   */
  public EditionEntry() {
    super();
  }

  /**
   * Setzt den Verlag.
   *
   * @param publisher neuer Verlag; {@code null} wird als leerer String behandelt
   */
  public void setPublisher(String publisher) {
    this.publisher = publisher == null ? "" : publisher.trim();
  }

  /**
   * Setzt den Erscheinungsort.
   *
   * @param place neuer Ort; {@code null} wird als leerer String behandelt
   */
  public void setPlace(String place) {
    this.place = place == null ? "" : place.trim();
  }

  /**
   * Setzt das Erscheinungsjahr.
   *
   * @param year neues Jahr; {@code null} wird als leerer String behandelt
   */
  public void setYear(String year) {
    this.year = year == null ? "" : year.trim();
  }

  /**
   * Setzt die ISBN.
   *
   * @param isbn neue ISBN; {@code null} wird als leerer String behandelt
   */
  public void setIsbn(String isbn) {
    this.isbn = isbn == null ? "" : isbn.trim();
  }

  /**
   * Setzt die Auflage.
   *
   * @param run neue Auflage; {@code null} wird als leerer String behandelt
   */
  public void setRun(String run) {
    this.run = run == null ? "" : run.trim();
  }

  /**
   * Setzt den Reihentitel.
   *
   * @param series neuer Reihentitel; {@code null} wird als leerer String behandelt
   */
  public void setSeries(String series) {
    this.series = series == null ? "" : series.trim();
  }

  /**
   * Setzt die Bandangabe.
   *
   * @param volume neue Bandangabe; {@code null} wird als leerer String behandelt
   */
  public void setVolume(String volume) {
    this.volume = volume == null ? "" : volume.trim();
  }
}
