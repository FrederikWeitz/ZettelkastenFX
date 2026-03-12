package de.zettelkastenfx.bibliography.model;

import lombok.Getter;
import lombok.Setter;

public class ArticleInCollectionEntry extends BibliographyEntry {

  @Getter private String articleInCollectionTitle = ""; // Titel des Sammelbandes
  @Getter private String pages = "";
  @Getter @Setter private Integer collectionEntryId;     // FK auf bibliography_entries(id) vom Typ COLLECTION, darf null sein
  @Getter private String collectionTitle = "";   // UI-Text (wird bei Save ggf. wieder geleert)

  public ArticleInCollectionEntry() {
    super();
  }

  public void setArticleInCollectionTitle(String articleIncollectionTitle) { this.articleInCollectionTitle = articleIncollectionTitle == null ? "" : articleIncollectionTitle.trim(); }
  public void setPages(String pages) { this.pages = pages == null ? "" : pages.trim(); }
  public void setCollectionTitle(String collectionTitle) { this.collectionTitle = collectionTitle == null ? "" : collectionTitle.trim(); }

  public void clearCollectionReference() {
    this.collectionEntryId = null;
    this.collectionTitle = "";
  }
}
