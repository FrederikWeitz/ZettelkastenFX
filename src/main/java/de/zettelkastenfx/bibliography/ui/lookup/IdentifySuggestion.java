package de.zettelkastenfx.bibliography.ui.lookup;

import de.zettelkastenfx.bibliography.model.Author;

import java.util.List;

/**
 * Generischer Lookup-Treffer für ein {@code identify_entry}-Feld.
 *
 * @param entryId Datenbank-ID des gefundenen Eintrags
 * @param displayValue sichtbarer Feldwert des gesuchten Identify-Feldes
 * @param authors optionale Personeninformationen des Treffers
 */
public record IdentifySuggestion(
    Integer entryId,
    String displayValue,
    List<Author> authors
) {
}
