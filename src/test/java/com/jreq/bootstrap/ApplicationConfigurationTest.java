package com.jreq.bootstrap;

import com.jreq.shared.exception.ErrorCategory;
import com.jreq.shared.exception.JReqException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicationConfigurationTest {
    @Test
    void loadsThePackagedApplicationProperties() {
        ApplicationConfiguration configuration = ApplicationConfiguration.load();

        assertThat(configuration.applicationName()).isEqualTo("jREQ");
        assertThat(configuration.applicationVersion()).isEqualTo("0.1.0-SNAPSHOT");
        assertThat(configuration.databaseFilename().value()).isEqualTo("jreq.db");
        assertThat(configuration.httpTimeout().value()).isEqualTo(Duration.ofSeconds(30));
        assertThat(configuration.windowTitle()).isEqualTo("jREQ 0.1.0-SNAPSHOT");
    }

    @Test
    void rejectsMissingProperties() {
        Properties properties = validProperties();
        properties.remove("application.name");

        assertConfigurationFailure(properties);
    }

    @Test
    void rejectsInvalidTimeouts() {
        Properties properties = validProperties();
        properties.setProperty("http.timeout.seconds", "0");

        assertConfigurationFailure(properties);

        properties.setProperty("http.timeout.seconds", "-1");
        assertConfigurationFailure(properties);

        properties.setProperty("http.timeout.seconds", "not-a-number");
        assertConfigurationFailure(properties);
    }

    @Test
    void rejectsDatabasePathsOutsideTheDataDirectory() {
        Properties properties = validProperties();
        properties.setProperty("database.filename", "../outside.db");

        assertConfigurationFailure(properties);
    }

    private Properties validProperties() {
        Properties properties = new Properties();
        properties.setProperty("application.name", "jREQ");
        properties.setProperty("application.version", "test");
        properties.setProperty("database.filename", "test.db");
        properties.setProperty("http.timeout.seconds", "15");
        return properties;
    }

    private void assertConfigurationFailure(Properties properties) {
        assertThatThrownBy(() -> ApplicationConfiguration.from(properties))
                .isInstanceOfSatisfying(JReqException.class, exception ->
                        assertThat(exception.category()).isEqualTo(ErrorCategory.CONFIGURATION_ERROR));
    }
}
