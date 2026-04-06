-- ============================================================
-- V12__bibliography_map_existing_entries_g.sql
-- Pflichtenheft Bibliographie, Schritt g)
--
-- Ziel:
-- Vorhandene Bibliographie-Einträge aus den alten Detailtabellen
-- in die neue BibTeX-nahe Struktur ummappen.
--
-- WICHTIG:
-- - Es wird nichts gelöscht.
-- - Alte Tabellen bleiben erhalten.
-- - author_mapping bleibt in diesem Schritt strukturell unverändert.
-- - Leere Zielzeilen sind ausdrücklich erlaubt.
-- - Integer-Felder werden nur vorsichtig übernommen:
--   rein numerisch -> CAST
--   sonst -> NULL
-- ============================================================

-- ------------------------------------------------------------
-- Hilfsregel:
-- Personenfelder (author/editor) bleiben in diesem Schritt
-- unverändert in author_mapping bestehen.
-- Die bisherige Verknüpfung über bibliography_entries.id bleibt
-- damit erhalten; die feinere Trennung wird später in der
-- Programmlogik bzw. in weiteren Schritten glattgezogen.
-- ------------------------------------------------------------

-- ============================================================
-- g1) article  (Quelle: bibliography_journal_article)
-- ============================================================

-- title
INSERT OR IGNORE INTO bibliography_entries_string (entries_id, bibtex_type_id, entry)
SELECT be.id,
       (SELECT id FROM bibtex_type WHERE bibtex_name = 'title'),
       NULLIF(TRIM(be.title), '')
FROM bibliography_entries be
WHERE be.type = 'JOURNAL_ARTICLE';

-- subtitle
INSERT OR IGNORE INTO bibliography_entries_string (entries_id, bibtex_type_id, entry)
SELECT bja.id,
       (SELECT id FROM bibtex_type WHERE bibtex_name = 'subtitle'),
       NULLIF(TRIM(bja.subtitle), '')
FROM bibliography_journal_article bja
         JOIN bibliography_entries be ON be.id = bja.id
WHERE be.type = 'JOURNAL_ARTICLE';

-- related_journal
-- Sonderfall:
-- journal_entry_id im Altbestand ist nicht verlässlich.
-- Daher Lookup über bibliography_entries.title = journal_title
-- und bibliography_entries.type = 'JOURNAL'.
INSERT OR IGNORE INTO bibliography_entries_integer (entries_id, bibtex_type_id, entry)
SELECT bja.id,
       (SELECT id FROM bibtex_type WHERE bibtex_name = 'related_journal'),
       (
           SELECT bej.id
           FROM bibliography_entries bej
           WHERE bej.type = 'JOURNAL'
             AND bej.title = bja.journal_title
           ORDER BY bej.id
           LIMIT 1
       )
FROM bibliography_journal_article bja
         JOIN bibliography_entries be ON be.id = bja.id
WHERE be.type = 'JOURNAL_ARTICLE';

-- volume
INSERT OR IGNORE INTO bibliography_entries_integer (entries_id, bibtex_type_id, entry)
SELECT bja.id,
       (SELECT id FROM bibtex_type WHERE bibtex_name = 'volume'),
       CASE
           WHEN TRIM(COALESCE(bja.volume, '')) <> ''
               AND TRIM(bja.volume) NOT GLOB '*[^0-9]*'
               THEN CAST(TRIM(bja.volume) AS INTEGER)
           ELSE NULL
           END
FROM bibliography_journal_article bja
         JOIN bibliography_entries be ON be.id = bja.id
WHERE be.type = 'JOURNAL_ARTICLE';

-- issue
INSERT OR IGNORE INTO bibliography_entries_string (entries_id, bibtex_type_id, entry)
SELECT bja.id,
       (SELECT id FROM bibtex_type WHERE bibtex_name = 'issue'),
        CASE
           WHEN TRIM(COALESCE(bja.issue, '')) <> ''
               AND TRIM(bja.issue) NOT GLOB '*[^0-9]*'
               THEN CAST(TRIM(bja.issue) AS INTEGER)
           ELSE NULL
           END
FROM bibliography_journal_article bja
         JOIN bibliography_entries be ON be.id = bja.id
WHERE be.type = 'JOURNAL_ARTICLE';

-- year
INSERT OR IGNORE INTO bibliography_entries_integer (entries_id, bibtex_type_id, entry)
SELECT bja.id,
       (SELECT id FROM bibtex_type WHERE bibtex_name = 'year'),
       CASE
           WHEN TRIM(COALESCE(bja.year, '')) <> ''
               AND TRIM(bja.year) NOT GLOB '*[^0-9]*'
               THEN CAST(TRIM(bja.year) AS INTEGER)
           ELSE NULL
           END
FROM bibliography_journal_article bja
         JOIN bibliography_entries be ON be.id = bja.id
WHERE be.type = 'JOURNAL_ARTICLE';

-- pages
INSERT OR IGNORE INTO bibliography_entries_string (entries_id, bibtex_type_id, entry)
SELECT bja.id,
       (SELECT id FROM bibtex_type WHERE bibtex_name = 'pages'),
       NULLIF(TRIM(bja.pages), '')
FROM bibliography_journal_article bja
         JOIN bibliography_entries be ON be.id = bja.id
WHERE be.type = 'JOURNAL_ARTICLE';

-- doi
INSERT OR IGNORE INTO bibliography_entries_string (entries_id, bibtex_type_id, entry)
SELECT bja.id,
       (SELECT id FROM bibtex_type WHERE bibtex_name = 'doi'),
       NULLIF(TRIM(bja.doi), '')
FROM bibliography_journal_article bja
         JOIN bibliography_entries be ON be.id = bja.id
WHERE be.type = 'JOURNAL_ARTICLE';

-- ============================================================
-- g2) book  (Quelle: bibliography_book)
-- ============================================================

-- title
INSERT OR IGNORE INTO bibliography_entries_string (entries_id, bibtex_type_id, entry)
SELECT be.id,
       (SELECT id FROM bibtex_type WHERE bibtex_name = 'title'),
       NULLIF(TRIM(be.title), '')
FROM bibliography_entries be
WHERE be.type = 'BOOK';

-- subtitle
INSERT OR IGNORE INTO bibliography_entries_string (entries_id, bibtex_type_id, entry)
SELECT bb.id,
       (SELECT id FROM bibtex_type WHERE bibtex_name = 'subtitle'),
       NULLIF(TRIM(bb.subtitle), '')
FROM bibliography_book bb
         JOIN bibliography_entries be ON be.id = bb.id
WHERE be.type = 'BOOK';

-- publisher
INSERT OR IGNORE INTO bibliography_entries_string (entries_id, bibtex_type_id, entry)
SELECT bb.id,
       (SELECT id FROM bibtex_type WHERE bibtex_name = 'publisher'),
       NULLIF(TRIM(bb.publisher), '')
FROM bibliography_book bb
         JOIN bibliography_entries be ON be.id = bb.id
WHERE be.type = 'BOOK';

-- location  (alte Quelle: place)
INSERT OR IGNORE INTO bibliography_entries_string (entries_id, bibtex_type_id, entry)
SELECT bb.id,
       (SELECT id FROM bibtex_type WHERE bibtex_name = 'location'),
       NULLIF(TRIM(bb.place), '')
FROM bibliography_book bb
         JOIN bibliography_entries be ON be.id = bb.id
WHERE be.type = 'BOOK';

-- year
INSERT OR IGNORE INTO bibliography_entries_integer (entries_id, bibtex_type_id, entry)
SELECT bb.id,
       (SELECT id FROM bibtex_type WHERE bibtex_name = 'year'),
       CASE
           WHEN TRIM(COALESCE(bb.year, '')) <> ''
               AND TRIM(bb.year) NOT GLOB '*[^0-9]*'
               THEN CAST(TRIM(bb.year) AS INTEGER)
           ELSE NULL
           END
FROM bibliography_book bb
         JOIN bibliography_entries be ON be.id = bb.id
WHERE be.type = 'BOOK';

-- edition  (alte Quelle: run)
INSERT OR IGNORE INTO bibliography_entries_integer (entries_id, bibtex_type_id, entry)
SELECT bb.id,
       (SELECT id FROM bibtex_type WHERE bibtex_name = 'edition'),
       CASE
           WHEN TRIM(COALESCE(bb.run, '')) <> ''
               AND TRIM(bb.run) NOT GLOB '*[^0-9]*'
               THEN CAST(TRIM(bb.run) AS INTEGER)
           ELSE NULL
           END
FROM bibliography_book bb
         JOIN bibliography_entries be ON be.id = bb.id
WHERE be.type = 'BOOK';

-- isbn
INSERT OR IGNORE INTO bibliography_entries_string (entries_id, bibtex_type_id, entry)
SELECT bb.id,
       (SELECT id FROM bibtex_type WHERE bibtex_name = 'isbn'),
       NULLIF(TRIM(bb.isbn), '')
FROM bibliography_book bb
         JOIN bibliography_entries be ON be.id = bb.id
WHERE be.type = 'BOOK';

-- series
INSERT OR IGNORE INTO bibliography_entries_string (entries_id, bibtex_type_id, entry)
SELECT bb.id,
       (SELECT id FROM bibtex_type WHERE bibtex_name = 'series'),
       NULLIF(TRIM(bb.series), '')
FROM bibliography_book bb
         JOIN bibliography_entries be ON be.id = bb.id
WHERE be.type = 'BOOK';

-- volume  (alte Quelle: series_number)
INSERT OR IGNORE INTO bibliography_entries_integer (entries_id, bibtex_type_id, entry)
SELECT bb.id,
       (SELECT id FROM bibtex_type WHERE bibtex_name = 'volume'),
       CASE
           WHEN TRIM(COALESCE(bb.series_number, '')) <> ''
               AND TRIM(bb.series_number) NOT GLOB '*[^0-9]*'
               THEN CAST(TRIM(bb.series_number) AS INTEGER)
           ELSE NULL
           END
FROM bibliography_book bb
         JOIN bibliography_entries be ON be.id = bb.id
WHERE be.type = 'BOOK';

-- ============================================================
-- g3) incollection  (Quelle: bibliography_article_in_collection)
-- ============================================================

-- title
INSERT OR IGNORE INTO bibliography_entries_string (entries_id, bibtex_type_id, entry)
SELECT be.id,
       (SELECT id FROM bibtex_type WHERE bibtex_name = 'title'),
       NULLIF(TRIM(be.title), '')
FROM bibliography_entries be
WHERE be.type = 'ARTICLE_IN_COLLECTION';

-- related_collection
INSERT OR IGNORE INTO bibliography_entries_integer (entries_id, bibtex_type_id, entry)
SELECT baic.id,
       (SELECT id FROM bibtex_type WHERE bibtex_name = 'related_collection'),
       baic.collection_entry_id
FROM bibliography_article_in_collection baic
         JOIN bibliography_entries be ON be.id = baic.id
WHERE be.type = 'ARTICLE_IN_COLLECTION';

-- pages
INSERT OR IGNORE INTO bibliography_entries_string (entries_id, bibtex_type_id, entry)
SELECT baic.id,
       (SELECT id FROM bibtex_type WHERE bibtex_name = 'pages'),
       NULLIF(TRIM(baic.pages), '')
FROM bibliography_article_in_collection baic
         JOIN bibliography_entries be ON be.id = baic.id
WHERE be.type = 'ARTICLE_IN_COLLECTION';

-- ============================================================
-- g4) website  (Quelle: bibliography_internet)
-- ============================================================

-- title
INSERT OR IGNORE INTO bibliography_entries_string (entries_id, bibtex_type_id, entry)
SELECT be.id,
       (SELECT id FROM bibtex_type WHERE bibtex_name = 'title'),
       NULLIF(TRIM(be.title), '')
FROM bibliography_entries be
WHERE be.type = 'INTERNET';

-- url
INSERT OR IGNORE INTO bibliography_entries_string (entries_id, bibtex_type_id, entry)
SELECT bi.id,
       (SELECT id FROM bibtex_type WHERE bibtex_name = 'url'),
       NULLIF(TRIM(bi.url), '')
FROM bibliography_internet bi
         JOIN bibliography_entries be ON be.id = bi.id
WHERE be.type = 'INTERNET';

-- urldate
INSERT OR IGNORE INTO bibliography_entries_string (entries_id, bibtex_type_id, entry)
SELECT bi.id,
       (SELECT id FROM bibtex_type WHERE bibtex_name = 'urldate'),
       NULLIF(TRIM(bi.accessed_at), '')
FROM bibliography_internet bi
         JOIN bibliography_entries be ON be.id = bi.id
WHERE be.type = 'INTERNET';

-- date
INSERT OR IGNORE INTO bibliography_entries_string (entries_id, bibtex_type_id, entry)
SELECT bi.id,
       (SELECT id FROM bibtex_type WHERE bibtex_name = 'date'),
       NULLIF(TRIM(bi.published), '')
FROM bibliography_internet bi
         JOIN bibliography_entries be ON be.id = bi.id
WHERE be.type = 'INTERNET';

-- hostname  (alte Quelle: host_name)
INSERT OR IGNORE INTO bibliography_entries_string (entries_id, bibtex_type_id, entry)
SELECT bi.id,
       (SELECT id FROM bibtex_type WHERE bibtex_name = 'hostname'),
       NULLIF(TRIM(bi.host_name), '')
FROM bibliography_internet bi
         JOIN bibliography_entries be ON be.id = bi.id
WHERE be.type = 'INTERNET';

-- ============================================================
-- g5) collection  (Quelle: bibliography_collection)
-- ============================================================

-- title
INSERT OR IGNORE INTO bibliography_entries_string (entries_id, bibtex_type_id, entry)
SELECT be.id,
       (SELECT id FROM bibtex_type WHERE bibtex_name = 'title'),
       NULLIF(TRIM(be.title), '')
FROM bibliography_entries be
WHERE be.type = 'COLLECTION';

-- subtitle
INSERT OR IGNORE INTO bibliography_entries_string (entries_id, bibtex_type_id, entry)
SELECT bc.id,
       (SELECT id FROM bibtex_type WHERE bibtex_name = 'subtitle'),
       NULLIF(TRIM(bc.subtitle), '')
FROM bibliography_collection bc
         JOIN bibliography_entries be ON be.id = bc.id
WHERE be.type = 'COLLECTION';

-- location  (alte Quelle: place)
INSERT OR IGNORE INTO bibliography_entries_string (entries_id, bibtex_type_id, entry)
SELECT bc.id,
       (SELECT id FROM bibtex_type WHERE bibtex_name = 'location'),
       NULLIF(TRIM(bc.place), '')
FROM bibliography_collection bc
         JOIN bibliography_entries be ON be.id = bc.id
WHERE be.type = 'COLLECTION';

-- year
INSERT OR IGNORE INTO bibliography_entries_integer (entries_id, bibtex_type_id, entry)
SELECT bc.id,
       (SELECT id FROM bibtex_type WHERE bibtex_name = 'year'),
       CASE
           WHEN TRIM(COALESCE(bc.year, '')) <> ''
               AND TRIM(bc.year) NOT GLOB '*[^0-9]*'
               THEN CAST(TRIM(bc.year) AS INTEGER)
           ELSE NULL
           END
FROM bibliography_collection bc
         JOIN bibliography_entries be ON be.id = bc.id
WHERE be.type = 'COLLECTION';

-- series
INSERT OR IGNORE INTO bibliography_entries_string (entries_id, bibtex_type_id, entry)
SELECT bc.id,
       (SELECT id FROM bibtex_type WHERE bibtex_name = 'series'),
       NULLIF(TRIM(bc.series), '')
FROM bibliography_collection bc
         JOIN bibliography_entries be ON be.id = bc.id
WHERE be.type = 'COLLECTION';

-- ============================================================
-- g6) journal  (Quelle: bibliography_journal)
-- ============================================================

-- title
INSERT OR IGNORE INTO bibliography_entries_string (entries_id, bibtex_type_id, entry)
SELECT be.id,
       (SELECT id FROM bibtex_type WHERE bibtex_name = 'title'),
       NULLIF(TRIM(be.title), '')
FROM bibliography_entries be
WHERE be.type = 'JOURNAL';

-- subtitle
INSERT OR IGNORE INTO bibliography_entries_string (entries_id, bibtex_type_id, entry)
SELECT bj.id,
       (SELECT id FROM bibtex_type WHERE bibtex_name = 'subtitle'),
       NULLIF(TRIM(bj.subtitle), '')
FROM bibliography_journal bj
         JOIN bibliography_entries be ON be.id = bj.id
WHERE be.type = 'JOURNAL';

-- publisher
INSERT OR IGNORE INTO bibliography_entries_string (entries_id, bibtex_type_id, entry)
SELECT bj.id,
       (SELECT id FROM bibtex_type WHERE bibtex_name = 'publisher'),
       NULLIF(TRIM(bj.publisher), '')
FROM bibliography_journal bj
         JOIN bibliography_entries be ON be.id = bj.id
WHERE be.type = 'JOURNAL';

-- location  (alte Quelle: place)
INSERT OR IGNORE INTO bibliography_entries_string (entries_id, bibtex_type_id, entry)
SELECT bj.id,
       (SELECT id FROM bibtex_type WHERE bibtex_name = 'location'),
       NULLIF(TRIM(bj.place), '')
FROM bibliography_journal bj
         JOIN bibliography_entries be ON be.id = bj.id
WHERE be.type = 'JOURNAL';

-- start_year
INSERT OR IGNORE INTO bibliography_entries_integer (entries_id, bibtex_type_id, entry)
SELECT bj.id,
       (SELECT id FROM bibtex_type WHERE bibtex_name = 'start_year'),
       CASE
           WHEN TRIM(COALESCE(bj.start_year, '')) <> ''
               AND TRIM(bj.start_year) NOT GLOB '*[^0-9]*'
               THEN CAST(TRIM(bj.start_year) AS INTEGER)
           ELSE NULL
           END
FROM bibliography_journal bj
         JOIN bibliography_entries be ON be.id = bj.id
WHERE be.type = 'JOURNAL';

-- end_year
INSERT OR IGNORE INTO bibliography_entries_integer (entries_id, bibtex_type_id, entry)
SELECT bj.id,
       (SELECT id FROM bibtex_type WHERE bibtex_name = 'end_year'),
       CASE
           WHEN TRIM(COALESCE(bj.end_year, '')) <> ''
               AND TRIM(bj.end_year) NOT GLOB '*[^0-9]*'
               THEN CAST(TRIM(bj.end_year) AS INTEGER)
           ELSE NULL
           END
FROM bibliography_journal bj
         JOIN bibliography_entries be ON be.id = bj.id
WHERE be.type = 'JOURNAL';

-- issn (print)
INSERT OR IGNORE INTO bibliography_entries_string (entries_id, bibtex_type_id, entry)
SELECT bj.id,
       (SELECT id FROM bibtex_type WHERE bibtex_name = 'issn (print)'),
       NULLIF(TRIM(bj.issn), '')
FROM bibliography_journal bj
         JOIN bibliography_entries be ON be.id = bj.id
WHERE be.type = 'JOURNAL';

-- issn (online)  -> bewusst leere Zielzeile
INSERT OR IGNORE INTO bibliography_entries_string (entries_id, bibtex_type_id, entry)
SELECT bj.id,
       (SELECT id FROM bibtex_type WHERE bibtex_name = 'issn (online)'),
       NULL
FROM bibliography_journal bj
         JOIN bibliography_entries be ON be.id = bj.id
WHERE be.type = 'JOURNAL';

-- url -> bewusst leere Zielzeile
INSERT OR IGNORE INTO bibliography_entries_string (entries_id, bibtex_type_id, entry)
SELECT bj.id,
       (SELECT id FROM bibtex_type WHERE bibtex_name = 'url'),
       NULL
FROM bibliography_journal bj
         JOIN bibliography_entries be ON be.id = bj.id
WHERE be.type = 'JOURNAL';

-- ============================================================
-- g7) aitext  (Quelle: bibliography_ai)
-- ============================================================

-- title
INSERT OR IGNORE INTO bibliography_entries_string (entries_id, bibtex_type_id, entry)
SELECT be.id,
       (SELECT id FROM bibtex_type WHERE bibtex_name = 'title'),
       NULLIF(TRIM(be.title), '')
FROM bibliography_entries be
WHERE be.type = 'AI';

-- subtitle -> bewusst leere Zielzeile
INSERT OR IGNORE INTO bibliography_entries_string (entries_id, bibtex_type_id, entry)
SELECT bai.id,
       (SELECT id FROM bibtex_type WHERE bibtex_name = 'subtitle'),
       NULL
FROM bibliography_ai bai
         JOIN bibliography_entries be ON be.id = bai.id
WHERE be.type = 'AI';

-- ai_provider
INSERT OR IGNORE INTO bibliography_entries_string (entries_id, bibtex_type_id, entry)
SELECT bai.id,
       (SELECT id FROM bibtex_type WHERE bibtex_name = 'ai_provider'),
       NULLIF(TRIM(bai.provider), '')
FROM bibliography_ai bai
         JOIN bibliography_entries be ON be.id = bai.id
WHERE be.type = 'AI';

-- ai_model
INSERT OR IGNORE INTO bibliography_entries_string (entries_id, bibtex_type_id, entry)
SELECT bai.id,
       (SELECT id FROM bibtex_type WHERE bibtex_name = 'ai_model'),
       NULLIF(TRIM(bai.model), '')
FROM bibliography_ai bai
         JOIN bibliography_entries be ON be.id = bai.id
WHERE be.type = 'AI';

-- ai_context
INSERT OR IGNORE INTO bibliography_entries_string (entries_id, bibtex_type_id, entry)
SELECT bai.id,
       (SELECT id FROM bibtex_type WHERE bibtex_name = 'ai_context'),
       NULLIF(TRIM(bai.context), '')
FROM bibliography_ai bai
         JOIN bibliography_entries be ON be.id = bai.id
WHERE be.type = 'AI';

-- ai_used_at
INSERT OR IGNORE INTO bibliography_entries_string (entries_id, bibtex_type_id, entry)
SELECT bai.id,
       (SELECT id FROM bibtex_type WHERE bibtex_name = 'ai_used_at'),
       NULLIF(TRIM(bai.used_at), '')
FROM bibliography_ai bai
         JOIN bibliography_entries be ON be.id = bai.id
WHERE be.type = 'AI';
