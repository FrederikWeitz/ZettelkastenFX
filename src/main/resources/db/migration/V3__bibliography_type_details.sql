-- JOURNAL ARTICLE
CREATE TABLE IF NOT EXISTS bibliography_journal_article (
id             INTEGER PRIMARY KEY,
subtitle       TEXT,
publisher      TEXT,
journal_title  TEXT NOT NULL DEFAULT '',
volume         TEXT NOT NULL DEFAULT '',
issue          TEXT NOT NULL DEFAULT '',
year           TEXT NOT NULL DEFAULT '',
pages          TEXT NOT NULL DEFAULT '',
doi            TEXT NOT NULL DEFAULT ''
);

-- COLLECTION
CREATE TABLE IF NOT EXISTS bibliography_collection (
id         INTEGER PRIMARY KEY,
subtitle   TEXT,
publisher  TEXT NOT NULL DEFAULT '',
place      TEXT NOT NULL DEFAULT '',
year       TEXT NOT NULL DEFAULT '',
series     TEXT
);

-- ARTICLE IN COLLECTION  (NEU: optionaler Verweis auf CollectionEntry)
CREATE TABLE IF NOT EXISTS bibliography_article_in_collection (
id                   INTEGER PRIMARY KEY,
subtitle             TEXT,
collection_entry_id  INTEGER,                 -- darf NULL sein
place                TEXT NOT NULL DEFAULT '',
year                 TEXT NOT NULL DEFAULT '',
pages                TEXT NOT NULL DEFAULT ''
);

CREATE INDEX IF NOT EXISTS idx_baic_collection_entry_id
    ON bibliography_article_in_collection(collection_entry_id);

-- EDITION
CREATE TABLE IF NOT EXISTS bibliography_edition (
id         INTEGER PRIMARY KEY,
subtitle   TEXT,
publisher  TEXT NOT NULL DEFAULT '',
place      TEXT NOT NULL DEFAULT '',
year       TEXT NOT NULL DEFAULT '',
series     TEXT,
volume     TEXT
);

-- INTERNET
CREATE TABLE IF NOT EXISTS bibliography_internet (
id           INTEGER PRIMARY KEY,
subtitle     TEXT,
url          TEXT NOT NULL DEFAULT '',
site_name    TEXT NOT NULL DEFAULT '',
published    TEXT NOT NULL DEFAULT '',
accessed_at  TEXT NOT NULL DEFAULT ''
);

-- AI
CREATE TABLE IF NOT EXISTS bibliography_ai (
id            INTEGER PRIMARY KEY,
provider      TEXT NOT NULL DEFAULT '',
model         TEXT NOT NULL DEFAULT '',
context       TEXT NOT NULL DEFAULT '',
prompt_title  TEXT NOT NULL DEFAULT '',
used_at       TEXT NOT NULL DEFAULT ''
);
