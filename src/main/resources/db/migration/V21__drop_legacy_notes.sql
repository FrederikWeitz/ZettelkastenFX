CREATE TABLE note_keywords_new (
  note_id     INTEGER NOT NULL,
  keyword_id  INTEGER NOT NULL,
  PRIMARY KEY(note_id, keyword_id),
  FOREIGN KEY(note_id) REFERENCES notes_web(id) ON DELETE CASCADE,
  FOREIGN KEY(keyword_id) REFERENCES keywords(id) ON DELETE CASCADE
);

INSERT INTO note_keywords_new (note_id, keyword_id)
SELECT nk.note_id, nk.keyword_id
FROM note_keywords nk
JOIN notes_web nw ON nw.id = nk.note_id;

DROP TABLE note_keywords;
ALTER TABLE note_keywords_new RENAME TO note_keywords;
CREATE INDEX IF NOT EXISTS idx_note_keywords_keyword_id ON note_keywords(keyword_id);

CREATE TABLE note_projects_new (
  note_id    INTEGER NOT NULL,
  project_id INTEGER NOT NULL,
  PRIMARY KEY(note_id, project_id),
  FOREIGN KEY(note_id) REFERENCES notes_web(id) ON DELETE CASCADE,
  FOREIGN KEY(project_id) REFERENCES projects(id) ON DELETE CASCADE
);

INSERT INTO note_projects_new (note_id, project_id)
SELECT np.note_id, np.project_id
FROM note_projects np
JOIN notes_web nw ON nw.id = np.note_id;

DROP TABLE note_projects;
ALTER TABLE note_projects_new RENAME TO note_projects;

CREATE TABLE note_links_new (
  from_note_id INTEGER NOT NULL,
  to_note_id   INTEGER NOT NULL,
  link_type    TEXT    NOT NULL CHECK (link_type IN ('SERIE', 'IDEE', 'FRAGE', 'KRITIK', 'AUFGABE')),
  PRIMARY KEY(from_note_id, to_note_id),
  FOREIGN KEY(from_note_id) REFERENCES notes_web(id) ON DELETE CASCADE
);

INSERT INTO note_links_new (from_note_id, to_note_id, link_type)
SELECT nl.from_note_id, nl.to_note_id, nl.link_type
FROM note_links nl
JOIN notes_web nw ON nw.id = nl.from_note_id
WHERE nl.link_type IN ('SERIE', 'IDEE', 'FRAGE', 'KRITIK', 'AUFGABE');

DROP TABLE note_links;
ALTER TABLE note_links_new RENAME TO note_links;

DROP TABLE IF EXISTS notes;
