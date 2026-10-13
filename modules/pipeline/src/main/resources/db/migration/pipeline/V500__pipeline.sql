-- Pipeline module: stored runs, so a plan can be reviewed later and recorded as bought exactly once.
CREATE TABLE pipeline_run (
    id           TEXT PRIMARY KEY,
    created_at   TEXT NOT NULL,
    summary      TEXT NOT NULL,
    total        REAL,
    request_json TEXT NOT NULL,
    result_json  TEXT NOT NULL,
    applied_at   TEXT
);
CREATE INDEX pipeline_run_created ON pipeline_run (created_at);
