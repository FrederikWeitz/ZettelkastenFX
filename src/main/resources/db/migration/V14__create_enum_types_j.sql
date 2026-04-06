-- ============================================================
-- V14__create_enum_types_j.sql
--
-- Schritt h:
-- enum_types anlegen und zunächst mit den Thesis-Typen befüllen.
-- ============================================================

CREATE TABLE IF NOT EXISTS enum_types (
type       TEXT NOT NULL,
typeindex  TEXT NOT NULL,
PRIMARY KEY (type, typeindex)
);

INSERT OR IGNORE INTO enum_types (type, typeindex) VALUES
('Thesis', 'Bachelor'),
('Thesis', 'Master'),
('Thesis', 'Dissertation'),
('Thesis', 'Habilitation');