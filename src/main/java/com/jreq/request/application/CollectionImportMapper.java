package com.jreq.request.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jreq.request.application.CollectionImportDocument.ImportAuth;
import com.jreq.request.application.CollectionImportDocument.ImportBody;
import com.jreq.request.application.CollectionImportDocument.CollectionItem;
import com.jreq.request.application.CollectionImportDocument.ImportKeyValue;
import com.jreq.request.application.CollectionImportDocument.ImportedRequest;
import com.jreq.request.application.CollectionImportDocument.ImportVariable;
import com.jreq.request.domain.EnvironmentScope;
import com.jreq.request.domain.EnvironmentVariable;
import com.jreq.request.domain.HttpMethod;
import com.jreq.request.domain.HttpRequestDefinition;
import com.jreq.request.domain.KeyValueEntry;
import com.jreq.request.domain.RequestAuthentication;
import com.jreq.request.domain.RequestBody;
import com.jreq.request.domain.RequestBodyType;
import com.jreq.request.domain.RequestCollection;
import com.jreq.request.domain.RequestEnvironment;
import com.jreq.request.domain.RequestLocation;
import com.jreq.request.domain.SavedRequest;
import com.jreq.request.domain.WorkspaceName;
import com.jreq.shared.json.JReqObjectMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Translates a validated {@link CollectionImportDocument} into jREQ domain objects.
 * Collections in jREQ are flat, so source folders are flattened into the request name
 * ("Folder / Request"). Anything jREQ cannot represent is dropped best-effort and
 * reported as an {@link ImportWarning}; warnings never include credential values.
 */
public final class CollectionImportMapper {
    static final String IMPORTED_ENVIRONMENT_NAME = "Imported variables";
    private static final int MAX_NAME_LENGTH = 200;
    private static final String FOLDER_SEPARATOR = " / ";
    private static final Set<String> UNSUPPORTED_BODY_MODES =
            Set.of("formdata", "binary", "graphql", "file");

    private final ObjectMapper objectMapper = JReqObjectMapper.create();

    public CollectionImportMapping map(CollectionImportDocument document) {
        Objects.requireNonNull(document, "document");
        MappingContext context = new MappingContext();
        UUID collectionId = UUID.randomUUID();
        Instant now = Instant.now();
        RequestCollection collection = new RequestCollection(
                collectionId, truncate(document.info().name().strip(), context), now, now);

        RequestAuthentication collectionAuth = mapAuth(document.auth(), context, true)
                .orElseGet(RequestAuthentication::none);

        List<SavedRequest> requests = new ArrayList<>();
        walkItems(
                document.item() == null ? List.of() : document.item(),
                "",
                collectionId,
                now,
                collectionAuth,
                requests,
                context);
        context.flushUnsupportedAuthWarnings();

        List<RequestEnvironment> environments =
                mapVariables(collectionId, document.variable(), now, context);
        return new CollectionImportMapping(
                new ImportedCollection(collection, List.copyOf(requests), environments),
                context.skippedRequests,
                List.copyOf(context.warnings));
    }

    private void walkItems(
            List<CollectionItem> items,
            String namePrefix,
            UUID collectionId,
            Instant now,
            RequestAuthentication collectionAuth,
            List<SavedRequest> requests,
            MappingContext context
    ) {
        for (CollectionItem item : items) {
            if (item == null) {
                continue;
            }
            if (item.item() != null) {
                String folderName = item.name() == null ? "" : item.name().strip();
                String childPrefix = folderName.isEmpty()
                        ? namePrefix
                        : namePrefix + folderName + FOLDER_SEPARATOR;
                walkItems(item.item(), childPrefix, collectionId, now, collectionAuth, requests, context);
                continue;
            }
            mapRequestItem(item, namePrefix, collectionId, now, collectionAuth, context)
                    .ifPresent(requests::add);
        }
    }

    private Optional<SavedRequest> mapRequestItem(
            CollectionItem item,
            String namePrefix,
            UUID collectionId,
            Instant now,
            RequestAuthentication collectionAuth,
            MappingContext context
    ) {
        String itemName = item.name() == null ? "" : item.name().strip();
        String displayName = itemName.isEmpty() ? "Request" : itemName;
        JsonNode requestNode = item.request();
        if (requestNode == null || !requestNode.isObject()) {
            context.skip(displayName, "its request definition is missing or uses a legacy format");
            return Optional.empty();
        }
        ImportedRequest request;
        try {
            request = objectMapper.treeToValue(requestNode, ImportedRequest.class);
        } catch (Exception unmappable) {
            context.skip(displayName, "its request definition could not be read");
            return Optional.empty();
        }

        HttpMethod method = mapMethod(request.method());
        if (method == null) {
            context.skip(displayName, "the HTTP method \"" + safeMethodLabel(request.method())
                    + "\" is not supported");
            return Optional.empty();
        }

        List<KeyValueEntry> queryParameters = new ArrayList<>();
        String url = extractUrl(request.url(), queryParameters);
        if (url == null || url.isBlank()) {
            context.skip(displayName, "it has no URL");
            return Optional.empty();
        }

        RequestAuthentication authentication = mapAuth(request.auth(), context, false)
                .orElse(collectionAuth);
        HttpRequestDefinition definition = new HttpRequestDefinition(
                UUID.randomUUID(),
                claimUniqueName(truncate(namePrefix + displayName, context), context),
                method,
                url.strip(),
                queryParameters,
                mapKeyValues(request.header()),
                mapBody(request.body(), displayName, context),
                authentication);
        return Optional.of(new SavedRequest(
                definition, RequestLocation.collection(collectionId), now, now));
    }

    private HttpMethod mapMethod(String method) {
        if (method == null || method.isBlank()) {
            return HttpMethod.GET;
        }
        try {
            return HttpMethod.valueOf(method.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unsupported) {
            return null;
        }
    }

    private String safeMethodLabel(String method) {
        return method == null ? "" : method.strip();
    }

    private String extractUrl(JsonNode urlNode, List<KeyValueEntry> queryParameters) {
        if (urlNode == null || urlNode.isNull()) {
            return null;
        }
        if (urlNode.isTextual()) {
            return urlNode.asText();
        }
        if (!urlNode.isObject()) {
            return null;
        }
        collectQueryEntries(urlNode.path("query"), queryParameters);
        String raw = urlNode.path("raw").isTextual() ? urlNode.path("raw").asText() : "";
        if (!raw.isBlank()) {
            return queryParameters.isEmpty() ? raw : stripQueryString(raw);
        }
        return reconstructUrl(urlNode);
    }

    private void collectQueryEntries(JsonNode queryNode, List<KeyValueEntry> queryParameters) {
        if (!queryNode.isArray()) {
            return;
        }
        for (JsonNode entry : queryNode) {
            String key = entry.path("key").isTextual() ? entry.path("key").asText().strip() : "";
            if (key.isEmpty()) {
                continue;
            }
            String value = entry.path("value").isTextual() ? entry.path("value").asText() : "";
            boolean disabled = entry.path("disabled").asBoolean(false);
            queryParameters.add(new KeyValueEntry(UUID.randomUUID(), key, value, !disabled));
        }
    }

    private String stripQueryString(String rawUrl) {
        int queryStart = rawUrl.indexOf('?');
        return queryStart >= 0 ? rawUrl.substring(0, queryStart) : rawUrl;
    }

    private String reconstructUrl(JsonNode urlNode) {
        String protocol = urlNode.path("protocol").isTextual()
                ? urlNode.path("protocol").asText().strip()
                : "";
        String host = joinTextArray(urlNode.path("host"), ".");
        String path = joinTextArray(urlNode.path("path"), "/");
        if (host.isEmpty()) {
            return null;
        }
        StringBuilder url = new StringBuilder();
        if (!protocol.isEmpty()) {
            url.append(protocol).append("://");
        }
        url.append(host);
        if (!path.isEmpty()) {
            url.append('/').append(path);
        }
        return url.toString();
    }

    private String joinTextArray(JsonNode node, String separator) {
        if (!node.isArray()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (JsonNode element : node) {
            if (element.isTextual() && !element.asText().isBlank()) {
                parts.add(element.asText().strip());
            }
        }
        return String.join(separator, parts);
    }

    private List<KeyValueEntry> mapKeyValues(List<ImportKeyValue> entries) {
        if (entries == null) {
            return List.of();
        }
        List<KeyValueEntry> mapped = new ArrayList<>();
        for (ImportKeyValue entry : entries) {
            if (entry == null || entry.key() == null || entry.key().isBlank()) {
                continue;
            }
            mapped.add(new KeyValueEntry(
                    UUID.randomUUID(),
                    entry.key().strip(),
                    entry.value() == null ? "" : entry.value(),
                    !entry.disabled()));
        }
        return List.copyOf(mapped);
    }

    private RequestBody mapBody(ImportBody body, String displayName, MappingContext context) {
        if (body == null || body.mode() == null) {
            return RequestBody.none();
        }
        return switch (body.mode().toLowerCase(Locale.ROOT)) {
            case "raw" -> mapRawBody(body);
            case "urlencoded" -> mapUrlencodedBody(body);
            default -> {
                if (UNSUPPORTED_BODY_MODES.contains(body.mode().toLowerCase(Locale.ROOT))) {
                    context.warn("The body of \"" + displayName + "\" uses mode \"" + body.mode()
                            + "\", which is not supported and was imported as empty.");
                }
                yield RequestBody.none();
            }
        };
    }

    private RequestBody mapRawBody(ImportBody body) {
        String content = body.raw() == null ? "" : body.raw();
        String language = body.options() != null
                && body.options().raw() != null
                && body.options().raw().language() != null
                ? body.options().raw().language()
                : "";
        if ("json".equalsIgnoreCase(language)) {
            return RequestBody.json(content);
        }
        return RequestBody.rawText(content);
    }

    private RequestBody mapUrlencodedBody(ImportBody body) {
        String encoded = (body.urlencoded() == null ? List.<ImportKeyValue>of() : body.urlencoded())
                .stream()
                .filter(entry -> entry != null && !entry.disabled())
                .filter(entry -> entry.key() != null && !entry.key().isBlank())
                .map(entry -> URLEncoder.encode(entry.key().strip(), StandardCharsets.UTF_8)
                        + "="
                        + URLEncoder.encode(entry.value() == null ? "" : entry.value(),
                                StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        return new RequestBody(
                RequestBodyType.RAW_TEXT, encoded, "application/x-www-form-urlencoded");
    }

    private Optional<RequestAuthentication> mapAuth(
            ImportAuth auth,
            MappingContext context,
            boolean collectionLevel
    ) {
        if (auth == null || auth.type() == null || auth.type().isBlank()) {
            return Optional.empty();
        }
        return switch (auth.type().toLowerCase(Locale.ROOT)) {
            case "noauth" -> Optional.of(RequestAuthentication.none());
            case "basic" -> Optional.of(new RequestAuthentication.Basic(
                    authValue(auth.basic(), "username"), authValue(auth.basic(), "password")));
            case "bearer" -> Optional.of(new RequestAuthentication.JwtBearer(
                    authValue(auth.bearer(), "token")));
            default -> {
                if (collectionLevel) {
                    context.warn("The collection-level auth (" + auth.type()
                            + ") is not supported and was ignored.");
                } else {
                    context.unsupportedRequestAuth.merge(auth.type(), 1, Integer::sum);
                }
                yield Optional.of(RequestAuthentication.none());
            }
        };
    }

    private String authValue(List<ImportKeyValue> attributes, String key) {
        if (attributes == null) {
            return "";
        }
        return attributes.stream()
                .filter(entry -> entry != null && key.equalsIgnoreCase(entry.key()))
                .map(entry -> entry.value() == null ? "" : entry.value())
                .findFirst()
                .orElse("");
    }

    private List<RequestEnvironment> mapVariables(
            UUID collectionId,
            List<ImportVariable> variables,
            Instant now,
            MappingContext context
    ) {
        if (variables == null || variables.isEmpty()) {
            return List.of();
        }
        List<EnvironmentVariable> mapped = new ArrayList<>();
        Set<String> usedKeys = new HashSet<>();
        int skipped = 0;
        for (ImportVariable variable : variables) {
            if (variable == null) {
                skipped++;
                continue;
            }
            String key = variable.key() == null ? "" : variable.key().strip();
            if (key.isEmpty() || !usedKeys.add(key)) {
                skipped++;
                continue;
            }
            mapped.add(new EnvironmentVariable(
                    UUID.randomUUID(), key, textValue(variable.value()),
                    !variable.disabled(), false, mapped.size()));
        }
        if (skipped > 0) {
            context.warn(skipped + " collection variable(s) with blank or duplicate keys were skipped.");
        }
        if (mapped.isEmpty()) {
            return List.of();
        }
        return List.of(new RequestEnvironment(
                UUID.randomUUID(), IMPORTED_ENVIRONMENT_NAME,
                EnvironmentScope.collection(collectionId),
                List.copyOf(mapped), now, now));
    }

    private String textValue(JsonNode value) {
        if (value == null || value.isNull()) {
            return "";
        }
        return value.isTextual() ? value.asText() : value.toString();
    }

    private String truncate(String name, MappingContext context) {
        if (name.length() <= MAX_NAME_LENGTH) {
            return name;
        }
        context.namesTruncated = true;
        return name.substring(0, MAX_NAME_LENGTH);
    }

    private String claimUniqueName(String requestedName, MappingContext context) {
        String candidate = WorkspaceName.require(requestedName);
        int suffix = 2;
        while (!context.usedRequestNames.add(WorkspaceName.comparisonKey(candidate))) {
            candidate = requestedName + " (" + suffix++ + ")";
        }
        return candidate;
    }

    public record CollectionImportMapping(
            ImportedCollection importedCollection,
            int skippedRequestCount,
            List<ImportWarning> warnings
    ) {
        public CollectionImportMapping {
            Objects.requireNonNull(importedCollection, "importedCollection");
            warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
        }
    }

    private static final class MappingContext {
        private final List<ImportWarning> warnings = new ArrayList<>();
        private final Set<String> usedRequestNames = new HashSet<>();
        private final Map<String, Integer> unsupportedRequestAuth = new LinkedHashMap<>();
        private int skippedRequests;
        private boolean namesTruncated;

        private void skip(String displayName, String reason) {
            skippedRequests++;
            warnings.add(new ImportWarning("\"" + displayName + "\" was skipped: " + reason + "."));
        }

        private void warn(String message) {
            warnings.add(new ImportWarning(message));
        }

        private void flushUnsupportedAuthWarnings() {
            unsupportedRequestAuth.forEach((type, count) -> warnings.add(new ImportWarning(
                    count + " request(s) use unsupported auth (" + type
                            + ") and were imported without authentication.")));
            if (namesTruncated) {
                warnings.add(new ImportWarning(
                        "Some names exceeded " + MAX_NAME_LENGTH + " characters and were truncated."));
            }
        }
    }
}
