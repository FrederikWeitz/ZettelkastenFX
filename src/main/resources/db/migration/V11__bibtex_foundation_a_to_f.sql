-- ============================================================
-- V11__bibtex_foundation_a_to_f.sql
-- Pflichtenheft Bibliographie, Schritt a bis f
--
-- Ziel:
-- Aufbau der neuen BibTeX-nahen Tabellenstruktur, ohne bestehende
-- Altdaten oder Altlogik bereits umzuhängen.
-- ============================================================

-- ============================================================
-- b) bibliography_entries um citekey erweitern
-- ============================================================

ALTER TABLE bibliography_entries
    ADD COLUMN citekey TEXT;

CREATE INDEX IF NOT EXISTS idx_bibliography_entries_citekey
    ON bibliography_entries(citekey);

-- ============================================================
-- a) Tabelle bibtex_type
-- ============================================================

CREATE TABLE IF NOT EXISTS bibtex_type (
id           INTEGER PRIMARY KEY,
bibtex_name  TEXT NOT NULL,
name         TEXT NOT NULL,
datatype     TEXT NOT NULL,
requires     TEXT,
UNIQUE (bibtex_name)
);

CREATE INDEX IF NOT EXISTS idx_bibtex_type_datatype
    ON bibtex_type(datatype);

INSERT OR IGNORE INTO bibtex_type (id, bibtex_name, name, datatype, requires) VALUES
(1, 'author', 'Autor', 'Person', NULL),
(2, 'bookauthor', 'Buchautor', 'Person', NULL),
(3, 'editor', 'Herausgeber', 'Person', NULL),
(4, 'editora', 'Zweiteditor', 'Person', NULL),
(5, 'editorb', 'Korrektur', 'Person', NULL),
(6, 'editorc', 'Gestaltung', 'Person', NULL),
(7, 'afterword', 'Autor des Nachworts', 'Person', NULL),
(8, 'foreword', 'Autor des Vorworts', 'Person', NULL),
(9, 'annotator', 'Autor der Anmerkungen', 'Person', NULL),
(10, 'commentator', 'Autor der Kommentierung', 'Person', NULL),
(11, 'introduction', 'Autor der Einführung', 'Person', NULL),
(12, 'translator', 'Übersetzer', 'Person', NULL),
(13, 'holder', 'Inhaber', 'Person', NULL),
(14, 'institution', 'Institution', 'String', NULL),
(15, 'organization', 'Organisation', 'String', NULL),
(16, 'publisher', 'Verlag', 'String', NULL),
(17, 'title', 'Titel', 'String', NULL),
(18, 'subtitle', 'Untertitel', 'String', NULL),
(19, 'indextitle', 'Titel eines Index', 'String', NULL),
(20, 'booktitle', 'Buchtitel', 'String', NULL),
(21, 'maintitle', 'Haupttitel', 'String', NULL),
(22, 'journaltitle', 'Journaltitel', 'String', NULL),
(23, 'journalsubtitle', 'Journaluntertitel', 'String', NULL),
(24, 'issuetitle', 'Titel der (Sonder-)Ausgabe', 'String', NULL),
(25, 'eventtitle', 'Titel der Veranstaltung', 'String', NULL),
(26, 'reprinttitle', 'Titel des Nachdrucks', 'String', NULL),
(27, 'series', 'Serie', 'String', NULL),
(28, 'volume', 'Band', 'Integer', NULL),
(29, 'number', 'Nummer', 'Integer', NULL),
(30, 'part', 'Nummer einer Teilausgabe', 'Integer', NULL),
(31, 'issue', 'Titel der Ausgabe', 'String', NULL),
(32, 'volumes', 'Gesamtanzahl der Bände einer Ausgabe', 'Integer', NULL),
(33, 'edition', 'Auflage', 'Integer', NULL),
(34, 'version', 'Version', 'String', NULL),
(35, 'pubstate', 'Publikationsstatus', 'String', NULL),
(36, 'pages', 'Seiten', 'String', NULL),
(37, 'pagetotal', 'Gesamtzahl der Seiten', 'Integer', NULL),
(38, 'pagination', 'Paginierung', 'String', NULL),
(39, 'date', 'Zeitpunkt der Publikation', 'Datum', NULL),
(40, 'year', 'Jahr', 'Year', NULL),
(41, 'month', 'Monat', 'Month', NULL),
(42, 'eventdate', 'Zeitpunkt der Veranstaltung', 'Datum', NULL),
(43, 'urldate', 'Zeitpunkt des Zugriffs', 'Datum', NULL),
(44, 'location', 'Ort', 'String', NULL),
(45, 'venue', 'Veranstaltungsort', 'String', NULL),
(46, 'url', 'Webseite', 'URL (String)', NULL),
(47, 'doi', 'DOI', 'String', NULL),
(48, 'eid', 'EID', 'String', NULL),
(49, 'eprint', 'elektronische Kennzeichnung einer Online-Publikation', 'String', NULL),
(50, 'eprinttype', 'Typ der Online-Publikation', 'String', NULL),
(51, 'type', 'Publikationstyp', 'Typindex', 'typeindex'),
(52, 'typeindex', 'Bestimmung des Publikationstyps', 'Indextyp', 'type'),
(53, 'entrysubtype', 'Untertyp von Typ', 'String', NULL),
(54, 'addendum', 'Bibliografischer Zusatz', 'String', NULL),
(55, 'note', 'Freie bibliografische Daten', 'String', NULL),
(56, 'howpublished', 'Ungewöhnliche Publikationsart', 'String', NULL),
(57, 'language', 'Sprache', 'String', NULL),
(58, 'isan', 'ISAN', 'String', NULL),
(59, 'isbn', 'ISBN', 'String', NULL),
(60, 'ismn', 'ISMN', 'String', NULL),
(61, 'isrn', 'ISRN', 'String', NULL),
(62, 'issn (print)', 'ISSN (print)', 'String', NULL),
(63, 'issn (online)', 'ISSN (online)', 'String', NULL),
(64, 'iswc', 'ISWC', 'String', NULL),
(65, 'abstract', 'Abstract', 'String', NULL),
(66, 'annotation', 'Anmerkung', 'String', NULL),
(67, 'file', 'Dateipfad', 'Dateipfad', 'filename'),
(68, 'filename', 'Dateiname', 'Dateiname', 'file'),
(69, 'library', 'Bibliothek', 'String', NULL),
(70, 'label', 'Label (falls Autor, Jahr, u. ä. fehlen)', 'String', NULL),
(71, 'shorthand', 'Ersatz für offizielle Zitierweise (z. B. eine Sigle)', 'String', NULL),
(72, 'shorthandintro', 'Hinweis auf den shorthand-Ersatz', 'String', NULL),
(73, 'related', 'Medium, auf das sich dieser Eintrag bezieht', 'Anderer Eintrag/Integer', NULL),
(74, 'crossedition', 'Medium gleichen oder ähnlichen Inhalts, auf das sich dieser Eintrag bezieht', 'Anderer Eintrag/Integer', NULL),
(75, 'related_journal', 'beinhaltendes Journal', 'Anderer Eintrag/Integer', NULL),
(76, 'related_collection', 'beinhaltendes Buch', 'Anderer Eintrag/Integer', NULL),
(77, 'ai_provider', 'KI-Anbieter', 'KI-Tabelle', 'ai_model'),
(78, 'ai_model', 'KI-Modell', 'KI-Tabelle', 'ai_provider'),
(79, 'ai_context', 'Kontext der AI-Nutzung', 'String', NULL),
(80, 'ai_used_at', 'Datum der AI-Nutzung', 'String', NULL),
(81, 'academic_supervisor', 'Betreuer einer universitären Arbeit', 'Person', NULL),
(82, 'related_proceedings', 'Beinhaltender Konferenzbericht', 'Anderer Eintrag/Integer', NULL),
(83, 'hostname', 'Name der anbietenden Website', 'String', NULL),
(84, 'start_year', 'Startjahr', 'Year', 'end_year'),
(85, 'end_year', 'Endjahr', 'Year', 'start_year');

-- ============================================================
-- c1) bibliography_entries_string
-- ============================================================

CREATE TABLE IF NOT EXISTS bibliography_entries_string (
entries_id       INTEGER NOT NULL,
bibtex_type_id   INTEGER NOT NULL,
entry            TEXT,
PRIMARY KEY (entries_id, bibtex_type_id),
FOREIGN KEY (entries_id) REFERENCES bibliography_entries(id) ON DELETE CASCADE,
FOREIGN KEY (bibtex_type_id) REFERENCES bibtex_type(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_bes_bibtex_type_id
    ON bibliography_entries_string(bibtex_type_id);

-- ============================================================
-- c2) bibliography_entries_integer
-- ============================================================

CREATE TABLE IF NOT EXISTS bibliography_entries_integer (
entries_id       INTEGER NOT NULL,
bibtex_type_id   INTEGER NOT NULL,
entry            INTEGER,
PRIMARY KEY (entries_id, bibtex_type_id),
FOREIGN KEY (entries_id) REFERENCES bibliography_entries(id) ON DELETE CASCADE,
FOREIGN KEY (bibtex_type_id) REFERENCES bibtex_type(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_bei_bibtex_type_id
    ON bibliography_entries_integer(bibtex_type_id);

-- ============================================================
-- d1) media_types
-- ============================================================

CREATE TABLE IF NOT EXISTS media_types (
id           INTEGER PRIMARY KEY,
name         TEXT NOT NULL,
bib_name     TEXT NOT NULL,
direct_link  INTEGER NOT NULL CHECK (direct_link IN (0, 1)),
UNIQUE (bib_name)
);

-- ============================================================
-- d2) media_attributes
-- ============================================================

CREATE TABLE IF NOT EXISTS media_attributes (
media_types_id   INTEGER NOT NULL,
bibtex_type_id   INTEGER NOT NULL,
mode             TEXT NOT NULL CHECK (mode IN ('identify', 'necessary', 'desired', 'mandatory', 'forbidden')),
PRIMARY KEY (media_types_id, bibtex_type_id),
FOREIGN KEY (media_types_id) REFERENCES media_types(id) ON DELETE CASCADE,
FOREIGN KEY (bibtex_type_id) REFERENCES bibtex_type(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_media_attributes_bibtex_type_id
    ON media_attributes(bibtex_type_id);

-- ============================================================
-- d3) related_media_type
-- ============================================================

CREATE TABLE IF NOT EXISTS related_media_type (
bibtex_type_id                  INTEGER NOT NULL,
media_types_id                  INTEGER NOT NULL,
media_attribute_bibtex_type_id  INTEGER NOT NULL,
PRIMARY KEY (bibtex_type_id),
FOREIGN KEY (bibtex_type_id) REFERENCES bibtex_type(id) ON DELETE CASCADE,
FOREIGN KEY (media_types_id) REFERENCES media_types(id) ON DELETE CASCADE,
FOREIGN KEY (media_types_id, media_attribute_bibtex_type_id)
    REFERENCES media_attributes(media_types_id, bibtex_type_id)
    ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_related_media_type_media_types_id
    ON related_media_type(media_types_id);

-- ============================================================
-- e) feste Medientypen
-- ============================================================

INSERT OR IGNORE INTO media_types (id, name, bib_name, direct_link) VALUES
(1,  'Journalartikel',     'article',        1),
(2,  'Buch',               'book',           1),
(3,  'Booklet',            'booklet',        1),
(4,  'Konferenzvortrag',   'inconference',   1),
(5,  'Teilbuch',           'inbook',         1),
(6,  'Sammelbandbeitrag',  'incollection',   1),
(7,  'Konferenzartikel',   'inproceedings',  1),
(8,  'Anleitung',          'manual',         1),
(9,  'Abschlussarbeit',    'thesis',         1),
(10, 'Konferenzbericht',   'proceedings',    1),
(11, 'Technischer Bericht','techreport',     1),
(12, 'unveröffentlicht',   'unpublished',    1),
(13, 'Webseite',           'website',        1),
(14, 'Sammelband',         'collection',     0),
(15, 'Journal',            'journal',        0),
(16, 'KI-Text',            'aitext',         1);

-- ============================================================
-- f) Attribute je Medientyp
-- ============================================================

-- f1) article
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'identify' FROM media_types mt JOIN bibtex_type bt
                                                         ON mt.bib_name = 'article' AND bt.bibtex_name = 'title';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'identify' FROM media_types mt JOIN bibtex_type bt
                                                         ON mt.bib_name = 'article' AND bt.bibtex_name = 'author';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'desired' FROM media_types mt JOIN bibtex_type bt
                                                        ON mt.bib_name = 'article' AND bt.bibtex_name = 'subtitle';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'necessary' FROM media_types mt JOIN bibtex_type bt
                                                          ON mt.bib_name = 'article' AND bt.bibtex_name = 'related_journal';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'necessary' FROM media_types mt JOIN bibtex_type bt
                                                          ON mt.bib_name = 'article' AND bt.bibtex_name = 'volume';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'necessary' FROM media_types mt JOIN bibtex_type bt
                                                          ON mt.bib_name = 'article' AND bt.bibtex_name = 'issue';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'desired' FROM media_types mt JOIN bibtex_type bt
                                                        ON mt.bib_name = 'article' AND bt.bibtex_name = 'date';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'necessary' FROM media_types mt JOIN bibtex_type bt
                                                          ON mt.bib_name = 'article' AND bt.bibtex_name = 'pages';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'desired' FROM media_types mt JOIN bibtex_type bt
                                                        ON mt.bib_name = 'article' AND bt.bibtex_name = 'doi';

-- f2) book
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'identify' FROM media_types mt JOIN bibtex_type bt
                                                         ON mt.bib_name = 'book' AND bt.bibtex_name = 'title';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'identify' FROM media_types mt JOIN bibtex_type bt
                                                         ON mt.bib_name = 'book' AND bt.bibtex_name = 'author';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'desired' FROM media_types mt JOIN bibtex_type bt
                                                        ON mt.bib_name = 'book' AND bt.bibtex_name = 'subtitle';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'desired' FROM media_types mt JOIN bibtex_type bt
                                                        ON mt.bib_name = 'book' AND bt.bibtex_name = 'publisher';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'necessary' FROM media_types mt JOIN bibtex_type bt
                                                          ON mt.bib_name = 'book' AND bt.bibtex_name = 'location';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'necessary' FROM media_types mt JOIN bibtex_type bt
                                                          ON mt.bib_name = 'book' AND bt.bibtex_name = 'date';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'desired' FROM media_types mt JOIN bibtex_type bt
                                                        ON mt.bib_name = 'book' AND bt.bibtex_name = 'edition';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'desired' FROM media_types mt JOIN bibtex_type bt
                                                        ON mt.bib_name = 'book' AND bt.bibtex_name = 'isbn';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'mandatory' FROM media_types mt JOIN bibtex_type bt
                                                          ON mt.bib_name = 'book' AND bt.bibtex_name = 'series';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'mandatory' FROM media_types mt JOIN bibtex_type bt
                                                          ON mt.bib_name = 'book' AND bt.bibtex_name = 'volume';

-- f3) booklet
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'identify' FROM media_types mt JOIN bibtex_type bt
                                                         ON mt.bib_name = 'booklet' AND bt.bibtex_name = 'title';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'identify' FROM media_types mt JOIN bibtex_type bt
                                                         ON mt.bib_name = 'booklet' AND bt.bibtex_name = 'author';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'desired' FROM media_types mt JOIN bibtex_type bt
                                                        ON mt.bib_name = 'booklet' AND bt.bibtex_name = 'date';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'desired' FROM media_types mt JOIN bibtex_type bt
                                                        ON mt.bib_name = 'booklet' AND bt.bibtex_name = 'location';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'desired' FROM media_types mt JOIN bibtex_type bt
                                                        ON mt.bib_name = 'booklet' AND bt.bibtex_name = 'note';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'necessary' FROM media_types mt JOIN bibtex_type bt
                                                          ON mt.bib_name = 'booklet' AND bt.bibtex_name = 'howpublished';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'desired' FROM media_types mt JOIN bibtex_type bt
                                                        ON mt.bib_name = 'booklet' AND bt.bibtex_name = 'organization';

-- f4) inconference
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'identify' FROM media_types mt JOIN bibtex_type bt
                                                         ON mt.bib_name = 'inconference' AND bt.bibtex_name = 'title';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'identify' FROM media_types mt JOIN bibtex_type bt
                                                         ON mt.bib_name = 'inconference' AND bt.bibtex_name = 'author';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'necessary' FROM media_types mt JOIN bibtex_type bt
                                                          ON mt.bib_name = 'inconference' AND bt.bibtex_name = 'eventdate';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'necessary' FROM media_types mt JOIN bibtex_type bt
                                                          ON mt.bib_name = 'inconference' AND bt.bibtex_name = 'institution';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'desired' FROM media_types mt JOIN bibtex_type bt
                                                        ON mt.bib_name = 'inconference' AND bt.bibtex_name = 'note';

-- f5) inbook
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'identify' FROM media_types mt JOIN bibtex_type bt
                                                         ON mt.bib_name = 'inbook' AND bt.bibtex_name = 'title';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'identify' FROM media_types mt JOIN bibtex_type bt
                                                         ON mt.bib_name = 'inbook' AND bt.bibtex_name = 'author';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'necessary' FROM media_types mt JOIN bibtex_type bt
                                                          ON mt.bib_name = 'inbook' AND bt.bibtex_name = 'related_collection';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'necessary' FROM media_types mt JOIN bibtex_type bt
                                                          ON mt.bib_name = 'inbook' AND bt.bibtex_name = 'pages';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'mandatory' FROM media_types mt JOIN bibtex_type bt
                                                          ON mt.bib_name = 'inbook' AND bt.bibtex_name = 'subtitle';

-- f6) incollection
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'identify' FROM media_types mt JOIN bibtex_type bt
                                                         ON mt.bib_name = 'incollection' AND bt.bibtex_name = 'title';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'identify' FROM media_types mt JOIN bibtex_type bt
                                                         ON mt.bib_name = 'incollection' AND bt.bibtex_name = 'author';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'necessary' FROM media_types mt JOIN bibtex_type bt
                                                          ON mt.bib_name = 'incollection' AND bt.bibtex_name = 'related_collection';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'necessary' FROM media_types mt JOIN bibtex_type bt
                                                          ON mt.bib_name = 'incollection' AND bt.bibtex_name = 'pages';

-- f7) inproceedings
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'identify' FROM media_types mt JOIN bibtex_type bt
                                                         ON mt.bib_name = 'inproceedings' AND bt.bibtex_name = 'title';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'identify' FROM media_types mt JOIN bibtex_type bt
                                                         ON mt.bib_name = 'inproceedings' AND bt.bibtex_name = 'author';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'necessary' FROM media_types mt JOIN bibtex_type bt
                                                          ON mt.bib_name = 'inproceedings' AND bt.bibtex_name = 'related_proceedings';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'desired' FROM media_types mt JOIN bibtex_type bt
                                                        ON mt.bib_name = 'inproceedings' AND bt.bibtex_name = 'pages';

-- f8) manual
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'identify' FROM media_types mt JOIN bibtex_type bt
                                                         ON mt.bib_name = 'manual' AND bt.bibtex_name = 'title';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'desired' FROM media_types mt JOIN bibtex_type bt
                                                        ON mt.bib_name = 'manual' AND bt.bibtex_name = 'date';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'desired' FROM media_types mt JOIN bibtex_type bt
                                                        ON mt.bib_name = 'manual' AND bt.bibtex_name = 'author';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'desired' FROM media_types mt JOIN bibtex_type bt
                                                        ON mt.bib_name = 'manual' AND bt.bibtex_name = 'organization';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'desired' FROM media_types mt JOIN bibtex_type bt
                                                        ON mt.bib_name = 'manual' AND bt.bibtex_name = 'location';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'desired' FROM media_types mt JOIN bibtex_type bt
                                                        ON mt.bib_name = 'manual' AND bt.bibtex_name = 'edition';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'desired' FROM media_types mt JOIN bibtex_type bt
                                                        ON mt.bib_name = 'manual' AND bt.bibtex_name = 'note';

-- f9) thesis
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'identify' FROM media_types mt JOIN bibtex_type bt
                                                         ON mt.bib_name = 'thesis' AND bt.bibtex_name = 'title';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'identify' FROM media_types mt JOIN bibtex_type bt
                                                         ON mt.bib_name = 'thesis' AND bt.bibtex_name = 'author';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'desired' FROM media_types mt JOIN bibtex_type bt
                                                        ON mt.bib_name = 'thesis' AND bt.bibtex_name = 'subtitle';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'desired' FROM media_types mt JOIN bibtex_type bt
                                                        ON mt.bib_name = 'thesis' AND bt.bibtex_name = 'institution';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'desired' FROM media_types mt JOIN bibtex_type bt
                                                        ON mt.bib_name = 'thesis' AND bt.bibtex_name = 'date';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'necessary' FROM media_types mt JOIN bibtex_type bt
                                                          ON mt.bib_name = 'thesis' AND bt.bibtex_name = 'type';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'necessary' FROM media_types mt JOIN bibtex_type bt
                                                          ON mt.bib_name = 'thesis' AND bt.bibtex_name = 'typeindex';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'necessary' FROM media_types mt JOIN bibtex_type bt
                                                          ON mt.bib_name = 'thesis' AND bt.bibtex_name = 'academic_supervisor';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'mandatory' FROM media_types mt JOIN bibtex_type bt
                                                          ON mt.bib_name = 'thesis' AND bt.bibtex_name = 'note';

-- f10) proceedings
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'identify' FROM media_types mt JOIN bibtex_type bt
                                                         ON mt.bib_name = 'proceedings' AND bt.bibtex_name = 'title';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'desired' FROM media_types mt JOIN bibtex_type bt
                                                        ON mt.bib_name = 'proceedings' AND bt.bibtex_name = 'editor';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'desired' FROM media_types mt JOIN bibtex_type bt
                                                        ON mt.bib_name = 'proceedings' AND bt.bibtex_name = 'date';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'desired' FROM media_types mt JOIN bibtex_type bt
                                                        ON mt.bib_name = 'proceedings' AND bt.bibtex_name = 'publisher';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'desired' FROM media_types mt JOIN bibtex_type bt
                                                        ON mt.bib_name = 'proceedings' AND bt.bibtex_name = 'venue';

-- f11) techreport
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'identify' FROM media_types mt JOIN bibtex_type bt
                                                         ON mt.bib_name = 'techreport' AND bt.bibtex_name = 'title';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'desired' FROM media_types mt JOIN bibtex_type bt
                                                        ON mt.bib_name = 'techreport' AND bt.bibtex_name = 'author';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'desired' FROM media_types mt JOIN bibtex_type bt
                                                        ON mt.bib_name = 'techreport' AND bt.bibtex_name = 'institution';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'desired' FROM media_types mt JOIN bibtex_type bt
                                                        ON mt.bib_name = 'techreport' AND bt.bibtex_name = 'date';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'mandatory' FROM media_types mt JOIN bibtex_type bt
                                                          ON mt.bib_name = 'techreport' AND bt.bibtex_name = 'note';

-- f12) unpublished
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'desired' FROM media_types mt JOIN bibtex_type bt
                                                        ON mt.bib_name = 'unpublished' AND bt.bibtex_name = 'author';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'identify' FROM media_types mt JOIN bibtex_type bt
                                                         ON mt.bib_name = 'unpublished' AND bt.bibtex_name = 'title';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'desired' FROM media_types mt JOIN bibtex_type bt
                                                        ON mt.bib_name = 'unpublished' AND bt.bibtex_name = 'institution';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'mandatory' FROM media_types mt JOIN bibtex_type bt
                                                          ON mt.bib_name = 'unpublished' AND bt.bibtex_name = 'date';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'mandatory' FROM media_types mt JOIN bibtex_type bt
                                                          ON mt.bib_name = 'unpublished' AND bt.bibtex_name = 'note';

-- f13) website
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'desired' FROM media_types mt JOIN bibtex_type bt
                                                        ON mt.bib_name = 'website' AND bt.bibtex_name = 'title';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'identify' FROM media_types mt JOIN bibtex_type bt
                                                         ON mt.bib_name = 'website' AND bt.bibtex_name = 'url';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'desired' FROM media_types mt JOIN bibtex_type bt
                                                        ON mt.bib_name = 'website' AND bt.bibtex_name = 'urldate';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'desired' FROM media_types mt JOIN bibtex_type bt
                                                        ON mt.bib_name = 'website' AND bt.bibtex_name = 'date';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'desired' FROM media_types mt JOIN bibtex_type bt
                                                        ON mt.bib_name = 'website' AND bt.bibtex_name = 'hostname';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'mandatory' FROM media_types mt JOIN bibtex_type bt
                                                          ON mt.bib_name = 'website' AND bt.bibtex_name = 'author';

-- f14) collection
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'identify' FROM media_types mt JOIN bibtex_type bt
                                                         ON mt.bib_name = 'collection' AND bt.bibtex_name = 'title';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'identify' FROM media_types mt JOIN bibtex_type bt
                                                         ON mt.bib_name = 'collection' AND bt.bibtex_name = 'editor';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'desired' FROM media_types mt JOIN bibtex_type bt
                                                        ON mt.bib_name = 'collection' AND bt.bibtex_name = 'subtitle';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'desired' FROM media_types mt JOIN bibtex_type bt
                                                        ON mt.bib_name = 'collection' AND bt.bibtex_name = 'location';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'desired' FROM media_types mt JOIN bibtex_type bt
                                                        ON mt.bib_name = 'collection' AND bt.bibtex_name = 'date';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'desired' FROM media_types mt JOIN bibtex_type bt
                                                        ON mt.bib_name = 'collection' AND bt.bibtex_name = 'series';

-- f15) journal
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'identify' FROM media_types mt JOIN bibtex_type bt
                                                         ON mt.bib_name = 'journal' AND bt.bibtex_name = 'title';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'desired' FROM media_types mt JOIN bibtex_type bt
                                                        ON mt.bib_name = 'journal' AND bt.bibtex_name = 'subtitle';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'desired' FROM media_types mt JOIN bibtex_type bt
                                                        ON mt.bib_name = 'journal' AND bt.bibtex_name = 'editor';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'desired' FROM media_types mt JOIN bibtex_type bt
                                                        ON mt.bib_name = 'journal' AND bt.bibtex_name = 'publisher';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'desired' FROM media_types mt JOIN bibtex_type bt
                                                        ON mt.bib_name = 'journal' AND bt.bibtex_name = 'location';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'desired' FROM media_types mt JOIN bibtex_type bt
                                                        ON mt.bib_name = 'journal' AND bt.bibtex_name = 'start_year';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'mandatory' FROM media_types mt JOIN bibtex_type bt
                                                          ON mt.bib_name = 'journal' AND bt.bibtex_name = 'end_year';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'desired' FROM media_types mt JOIN bibtex_type bt
                                                        ON mt.bib_name = 'journal' AND bt.bibtex_name = 'issn (print)';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'desired' FROM media_types mt JOIN bibtex_type bt
                                                        ON mt.bib_name = 'journal' AND bt.bibtex_name = 'issn (online)';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'desired' FROM media_types mt JOIN bibtex_type bt
                                                        ON mt.bib_name = 'journal' AND bt.bibtex_name = 'url';

-- f16) aitext
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'identify' FROM media_types mt JOIN bibtex_type bt
                                                         ON mt.bib_name = 'aitext' AND bt.bibtex_name = 'title';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'desired' FROM media_types mt JOIN bibtex_type bt
                                                        ON mt.bib_name = 'aitext' AND bt.bibtex_name = 'subtitle';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'desired' FROM media_types mt JOIN bibtex_type bt
                                                        ON mt.bib_name = 'aitext' AND bt.bibtex_name = 'ai_provider';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'desired' FROM media_types mt JOIN bibtex_type bt
                                                        ON mt.bib_name = 'aitext' AND bt.bibtex_name = 'ai_model';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'desired' FROM media_types mt JOIN bibtex_type bt
                                                        ON mt.bib_name = 'aitext' AND bt.bibtex_name = 'ai_context';
INSERT OR IGNORE INTO media_attributes (media_types_id, bibtex_type_id, mode)
SELECT mt.id, bt.id, 'desired' FROM media_types mt JOIN bibtex_type bt
                                                        ON mt.bib_name = 'aitext' AND bt.bibtex_name = 'ai_used_at';

-- ============================================================
-- related_media_type für benötigte related_*-Felder
-- Anzeige jeweils über das identify-Feld "title"
-- ============================================================

INSERT OR IGNORE INTO related_media_type (bibtex_type_id, media_types_id, media_attribute_bibtex_type_id)
SELECT rel.id, mt.id, attr.bibtex_type_id
FROM bibtex_type rel
         JOIN media_types mt
              ON mt.bib_name = 'journal'
         JOIN media_attributes attr
              ON attr.media_types_id = mt.id
         JOIN bibtex_type attr_bt
              ON attr_bt.id = attr.bibtex_type_id
WHERE rel.bibtex_name = 'related_journal'
  AND attr_bt.bibtex_name = 'title';

INSERT OR IGNORE INTO related_media_type (bibtex_type_id, media_types_id, media_attribute_bibtex_type_id)
SELECT rel.id, mt.id, attr.bibtex_type_id
FROM bibtex_type rel
         JOIN media_types mt
              ON mt.bib_name = 'collection'
         JOIN media_attributes attr
              ON attr.media_types_id = mt.id
         JOIN bibtex_type attr_bt
              ON attr_bt.id = attr.bibtex_type_id
WHERE rel.bibtex_name = 'related_collection'
  AND attr_bt.bibtex_name = 'title';

INSERT OR IGNORE INTO related_media_type (bibtex_type_id, media_types_id, media_attribute_bibtex_type_id)
SELECT rel.id, mt.id, attr.bibtex_type_id
FROM bibtex_type rel
         JOIN media_types mt
              ON mt.bib_name = 'proceedings'
         JOIN media_attributes attr
              ON attr.media_types_id = mt.id
         JOIN bibtex_type attr_bt
              ON attr_bt.id = attr.bibtex_type_id
WHERE rel.bibtex_name = 'related_proceedings'
  AND attr_bt.bibtex_name = 'title';
