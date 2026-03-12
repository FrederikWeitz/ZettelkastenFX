package de.zettelkastenfx.bibliography.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class BookEntry extends BibliographyEntry {

  @Getter private String subtitle = "";
  @Getter private String publisher = "";
  @Getter private String place = "";
  @Getter private String year = "";
  @Getter private String isbn = "";
  @Getter private String run = "";
  @Getter private String series = "";
  @Getter private String seriesNumber = "";

  public BookEntry() {
    super();
  }

  public void setSubtitle(String subtitle) { this.subtitle = subtitle == null ? "" : subtitle.trim(); }
  public void setPublisher(String publisher) { this.publisher = publisher == null ? "" : publisher.trim(); }
  public void setPlace(String place) { this.place = place == null ? "" : place.trim(); }
  public void setYear(String year) { this.year = year == null ? "" : year.trim(); }
  public void setIsbn(String isbn) { this.isbn = isbn == null ? "" : isbn.trim(); }
  public void setRun(String run) { this.run = run == null ? "" : run.trim(); }
  public void setSeries(String series) { this.series = series == null ? "" : series.trim(); }
  public void setSeriesNumber(String seriesNumber) { this.seriesNumber = seriesNumber == null ? "" : seriesNumber.trim(); }
}
