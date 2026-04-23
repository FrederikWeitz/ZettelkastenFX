-- ============================================================
-- V18__drop_legacy_bibliography_tables_and_type_columns.sql
--
-- Schritt 7a:
-- Abschluss der Altbereinigung nach Umstellung auf das
-- dynamische Bibliographie-Modell.
--
-- Ziel:
-- - notes.bibliography_type entfernen
-- - bibliography_entries.type entfernen
-- - alte typgebundene Bibliographie-Detailtabellen entfernen
--
-- WICHTIG:
-- - Diese Migration setzt voraus, dass die Java-Seite bereits
--   nicht mehr auf notes.bibliography_type bzw.
--   bibliography_entries.type schreibt.
-- - bibliography_entries.title bleibt als technischer Titelfallback erhalten.
-- - bibliography_entries.media_types_id bleibt maßgeblich.
-- ============================================================

-- ------------------------------------------------------------
-- 1) notes ohne bibliography_type neu aufbauen
-- ------------------------------------------------------------
CREATE TABLE notes_new (
id                 INTEGER PRIMARY KEY,
title              TEXT    NOT NULL,
content_blob       BLOB    NOT NULL,
content_codec      TEXT    NOT NULL DEFAULT 'rtfx-gzip',
bibliography_ref_id INTEGER,
created_at         TEXT    NOT NULL,
updated_at         TEXT
);

INSERT INTO notes_new (
    id,
    title,
    content_blob,
    content_codec,
    bibliography_ref_id,
    created_at,
    updated_at
)
SELECT
    id,
    title,
    content_blob,
    content_codec,
    bibliography_ref_id,
    created_at,
    updated_at
FROM notes;

DROP TABLE notes;
ALTER TABLE notes_new RENAME TO notes;

-- ------------------------------------------------------------
-- 2) bibliography_entries ohne type neu aufbauen
-- ------------------------------------------------------------
CREATE TABLE bibliography_entries_new (
id             INTEGER PRIMARY KEY AUTOINCREMENT,
title          TEXT NOT NULL,
created_at     TEXT NOT NULL,
updated_at     TEXT,
citekey        TEXT,
media_types_id INTEGER REFERENCES media_types(id)
);

INSERT INTO bibliography_entries_new (
    id,
    title,
    created_at,
    updated_at,
    citekey,
    media_types_id
)
SELECT
    id,
    title,
    created_at,
    updated_at,
    citekey,
    media_types_id
FROM bibliography_entries;

DROP TABLE bibliography_entries;
ALTER TABLE bibliography_entries_new RENAME TO bibliography_entries;

CREATE INDEX IF NOT EXISTS idx_bibliography_entries_citekey
    ON bibliography_entries(citekey);

CREATE INDEX IF NOT EXISTS idx_bibliography_entries_media_types_id
    ON bibliography_entries(media_types_id);

-- ------------------------------------------------------------
-- 3) Alte Bibliographie-Detailtabellen entfernen
-- ------------------------------------------------------------
DROP TABLE IF EXISTS bibliography_ai;
DROP TABLE IF EXISTS bibliography_article_in_collection;
DROP TABLE IF EXISTS bibliography_book;
DROP TABLE IF EXISTS bibliography_collection;
DROP TABLE IF EXISTS bibliography_edition;
DROP TABLE IF EXISTS bibliography_internet;
DROP TABLE IF EXISTS bibliography_journal;
DROP TABLE IF EXISTS bibliography_journal_article;
DROP TABLE IF EXISTS bibliography_thesis;

