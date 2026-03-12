-- ============================================================
-- bibliography_internet:
-- alte Tabelle ersetzen
-- site_name entfällt
-- host_name bleibt
-- accessed_at bleibt nullable
-- ============================================================

DROP TABLE IF EXISTS bibliography_internet;

CREATE TABLE bibliography_internet (
id           INTEGER PRIMARY KEY,
url          TEXT NOT NULL DEFAULT '',
host_name    TEXT NOT NULL DEFAULT '',
published    TEXT NOT NULL DEFAULT '',
accessed_at  TEXT NOT NULL DEFAULT '',
FOREIGN KEY (id) REFERENCES bibliography_entries(id) ON DELETE CASCADE
);

-- ============================================================
-- bibliography_ai:
-- used_at soll nullable sein
-- ============================================================

CREATE TABLE bibliography_ai_new (
id            INTEGER PRIMARY KEY,
provider      TEXT NOT NULL DEFAULT '',
model         TEXT NOT NULL DEFAULT '',
context       TEXT NOT NULL DEFAULT '',
prompt_title  TEXT NOT NULL DEFAULT '',
used_at       TEXT NOT NULL DEFAULT '',
FOREIGN KEY (id) REFERENCES bibliography_entries(id) ON DELETE CASCADE
);

INSERT INTO bibliography_ai_new (id, provider, model, context, prompt_title, used_at)
SELECT id, provider, model, context, prompt_title, used_at
FROM bibliography_ai;

DROP TABLE bibliography_ai;
ALTER TABLE bibliography_ai_new RENAME TO bibliography_ai;

-- ============================================================
-- bibliography_edition:
-- isbn und run ergänzen
-- ============================================================

ALTER TABLE bibliography_edition
ADD COLUMN isbn        TEXT NOT NULL DEFAULT '';

ALTER TABLE bibliography_edition
ADD COLUMN run         TEXT NOT NULL DEFAULT '';

-- ============================================================
-- bibliography_book:
-- edition -> run umbenennen
-- ============================================================

ALTER TABLE bibliography_book
RENAME COLUMN edition TO run;
