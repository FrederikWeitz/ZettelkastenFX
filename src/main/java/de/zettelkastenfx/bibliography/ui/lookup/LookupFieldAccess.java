package de.zettelkastenfx.bibliography.ui.lookup;

import de.zettelkastenfx.bibliography.ui.support.BibliographyFieldView;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Bündelt Feldzugriffe für die Lookup-Orchestrierung.
 *
 * @param bookTitleField Titelfeld für Bücher
 * @param collectionTitleField Titelfeld für Sammelbände
 * @param articleTitleField Titelfeld für Artikel in Sammelbänden
 * @param journalTitleField Titelfeld für Zeitschriften
 * @param journalArticleTitleField Titelfeld für Zeitschriftenartikel
 * @param internetTitleField Titelfeld für Internetseite
 * @param editionTitleField Titelfeld für Edition
 * @param bookSeriesField Reihenfeld für Bücher
 * @param collectionSeriesField Reihenfeld für Sammelbände
 * @param articleCollectionTitleField Sammelbandtitelfeld des Artikels
 * @param journalArticleJournalTitleField Zeitschriftentitelfeld des Zeitschriftenartikels
 * @param collectionTitleGetter liefert den aktuellen Sammelbandtitel
 * @param collectionTitleSetter setzt den Sammelbandtitel
 * @param journalTitleGetter liefert den aktuellen Zeitschriftentitel im JournalArticle-Formular
 * @param journalTitleSetter setzt den Zeitschriftentitel im JournalArticle-Formular
 */
public record LookupFieldAccess(
    BibliographyFieldView bookTitleField,
    BibliographyFieldView collectionTitleField,
    BibliographyFieldView articleTitleField,
    BibliographyFieldView journalTitleField,
    BibliographyFieldView journalArticleTitleField,
    BibliographyFieldView internetTitleField,
    BibliographyFieldView editionTitleField,
    BibliographyFieldView bookSeriesField,
    BibliographyFieldView collectionSeriesField,
    BibliographyFieldView articleCollectionTitleField,
    BibliographyFieldView journalArticleJournalTitleField,
    Supplier<String> collectionTitleGetter,
    Consumer<String> collectionTitleSetter,
    Supplier<String> journalTitleGetter,
    Consumer<String> journalTitleSetter
) {

  /**
   * Liefert den aktuellen Sammelbandtitel.
   *
   * @return aktueller Sammelbandtitel
   */
  public String getCollectionTitle() {
    return collectionTitleGetter.get();
  }

  /**
   * Setzt den Sammelbandtitel.
   *
   * @param title neuer Sammelbandtitel
   */
  public void setCollectionTitle(String title) {
    collectionTitleSetter.accept(title);
  }

  /**
   * Liefert den aktuellen Zeitschriftentitel im JournalArticle-Formular.
   *
   * @return aktueller Zeitschriftentitel
   */
  public String getJournalTitle() {
    return journalTitleGetter.get();
  }

  /**
   * Setzt den Zeitschriftentitel im JournalArticle-Formular.
   *
   * @param title neuer Zeitschriftentitel
   */
  public void setJournalTitle(String title) {
    journalTitleSetter.accept(title);
  }
}
