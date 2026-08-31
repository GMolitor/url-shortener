CREATE TABLE links (
    id INTEGER PRIMARY KEY,
    code TEXT NOT NULL COLLATE BINARY UNIQUE
        CHECK (length(code) = 7)
        CHECK (code NOT GLOB '*[^A-Za-z0-9]*'),
    destination_url TEXT NOT NULL
        CHECK (length(destination_url) BETWEEN 1 AND 2048),
    created_at TEXT NOT NULL
);
