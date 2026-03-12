package de.zettelkastenfx.bibliography.model;

import lombok.Getter;
import lombok.Setter;

/**
 * Domänenobjekt für bibliographische Angaben vom Typ Zeitschriftenartikel.
 * Der Eintrag kann optional auf eine vorhandene Zeitschrift referenzieren.
 */
public class JournalArticleEntry extends BibliographyEntry {

  @Getter private String subtitle = "";
  @Getter private String publisher = "";
  @Getter private String journalTitle = "";
  @Getter private String volume = "";
  @Getter private String issue = "";
  @Getter private String year = "";
  @Getter private String pages = "";
  @Getter private String doi = "";
  @Getter @Setter private Integer journalEntryId;
  public JournalArticleEntry() {
    super();
  }
  public void setSubtitle(String subtitle) {
    this.subtitle = subtitle == null ? "" : subtitle.trim();
  }
  public void setPublisher(String publisher) {
    this.publisher = publisher == null ? "" : publisher.trim();
  }
  public void setJournalTitle(String journalTitle) {
    this.journalTitle = journalTitle == null ? "" : journalTitle.trim();
  }
  public void setVolume(String volume) {
    this.volume = volume == null ? "" : volume.trim();
  }
  public void setIssue(String issue) {
    this.issue = issue == null ? "" : issue.trim();
  }
  public void setYear(String year) {
    this.year = year == null ? "" : year.trim();
  }
  public void setPages(String pages) {
    this.pages = pages == null ? "" : pages.trim();
  }
  public void setDoi(String doi) {
    this.doi = doi == null ? "" : doi.trim();
  }
  public void clearJournalReference() {
    this.journalEntryId = null;
    this.journalTitle = "";
  }
}
