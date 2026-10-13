-- Item-rules module. Rules are stored as a JSON document per rule set.
CREATE TABLE rules_rule_set (
    id          TEXT PRIMARY KEY,
    name        TEXT NOT NULL,
    description TEXT,
    rules_json  TEXT NOT NULL,
    updated_at  TEXT NOT NULL
);
