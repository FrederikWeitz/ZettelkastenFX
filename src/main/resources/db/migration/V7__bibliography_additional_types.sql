-- ============================================================
-- Neue Bibliographietypen:
-- THESIS, INTERNET, EDITION, AI
--
-- WICHTIG:
-- Die CHECK-Constraint auf bibliography_entries.type muss hier
-- um die neuen Typen erweitert werden.
-- ============================================================

-- ------------------------------------------------------------
-- bibliography_entries mit erweiterter CHECK-Constraint neu aufbauen
-- ------------------------------------------------------------

CREATE TABLE bibliography_entries_new (
id          INTEGER PRIMARY KEY AUTOINCREMENT,
type        TEXT NOT NULL CHECK (
       type IN (
              'USER',
              'BOOK',
              'COLLECTION',
              'ARTICLE_IN_COLLECTION',
              'JOURNAL',
              'JOURNAL_ARTICLE',
              'THESIS',
              'INTERNET',
              'EDITION',
              'AI'
       )
),
title       TEXT NOT NULL,
created_at  TEXT NOT NULL,
updated_at  TEXT
);

INSERT INTO bibliography_entries_new (id, type, title, created_at, updated_at)
SELECT id, type, title, created_at, updated_at
FROM bibliography_entries;

DROP TABLE bibliography_entries;
ALTER TABLE bibliography_entries_new RENAME TO bibliography_entries;

-- ------------------------------------------------------------
-- THESIS
-- ------------------------------------------------------------

CREATE TABLE IF NOT EXISTS bibliography_thesis (
id           INTEGER PRIMARY KEY,
subtitle     TEXT NOT NULL DEFAULT '',
thesis_type  TEXT NOT NULL DEFAULT 'DISSERTATION',
university   TEXT NOT NULL DEFAULT '',
place        TEXT NOT NULL DEFAULT '',
year         TEXT NOT NULL DEFAULT '',
advisor      TEXT NOT NULL DEFAULT '',
series       TEXT NOT NULL DEFAULT '',
note         TEXT NOT NULL DEFAULT '',
FOREIGN KEY (id) REFERENCES bibliography_entries(id) ON DELETE CASCADE
);

-- ------------------------------------------------------------
-- INTERNET
-- ------------------------------------------------------------

CREATE TABLE IF NOT EXISTS bibliography_internet (
id           INTEGER PRIMARY KEY,
url          TEXT NOT NULL DEFAULT '',
site_name    TEXT NOT NULL DEFAULT '',
host_name    TEXT NOT NULL DEFAULT '',
published    TEXT NOT NULL DEFAULT '',
accessed_at  TEXT,
FOREIGN KEY (id) REFERENCES bibliography_entries(id) ON DELETE CASCADE
);

-- ------------------------------------------------------------
-- EDITION
-- ------------------------------------------------------------

CREATE TABLE IF NOT EXISTS bibliography_edition (
id          INTEGER PRIMARY KEY,
publisher   TEXT NOT NULL DEFAULT '',
place       TEXT NOT NULL DEFAULT '',
year        TEXT NOT NULL DEFAULT '',
series      TEXT NOT NULL DEFAULT '',
volume      TEXT NOT NULL DEFAULT '',
FOREIGN KEY (id) REFERENCES bibliography_entries(id) ON DELETE CASCADE
);

-- ------------------------------------------------------------
-- AI
-- ------------------------------------------------------------

CREATE TABLE IF NOT EXISTS bibliography_ai (
id            INTEGER PRIMARY KEY,
provider      TEXT NOT NULL DEFAULT '',
model         TEXT NOT NULL DEFAULT '',
context       TEXT NOT NULL DEFAULT '',
prompt_title  TEXT NOT NULL DEFAULT '',
used_at       TEXT,
FOREIGN KEY (id) REFERENCES bibliography_entries(id) ON DELETE CASCADE
);
