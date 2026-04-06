package de.zettelkastenfx.bibliography.model;

/**
 * Beschreibt die Metadaten eines Related-Feldes aus der Tabelle
 * {@code related_media_type}.
 *
 * @param bibtexTypeId Datenbank-ID des Related-Feldes
 * @param mediaTypeId Datenbank-ID des erlaubten Ziel-Medientyps
 * @param mediaAttributeBibtexTypeId Datenbank-ID des Anzeigeattributs des
 *                                   Ziel-Medientyps
 * @param relatedFieldDefinition Definition des Related-Feldes
 * @param targetMediaTypeDefinition Definition des Ziel-Medientyps
 * @param displayFieldDefinition Definition des Anzeigeattributs im Zielmedium
 */
public record RelatedMediaDefinition(
    int bibtexTypeId,
    int mediaTypeId,
    int mediaAttributeBibtexTypeId,
    BibtexFieldDefinition relatedFieldDefinition,
    MediaTypeDefinition targetMediaTypeDefinition,
    BibtexFieldDefinition displayFieldDefinition
) {
}