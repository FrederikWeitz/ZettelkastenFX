package de.zettelkastenfx.bibliography.ui.lookup;

import de.zettelkastenfx.bibliography.model.Author;

import java.util.List;

/**
 * Vorschlagsdatensatz für Titel-Lookups.
 *
 * @param entryId ID des Eintrags.
 * @param title Titel des Eintrags.
 * @param authors Autoren des Eintrags.
 */
public record TitleSuggestion(Integer entryId, String title, List<Author> authors) {

  /**
   * Liefert einen kompakten Anzeigetext für den Vorschlag.
   *
   * @return Titel mit optionalem Untertitel
   */
  public String displayText() {
    return title == null ? "" : title.trim();
  }
}
