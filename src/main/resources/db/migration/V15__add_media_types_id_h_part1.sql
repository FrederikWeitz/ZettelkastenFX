-- ============================================================
-- V15__add_media_types_id_h_part1.sql
--
-- Schritt j:
-- bibliography_entries um media_types_id erweitern und vorhandene
-- alte Typen auf media_types(id) mappen.
--
-- WICHTIG:
-- - title bleibt erhalten
-- - type bleibt erhalten
-- - es wird nichts gelöscht
-- ============================================================

ALTER TABLE bibliography_entries
    ADD COLUMN media_types_id INTEGER
        REFERENCES media_types(id);

CREATE INDEX IF NOT EXISTS idx_bibliography_entries_media_types_id
    ON bibliography_entries(media_types_id);

UPDATE bibliography_entries
SET media_types_id = (
    SELECT mt.id
    FROM media_types mt
    WHERE mt.bib_name = CASE bibliography_entries.type
        WHEN 'COLLECTION' THEN 'collection'
        WHEN 'ARTICLE_IN_COLLECTION' THEN 'incollection'
        WHEN 'JOURNAL' THEN 'journal'
        WHEN 'JOURNAL_ARTICLE' THEN 'article'
        WHEN 'BOOK' THEN 'book'
        WHEN 'AI' THEN 'aitext'
        ELSE NULL
        END
)
WHERE type IN (
'COLLECTION',
'ARTICLE_IN_COLLECTION',
'JOURNAL',
'JOURNAL_ARTICLE',
'BOOK',
'AI'
);