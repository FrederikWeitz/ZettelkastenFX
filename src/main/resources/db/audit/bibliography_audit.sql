-- bibliography_audit.sql
-- ZettelkastenFX – Entwickler-Audit für dynamische Bibliographie
-- KEINE Flyway-Migration. Nur Prüf-SQLs für Konsistenzkontrollen.

-- ============================================================
-- 1. Leere String-Werte
-- Erwartung: 0
-- ============================================================
SELECT COUNT(*) AS empty_string_rows
FROM bibliography_entries_string
WHERE entry IS NULL
   OR TRIM(entry) = '';

SELECT
    bes.entries_id AS entry_id,
    mt.bib_name AS media_type,
    be.title AS entry_title,
    bt.bibtex_name AS field_name,
    bes.entry AS stored_value
FROM bibliography_entries_string bes
JOIN bibliography_entries be
  ON be.id = bes.entries_id
JOIN media_types mt
  ON mt.id = be.media_types_id
JOIN bibtex_type bt
  ON bt.id = bes.bibtex_type_id
WHERE bes.entry IS NULL
   OR TRIM(bes.entry) = ''
ORDER BY mt.bib_name, bes.entries_id, bt.bibtex_name;

-- ============================================================
-- 2. Leere Integer-/Related-Werte ohne Personenfelder
-- Erwartung: 0
-- ============================================================
SELECT COUNT(*) AS empty_integer_rows
FROM bibliography_entries_integer
WHERE entry IS NULL
  AND bibtex_type_id NOT IN (
      SELECT id
      FROM bibtex_type
      WHERE LOWER(TRIM(datatype)) = 'person'
  );

SELECT
    bei.entries_id AS entry_id,
    mt.bib_name AS media_type,
    be.title AS entry_title,
    bt.bibtex_name AS field_name,
    bt.datatype AS datatype,
    bei.entry AS stored_value
FROM bibliography_entries_integer bei
JOIN bibliography_entries be
  ON be.id = bei.entries_id
JOIN media_types mt
  ON mt.id = be.media_types_id
JOIN bibtex_type bt
  ON bt.id = bei.bibtex_type_id
WHERE bei.entry IS NULL
  AND LOWER(TRIM(bt.datatype)) <> 'person'
ORDER BY mt.bib_name, bei.entries_id, bt.bibtex_name;

-- ============================================================
-- 3. Defekte Personenreferenzen
-- Erwartung: leere Tabelle
-- ============================================================
SELECT
    bei.entries_id AS entry_id,
    mt.bib_name AS media_type,
    be.title AS entry_title,
    bt.bibtex_name AS person_field,
    bei.entry AS person_group_id,
    COUNT(am.author_id) AS mapped_persons
FROM bibliography_entries_integer bei
JOIN bibliography_entries be
  ON be.id = bei.entries_id
JOIN media_types mt
  ON mt.id = be.media_types_id
JOIN bibtex_type bt
  ON bt.id = bei.bibtex_type_id
LEFT JOIN author_mapping am
  ON am.person_group_id = bei.entry
WHERE LOWER(TRIM(bt.datatype)) = 'person'
GROUP BY bei.entries_id, mt.bib_name, be.title, bt.bibtex_name, bei.entry
HAVING bei.entry IS NULL
    OR COUNT(am.author_id) = 0
ORDER BY mt.bib_name, bei.entries_id, bt.bibtex_name;

-- ============================================================
-- 4. Verwaiste author_mapping-Zeilen
-- Erwartung: leere Tabelle
-- ============================================================
SELECT
    am.person_group_id,
    am.author_id,
    am.author_pos
FROM author_mapping am
WHERE NOT EXISTS (
    SELECT 1
    FROM bibliography_entries_integer bei
    JOIN bibtex_type bt
      ON bt.id = bei.bibtex_type_id
    WHERE bei.entry = am.person_group_id
      AND LOWER(TRIM(bt.datatype)) = 'person'
)
ORDER BY am.person_group_id, am.author_pos, am.author_id;

SELECT
    am.person_group_id,
    COUNT(*) AS mapped_persons
FROM author_mapping am
WHERE NOT EXISTS (
    SELECT 1
    FROM bibliography_entries_integer bei
    JOIN bibtex_type bt
      ON bt.id = bei.bibtex_type_id
    WHERE bei.entry = am.person_group_id
      AND LOWER(TRIM(bt.datatype)) = 'person'
)
GROUP BY am.person_group_id
ORDER BY am.person_group_id;

-- ============================================================
-- 5. Personenfelder, die für ihren Medientyp nicht vorgesehen sind
-- Hinweisgeber, kein automatischer Fehlerbeweis: Zusatzfelder können erlaubt sein.
-- ============================================================
SELECT
    be.id AS entry_id,
    mt.bib_name AS media_type,
    be.title AS entry_title,
    bt.bibtex_name AS person_field
FROM bibliography_entries_integer bei
JOIN bibliography_entries be
  ON be.id = bei.entries_id
JOIN media_types mt
  ON mt.id = be.media_types_id
JOIN bibtex_type bt
  ON bt.id = bei.bibtex_type_id
LEFT JOIN media_attributes ma
  ON ma.media_types_id = be.media_types_id
 AND ma.bibtex_type_id = bei.bibtex_type_id
WHERE LOWER(TRIM(bt.datatype)) = 'person'
  AND ma.bibtex_type_id IS NULL
ORDER BY mt.bib_name, be.id, bt.bibtex_name;

-- ============================================================
-- 6. Tatsächlich verwendete Personenfelder pro Medientyp
-- Erwartung: fachlich plausibles Bild
-- ============================================================
SELECT
    mt.bib_name AS media_type,
    bt.bibtex_name AS actual_person_field,
    COUNT(*) AS cnt
FROM bibliography_entries_integer bei
JOIN bibliography_entries be
  ON be.id = bei.entries_id
JOIN media_types mt
  ON mt.id = be.media_types_id
JOIN bibtex_type bt
  ON bt.id = bei.bibtex_type_id
WHERE LOWER(TRIM(bt.datatype)) = 'person'
GROUP BY mt.bib_name, bt.bibtex_name
ORDER BY mt.bib_name, bt.bibtex_name;

-- ============================================================
-- 7. Related-Felder mit nicht existierendem Ziel
-- Erwartung: leere Tabelle
-- ============================================================
SELECT
    bei.entries_id AS source_entry_id,
    source_mt.bib_name AS source_media_type,
    source.title AS source_title,
    bt.bibtex_name AS related_field,
    bei.entry AS target_entry_id
FROM bibliography_entries_integer bei
JOIN bibliography_entries source
  ON source.id = bei.entries_id
JOIN media_types source_mt
  ON source_mt.id = source.media_types_id
JOIN bibtex_type bt
  ON bt.id = bei.bibtex_type_id
LEFT JOIN bibliography_entries target
  ON target.id = bei.entry
WHERE LOWER(TRIM(bt.datatype)) = LOWER(TRIM('Anderer Eintrag/Integer'))
  AND bei.entry IS NOT NULL
  AND target.id IS NULL
ORDER BY source_mt.bib_name, bei.entries_id, bt.bibtex_name;

-- ============================================================
-- 8. Related-Felder mit falschem Ziel-Medientyp
-- Erwartung: leere Tabelle
-- ============================================================
SELECT
    bei.entries_id AS source_entry_id,
    source_mt.bib_name AS source_media_type,
    source.title AS source_title,
    bt.bibtex_name AS related_field,
    bei.entry AS target_entry_id,
    target_mt.bib_name AS actual_target_media_type,
    expected_mt.bib_name AS expected_target_media_type
FROM bibliography_entries_integer bei
JOIN bibliography_entries source
  ON source.id = bei.entries_id
JOIN media_types source_mt
  ON source_mt.id = source.media_types_id
JOIN bibtex_type bt
  ON bt.id = bei.bibtex_type_id
JOIN related_media_type rmt
  ON rmt.bibtex_type_id = bei.bibtex_type_id
JOIN media_types expected_mt
  ON expected_mt.id = rmt.media_types_id
JOIN bibliography_entries target
  ON target.id = bei.entry
JOIN media_types target_mt
  ON target_mt.id = target.media_types_id
WHERE LOWER(TRIM(bt.datatype)) = LOWER(TRIM('Anderer Eintrag/Integer'))
  AND target.media_types_id <> rmt.media_types_id
ORDER BY source_mt.bib_name, bei.entries_id, bt.bibtex_name;

-- ============================================================
-- 9. Bibliographie-Einträge ohne gültigen Medientyp
-- Erwartung: leere Tabelle
-- ============================================================
SELECT
    be.id AS entry_id,
    be.title AS entry_title,
    be.media_types_id
FROM bibliography_entries be
LEFT JOIN media_types mt
  ON mt.id = be.media_types_id
WHERE be.media_types_id IS NULL
   OR mt.id IS NULL
ORDER BY be.id;

-- ============================================================
-- 10. Leerer technischer Titelfallback
-- Erwartung: leere Tabelle
-- ============================================================
SELECT
    be.id AS entry_id,
    mt.bib_name AS media_type,
    be.title AS entry_title
FROM bibliography_entries be
JOIN media_types mt
  ON mt.id = be.media_types_id
WHERE be.title IS NULL
   OR TRIM(be.title) = ''
ORDER BY mt.bib_name, be.id;

-- ============================================================
-- 11. Abweichung zwischen technischem Titelfallback und dynamischem title
-- Erwartung: leere Tabelle
-- ============================================================
SELECT
    be.id AS entry_id,
    mt.bib_name AS media_type,
    be.title AS technical_title,
    bes.entry AS dynamic_title
FROM bibliography_entries be
JOIN media_types mt
  ON mt.id = be.media_types_id
JOIN bibtex_type bt
  ON LOWER(TRIM(bt.bibtex_name)) = 'title'
JOIN bibliography_entries_string bes
  ON bes.entries_id = be.id
 AND bes.bibtex_type_id = bt.id
WHERE TRIM(be.title) <> TRIM(bes.entry)
ORDER BY mt.bib_name, be.id;

-- ============================================================
-- 12. Schema-Check: bibliography_entries
-- Erwartung: media_types_id vorhanden, type nicht vorhanden
-- ============================================================
PRAGMA table_info(bibliography_entries);

-- ============================================================
-- 13. Schema-Check: notes
-- Erwartung: bibliography_ref_id vorhanden, bibliography_type nicht vorhanden
-- ============================================================
PRAGMA table_info(notes);

-- ============================================================
-- 14. Optionaler Überblick: Feldtypen im Metamodell
-- Erwartung: fachlich plausibel
-- ============================================================
SELECT
    datatype,
    COUNT(*) AS field_count
FROM bibtex_type
GROUP BY datatype
ORDER BY LOWER(TRIM(datatype));

-- ============================================================
-- 15. Optionaler Überblick: Personenfelder laut Metamodell
-- Erwartung: collection/proceedings/journal mit editor,
-- thesis mit author und academic_supervisor,
-- article/book/incollection typischerweise mit author.
-- ============================================================
SELECT
    mt.id AS media_type_id,
    mt.bib_name AS media_type,
    bt.bibtex_name AS person_field,
    ma.mode AS mode
FROM media_attributes ma
JOIN media_types mt
  ON mt.id = ma.media_types_id
JOIN bibtex_type bt
  ON bt.id = ma.bibtex_type_id
WHERE LOWER(TRIM(bt.datatype)) = 'person'
ORDER BY mt.bib_name, ma.mode, bt.bibtex_name;
