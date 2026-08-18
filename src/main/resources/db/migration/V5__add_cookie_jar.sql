CREATE TABLE stored_cookie (
    id TEXT PRIMARY KEY,
    cookie_name TEXT NOT NULL,
    cookie_value TEXT NOT NULL,
    domain TEXT NOT NULL,
    cookie_path TEXT NOT NULL,
    host_only INTEGER NOT NULL CHECK (host_only IN (0, 1)),
    secure INTEGER NOT NULL CHECK (secure IN (0, 1)),
    http_only INTEGER NOT NULL CHECK (http_only IN (0, 1)),
    expires_at TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    UNIQUE (cookie_name, domain, cookie_path)
);

CREATE INDEX idx_stored_cookie_domain_path
    ON stored_cookie(domain, cookie_path);

CREATE INDEX idx_stored_cookie_expiration
    ON stored_cookie(expires_at);
