package com.jreq.bootstrap;

import com.jreq.request.application.HttpTimeout;
import com.jreq.shared.exception.ErrorCategory;
import com.jreq.shared.exception.JReqException;
import com.jreq.shared.validation.Constraints;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;

public record ApplicationConfiguration(
        String applicationName,
        String applicationVersion,
        DatabaseFilename databaseFilename,
        HttpTimeout httpTimeout
) {
    private static final String RESOURCE = "/application.properties";
    private static final String APPLICATION_NAME = "application.name";
    private static final String APPLICATION_VERSION = "application.version";
    private static final String DATABASE_FILENAME = "database.filename";
    private static final String HTTP_TIMEOUT_SECONDS = "http.timeout.seconds";

    public ApplicationConfiguration {
        applicationName = Constraints.requiredText(
                applicationName, "applicationName", APPLICATION_NAME + " is required");
        applicationVersion = Constraints.requiredText(
                applicationVersion, "applicationVersion", APPLICATION_VERSION + " is required");
        Objects.requireNonNull(databaseFilename, "databaseFilename");
        Objects.requireNonNull(httpTimeout, "httpTimeout");
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
        try {
            RequiredProperties required = new RequiredProperties(properties);
            return new ApplicationConfiguration(
                    required.text(APPLICATION_NAME),
                    required.text(APPLICATION_VERSION),
                    DatabaseFilename.of(required.text(DATABASE_FILENAME)),
                    HttpTimeout.ofSeconds(required.wholeNumber(HTTP_TIMEOUT_SECONDS)));
        } catch (IllegalArgumentException exception) {
            throw failure("Application configuration is invalid", exception);
        }
    }

    public String windowTitle() {
        return applicationName + " " + applicationVersion;
    }

    private static JReqException failure(String message, Exception cause) {
        return new JReqException(ErrorCategory.CONFIGURATION_ERROR, message, cause);
    }
}
