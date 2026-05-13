CREATE TABLE IF NOT EXISTS notes_web (
id                  INTEGER PRIMARY KEY,
title               TEXT    NOT NULL,
content_text        TEXT    NOT NULL,
content_codec       TEXT    NOT NULL DEFAULT 'html-body',
bibliography_ref_id INTEGER,
created_at          TEXT    NOT NULL,
updated_at          TEXT
);
