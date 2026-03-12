package de.zettelkastenfx.bibliography.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public abstract class BibliographyEntry {

  @Getter String title = "";

  @Getter @Setter Integer id; // DB-ID (kommt in Schritt 2)

  @Getter final List<Author> authors = new ArrayList<>(1);

  @Getter @Setter LocalDateTime createdAt;
  @Getter @Setter LocalDateTime updatedAt;

  public void setTitle(String title) { this.title = title == null ? "" : title.trim(); }
}