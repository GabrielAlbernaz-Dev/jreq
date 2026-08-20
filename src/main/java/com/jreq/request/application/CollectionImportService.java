package com.jreq.request.application;

import com.jreq.request.domain.RequestCollection;
import com.jreq.shared.concurrent.AsyncTaskExecutor;
import com.jreq.shared.exception.ErrorCategory;
import com.jreq.shared.exception.JReqException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Imports a collection file into the workspace. The pipeline is: file safety checks
 * (type, size, content sniffing) → parse → validate contract → map to domain → persist
 * atomically. Everything runs on the database executor so the JavaFX thread never
 * blocks and the import is serialized with the other database work.
 */
public final class CollectionImportService {
    static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    private static final String SIZE_LIMIT_LABEL = "10 MB";

    private final CollectionRepository collectionRepository;
    private final AsyncTaskExecutor databaseExecutor;
    private final CollectionImportParser parser;
    private final CollectionImportMapper mapper;

    public CollectionImportService(
            CollectionRepository collectionRepository,
            AsyncTaskExecutor databaseExecutor
    ) {
        this(collectionRepository, databaseExecutor, new CollectionImportParser(),
                new CollectionImportMapper());
    }

    public CollectionImportService(
            CollectionRepository collectionRepository,
            AsyncTaskExecutor databaseExecutor,
            CollectionImportParser parser,
            CollectionImportMapper mapper
    ) {
        this.collectionRepository = Objects.requireNonNull(collectionRepository, "collectionRepository");
        this.databaseExecutor = Objects.requireNonNull(databaseExecutor, "databaseExecutor");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    public CompletableFuture<CollectionImportResult> importCollection(Path file) {
        Objects.requireNonNull(file, "file");
        return databaseExecutor.submit(() -> doImport(file));
    }

    private CollectionImportResult doImport(Path file) {
        byte[] content = readImportFile(file);
        CollectionImportDocument document = parser.parse(content);
        CollectionImportMapper.CollectionImportMapping mapping = mapper.map(document);
        RequestCollection persisted = collectionRepository.saveImported(mapping.importedCollection());
        return new CollectionImportResult(
                persisted,
                mapping.importedCollection().requests().size(),
                mapping.skippedRequestCount(),
                mapping.warnings());
    }

    private byte[] readImportFile(Path file) {
        String fileName = file.getFileName() == null ? "" : file.getFileName().toString();
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".json")) {
            throw invalid("Only .json collection files can be imported.");
        }
        if (!Files.isRegularFile(file)) {
            throw invalid("The selected file is not a readable file.");
        }
        long size;
        try {
            size = Files.size(file);
        } catch (IOException unreadable) {
            throw invalid("The file could not be read.");
        }
        if (size > MAX_FILE_BYTES) {
            throw invalid("The file is larger than the " + SIZE_LIMIT_LABEL + " import limit.");
        }
        byte[] content;
        try (InputStream input = Files.newInputStream(file)) {
            content = input.readNBytes((int) MAX_FILE_BYTES + 1);
        } catch (IOException unreadable) {
            throw invalid("The file could not be read.");
        }
        if (content.length > MAX_FILE_BYTES) {
            throw invalid("The file is larger than the " + SIZE_LIMIT_LABEL + " import limit.");
        }
        if (!looksLikeJsonObject(content)) {
            throw invalid("The file does not look like a JSON collection document.");
        }
        return content;
    }

    /**
     * Sniffs the payload before parsing: after an optional UTF-8 BOM and JSON
     * whitespace, the first byte must be '{'. This rejects executables, HTML pages and
     * other binaries renamed to .json without executing or interpreting them.
     */
    private boolean looksLikeJsonObject(byte[] content) {
        int index = 0;
        if (content.length >= 3
                && (content[0] & 0xFF) == 0xEF
                && (content[1] & 0xFF) == 0xBB
                && (content[2] & 0xFF) == 0xBF) {
            index = 3;
        }
        while (index < content.length && isJsonWhitespace(content[index])) {
            index++;
        }
        return index < content.length && content[index] == '{';
    }

    private boolean isJsonWhitespace(byte value) {
        return value == ' ' || value == '\t' || value == '\n' || value == '\r';
    }

    private JReqException invalid(String message) {
        return new JReqException(ErrorCategory.VALIDATION_ERROR, message, null);
    }
}
