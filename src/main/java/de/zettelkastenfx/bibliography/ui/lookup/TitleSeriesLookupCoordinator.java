package de.zettelkastenfx.bibliography.ui.lookup;

import de.zettelkastenfx.bibliography.model.*;
import de.zettelkastenfx.bibliography.ui.TypeChoice;
import de.zettelkastenfx.bibliography.ui.support.BibliographyFieldView;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class TitleSeriesLookupCoordinator {

  private final Supplier<BibliographyLookupProvider> lookupProviderSupplier;

  private final LookupStateAccess stateAccess;
  private final LookupFieldAccess fieldAccess;
  private final LookupEntryAccess entryAccess;

  @Getter
  private boolean suppressLookupCallbacks;

  public TitleSeriesLookupCoordinator(
      Supplier<BibliographyLookupProvider> lookupProviderSupplier,
      LookupStateAccess stateAccess,
      LookupFieldAccess fieldAccess,
      LookupEntryAccess entryAccess
  ) {
    this.lookupProviderSupplier = lookupProviderSupplier;
    this.stateAccess = stateAccess;
    this.fieldAccess = fieldAccess;
    this.entryAccess = entryAccess;
  }

  public void wireLookupInteractions() {
    wireTitleLookup(TypeChoice.BOOK, fieldAccess.bookTitleField());
    wireTitleLookup(TypeChoice.COLLECTION, fieldAccess.collectionTitleField());
    wireTitleLookup(TypeChoice.ARTICLE_IN_COLLECTION, fieldAccess.articleTitleField());
    wireTitleLookup(TypeChoice.JOURNAL, fieldAccess.journalTitleField());
    wireTitleLookup(TypeChoice.JOURNAL_ARTICLE, fieldAccess.journalArticleTitleField());
    wireTitleLookup(TypeChoice.INTERNET, fieldAccess.internetTitleField());
    wireTitleLookup(TypeChoice.EDITION, fieldAccess.editionTitleField());

    wireSeriesLookup(TypeChoice.BOOK, fieldAccess.bookSeriesField());
    wireSeriesLookup(TypeChoice.COLLECTION, fieldAccess.collectionSeriesField());
  }

  private void wireTitleLookup(TypeChoice type, BibliographyFieldView field) {
    if (field == null) {
      return;
    }

    field.getEditor().textProperty().addListener((obs, old, value) -> {
      if (suppressLookupCallbacks) {
        return;
      }

      if (wasAutoFilled(type)) {
        stateAccess.setAutoFillLocked(type, true);
        stateAccess.setAutoFilled(type, false);
      }

      stateAccess.setSelectedEntryId(type, null);
      showTitleSuggestions(type, field);
    });

    field.getEditor().focusedProperty().addListener((obs, old, focused) -> {
      if (!focused) {
        handleTitleCommit(type, field);
      }
    });

    field.getEditor().setOnKeyPressed(event -> {
      if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.TAB) {
        handleTitleCommit(type, field);
      } else if (event.getCode() == KeyCode.ESCAPE) {
        field.hideSuggestions();
      }
    });
  }

  private void wireSeriesLookup(TypeChoice type, BibliographyFieldView field) {
    if (field == null) {
      return;
    }

    field.getEditor().textProperty().addListener((obs, old, value) -> {
      if (!suppressLookupCallbacks) {
        showSeriesSuggestions(type, field);
      }
    });

    field.getEditor().setOnKeyPressed(event -> {
      if (event.getCode() == KeyCode.ESCAPE) {
        field.hideSuggestions();
      }
    });
  }

  private void showSeriesSuggestions(TypeChoice type, BibliographyFieldView field) {
    String query = field.getText();
    if (query.length() < 3) {
      field.hideSuggestions();
      return;
    }

    List<String> hits = lookupProvider().findSeries(type, query);
    if (hits.isEmpty()) {
      field.hideSuggestions();
      return;
    }

    List<CustomMenuItem> items = new ArrayList<>();
    for (String hit : hits) {
      Label option = new Label(hit);
      CustomMenuItem item = new CustomMenuItem(option, true);
      item.setOnAction(e -> applySeriesSuggestion(field, hit));
      items.add(item);
    }

    field.showSuggestions(items);
  }

  private void applySeriesSuggestion(BibliographyFieldView field, String hit) {
    runSuppressed(() -> field.setText(hit));
    field.hideSuggestions();
  }

  private void showTitleSuggestions(TypeChoice type, BibliographyFieldView field) {
    String query = field.getText();
    if (query.length() < 3) {
      field.hideSuggestions();
      return;
    }

    List<TitleSuggestion> hits = lookupProvider().findTitles(type, query, stateAccess.getAuthors(type));
    if (hits.isEmpty()) {
      field.hideSuggestions();
      return;
    }

    List<CustomMenuItem> items = new ArrayList<>();
    for (TitleSuggestion hit : hits) {
      Label option = new Label(hit.displayText());
      CustomMenuItem item = new CustomMenuItem(option, true);
      item.setOnAction(e -> {
        applyResolvedEntry(type, hit.entryId(), false);
        field.hideSuggestions();
      });
      items.add(item);
    }

    field.showSuggestions(items);
  }

  private void handleTitleCommit(TypeChoice type, BibliographyFieldView field) {
    field.hideSuggestions();

    if (suppressLookupCallbacks) {
      return;
    }

    String text = field.getText();
    if (text.isBlank()) {
      stateAccess.setSelectedEntryId(type, null);
      stateAccess.setAutoFillLocked(type, false);
      stateAccess.setAutoFilled(type, false);
      return;
    }

    if (isAutoFillLocked(type)) {
      stateAccess.setSelectedEntryId(type, null);
      return;
    }

    TitleSuggestion exact = lookupProvider().resolveExactTitle(type, text, stateAccess.getAuthors(type));
    if (exact != null) {
      applyResolvedEntry(type, exact.entryId(), false);
    } else {
      stateAccess.setSelectedEntryId(type, null);
    }
  }

  private void applyResolvedEntry(TypeChoice type, Integer entryId, boolean markAsAutoFill) {
    if (entryId == null || entryId <= 0) {
      return;
    }

    Object loaded = lookupProvider().loadEntry(type, entryId);
    if (loaded == null) {
      return;
    }

    runSuppressed(() -> {
      switch (type) {
        case BOOK -> {
          if (loaded instanceof BookEntry entry) {
            entryAccess.readBookEntry(entry);
          }
        }
        case COLLECTION -> {
          if (loaded instanceof CollectionEntry entry) {
            entryAccess.readCollectionEntry(entry);
          }
        }
        case ARTICLE_IN_COLLECTION -> {
          if (loaded instanceof ArticleInCollectionEntry entry) {
            entryAccess.readArticleEntry(entry);
          }
        }
        case JOURNAL -> {
          if (loaded instanceof JournalEntry entry) {
            entryAccess.readJournalEntry(entry);
          }
        }
        case JOURNAL_ARTICLE -> {
          if (loaded instanceof JournalArticleEntry entry) {
            entryAccess.readJournalArticleEntry(entry);
          }
        }
        case INTERNET -> {
          if (loaded instanceof InternetEntry entry) {
            entryAccess.readInternetEntry(entry);
          }
        }
        case EDITION -> {
          if (loaded instanceof EditionEntry entry) {
            entryAccess.readEditionEntry(entry);
          }
        }
        default -> {
        }
      }

      stateAccess.setSelectedEntryId(type, entryId);
      stateAccess.setSourceEntryId(type, entryId);
      stateAccess.setAutoFilled(type, markAsAutoFill);
      stateAccess.setAutoFillLocked(type, false);
    });
  }

  public void handleAuthorsEdited(TypeChoice type) {
    if (suppressLookupCallbacks) {
      return;
    }

    stateAccess.setSelectedEntryId(type, null);

    if (stateAccess.isAutoFillLocked(type)) {
      return;
    }

    TitleSuggestion unique = lookupProvider().resolveUniqueTitleForAuthors(type, stateAccess.getAuthors(type));
    if (unique != null) {
      applyResolvedEntry(type, unique.entryId(), true);
    }
  }

  private BibliographyLookupProvider lookupProvider() {
    BibliographyLookupProvider provider = lookupProviderSupplier.get();
    return provider == null ? BibliographyLookupProvider.NONE : provider;
  }

  private boolean isAutoFillLocked(TypeChoice type) {
    return stateAccess.isAutoFillLocked(type);
  }

  private boolean wasAutoFilled(TypeChoice type) {
    return stateAccess.wasAutoFilled(type);
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
}
