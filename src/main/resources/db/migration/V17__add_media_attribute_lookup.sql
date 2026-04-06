-- ============================================================
-- V17__add_media_attribute_lookup.sql
--
-- Ziel:
-- Einführung einer eigenständigen Lookup-Steuertabelle für die
-- dynamische Bibliographie-Architektur.
--
-- Diese Tabelle beschreibt, WIE ein Feld Lookup-Verhalten besitzt,
-- unabhängig davon, OB es im Medium identify / necessary / desired /
-- mandatory / forbidden ist.
--
-- Lookup-Strategien:
-- - identify_entry : sucht vorhandene bibliographische Einträge über
--                    ein Identifikationsfeld des gleichen Medientyps
-- - person         : sucht Personen-/Autorenvorschläge
-- - related_entry  : sucht bibliographische Zielmedien für Related-Felder
-- - value_list     : sucht wiederverwendbare freie Werte
-- - ai_provider    : sucht KI-Anbieter
-- - ai_model       : sucht KI-Modelle
-- - none           : keine aktive Lookup-Logik
-- ============================================================

CREATE TABLE IF NOT EXISTS media_attribute_lookup (
media_types_id        INTEGER NOT NULL,
bibtex_type_id        INTEGER NOT NULL,
lookup_strategy       TEXT NOT NULL
  CHECK (lookup_strategy IN (
    'identify_entry',
    'person',
    'related_entry',
    'value_list',
    'ai_provider',
    'ai_model',
    'none'
)),
min_query_length      INTEGER NOT NULL DEFAULT 1
    CHECK (min_query_length >= 0),
auto_resolve_unique   INTEGER NOT NULL DEFAULT 1
    CHECK (auto_resolve_unique IN (0, 1)),
allow_free_text       INTEGER NOT NULL DEFAULT 0
    CHECK (allow_free_text IN (0, 1)),
PRIMARY KEY (media_types_id, bibtex_type_id),
FOREIGN KEY (media_types_id, bibtex_type_id)
    REFERENCES media_attributes(media_types_id, bibtex_type_id)
    ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_media_attribute_lookup_strategy
    ON media_attribute_lookup(lookup_strategy);

CREATE INDEX IF NOT EXISTS idx_media_attribute_lookup_bibtex_type_id
    ON media_attribute_lookup(bibtex_type_id);

-- ============================================================
-- 1) Personenfelder
-- Alle Person-Felder erhalten generisch die Strategie "person".
-- Das gilt medientypübergreifend und bleibt dadurch auch bei neu
-- ergänzten Personenfeldern flexibel.
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
    'person',
    1,
    1,
    0
FROM media_attributes ma
         JOIN bibtex_type bt
              ON bt.id = ma.bibtex_type_id
WHERE LOWER(TRIM(bt.datatype)) = LOWER(TRIM('Person'));

-- ============================================================
-- 2) Related-Felder
-- Alle Related-Felder erhalten generisch die Strategie "related_entry".
-- Die spätere Laufzeitlogik nutzt bevorzugt related_media_type.
-- Existiert dort kein Eintrag, fällt sie auf eine globale Suche mit
-- Anzeigeattribut "title" zurück.
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
    'related_entry',
    1,
    1,
    0
FROM media_attributes ma
         JOIN bibtex_type bt
              ON bt.id = ma.bibtex_type_id
WHERE LOWER(TRIM(bt.datatype)) = LOWER(TRIM('Anderer Eintrag/Integer'));

-- ============================================================
-- 3) Identifikationsfelder normaler Einträge
-- Identify-Felder sollen bestehende Einträge des gleichen Medientyps
-- finden können, aber nur dann, wenn sie weder Person- noch Related-
-- Felder und auch keine KI-Tabellenfelder sind.
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
    'identify_entry',
    1,
    1,
    CASE
        WHEN LOWER(TRIM(bt.bibtex_name)) = LOWER(TRIM('url')) THEN 1
        ELSE 0
        END
FROM media_attributes ma
         JOIN bibtex_type bt
              ON bt.id = ma.bibtex_type_id
WHERE LOWER(TRIM(ma.mode)) = LOWER(TRIM('identify'))
  AND LOWER(TRIM(bt.datatype)) NOT IN (
         LOWER(TRIM('Person')),
         LOWER(TRIM('Anderer Eintrag/Integer')),
         LOWER(TRIM('KI-Tabelle'))
    );

-- ============================================================
-- 4) Wiederverwendbare freie Wertelisten
-- Zunächst bewusst konservativ nur für "series".
-- Weitere Felder können später rein metadatengetrieben ergänzt werden.
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
         JOIN bibtex_type bt
              ON bt.id = ma.bibtex_type_id
WHERE LOWER(TRIM(bt.bibtex_name)) = LOWER(TRIM('series'));

-- ============================================================
-- 5) KI-Felder
-- KI-Anbieter und KI-Modelle bleiben eigene Strategien, damit diese
-- später unabhängig von normalen Textfeldern behandelt werden können.
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
    'ai_provider',
    0,
    0,
    1
FROM media_attributes ma
         JOIN bibtex_type bt
              ON bt.id = ma.bibtex_type_id
WHERE LOWER(TRIM(bt.bibtex_name)) = LOWER(TRIM('ai_provider'));

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
    'ai_model',
    0,
    0,
    1
FROM media_attributes ma
         JOIN bibtex_type bt
              ON bt.id = ma.bibtex_type_id
WHERE LOWER(TRIM(bt.bibtex_name)) = LOWER(TRIM('ai_model'));