package com.jreq.bootstrap;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AppDirectoriesTest {
    private final Path home = Path.of("/users/tester");

    @Test
    void resolvesLinuxXdgDirectoryWhenConfigured() {
        Path result = AppDirectories.resolveDataDirectory(
                "Linux", home, Map.of("XDG_DATA_HOME", "/data/tester"));

        assertThat(result).isEqualTo(Path.of("/data/tester/jreq"));
    }

    @Test
    void fallsBackToLinuxLocalShareDirectory() {
        Path result = AppDirectories.resolveDataDirectory("Linux", home, Map.of());

        assertThat(result).isEqualTo(home.resolve(".local/share/jreq"));
    }

    @Test
    void resolvesWindowsAppDataDirectory() {
        Path result = AppDirectories.resolveDataDirectory(
                "Windows 11", home, Map.of("APPDATA", "/roaming"));

        assertThat(result).isEqualTo(Path.of("/roaming/jREQ"));
    }

    @Test
    void resolvesMacApplicationSupportDirectory() {
        Path result = AppDirectories.resolveDataDirectory("Mac OS X", home, Map.of());

        assertThat(result).isEqualTo(home.resolve("Library/Application Support/jREQ"));
    }
}
