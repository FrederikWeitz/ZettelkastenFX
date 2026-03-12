package de.zettelkastenfx.bibliography.ui.lookup;

import de.zettelkastenfx.bibliography.model.Author;
import de.zettelkastenfx.bibliography.ui.TypeChoice;

import java.util.List;

/**
 * Abstraktion für alle Lookup-Funktionen der Shell.
 */
public interface BibliographyLookupProvider {
  /**
   * Sucht Autorenvorschläge.
   *
   * @param type Bibliographietyp.
   * @param lastNamePrefix aktuelles Präfix des Nachnamens.
   * @param selectedEntryId aktuell selektierter Eintrag.
   * @return passende Autorvorschläge.
   */
  List<AuthorSuggestion> findAuthors(TypeChoice type, String lastNamePrefix, Integer selectedEntryId);

  /**
   * Sucht Titelvorschläge.
   *
   * @param type Bibliographietyp.
   * @param query Suchtext.
   * @param requiredAuthors optionale Autorenfilter.
   * @return passende Titelvorschläge.
   */
  List<TitleSuggestion> findTitles(TypeChoice type, String query, List<Author> requiredAuthors);

  /**
   * Löst einen exakten Titel auf.
   *
   * @param type Bibliographietyp.
   * @param title exakter Titel.
   * @param requiredAuthors optionale Autorenfilter.
   * @return eindeutiger Treffer oder {@code null}.
   */
  TitleSuggestion resolveExactTitle(TypeChoice type, String title, List<Author> requiredAuthors);

  /**
   * Löst anhand der Autoren einen eindeutigen Titel auf.
   *
   * @param type Bibliographietyp.
   * @param requiredAuthors Autorenfilter.
   * @return eindeutiger Treffer oder {@code null}.
   */
  TitleSuggestion resolveUniqueTitleForAuthors(TypeChoice type, List<Author> requiredAuthors);

  /**
   * Sucht Reihenvorschläge.
   *
   * @param type Bibliographietyp.
   * @param query Suchtext.
   * @return passende Reihenwerte.
   */
  List<String> findSeries(TypeChoice type, String query);

  /**
   * Lädt einen Eintrag anhand seines Typs und seiner ID.
   *
   * @param type Bibliographietyp.
   * @param entryId ID des Eintrags.
   * @return geladenes Domänenobjekt oder {@code null}.
   */
  Object loadEntry(TypeChoice type, Integer entryId);

  /** Leerer Standardprovider ohne Treffer. */
  BibliographyLookupProvider NONE = new BibliographyLookupProvider() {
    /** {@inheritDoc} */
    @Override
    public List<AuthorSuggestion> findAuthors(TypeChoice type, String lastNamePrefix, Integer selectedEntryId) {
      return List.of();
    }

    /** {@inheritDoc} */
    @Override
    public List<TitleSuggestion> findTitles(TypeChoice type, String query, List<Author> requiredAuthors) {
      return List.of();
    }

    /** {@inheritDoc} */
    @Override
    public TitleSuggestion resolveExactTitle(TypeChoice type, String title, List<Author> requiredAuthors) {
      return null;
    }

    /** {@inheritDoc} */
    @Override
    public TitleSuggestion resolveUniqueTitleForAuthors(TypeChoice type, List<Author> requiredAuthors) {
      return null;
    }

    /** {@inheritDoc} */
    @Override
    public List<String> findSeries(TypeChoice type, String query) {
      return List.of();
    }

    /** {@inheritDoc} */
    @Override
    public Object loadEntry(TypeChoice type, Integer entryId) {
      return null;
    }
  };
}
