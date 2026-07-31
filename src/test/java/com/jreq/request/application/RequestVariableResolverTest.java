package com.jreq.request.application;

import com.jreq.request.domain.EnvironmentScope;
import com.jreq.request.domain.EnvironmentVariable;
import com.jreq.request.domain.HttpMethod;
import com.jreq.request.domain.HttpRequestDefinition;
import com.jreq.request.domain.KeyValueEntry;
import com.jreq.request.domain.RequestBody;
import com.jreq.request.domain.RequestEnvironment;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestVariableResolverTest {
    private final RequestVariableResolver resolver = new RequestVariableResolver();

    @Test
    void resolvesSupportedValuesRecursivelyFromTheSelectedEnvironment() {
        HttpRequestDefinition template = new HttpRequestDefinition(
                UUID.randomUUID(),
                "Users",
                HttpMethod.POST,
                "{{base_url}}/users",
                List.of(
                        entry("limit", "{{limit}}", true),
                        entry("ignored", "{{missing}}", false)),
                List.of(entry("Authorization", "Bearer {{token}}", true)),
                RequestBody.json("{\"source\":\"{{source}}\"}"));
        List<EnvironmentVariable> globals = List.of(
                variable("host", "https://global.example"));
        RequestEnvironment environment = environment(List.of(
                variable("host", "https://dev.example"),
                variable("base_url", "{{host}}/v1"),
                variable("limit", "20"),
                variable("source", "jREQ"),
                variable("token", "secret")));

        HttpRequestDefinition resolved = resolver.resolve(template, globals, Optional.of(environment));
        VariableResolutionStatus status = resolver.inspect(template, globals, Optional.of(environment));

        assertThat(resolved.url()).isEqualTo("https://dev.example/v1/users");
        assertThat(resolved.queryParameters().getFirst().value()).isEqualTo("20");
        assertThat(resolved.queryParameters().get(1).value()).isEqualTo("{{missing}}");
        assertThat(resolved.headers().getFirst().value()).isEqualTo("Bearer secret");
        assertThat(resolved.body().content()).isEqualTo("{\"source\":\"jREQ\"}");
        assertThat(template.url()).isEqualTo("{{base_url}}/users");
        assertThat(status.isResolved()).isTrue();
        assertThat(status.referenceCount()).isEqualTo(5);
        assertThat(status.isReferenceResolved("base_url")).isTrue();
    }

    @Test
    void doesNotUseGlobalsAsFallbackForASelectedEnvironment() {
        HttpRequestDefinition template = new HttpRequestDefinition(
                UUID.randomUUID(), "Scoped", HttpMethod.GET,
                "{{global_only}}/{{environment_only}}",
                List.of(), List.of(), RequestBody.none());
        List<EnvironmentVariable> globals = List.of(variable("global_only", "global"));
        RequestEnvironment environment = environment(List.of(variable("environment_only", "environment")));

        VariableResolutionStatus globalsStatus = resolver.inspect(template, globals, Optional.empty());
        VariableResolutionStatus environmentStatus = resolver.inspect(
                template, globals, Optional.of(environment));

        assertThat(globalsStatus.isReferenceResolved("global_only")).isTrue();
        assertThat(globalsStatus.isReferenceResolved("environment_only")).isFalse();
        assertThat(environmentStatus.isReferenceResolved("global_only")).isFalse();
        assertThat(environmentStatus.isReferenceResolved("environment_only")).isTrue();
        assertThatThrownBy(() -> resolver.resolve(template, globals, Optional.of(environment)))
                .isInstanceOf(VariableResolutionException.class)
                .hasMessageContaining("missing {{global_only}}");
    }

    @Test
    void reportsMissingVariablesAndCyclesWithoutValues() {
        HttpRequestDefinition template = new HttpRequestDefinition(
                UUID.randomUUID(), "Broken", HttpMethod.GET, "{{one}}/{{absent}}",
                List.of(), List.of(), RequestBody.none());
        List<EnvironmentVariable> globals = List.of(
                variable("one", "{{two}}"), variable("two", "{{one}}"));

        assertThatThrownBy(() -> resolver.resolve(template, globals, Optional.empty()))
                .isInstanceOf(VariableResolutionException.class)
                .hasMessageContaining("cycle one -> two -> one", "missing {{absent}}")
                .hasMessageNotContaining("secret");
        VariableResolutionStatus status = resolver.inspect(template, globals, Optional.empty());
        assertThat(status.issues())
                .containsExactly("cycle one -> two -> one", "missing {{absent}}");
        assertThat(status.invalidReferences()).containsExactlyInAnyOrder("one", "two", "absent");
        assertThat(status.isReferenceResolved("one")).isFalse();
    }

    @Test
    void acceptsEmptyValuesAndTreatsDisabledVariablesAsMissing() {
        HttpRequestDefinition template = new HttpRequestDefinition(
                UUID.randomUUID(), "Empty", HttpMethod.GET, "https://example.com/{{empty}}/{{disabled}}",
                List.of(), List.of(), RequestBody.none());
        EnvironmentVariable empty = variable("empty", "");
        EnvironmentVariable disabled = new EnvironmentVariable(
                UUID.randomUUID(), "disabled", "value", false, false, 1);

        assertThatThrownBy(() -> resolver.resolve(template, List.of(empty, disabled), Optional.empty()))
                .isInstanceOf(VariableResolutionException.class)
                .hasMessageContaining("missing {{disabled}}")
                .hasMessageNotContaining("missing {{empty}}");
    }

    private EnvironmentVariable variable(String key, String value) {
        return new EnvironmentVariable(UUID.randomUUID(), key, value, true, false, 0);
    }

    private KeyValueEntry entry(String key, String value, boolean enabled) {
        return new KeyValueEntry(UUID.randomUUID(), key, value, enabled);
    }

    private RequestEnvironment environment(List<EnvironmentVariable> variables) {
        Instant now = Instant.now();
        return new RequestEnvironment(
                UUID.randomUUID(), "Development", EnvironmentScope.global(), variables, now, now);
    }
}
