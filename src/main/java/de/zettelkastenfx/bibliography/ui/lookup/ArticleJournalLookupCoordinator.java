package de.zettelkastenfx.bibliography.ui.lookup;

import de.zettelkastenfx.bibliography.ui.TypeChoice;
import de.zettelkastenfx.bibliography.ui.support.BibliographyFieldView;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Orchestriert den Journal-Lookup im Formular für Zeitschriftenartikel.
 * Die Klasse spiegelt die bestehende Collection-Logik für Artikel in Sammelbänden.
 */
public class ArticleJournalLookupCoordinator {

  private final Supplier<BibliographyLookupProvider> lookupProviderSupplier;
  private final LookupFieldAccess fieldAccess;
  private final LookupEntryAccess entryAccess;

  private boolean suppressLookupCallbacks;
  private boolean journalAutoFillLocked;
  private boolean journalWasAutoFilled;

  /**
   * Erzeugt einen neuen Lookup-Koordinator für die Journal-Referenz im JournalArticle-Formular.
   *
   * @param lookupProviderSupplier liefert den aktuellen Lookup-Provider
   * @param fieldAccess gebündelte Feldzugriffe
   * @param entryAccess gebündelte Entry- und Referenzzugriffe
   */
  public ArticleJournalLookupCoordinator(
      Supplier<BibliographyLookupProvider> lookupProviderSupplier,
      LookupFieldAccess fieldAccess,
      LookupEntryAccess entryAccess
  ) {
    this.lookupProviderSupplier = lookupProviderSupplier;
    this.fieldAccess = fieldAccess;
    this.entryAccess = entryAccess;
  }

  /**
   * Prüft, ob Lookup-Callbacks momentan unterdrückt werden.
   *
   * @return {@code true}, wenn Callbacks unterdrückt sind
   */
  public boolean isSuppressLookupCallbacks() {
    return suppressLookupCallbacks;
  }

  /**
   * Verdrahtet die Interaktion für das Journal-Feld im JournalArticle-Formular.
   */
  public void wireLookupInteractions() {
    wireArticleJournalLookup();
  }

  private BibliographyLookupProvider lookupProvider() {
    BibliographyLookupProvider provider = lookupProviderSupplier.get();
    return provider == null ? BibliographyLookupProvider.NONE : provider;
  }

  /**
   * Führt eine Aktion mit temporär unterdrückten Lookup-Callbacks aus.
   *
   * @param action auszuführende Aktion
   */
  private void runSuppressed(Runnable action) {
    suppressLookupCallbacks = true;
    try {
      action.run();
    } finally {
      suppressLookupCallbacks = false;
    }
  }

  /**
   * Verdrahtet Listener für das Journal-Feld im JournalArticle-Formular.
   */
  private void wireArticleJournalLookup() {
    BibliographyFieldView journalField = fieldAccess.journalArticleJournalTitleField();
    if (journalField == null) {
      return;
    }

    journalField.getEditor().textProperty().addListener((obs, old, value) -> {
      if (suppressLookupCallbacks) {
        return;
      }

      if (journalWasAutoFilled) {
        journalAutoFillLocked = true;
        journalWasAutoFilled = false;
      }

      entryAccess.setJournalEntryId(null);
      showJournalSuggestions();

      if (!journalAutoFillLocked) {
        tryAutoResolveUniqueJournal();
      }
    });

    journalField.getEditor().focusedProperty().addListener((obs, old, focused) -> {
      if (!focused) {
        handleArticleJournalCommit();
      }
    });

    journalField.getEditor().setOnKeyPressed(event -> {
      if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.TAB) {
        handleArticleJournalCommit();
      } else if (event.getCode() == KeyCode.ESCAPE) {
        journalField.hideSuggestions();
      }
    });
  }

  /**
   * Zeigt Vorschläge für Zeitschriften im JournalArticle-Formular.
   */
  private void showJournalSuggestions() {
    BibliographyFieldView journalField = fieldAccess.journalArticleJournalTitleField();
    String query = fieldAccess.getJournalTitle().trim();

    if (query.length() < 3) {
      journalField.hideSuggestions();
      return;
    }

    List<TitleSuggestion> hits = lookupProvider().findTitles(TypeChoice.JOURNAL, query, List.of());
    if (hits.isEmpty()) {
      journalField.hideSuggestions();
      return;
    }

    List<CustomMenuItem> items = new ArrayList<>();
    for (TitleSuggestion hit : hits) {
      Label option = new Label(hit.displayText());
      CustomMenuItem item = new CustomMenuItem(option, true);
      item.setOnAction(e -> applyJournalSuggestion(hit));
      items.add(item);
    }

    journalField.showSuggestions(items);
  }

  /**
   * Übernimmt einen ausgewählten Journal-Vorschlag in das JournalArticle-Formular.
   *
   * @param hit gewählter Vorschlag
   */
  private void applyJournalSuggestion(TitleSuggestion hit) {
    runSuppressed(() -> {
      entryAccess.setJournalEntryId(hit.entryId());
      fieldAccess.setJournalTitle(hit.title());
      journalWasAutoFilled = false;
      journalAutoFillLocked = false;
    });
    fieldAccess.journalArticleJournalTitleField().hideSuggestions();
  }

  /**
   * Versucht, einen eindeutig passenden Journal-Titel automatisch aufzulösen.
   */
  private void tryAutoResolveUniqueJournal() {
    String query = fieldAccess.getJournalTitle().trim();
    if (query.length() < 3 || suppressLookupCallbacks) {
      return;
    }

    List<TitleSuggestion> hits = lookupProvider().findTitles(TypeChoice.JOURNAL, query, List.of());
    if (hits.size() == 1) {
      TitleSuggestion hit = hits.getFirst();
      runSuppressed(() -> {
        entryAccess.setJournalEntryId(hit.entryId());
        fieldAccess.setJournalTitle(hit.title());
        journalWasAutoFilled = true;
        journalAutoFillLocked = false;
      });
    }
  }

  /**
   * Reagiert auf Commit des Journal-Felds im JournalArticle-Formular.
   */
  private void handleArticleJournalCommit() {
    fieldAccess.journalArticleJournalTitleField().hideSuggestions();

    if (suppressLookupCallbacks) {
      return;
    }

    String text = fieldAccess.getJournalTitle().trim();
    if (text.isBlank()) {
      entryAccess.setJournalEntryId(null);
      journalAutoFillLocked = false;
      journalWasAutoFilled = false;
      return;
    }

    if (journalAutoFillLocked) {
      return;
    }

    TitleSuggestion exact = lookupProvider().resolveExactTitle(TypeChoice.JOURNAL, text, List.of());
    if (exact != null) {
      runSuppressed(() -> {
        entryAccess.setJournalEntryId(exact.entryId());
        fieldAccess.setJournalTitle(exact.title());
      });
      return;
    }

    entryAccess.setJournalEntryId(null);
    entryAccess.requestCreateJournal();
  }
}
