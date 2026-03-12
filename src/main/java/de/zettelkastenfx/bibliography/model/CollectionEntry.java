package de.zettelkastenfx.bibliography.model;

import lombok.Getter;

public class CollectionEntry extends BibliographyEntry {

  @Getter private String subtitle = "";
  @Getter private String publisher = "";
  @Getter private String place = "";
  @Getter private String year = "";
  @Getter private String series = "";

  public CollectionEntry() {
    super();
  }

  public void setSubtitle(String subtitle) { this.subtitle = subtitle == null ? "" : subtitle.trim(); }
  public void setPublisher(String publisher) { this.publisher = publisher == null ? "" : publisher.trim(); }
  public void setPlace(String place) { this.place = place == null ? "" : place.trim(); }
  public void setYear(String year) { this.year = year == null ? "" : year.trim(); }
  public void setSeries(String series) { this.series = series == null ? "" : series.trim(); }
}
