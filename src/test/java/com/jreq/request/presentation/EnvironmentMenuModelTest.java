package com.jreq.request.presentation;

import com.jreq.request.application.EnvironmentConfiguration;
import com.jreq.request.domain.EnvironmentScope;
import com.jreq.request.domain.EnvironmentVariable;
import com.jreq.request.domain.RequestCollection;
import com.jreq.request.domain.RequestEnvironment;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EnvironmentMenuModelTest {
    private static final Instant NOW = Instant.parse("2026-07-30T12:00:00Z");

    @Test
    void groupsEveryEnvironmentByItsOwningScope() {
        RequestCollection payments = collection("Payments");
        RequestCollection catalog = collection("Catalog");
        RequestEnvironment global = environment("Local", EnvironmentScope.global());
        RequestEnvironment paymentQa = environment("QA", EnvironmentScope.collection(payments.id()));
        RequestEnvironment catalogDev = environment("Development", EnvironmentScope.collection(catalog.id()));

        EnvironmentMenuModel menu = EnvironmentMenuModel.from(
                configuration(global, paymentQa, catalogDev),
                List.of(payments, catalog));

        assertThat(menu.groups()).extracting(EnvironmentMenuModel.Group::label)
                .containsExactly("GLOBAL ENVIRONMENTS", "COLLECTION · CATALOG", "COLLECTION · PAYMENTS");
        assertThat(menu.groups().get(0).entries()).extracting(EnvironmentMenuModel.Entry::label)
                .containsExactly("Local");
        assertThat(menu.groups().get(1).entries()).extracting(EnvironmentMenuModel.Entry::label)
                .containsExactly("Development");
        assertThat(menu.groups().get(2).entries()).extracting(EnvironmentMenuModel.Entry::label)
                .containsExactly("QA");
    }

    @Test
    void reportsHowManyGlobalVariablesAreActive() {
        EnvironmentVariable enabled = variable("host", true);
        EnvironmentVariable disabled = variable("token", false);

        EnvironmentMenuModel menu = EnvironmentMenuModel.from(
                new EnvironmentConfiguration(List.of(enabled, disabled), List.of()),
                List.of());

        assertThat(menu.globalsOnlyLabel()).isEqualTo("Globals only · 1 active variable");
    }

    @Test
    void explainsWhenThereAreNoNamedEnvironments() {
        EnvironmentMenuModel emptyMenu = EnvironmentMenuModel.from(
                EnvironmentConfiguration.empty(),
                List.of());

        assertThat(emptyMenu.groups()).isEmpty();
        assertThat(emptyMenu.emptyMessage()).isEqualTo("No named environments configured");
    }

    private RequestCollection collection(String name) {
        return new RequestCollection(UUID.randomUUID(), name, NOW, NOW);
    }

    private RequestEnvironment environment(String name, EnvironmentScope scope) {
        return new RequestEnvironment(UUID.randomUUID(), name, scope, List.of(), NOW, NOW);
    }

    private EnvironmentConfiguration configuration(RequestEnvironment... environments) {
        return new EnvironmentConfiguration(List.of(), List.of(environments));
    }

    private EnvironmentVariable variable(String key, boolean enabled) {
        return new EnvironmentVariable(UUID.randomUUID(), key, key + "-value", enabled, false, 0);
    }
}
