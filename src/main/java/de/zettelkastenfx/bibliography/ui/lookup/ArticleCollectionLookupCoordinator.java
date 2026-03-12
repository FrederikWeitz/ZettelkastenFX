package de.zettelkastenfx.bibliography.ui.lookup;

import de.zettelkastenfx.bibliography.ui.TypeChoice;
import de.zettelkastenfx.bibliography.ui.support.BibliographyFieldView;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class ArticleCollectionLookupCoordinator {

  private final Supplier<BibliographyLookupProvider> lookupProviderSupplier;

  private final LookupFieldAccess fieldAccess;
  private final LookupEntryAccess entryAccess;

  @Getter
  private boolean suppressLookupCallbacks;
  private boolean collectionAutoFillLocked;
  private boolean collectionWasAutoFilled;

  public ArticleCollectionLookupCoordinator(
      Supplier<BibliographyLookupProvider> lookupProviderSupplier,
      LookupFieldAccess fieldAccess,
      LookupEntryAccess entryAccess
  ) {
    this.lookupProviderSupplier = lookupProviderSupplier;
    this.fieldAccess = fieldAccess;
    this.entryAccess = entryAccess;
  }

  public void wireLookupInteractions() {
    wireArticleCollectionLookup();
  }

  private void wireArticleCollectionLookup() {
    BibliographyFieldView articleCollectionTitleField = fieldAccess.articleCollectionTitleField();
    if (articleCollectionTitleField == null) {
      return;
    }

    articleCollectionTitleField.getEditor().textProperty().addListener((obs, old, value) -> {
      if (suppressLookupCallbacks) {
        return;
      }

      if (collectionWasAutoFilled) {
        collectionAutoFillLocked = true;
        collectionWasAutoFilled = false;
      }

      entryAccess.setCollectionEntryId(null);
      showCollectionSuggestions();

      if (!collectionAutoFillLocked) {
        tryAutoResolveUniqueCollection();
      }
    });

    articleCollectionTitleField.getEditor().focusedProperty().addListener((obs, old, focused) -> {
      if (!focused) {
        handleArticleCollectionCommit();
      }
    });

    articleCollectionTitleField.getEditor().setOnKeyPressed(event -> {
      if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.TAB) {
        handleArticleCollectionCommit();
      } else if (event.getCode() == KeyCode.ESCAPE) {
        articleCollectionTitleField.hideSuggestions();
      }
    });
  }

  private void showCollectionSuggestions() {
    String query = fieldAccess.getCollectionTitle().trim();
    if (query.length() < 3) {
      fieldAccess.articleCollectionTitleField().hideSuggestions();
      return;
    }

    List<TitleSuggestion> hits = lookupProvider().findTitles(TypeChoice.COLLECTION, query, List.of());
    if (hits.isEmpty()) {
      fieldAccess.articleCollectionTitleField().hideSuggestions();
      return;
    }

    List<CustomMenuItem> items = new ArrayList<>();
    for (TitleSuggestion hit : hits) {
      Label option = new Label(hit.displayText());
      CustomMenuItem item = new CustomMenuItem(option, true);
      item.setOnAction(e -> applyCollectionSuggestion(hit));
      items.add(item);
    }

    fieldAccess.articleCollectionTitleField().showSuggestions(items);
  }

  private void applyCollectionSuggestion(TitleSuggestion hit) {
    runSuppressed(() -> {
      entryAccess.setCollectionEntryId(hit.entryId());
      fieldAccess.setCollectionTitle(hit.title());
      collectionWasAutoFilled = false;
      collectionAutoFillLocked = false;
    });
    fieldAccess.articleCollectionTitleField().hideSuggestions();
  }

  private void tryAutoResolveUniqueCollection() {
    String query = fieldAccess.getCollectionTitle().trim();
    if (query.length() < 3 || suppressLookupCallbacks) {
      return;
    }

    List<TitleSuggestion> hits = lookupProvider().findTitles(TypeChoice.COLLECTION, query, List.of());
    if (hits.size() == 1) {
      TitleSuggestion hit = hits.getFirst();
      runSuppressed(() -> {
        entryAccess.setCollectionEntryId(hit.entryId());
        fieldAccess.setCollectionTitle(hit.title());
        collectionWasAutoFilled = true;
        collectionAutoFillLocked = false;
      });
    }
  }

  private void handleArticleCollectionCommit() {
    fieldAccess.articleCollectionTitleField().hideSuggestions();

    if (suppressLookupCallbacks) {
      return;
    }

    String text = fieldAccess.getCollectionTitle().trim();
    if (text.isBlank()) {
      entryAccess.setCollectionEntryId(null);
      collectionAutoFillLocked = false;
      collectionWasAutoFilled = false;
      return;
    }

    if (collectionAutoFillLocked) {
      return;
    }

    TitleSuggestion exact = lookupProvider().resolveExactTitle(TypeChoice.COLLECTION, text, List.of());
    if (exact != null) {
      runSuppressed(() -> {
        entryAccess.setCollectionEntryId(exact.entryId());
        fieldAccess.setCollectionTitle(exact.title());
      });
      return;
    }

    entryAccess.setCollectionEntryId(null);
    entryAccess.requestCreateCollection();
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
}
