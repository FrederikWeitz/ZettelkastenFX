-- ============================================================
-- V13__fix_issue_mapping.sql
--
-- Korrigiert das fehlerhafte Mapping von JOURNAL_ARTICLE.issue:
-- In V12 wurde issue versehentlich nach bibliography_entries_string
-- geschrieben. Künftig gehört issue nach bibliography_entries_integer.
--
-- Vorgehen:
-- 1. Falsche issue-Einträge für JOURNAL_ARTICLE aus
--    bibliography_entries_string entfernen
-- 2. issue für JOURNAL_ARTICLE korrekt nach
--    bibliography_entries_integer schreiben
--
-- Es wird nichts an alten Quelltabellen gelöscht.
-- ============================================================

-- ------------------------------------------------------------
-- 1. Falsch gemappte issue-Werte aus bibliography_entries_string
--    entfernen, aber nur für JOURNAL_ARTICLE
-- ------------------------------------------------------------
DELETE FROM bibliography_entries_string
WHERE bibtex_type_id = (
    SELECT id
    FROM bibtex_type
    WHERE bibtex_name = 'issue'
)
  AND entries_id IN (
    SELECT be.id
    FROM bibliography_entries be
    WHERE be.type = 'JOURNAL_ARTICLE'
);

-- ------------------------------------------------------------
-- 2. issue korrekt nach bibliography_entries_integer schreiben
--    Nur rein numerische Werte werden übernommen.
--    Leere oder nicht numerische Werte werden als NULL eingetragen.
-- ------------------------------------------------------------
INSERT OR REPLACE INTO bibliography_entries_integer (entries_id, bibtex_type_id, entry)
SELECT bja.id,
       (
           SELECT id
           FROM bibtex_type
           WHERE bibtex_name = 'issue'
       ),
       CASE
           WHEN TRIM(COALESCE(bja.issue, '')) <> ''
               AND TRIM(bja.issue) NOT GLOB '*[^0-9]*'
               THEN CAST(TRIM(bja.issue) AS INTEGER)
           ELSE NULL
           END
FROM bibliography_journal_article bja
         JOIN bibliography_entries be
              ON be.id = bja.id
WHERE be.type = 'JOURNAL_ARTICLE';
