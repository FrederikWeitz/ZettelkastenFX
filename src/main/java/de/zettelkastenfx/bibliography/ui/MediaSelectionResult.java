package de.zettelkastenfx.bibliography.ui;

/**
 * Ergebnis einer Medienaktion aus dem Bibliographie-Popup.
 *
 * @param entryId ID des gewählten oder gespeicherten Bibliographie-Eintrags
 * @param displayText Anzeigeinformation für die Zettelwelt
 */
public record MediaSelectionResult(Integer entryId, String displayText) {
}