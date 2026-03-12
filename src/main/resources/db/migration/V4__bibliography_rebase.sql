-- 1) Globale Bibliographie-Basistabelle wieder einführen
CREATE TABLE IF NOT EXISTS bibliography_entries (
id         INTEGER PRIMARY KEY,
type       TEXT NOT NULL CHECK (
   type IN (
      'BOOK',
      'JOURNAL_ARTICLE',
      'COLLECTION',
      'ARTICLE_IN_COLLECTION',
      'JOURNAL',
      'JOURNAL_ARTICLE',
      'EDITION',
      'INTERNET',
      'AI'
   )
),
title      TEXT NOT NULL DEFAULT '',
created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
updated_at TEXT
);

CREATE INDEX IF NOT EXISTS idx_bibliography_entries_type
    ON bibliography_entries(type);

-- 2) bibliography_book auf reine Detailtabelle zurückbauen
--    title und authors_id fliegen hier bewusst raus.
CREATE TABLE IF NOT EXISTS bibliography_book (
id            INTEGER PRIMARY KEY,
subtitle      TEXT,
publisher     TEXT,
place         TEXT NOT NULL DEFAULT '',
year          TEXT NOT NULL DEFAULT '',
isbn          TEXT NOT NULL DEFAULT '',
edition       TEXT,
series        TEXT,
series_number TEXT,
FOREIGN KEY (id) REFERENCES bibliography_entries(id) ON DELETE CASCADE
);

-- 3) author_mapping neu aufbauen, diesmal mit sauberem FK auf bibliography_entries
CREATE TABLE IF NOT EXISTS author_mapping (
entry_id   INTEGER NOT NULL,
author_id  INTEGER NOT NULL,
author_pos INTEGER NOT NULL,
PRIMARY KEY (entry_id, author_id),
FOREIGN KEY (entry_id) REFERENCES bibliography_entries(id) ON DELETE CASCADE,
FOREIGN KEY (author_id) REFERENCES authors(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_bea_entry_pos
    ON author_mapping(entry_id, author_pos);

CREATE INDEX IF NOT EXISTS idx_bea_author
    ON author_mapping(author_id);

CREATE INDEX IF NOT EXISTS idx_bea_entry
    ON author_mapping(entry_id);

