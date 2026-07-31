package com.jreq.bootstrap;

import com.jreq.shared.validation.Constraints;

import java.util.Objects;
import java.util.Properties;

final class RequiredProperties {
    private final Properties values;

    RequiredProperties(Properties values) {
        Objects.requireNonNull(values, "values");
        this.values = new Properties();
        this.values.putAll(values);
    }

    String text(String key) {
        Objects.requireNonNull(key, "key");
        String value = values.getProperty(key);
        Constraints.requireArgument(value != null, key + " is required");
        return Constraints.requiredText(value, key, key + " is required");
    }

    long wholeNumber(String key) {
        String value = text(key);
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be a whole number", exception);
        }
    }
}
