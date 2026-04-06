-- ============================================================
-- V16__person_group_mapping_l.sql
--
-- Schritt l:
-- author_mapping wird semantisch auf Personenlisten umgestellt.
--
-- Ziel:
-- - author_mapping.entry_id heißt künftig person_group_id
-- - bibliography_entries_integer verweist für Person-Felder
--   auf diese Personenlisten
-- - vorhandene Altdaten bleiben erhalten
--
-- WICHTIG:
-- - Es wird nichts fachlich gelöscht.
-- - Alte Personenlisten bleiben bestehen.
-- - Für bestehende Altbestände wird zunächst das Feld 'author'
--   sauber in bibliography_entries_integer gespiegelt.
-- - Weitere Person-Felder (z. B. editor) werden danach in der
--   Programmlogik feldspezifisch neu gespeichert.
-- ============================================================

-- ------------------------------------------------------------
-- 1. author_mapping mit korrekter Semantik neu aufbauen
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS author_mapping_new (
                                                  person_group_id INTEGER NOT NULL,
                                                  author_id       INTEGER NOT NULL,
                                                  author_pos      INTEGER NOT NULL,
                                                  PRIMARY KEY (person_group_id, author_id),
                                                  FOREIGN KEY (author_id) REFERENCES authors(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_author_mapping_new_group_pos
    ON author_mapping_new(person_group_id, author_pos);

CREATE INDEX IF NOT EXISTS idx_author_mapping_new_author
    ON author_mapping_new(author_id);

-- Bestehende Daten 1:1 übernehmen
INSERT INTO author_mapping_new (person_group_id, author_id, author_pos)
SELECT entry_id, author_id, author_pos
FROM author_mapping;

DROP TABLE author_mapping;
ALTER TABLE author_mapping_new RENAME TO author_mapping;

CREATE UNIQUE INDEX IF NOT EXISTS ux_author_mapping_group_pos
    ON author_mapping(person_group_id, author_pos);

CREATE INDEX IF NOT EXISTS idx_author_mapping_author
    ON author_mapping(author_id);

-- ------------------------------------------------------------
-- 2. Alte Autorenlisten in bibliography_entries_integer spiegeln
--    Nur dann, wenn noch kein Person-Feld 'author' existiert
-- ------------------------------------------------------------
INSERT OR IGNORE INTO bibliography_entries_integer (entries_id, bibtex_type_id, entry)
SELECT be.id,
       (
           SELECT bt.id
           FROM bibtex_type bt
           WHERE bt.bibtex_name = 'author'
       ),
       be.id
FROM bibliography_entries be
WHERE EXISTS (
    SELECT 1
    FROM author_mapping am
    WHERE am.person_group_id = be.id
)
  AND NOT EXISTS (
    SELECT 1
    FROM bibliography_entries_integer bei
    WHERE bei.entries_id = be.id
      AND bei.bibtex_type_id = (
        SELECT bt.id
        FROM bibtex_type bt
        WHERE bt.bibtex_name = 'author'
    )
);
