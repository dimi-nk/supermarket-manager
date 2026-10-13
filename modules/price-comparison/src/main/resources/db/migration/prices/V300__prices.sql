-- Price-comparison module.
CREATE TABLE prices_cache (
    cache_key  TEXT PRIMARY KEY,
    provider   TEXT NOT NULL,
    query      TEXT NOT NULL,
    fetched_at INTEGER NOT NULL,
    payload    TEXT NOT NULL
);

-- Per-retailer delivery fee / free-delivery threshold / minimum basket used by the optimizer.
CREATE TABLE prices_retailer (
    retailer     TEXT PRIMARY KEY,
    display_name TEXT,
    fee          REAL NOT NULL DEFAULT 0,
    free_above   REAL,
    min_basket   REAL
);

CREATE TABLE prices_settings (
    id                      INTEGER PRIMARY KEY CHECK (id = 1),
    max_retailers           INTEGER NOT NULL DEFAULT 2,
    pack_rounding_tolerance REAL    NOT NULL DEFAULT 0.1,
    max_price_age_days      INTEGER NOT NULL DEFAULT 7,
    search_pages            INTEGER NOT NULL DEFAULT 1
);
INSERT INTO prices_settings (id) VALUES (1);
