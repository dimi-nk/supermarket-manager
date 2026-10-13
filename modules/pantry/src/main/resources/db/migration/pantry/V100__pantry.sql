-- Pantry module. Owns every table prefixed "pantry_".

CREATE TABLE pantry_category (
    id           TEXT PRIMARY KEY,
    name         TEXT NOT NULL,
    parent_id    TEXT REFERENCES pantry_category (id) ON DELETE RESTRICT,
    measure      TEXT NOT NULL CHECK (measure IN ('kg', 'l', 'each')),
    search_query TEXT,
    notes        TEXT
);

CREATE TABLE pantry_product (
    id              TEXT PRIMARY KEY,
    name            TEXT NOT NULL,
    brand           TEXT,
    category_id     TEXT NOT NULL REFERENCES pantry_category (id) ON DELETE RESTRICT,
    pack_size       REAL NOT NULL CHECK (pack_size > 0),
    barcode         TEXT,
    external_source TEXT,
    external_id     TEXT,
    search_query    TEXT,
    private_label   INTEGER NOT NULL DEFAULT 0,
    notes           TEXT
);

CREATE TABLE pantry_profile (
    id          TEXT PRIMARY KEY,
    name        TEXT NOT NULL,
    description TEXT,
    active      INTEGER NOT NULL DEFAULT 0,
    created_at  TEXT NOT NULL
);

-- A profile's targets change over time: each version applies from effective_from until the next one.
CREATE TABLE pantry_target_version (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    profile_id     TEXT NOT NULL REFERENCES pantry_profile (id) ON DELETE CASCADE,
    effective_from TEXT NOT NULL,
    note           TEXT,
    created_at     TEXT NOT NULL,
    UNIQUE (profile_id, effective_from)
);

-- subject_type/subject_id: either a generic category ("2 x 500 g tubs of yoghurt") or a concrete product.
CREATE TABLE pantry_target (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    version_id   INTEGER NOT NULL REFERENCES pantry_target_version (id) ON DELETE CASCADE,
    subject_type TEXT NOT NULL CHECK (subject_type IN ('category', 'product')),
    subject_id   TEXT NOT NULL,
    amount       REAL NOT NULL CHECK (amount >= 0),
    pack_count   INTEGER,
    pack_size    REAL,
    note         TEXT,
    UNIQUE (version_id, subject_type, subject_id)
);

CREATE TABLE pantry_stock (
    subject_type TEXT NOT NULL CHECK (subject_type IN ('category', 'product')),
    subject_id   TEXT NOT NULL,
    amount       REAL NOT NULL CHECK (amount >= 0),
    updated_at   TEXT NOT NULL,
    PRIMARY KEY (subject_type, subject_id)
);

-- Append-only history of stock changes (enables consumption-rate and price history features later).
CREATE TABLE pantry_stock_event (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    at           TEXT NOT NULL,
    kind         TEXT NOT NULL CHECK (kind IN ('set', 'add', 'consume', 'purchase')),
    subject_type TEXT NOT NULL,
    subject_id   TEXT NOT NULL,
    delta        REAL NOT NULL,
    resulting    REAL NOT NULL,
    price        REAL,
    retailer     TEXT,
    reference    TEXT,
    note         TEXT
);

CREATE INDEX pantry_stock_event_subject ON pantry_stock_event (subject_type, subject_id, at);
CREATE INDEX pantry_stock_event_reference ON pantry_stock_event (reference);
