package de.zettelkastenfx.bibliography.ui.lookup;

import de.zettelkastenfx.bibliography.model.*;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Bündelt Entry-bezogene Rückschreib- und Referenzzugriffe für Lookups.
 *
 * @param bookEntryReader liest einen Bucheintrag in das Formular ein
 * @param collectionEntryReader liest einen Sammelbandeintrag in das Formular ein
 * @param articleEntryReader liest einen Artikeleintrag in das Formular ein
 * @param journalEntryReader liest einen Zeitschrifteneintrag in das Formular ein
 * @param journalArticleEntryReader liest einen Zeitschriftenartikel in das Formular ein
 * @param collectionEntryIdGetter liefert die aktuelle Sammelband-ID
 * @param collectionEntryIdSetter setzt die aktuelle Sammelband-ID
 * @param journalEntryIdGetter liefert die aktuelle Zeitschriften-ID
 * @param journalEntryIdSetter setzt die aktuelle Zeitschriften-ID
 * @param requestCreateCollectionAction Aktion zum Anlegen eines neuen Sammelbands
 * @param requestCreateJournalAction Aktion zum Anlegen einer neuen Zeitschrift
 */
public record LookupEntryAccess(
    Consumer<BookEntry> bookEntryReader,
    Consumer<CollectionEntry> collectionEntryReader,
    Consumer<ArticleInCollectionEntry> articleEntryReader,
    Consumer<JournalEntry> journalEntryReader,
    Consumer<JournalArticleEntry> journalArticleEntryReader,
    Consumer<InternetEntry> internetEntryReader,
    Consumer<EditionEntry> editionEntryReader,
    Supplier<Integer> collectionEntryIdGetter,
    Consumer<Integer> collectionEntryIdSetter,
    Supplier<Integer> journalEntryIdGetter,
    Consumer<Integer> journalEntryIdSetter,
    Runnable requestCreateCollectionAction,
    Runnable requestCreateJournalAction
) {

  /**
   * Liest einen Bucheintrag in das Formular ein.
   *
   * @param entry geladener Bucheintrag
   */
  public void readBookEntry(BookEntry entry) {
    bookEntryReader.accept(entry);
  }

  /**
   * Liest einen Sammelbandeintrag in das Formular ein.
   *
   * @param entry geladener Sammelbandeintrag
   */
  public void readCollectionEntry(CollectionEntry entry) {
    collectionEntryReader.accept(entry);
  }

  /**
   * Liest einen Artikeleintrag in das Formular ein.
   *
   * @param entry geladener Artikeleintrag
   */
  public void readArticleEntry(ArticleInCollectionEntry entry) {
    articleEntryReader.accept(entry);
  }

  /**
   * Liest einen Zeitschrifteneintrag in das Formular ein.
   *
   * @param entry geladener Zeitschrifteneintrag
   */
  public void readJournalEntry(JournalEntry entry) {
    journalEntryReader.accept(entry);
  }

  /**
   * Liest einen Zeitschriftenartikel in das Formular ein.
   *
   * @param entry geladener Zeitschriftenartikel
   */
  public void readJournalArticleEntry(JournalArticleEntry entry) {
    journalArticleEntryReader.accept(entry);
  }

  /**
   * Liest eine Internetquelle in das Formular ein.
   *
   * @param entry geladene Internetquelle
   */
  public void readInternetEntry(InternetEntry entry) {
    internetEntryReader.accept(entry);
  }

  /**
   * Liest eine Edition in das Formular ein.
   *
   * @param entry geladene Edition
   */
  public void readEditionEntry(EditionEntry entry) {
    editionEntryReader.accept(entry);
  }

  /**
   * Liefert die aktuelle Sammelband-ID.
   *
   * @return Sammelband-ID oder {@code null}
   */
  public Integer getCollectionEntryId() {
    return collectionEntryIdGetter.get();
  }

  /**
   * Setzt die aktuelle Sammelband-ID.
   *
   * @param entryId Sammelband-ID oder {@code null}
   */
  public void setCollectionEntryId(Integer entryId) {
    collectionEntryIdSetter.accept(entryId);
  }

  /**
   * Liefert die aktuelle Zeitschriften-ID.
   *
   * @return Zeitschriften-ID oder {@code null}
   */
  public Integer getJournalEntryId() {
    return journalEntryIdGetter.get();
  }

  /**
   * Setzt die aktuelle Zeitschriften-ID.
   *
   * @param entryId Zeitschriften-ID oder {@code null}
   */
  public void setJournalEntryId(Integer entryId) {
    journalEntryIdSetter.accept(entryId);
  }

  /**
   * Fordert das Anlegen eines neuen Sammelbands an.
   */
  public void requestCreateCollection() {
    requestCreateCollectionAction.run();
  }

  /**
   * Fordert das Anlegen einer neuen Zeitschrift an.
   */
  public void requestCreateJournal() {
    requestCreateJournalAction.run();
  }
}
