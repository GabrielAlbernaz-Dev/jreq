package com.jreq.request.presentation;

import com.jreq.request.application.EnvironmentConfiguration;
import com.jreq.request.domain.EnvironmentScope;
import com.jreq.request.domain.RequestCollection;
import com.jreq.request.domain.RequestEnvironment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

record EnvironmentMenuModel(
        String globalsOnlyLabel,
        List<Group> groups,
        String emptyMessage
) {
    EnvironmentMenuModel {
        globalsOnlyLabel = Objects.requireNonNull(globalsOnlyLabel, "globalsOnlyLabel");
        groups = List.copyOf(Objects.requireNonNull(groups, "groups"));
        emptyMessage = Objects.requireNonNull(emptyMessage, "emptyMessage");
    }

    static EnvironmentMenuModel from(
            EnvironmentConfiguration configuration,
            List<RequestCollection> collections
    ) {
        Objects.requireNonNull(configuration, "configuration");
        List<RequestCollection> knownCollections =
                List.copyOf(Objects.requireNonNull(collections, "collections"));

        List<Group> groups = new ArrayList<>();
        List<RequestEnvironment> globalEnvironments = configuration.environments().stream()
                .filter(environment -> environment.scope() instanceof EnvironmentScope.Global)
                .toList();
        if (!globalEnvironments.isEmpty()) {
            groups.add(new Group("GLOBAL ENVIRONMENTS", entries(globalEnvironments)));
        }

        Map<UUID, List<RequestEnvironment>> collectionEnvironments = new LinkedHashMap<>();
        configuration.environments().stream()
                .filter(environment -> environment.scope() instanceof EnvironmentScope.Collection)
                .forEach(environment -> {
                    UUID collectionId = ((EnvironmentScope.Collection) environment.scope()).collectionId();
                    collectionEnvironments.computeIfAbsent(collectionId, ignored -> new ArrayList<>())
                            .add(environment);
                });
        collectionEnvironments.entrySet().stream()
                .sorted(Comparator.comparing(
                        entry -> collectionName(entry.getKey(), knownCollections),
                        String.CASE_INSENSITIVE_ORDER))
                .map(entry -> new Group(
                        "COLLECTION · " + collectionName(entry.getKey(), knownCollections)
                                .toUpperCase(Locale.ROOT),
                        entries(entry.getValue())))
                .forEach(groups::add);

        long enabledGlobals = configuration.globals().stream()
                .filter(variable -> variable.enabled())
                .count();
        return new EnvironmentMenuModel(
                globalsOnlyLabel(enabledGlobals),
                groups,
                "No named environments configured");
    }

    private static List<Entry> entries(List<RequestEnvironment> environments) {
        return environments.stream()
                .map(environment -> new Entry(environment, environment.name()))
                .sorted(Comparator.comparing(Entry::label, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static String globalsOnlyLabel(long enabledGlobals) {
        if (enabledGlobals == 0) {
            return "Globals only";
        }
        String noun = enabledGlobals == 1 ? "variable" : "variables";
        return "Globals only · " + enabledGlobals + " active " + noun;
    }

    private static String collectionName(UUID collectionId, List<RequestCollection> collections) {
        return collections.stream()
                .filter(collection -> collection.id().equals(collectionId))
                .map(RequestCollection::name)
                .findFirst()
                .orElse("Unknown collection");
    }

    record Entry(RequestEnvironment environment, String label) {
        Entry {
            Objects.requireNonNull(environment, "environment");
            label = Objects.requireNonNull(label, "label");
        }
    }

    record Group(String label, List<Entry> entries) {
        Group {
            label = Objects.requireNonNull(label, "label");
            entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        }
    }
}
