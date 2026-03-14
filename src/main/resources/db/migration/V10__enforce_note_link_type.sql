DELETE FROM note_links
WHERE link_type IS NULL
   OR TRIM(link_type) = ''
   OR UPPER(TRIM(link_type)) = 'UNSPECIFIED';

CREATE TABLE note_links_new (
                                from_note_id INTEGER NOT NULL,
                                to_note_id   INTEGER NOT NULL,
                                link_type    TEXT    NOT NULL CHECK (link_type IN ('SERIE', 'IDEE', 'FRAGE', 'KRITIK', 'AUFGABE')),
                                PRIMARY KEY(from_note_id, to_note_id),
                                FOREIGN KEY(from_note_id) REFERENCES notes(id) ON DELETE CASCADE
    -- to_note_id absichtlich ohne FK: Zettelnummern dürfen “schon” eingetragen werden
);

INSERT INTO note_links_new (from_note_id, to_note_id, link_type)
SELECT from_note_id, to_note_id, link_type
FROM note_links
WHERE link_type IN ('SERIE', 'IDEE', 'FRAGE', 'KRITIK', 'AUFGABE');

DROP TABLE note_links;

ALTER TABLE note_links_new RENAME TO note_links;