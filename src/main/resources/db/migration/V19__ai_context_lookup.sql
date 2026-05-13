-- ============================================================
-- V19__ai_context_lookup.sql
--
-- Ziel:
-- ai_context als freie Werteliste fuer KI-Texte mit Dropdown-
-- Vorschlaegen aktivieren.
-- ============================================================

INSERT OR IGNORE INTO media_attribute_lookup (
    media_types_id,
    bibtex_type_id,
    lookup_strategy,
    min_query_length,
    auto_resolve_unique,
    allow_free_text
)
SELECT
    ma.media_types_id,
    ma.bibtex_type_id,
    'value_list',
    1,
    0,
    1
FROM media_attributes ma
         JOIN media_types mt ON mt.id = ma.media_types_id
         JOIN bibtex_type bt ON bt.id = ma.bibtex_type_id
WHERE LOWER(TRIM(mt.bib_name)) = LOWER(TRIM('aitext'))
  AND LOWER(TRIM(bt.bibtex_name)) = LOWER(TRIM('ai_context'));
