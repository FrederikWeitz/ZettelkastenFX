-- JOURNAL
CREATE TABLE IF NOT EXISTS bibliography_journal (
id          INTEGER PRIMARY KEY,
subtitle    TEXT,
issn        TEXT NOT NULL DEFAULT '',
publisher   TEXT NOT NULL DEFAULT '',
place       TEXT NOT NULL DEFAULT '',
start_year  TEXT NOT NULL DEFAULT '',
end_year    TEXT NOT NULL DEFAULT '',
note        TEXT NOT NULL DEFAULT ''
);

-- JOURNAL ARTICLE: optionaler Verweis auf JournalEntry
ALTER TABLE bibliography_journal_article
    ADD COLUMN journal_entry_id INTEGER;

CREATE INDEX IF NOT EXISTS idx_bja_journal_entry_id
    ON bibliography_journal_article(journal_entry_id);
