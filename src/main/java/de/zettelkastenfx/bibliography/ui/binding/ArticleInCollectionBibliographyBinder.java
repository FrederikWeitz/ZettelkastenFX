package de.zettelkastenfx.bibliography.ui.binding;

import de.zettelkastenfx.bibliography.model.ArticleInCollectionEntry;
import de.zettelkastenfx.bibliography.ui.support.BibliographyAuthorsView;
import de.zettelkastenfx.bibliography.ui.support.BibliographyFieldView;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.function.Consumer;

public class ArticleInCollectionBibliographyBinder extends AbstractBibliographyBinder<ArticleInCollectionEntry> {

  private final BibliographyFieldView titleField;
  private final BibliographyFieldView collectionTitleField;
  private final BibliographyFieldView pagesField;
  @Setter @Getter private Integer collectionEntryId;

  private final BibliographyAuthorsView authorsView;

  public ArticleInCollectionBibliographyBinder(
      BibliographyFieldView titleField,
      BibliographyFieldView collectionTitleField,
      BibliographyFieldView pagesField,
      BibliographyAuthorsView authorsView,
      Consumer<Integer> selectedEntryIdSetter,
      Consumer<Integer> sourceEntryIdSetter,
      Consumer<Boolean> autoFilledSetter,
      Consumer<Boolean> autoFillLockedSetter
  ) {
    super(selectedEntryIdSetter, sourceEntryIdSetter, autoFilledSetter, autoFillLockedSetter);
    this.titleField = titleField;
    this.collectionTitleField = collectionTitleField;
    this.pagesField = pagesField;
    this.authorsView = authorsView;
  }

  /**
   * Schreibt die UI-Daten des Artikelformulars in einen Artikeleintrag.
   *
   * @param entry Zielobjekt für die Übernahme.
   */
  public void writeInto(ArticleInCollectionEntry entry) {
    if (entry == null) {
      return;
    }

    entry.setTitle(titleField.getText());
    entry.setPages(pagesField.getText());

    String collectionTitle = collectionTitleField.getText();
    if (collectionTitle.isBlank()) {
      entry.clearCollectionReference();
      collectionEntryId = null;
    } else {
      entry.setCollectionTitle(collectionTitle);
      entry.setCollectionEntryId(collectionEntryId);
    }

    entry.getAuthors().clear();
    entry.getAuthors().addAll(authorsView.toAuthors());
  }

  /**
   * Liest einen Artikeleintrag in das Formular ein.
   *
   * @param entry Quellobjekt; {@code null} leert das Formular.
   */
  public void readFrom(ArticleInCollectionEntry entry) {
    if (entry == null) {
      clear();
      return;
    }

    String articleTitle = (entry.getTitle() == null || entry.getTitle().isBlank())
                              ? entry.getArticleInCollectionTitle()
                              : entry.getTitle();

    titleField.setText(articleTitle);
    pagesField.setText(entry.getPages());
    collectionTitleField.setText(entry.getCollectionTitle());
    collectionEntryId = entry.getCollectionEntryId();
    authorsView.fromAuthors(entry.getAuthors());

    applyEntryState(entry.getId());
  }

  /**
   * Leert das Artikelformular und setzt seinen Lookup-Zustand zurück.
   */
  public void clear() {
    titleField.setText("");
    pagesField.setText("");
    collectionTitleField.setText("");
    collectionEntryId = null;
    authorsView.fromAuthors(List.of());

    clearEntryState();
  }

}
