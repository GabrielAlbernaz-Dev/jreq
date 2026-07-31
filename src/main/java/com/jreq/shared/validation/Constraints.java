package com.jreq.shared.validation;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

public final class Constraints {
    private Constraints() {
    }

    public static String requiredText(
            String value,
            String parameterName,
            String violationMessage
    ) {
        String normalized = Objects.requireNonNull(value, parameterName).strip();
        requireArgument(!normalized.isEmpty(), violationMessage);
        return normalized;
    }

    public static Duration positive(
            Duration value,
            String parameterName,
            String violationMessage
    ) {
        Duration duration = Objects.requireNonNull(value, parameterName);
        requireArgument(!duration.isZero() && !duration.isNegative(), violationMessage);
        return duration;
    }

    public static Duration nonNegative(
            Duration value,
            String parameterName,
            String violationMessage
    ) {
        Duration duration = Objects.requireNonNull(value, parameterName);
        requireArgument(!duration.isNegative(), violationMessage);
        return duration;
    }

    public static int positive(int value, String violationMessage) {
        requireArgument(value > 0, violationMessage);
        return value;
    }

    public static int nonNegative(int value, String violationMessage) {
        requireArgument(value >= 0, violationMessage);
        return value;
    }

    public static long nonNegative(long value, String violationMessage) {
        requireArgument(value >= 0, violationMessage);
        return value;
    }

    public static int inRange(int value, int minimum, int maximum, String violationMessage) {
        requireArgument(value >= minimum && value <= maximum, violationMessage);
        return value;
    }

    public static void requireArgument(boolean condition, String violationMessage) {
        if (!condition) {
            throw new IllegalArgumentException(violationMessage);
        }
    }

    public static <T, K> List<T> immutableUniqueList(
            List<T> values,
            String parameterName,
            Function<T, K> keyExtractor,
            Function<K, String> duplicateMessage
    ) {
        List<T> immutableValues = List.copyOf(Objects.requireNonNull(values, parameterName));
        Objects.requireNonNull(keyExtractor, "keyExtractor");
        Objects.requireNonNull(duplicateMessage, "duplicateMessage");
        Set<K> keys = new HashSet<>();
        for (T value : immutableValues) {
            K key = keyExtractor.apply(value);
            requireArgument(keys.add(key), duplicateMessage.apply(key));
        }
        return immutableValues;
    }
}
