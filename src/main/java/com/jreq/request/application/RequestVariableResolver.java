package com.jreq.request.application;

import com.jreq.request.domain.EnvironmentVariable;
import com.jreq.request.domain.HttpRequestDefinition;
import com.jreq.request.domain.KeyValueEntry;
import com.jreq.request.domain.RequestBody;
import com.jreq.request.domain.RequestAuthentication;
import com.jreq.request.domain.RequestEnvironment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RequestVariableResolver {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([^{}]+?)\\s*}}");

    public HttpRequestDefinition resolve(
            HttpRequestDefinition template,
            List<EnvironmentVariable> globals,
            Optional<RequestEnvironment> environment
    ) {
        Resolution resolution = new Resolution(activeValues(globals, environment));
        HttpRequestDefinition resolved = resolveDefinition(template, resolution);
        resolution.requireSuccess();
        return resolved;
    }

    public VariableResolutionStatus inspect(
            HttpRequestDefinition template,
            List<EnvironmentVariable> globals,
            Optional<RequestEnvironment> environment
    ) {
        Resolution resolution = new Resolution(activeValues(globals, environment));
        resolveDefinition(template, resolution);
        return resolution.status();
    }

    private Map<String, String> activeValues(
            List<EnvironmentVariable> globals,
            Optional<RequestEnvironment> environment
    ) {
        return environment
                .map(RequestEnvironment::variables)
                .map(this::enabledValues)
                .orElseGet(() -> enabledValues(globals));
    }

    private HttpRequestDefinition resolveDefinition(
            HttpRequestDefinition template,
            Resolution resolution
    ) {
        String url = resolution.text(template.url());
        List<KeyValueEntry> query = resolveEntries(template.queryParameters(), resolution);
        List<KeyValueEntry> headers = resolveEntries(template.headers(), resolution);
        RequestBody body = template.body().isPresent()
                ? new RequestBody(template.body().type(), resolution.text(template.body().content()),
                        template.body().contentType())
                : RequestBody.none();
        RequestAuthentication authentication = resolveAuthentication(template.authentication(), resolution);
        return new HttpRequestDefinition(
                template.id(), template.name(), template.method(), url, query, headers, body, authentication);
    }

    private RequestAuthentication resolveAuthentication(
            RequestAuthentication authentication,
            Resolution resolution
    ) {
        return switch (authentication) {
            case RequestAuthentication.None none -> none;
            case RequestAuthentication.Basic basic -> new RequestAuthentication.Basic(
                    resolution.text(basic.username()), resolution.text(basic.password()));
            case RequestAuthentication.JwtBearer jwt ->
                    new RequestAuthentication.JwtBearer(resolution.text(jwt.token()));
        };
    }

    private Map<String, String> enabledValues(List<EnvironmentVariable> variables) {
        Map<String, String> values = new LinkedHashMap<>();
        variables.stream().filter(EnvironmentVariable::enabled)
                .forEach(variable -> values.put(variable.key(), variable.value()));
        return values;
    }

    private List<KeyValueEntry> resolveEntries(List<KeyValueEntry> entries, Resolution resolution) {
        return entries.stream()
                .map(entry -> entry.enabled()
                        ? entry.withValues(entry.key(), resolution.text(entry.value()), true)
                        : entry)
                .toList();
    }

    private static final class Resolution {
        private final Map<String, String> source;
        private final Map<String, String> cache = new LinkedHashMap<>();
        private final Set<String> issues = new LinkedHashSet<>();
        private final Set<String> references = new LinkedHashSet<>();
        private final Set<String> invalidReferences = new LinkedHashSet<>();

        private Resolution(Map<String, String> source) {
            this.source = source;
        }

        private String text(String input) {
            return replace(input, new ArrayList<>());
        }

        private String replace(String input, List<String> path) {
            Matcher matcher = PLACEHOLDER.matcher(input);
            StringBuilder output = new StringBuilder();
            while (matcher.find()) {
                String key = matcher.group(1).strip();
                references.add(key);
                String value = variable(key, path);
                matcher.appendReplacement(output, Matcher.quoteReplacement(value));
            }
            matcher.appendTail(output);
            return output.toString();
        }

        private String variable(String key, List<String> path) {
            if (cache.containsKey(key)) {
                return cache.get(key);
            }
            if (!source.containsKey(key)) {
                issues.add("missing {{" + key + "}}");
                invalidReferences.add(key);
                return "{{" + key + "}}";
            }
            int cycleStart = path.indexOf(key);
            if (cycleStart >= 0) {
                List<String> cycle = new ArrayList<>(path.subList(cycleStart, path.size()));
                cycle.add(key);
                issues.add("cycle " + String.join(" -> ", cycle));
                invalidReferences.addAll(cycle);
                return "{{" + key + "}}";
            }
            List<String> nextPath = new ArrayList<>(path);
            nextPath.add(key);
            String sourceValue = source.get(key);
            String value = replace(sourceValue, nextPath);
            if (containsInvalidReference(sourceValue)) {
                invalidReferences.add(key);
            }
            cache.put(key, value);
            return value;
        }

        private boolean containsInvalidReference(String value) {
            Matcher matcher = PLACEHOLDER.matcher(value);
            while (matcher.find()) {
                if (invalidReferences.contains(matcher.group(1).strip())) {
                    return true;
                }
            }
            return false;
        }

        private void requireSuccess() {
            if (!issues.isEmpty()) {
                throw new VariableResolutionException(List.copyOf(issues));
            }
        }

        private VariableResolutionStatus status() {
            return new VariableResolutionStatus(
                    references.size(), List.copyOf(issues), Set.copyOf(invalidReferences));
        }
    }
}
