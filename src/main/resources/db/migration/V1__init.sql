-- Notes
CREATE TABLE IF NOT EXISTS notes (
id                INTEGER PRIMARY KEY,
title             TEXT    NOT NULL,
content_blob      BLOB    NOT NULL,
content_codec     TEXT    NOT NULL DEFAULT 'rtfx-gzip', -- später erweiterbar

bibliography_type TEXT    NOT NULL DEFAULT 'USER',
bibliography_ref_id INTEGER,

created_at        TEXT    NOT NULL,
updated_at        TEXT
);

-- Keywords (global)
CREATE TABLE IF NOT EXISTS keywords (
id       INTEGER PRIMARY KEY,
keyword  TEXT NOT NULL UNIQUE
);

-- Note <-> Keywords
CREATE TABLE IF NOT EXISTS note_keywords (
note_id     INTEGER NOT NULL,
keyword_id  INTEGER NOT NULL,
PRIMARY KEY(note_id, keyword_id),
FOREIGN KEY(note_id) REFERENCES notes(id) ON DELETE CASCADE,
FOREIGN KEY(keyword_id) REFERENCES keywords(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_note_keywords_keyword_id ON note_keywords(keyword_id);

-- Follow-up links (Folgezettel)
CREATE TABLE IF NOT EXISTS note_links (
from_note_id INTEGER NOT NULL,
to_note_id   INTEGER NOT NULL,
link_type    TEXT    NOT NULL DEFAULT 'UNSPECIFIED',
PRIMARY KEY(from_note_id, to_note_id),
FOREIGN KEY(from_note_id) REFERENCES notes(id) ON DELETE CASCADE
-- to_note_id absichtlich ohne FK: Zettelnummern dürfen “schon” eingetragen werden
);

-- Projects
CREATE TABLE IF NOT EXISTS projects (
id      INTEGER PRIMARY KEY,
name    TEXT NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS note_projects (
note_id    INTEGER NOT NULL,
project_id INTEGER NOT NULL,
PRIMARY KEY(note_id, project_id),
FOREIGN KEY(note_id) REFERENCES notes(id) ON DELETE CASCADE,
FOREIGN KEY(project_id) REFERENCES projects(id) ON DELETE CASCADE
);
