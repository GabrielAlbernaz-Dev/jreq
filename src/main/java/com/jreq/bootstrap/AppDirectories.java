package com.jreq.bootstrap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class AppDirectories {
    private final Path dataDirectory;

    public AppDirectories(Path dataDirectory) {
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory").toAbsolutePath().normalize();
    }

    public static AppDirectories systemDefault() {
        Path home = Path.of(System.getProperty("user.home"));
        Path resolved = resolveDataDirectory(System.getProperty("os.name"), home, System.getenv());
        return new AppDirectories(resolved);
    }

    public static Path resolveDataDirectory(String osName, Path userHome, Map<String, String> environment) {
        Objects.requireNonNull(osName, "osName");
        Objects.requireNonNull(userHome, "userHome");
        Objects.requireNonNull(environment, "environment");
        String normalizedOs = osName.toLowerCase(Locale.ROOT);

        if (normalizedOs.contains("win")) {
            String appData = environment.getOrDefault("APPDATA", "");
            Path root = appData.isBlank() ? userHome.resolve("AppData/Roaming") : Path.of(appData);
            return root.resolve("jREQ");
        }
        if (normalizedOs.contains("mac")) {
            return userHome.resolve("Library/Application Support/jREQ");
        }

        String xdgDataHome = environment.getOrDefault("XDG_DATA_HOME", "");
        Path root = xdgDataHome.isBlank() ? userHome.resolve(".local/share") : Path.of(xdgDataHome);
        return root.resolve("jreq");
    }

    public Path ensureDataDirectory() {
        try {
            return Files.createDirectories(dataDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create the jREQ data directory", exception);
        }
    }

    public Path dataDirectory() {
        return dataDirectory;
    }

    public Path databasePath(DatabaseFilename databaseFilename) {
        return dataDirectory.resolve(Objects.requireNonNull(databaseFilename, "databaseFilename").value());
    }
}
