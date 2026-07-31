package com.jreq.bootstrap;

import com.jreq.shared.exception.ErrorCategory;
import com.jreq.shared.exception.JReqException;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Objects;
import java.util.Properties;

public record ApplicationConfiguration(
        String applicationName,
        String applicationVersion,
        String databaseFilename,
        Duration httpTimeout
) {
    private static final String RESOURCE = "/application.properties";
    private static final String APPLICATION_NAME = "application.name";
    private static final String APPLICATION_VERSION = "application.version";
    private static final String DATABASE_FILENAME = "database.filename";
    private static final String HTTP_TIMEOUT_SECONDS = "http.timeout.seconds";

    public ApplicationConfiguration {
        applicationName = requireText(applicationName, APPLICATION_NAME);
        applicationVersion = requireText(applicationVersion, APPLICATION_VERSION);
        databaseFilename = AppDirectories.requireDatabaseFilename(databaseFilename);
        Objects.requireNonNull(httpTimeout, "httpTimeout");
        if (httpTimeout.isZero() || httpTimeout.isNegative()) {
            throw new IllegalArgumentException(HTTP_TIMEOUT_SECONDS + " must be positive");
        }
    }

    public static ApplicationConfiguration load() {
        Properties properties = new Properties();
        try (InputStream input = ApplicationConfiguration.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IOException("Missing classpath resource " + RESOURCE);
            }
            properties.load(input);
            return from(properties);
        } catch (IOException exception) {
            throw failure("Unable to load application configuration", exception);
        }
    }

    static ApplicationConfiguration from(Properties properties) {
        Objects.requireNonNull(properties, "properties");
        try {
            return new ApplicationConfiguration(
                    requiredProperty(properties, APPLICATION_NAME),
                    requiredProperty(properties, APPLICATION_VERSION),
                    requiredProperty(properties, DATABASE_FILENAME),
                    Duration.ofSeconds(positiveLong(properties, HTTP_TIMEOUT_SECONDS)));
        } catch (IllegalArgumentException exception) {
            throw failure("Application configuration is invalid", exception);
        }
    }

    public String windowTitle() {
        return applicationName + " " + applicationVersion;
    }

    private static String requiredProperty(Properties properties, String key) {
        return requireText(properties.getProperty(key), key);
    }

    private static String requireText(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value.strip();
    }

    private static long positiveLong(Properties properties, String key) {
        String value = requiredProperty(properties, key);
        try {
            long parsed = Long.parseLong(value);
            if (parsed < 1) {
                throw new IllegalArgumentException(key + " must be positive");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be a whole number", exception);
        }
    }

    private static JReqException failure(String message, Exception cause) {
        return new JReqException(ErrorCategory.CONFIGURATION_ERROR, message, cause);
    }
}
