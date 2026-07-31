CREATE TABLE environment (
    id TEXT PRIMARY KEY,
    collection_id TEXT,
    name TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    FOREIGN KEY (collection_id) REFERENCES collection(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uq_global_environment_name_ci
    ON environment(lower(name))
    WHERE collection_id IS NULL;

CREATE UNIQUE INDEX uq_collection_environment_name_ci
    ON environment(collection_id, lower(name))
    WHERE collection_id IS NOT NULL;

CREATE TABLE environment_variable (
    id TEXT PRIMARY KEY,
    environment_id TEXT NOT NULL,
    variable_key TEXT NOT NULL,
    variable_value TEXT NOT NULL,
    enabled INTEGER NOT NULL DEFAULT 1 CHECK (enabled IN (0, 1)),
    secret INTEGER NOT NULL DEFAULT 0 CHECK (secret IN (0, 1)),
    display_order INTEGER NOT NULL DEFAULT 0 CHECK (display_order >= 0),
    FOREIGN KEY (environment_id) REFERENCES environment(id) ON DELETE CASCADE,
    UNIQUE (environment_id, variable_key)
);

CREATE INDEX idx_environment_variable_order
    ON environment_variable(environment_id, display_order, id);

CREATE TABLE global_variable (
    id TEXT PRIMARY KEY,
    variable_key TEXT NOT NULL UNIQUE,
    variable_value TEXT NOT NULL,
    enabled INTEGER NOT NULL DEFAULT 1 CHECK (enabled IN (0, 1)),
    secret INTEGER NOT NULL DEFAULT 0 CHECK (secret IN (0, 1)),
    display_order INTEGER NOT NULL DEFAULT 0 CHECK (display_order >= 0)
);

CREATE INDEX idx_global_variable_order
    ON global_variable(display_order, id);

CREATE TABLE environment_selection (
    context_key TEXT PRIMARY KEY,
    collection_id TEXT,
    environment_id TEXT NOT NULL,
    CHECK (
        (context_key = 'ROOT' AND collection_id IS NULL)
        OR (context_key <> 'ROOT' AND collection_id = context_key)
    ),
    FOREIGN KEY (collection_id) REFERENCES collection(id) ON DELETE CASCADE,
    FOREIGN KEY (environment_id) REFERENCES environment(id) ON DELETE CASCADE
);

ALTER TABLE request_history ADD COLUMN request_collection_id TEXT;
ALTER TABLE request_history ADD COLUMN environment_id TEXT;
ALTER TABLE request_history ADD COLUMN environment_name TEXT;
ALTER TABLE request_history ADD COLUMN environment_collection_id TEXT;
