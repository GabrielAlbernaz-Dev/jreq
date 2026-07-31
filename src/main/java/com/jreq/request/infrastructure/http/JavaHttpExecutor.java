package com.jreq.request.infrastructure.http;

import com.jreq.request.application.HttpExecutor;
import com.jreq.request.application.HttpResponseFailure;
import com.jreq.request.application.HttpResponseResult;
import com.jreq.request.application.HttpResponseSuccess;
import com.jreq.request.domain.HttpRequestDefinition;
import com.jreq.request.domain.KeyValueEntry;
import com.jreq.shared.exception.ErrorCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class JavaHttpExecutor implements HttpExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(JavaHttpExecutor.class);

    private final HttpClient client;
    private final Duration requestTimeout;

    public JavaHttpExecutor(Duration requestTimeout) {
        this(HttpClient.newBuilder()
                .connectTimeout(requestTimeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build(), requestTimeout);
    }

    public JavaHttpExecutor(HttpClient client, Duration requestTimeout) {
        this.client = Objects.requireNonNull(client, "client");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        if (requestTimeout.isZero() || requestTimeout.isNegative()) {
            throw new IllegalArgumentException("requestTimeout must be positive");
        }
    }

    @Override
    public CompletableFuture<HttpResponseResult> execute(HttpRequestDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        long startedAt = System.nanoTime();

        final HttpRequest request;
        try {
            URI uri = URI.create(buildUrl(definition));
            request = createRequest(definition, uri);
            LOGGER.debug("Executing {} request", definition.method());
        } catch (IllegalArgumentException exception) {
            return CompletableFuture.completedFuture(new HttpResponseFailure(
                    ErrorCategory.INVALID_URL,
                    "The request URL or headers are invalid.",
                    elapsedSince(startedAt)
            ));
        }

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .<HttpResponseResult>handle((response, error) -> {
                    Duration duration = elapsedSince(startedAt);
                    if (error != null) {
                        Throwable cause = unwrap(error);
                        ErrorCategory category = categorize(cause);
                        LOGGER.warn("HTTP execution failed: category={}, type={}",
                                category, cause.getClass().getSimpleName());
                        return new HttpResponseFailure(category, messageFor(category), duration);
                    }

                    byte[] body = response.body();
                    return new HttpResponseSuccess(
                            response.statusCode(),
                            response.headers().map(),
                            body,
                            duration,
                            body.length
                    );
                });
    }

    private HttpRequest createRequest(HttpRequestDefinition definition, URI uri) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(requestTimeout);
        definition.headers().stream()
                .filter(KeyValueEntry::enabled)
                .filter(entry -> !entry.key().isBlank())
                .forEach(entry -> builder.header(entry.key(), entry.value()));

        if (definition.body().isPresent()
                && definition.headers().stream().noneMatch(this::isEnabledContentType)) {
            builder.header("Content-Type", definition.body().contentType());
        }

        HttpRequest.BodyPublisher publisher = definition.body().isPresent()
                ? HttpRequest.BodyPublishers.ofByteArray(definition.body().bytes())
                : HttpRequest.BodyPublishers.noBody();
        return builder.method(definition.method().name(), publisher).build();
    }

    private boolean isEnabledContentType(KeyValueEntry entry) {
        return entry.enabled() && entry.key().equalsIgnoreCase("Content-Type");
    }

    private String buildUrl(HttpRequestDefinition definition) {
        String query = definition.queryParameters().stream()
                .filter(KeyValueEntry::enabled)
                .filter(entry -> !entry.key().isBlank())
                .map(entry -> encode(entry.key()) + "=" + encode(entry.value()))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
        if (query.isEmpty()) {
            return definition.url();
        }
        return definition.url() + (definition.url().contains("?") ? "&" : "?") + query;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private Duration elapsedSince(long startedAt) {
        return Duration.ofNanos(Math.max(0L, System.nanoTime() - startedAt));
    }

    private Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException) && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private ErrorCategory categorize(Throwable error) {
        if (error instanceof HttpTimeoutException) {
            return ErrorCategory.TIMEOUT;
        }
        if (error instanceof UnknownHostException) {
            return ErrorCategory.DNS_ERROR;
        }
        if (error instanceof ConnectException) {
            return ErrorCategory.CONNECTION_REFUSED;
        }
        if (error instanceof SSLException) {
            return ErrorCategory.TLS_ERROR;
        }
        return ErrorCategory.UNKNOWN;
    }

    private String messageFor(ErrorCategory category) {
        return switch (category) {
            case DNS_ERROR -> "The server address could not be resolved.";
            case CONNECTION_REFUSED -> "The server refused the connection.";
            case TIMEOUT -> "The request timed out.";
            case TLS_ERROR -> "A secure connection could not be established.";
            default -> "The request could not be completed.";
        };
    }
}
