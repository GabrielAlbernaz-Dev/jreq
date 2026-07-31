package com.jreq.bootstrap;

import com.jreq.shared.validation.Constraints;

import java.nio.file.Path;

public record DatabaseFilename(String value) {
    private static final String VIOLATION_MESSAGE = "database.filename must be a file name";

    public DatabaseFilename {
        value = Constraints.requiredText(value, "value", VIOLATION_MESSAGE);
        Path path = Path.of(value);
        Constraints.requireArgument(
                !path.isAbsolute()
                        && path.getNameCount() == 1
                        && !value.equals(".")
                        && !value.equals(".."),
                VIOLATION_MESSAGE);
    }

    public static DatabaseFilename of(String value) {
        return new DatabaseFilename(value);
    }
}
