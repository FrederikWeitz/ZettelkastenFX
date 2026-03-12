-- Autoren (global, wiederverwendbar)
CREATE TABLE IF NOT EXISTS authors (
id         INTEGER PRIMARY KEY,
first_name TEXT NOT NULL,
last_name  TEXT NOT NULL
);

-- m:n Entry <-> Author inkl. Reihenfolge
CREATE TABLE IF NOT EXISTS author_mapping (
entry_id   INTEGER NOT NULL,
author_id  INTEGER NOT NULL,
author_pos INTEGER NOT NULL,            -- 1..n, Reihenfolge der Autoren
PRIMARY KEY(entry_id, author_id),
FOREIGN KEY(author_id) REFERENCES authors(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_bea_entry_pos
    ON author_mapping(entry_id, author_pos);

CREATE INDEX IF NOT EXISTS idx_bea_author
    ON author_mapping(author_id);

CREATE UNIQUE INDEX IF NOT EXISTS ux_bibliography_authors_name
    ON authors(first_name, last_name);
