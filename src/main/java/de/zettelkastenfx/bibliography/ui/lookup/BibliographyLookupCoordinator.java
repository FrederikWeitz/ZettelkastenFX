package de.zettelkastenfx.bibliography.ui.lookup;

import de.zettelkastenfx.bibliography.ui.TypeChoice;

import java.util.function.Supplier;

/**
 * Fassade für die verschiedenen Lookup-Koordinatoren des Bibliographie-Editors.
 */
public class BibliographyLookupCoordinator {

  private final TitleSeriesLookupCoordinator titleSeriesCoordinator;
  private final ArticleCollectionLookupCoordinator articleCollectionCoordinator;
  private final ArticleJournalLookupCoordinator articleJournalCoordinator;

  /**
   * Erzeugt den übergeordneten Lookup-Koordinator.
   *
   * @param lookupProviderSupplier liefert den aktuellen Lookup-Provider
   * @param stateAccess gebündelte Zustandszugriffe
   * @param fieldAccess gebündelte Feldzugriffe
   * @param entryAccess gebündelte Entry- und Referenzzugriffe
   */
  public BibliographyLookupCoordinator(
      Supplier<BibliographyLookupProvider> lookupProviderSupplier,
      LookupStateAccess stateAccess,
      LookupFieldAccess fieldAccess,
      LookupEntryAccess entryAccess
  ) {
    this.titleSeriesCoordinator = new TitleSeriesLookupCoordinator(
        lookupProviderSupplier,
        stateAccess,
        fieldAccess,
        entryAccess
    );
    this.articleCollectionCoordinator = new ArticleCollectionLookupCoordinator(
        lookupProviderSupplier,
        fieldAccess,
        entryAccess
    );
    this.articleJournalCoordinator = new ArticleJournalLookupCoordinator(
        lookupProviderSupplier,
        fieldAccess,
        entryAccess
    );
  }

  /**
   * Prüft, ob irgendein Lookup-Subsystem momentan Callbacks unterdrückt.
   *
   * @return {@code true}, wenn mindestens ein Subsystem unterdrückt
   */
  public boolean isSuppressLookupCallbacks() {
    return titleSeriesCoordinator.isSuppressLookupCallbacks()
               || articleCollectionCoordinator.isSuppressLookupCallbacks()
               || articleJournalCoordinator.isSuppressLookupCallbacks();
  }

  /**
   * Verdrahtet alle Lookup-Interaktionen.
   */
  public void wireLookupInteractions() {
    titleSeriesCoordinator.wireLookupInteractions();
    articleCollectionCoordinator.wireLookupInteractions();
    articleJournalCoordinator.wireLookupInteractions();
  }

  /**
   * Reagiert auf Änderungen in Autorenfeldern.
   *
   * @param type Typ des betroffenen Formulars
   */
  public void handleAuthorsEdited(TypeChoice type) {
    titleSeriesCoordinator.handleAuthorsEdited(type);
  }
}
