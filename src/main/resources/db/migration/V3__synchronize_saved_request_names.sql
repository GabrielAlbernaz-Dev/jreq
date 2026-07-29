UPDATE saved_request
SET definition_json = json_set(definition_json, '$.name', name)
WHERE json_extract(definition_json, '$.name') <> name;
