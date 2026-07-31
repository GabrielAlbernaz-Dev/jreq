UPDATE collection
SET name = name || ' [' || id || ']'
WHERE rowid NOT IN (
    SELECT MIN(rowid)
    FROM collection
    GROUP BY lower(name)
);

UPDATE saved_request
SET name = name || ' [' || id || ']'
WHERE rowid NOT IN (
    SELECT MIN(rowid)
    FROM saved_request
    GROUP BY collection_id, lower(name)
);

CREATE UNIQUE INDEX uq_collection_name_ci
    ON collection(lower(name));

CREATE UNIQUE INDEX uq_saved_request_root_name_ci
    ON saved_request(lower(name))
    WHERE collection_id IS NULL;

CREATE UNIQUE INDEX uq_saved_request_collection_name_ci
    ON saved_request(collection_id, lower(name))
    WHERE collection_id IS NOT NULL;
