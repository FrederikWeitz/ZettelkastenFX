CREATE TABLE bibliography_article_in_collection_new (
id                  INTEGER PRIMARY KEY,
collection_entry_id INTEGER,
pages               TEXT,
FOREIGN KEY (id) REFERENCES bibliography_entries(id) ON DELETE CASCADE,
FOREIGN KEY (collection_entry_id) REFERENCES bibliography_entries(id) ON DELETE SET NULL
);

INSERT INTO bibliography_article_in_collection_new (
    id,
    collection_entry_id,
    pages
)
SELECT
    id,
    collection_entry_id,
    pages
FROM bibliography_article_in_collection;

DROP TABLE bibliography_article_in_collection;

ALTER TABLE bibliography_article_in_collection_new
    RENAME TO bibliography_article_in_collection;
