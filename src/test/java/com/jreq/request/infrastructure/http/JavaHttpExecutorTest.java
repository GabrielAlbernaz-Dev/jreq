package com.jreq.request.infrastructure.http;

import com.jreq.request.application.HttpResponseResult;
import com.jreq.request.application.HttpResponseSuccess;
import com.jreq.request.domain.HttpMethod;
import com.jreq.request.domain.HttpRequestDefinition;
import com.jreq.request.domain.KeyValueEntry;
import com.jreq.request.domain.RequestBody;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class JavaHttpExecutorTest {
    private final CountDownLatch requestReceived = new CountDownLatch(1);
    private final CountDownLatch releaseResponse = new CountDownLatch(1);

    private HttpServer server;
    private ExecutorService serverExecutor;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/users", this::handleRequest);
        serverExecutor = Executors.newSingleThreadExecutor();
        server.setExecutor(serverExecutor);
        server.start();
    }

    @AfterEach
    void stopServer() {
        releaseResponse.countDown();
        server.stop(0);
        serverExecutor.shutdownNow();
    }

    @Test
    void executesGetAsynchronouslyAndCapturesResponseData() throws Exception {
        JavaHttpExecutor executor = new JavaHttpExecutor(Duration.ofSeconds(3));
        HttpRequestDefinition request = new HttpRequestDefinition(
                UUID.randomUUID(),
                "Local users",
                HttpMethod.GET,
                "http://127.0.0.1:" + server.getAddress().getPort() + "/users",
                List.of(new KeyValueEntry(UUID.randomUUID(), "page", "1", true)),
                List.of(),
                RequestBody.none()
        );

        CompletableFuture<HttpResponseResult> resultFuture = executor.execute(request);

        assertThat(requestReceived.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(resultFuture.isDone()).isFalse();
        releaseResponse.countDown();

        HttpResponseResult result = resultFuture.get(3, TimeUnit.SECONDS);
        assertThat(result).isInstanceOf(HttpResponseSuccess.class);
        HttpResponseSuccess success = (HttpResponseSuccess) result;
        assertThat(success.statusCode()).isEqualTo(200);
        assertThat(success.bodyAsUtf8()).isEqualTo("{\"result\":\"ok\"}");
        assertThat(success.headers()).containsKey("x-jreq-test");
        assertThat(success.headers().get("x-jreq-test")).containsExactly("local");
        assertThat(success.size()).isEqualTo(success.body().length);
        assertThat(success.duration()).isPositive();
    }

    private void handleRequest(HttpExchange exchange) throws IOException {
        try (exchange) {
            requestReceived.countDown();
            try {
                if (!releaseResponse.await(2, TimeUnit.SECONDS)) {
                    exchange.sendResponseHeaders(504, -1);
                    return;
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                exchange.sendResponseHeaders(500, -1);
                return;
            }

            assertThat(exchange.getRequestMethod()).isEqualTo("GET");
            assertThat(exchange.getRequestURI().getQuery()).isEqualTo("page=1");
            byte[] body = "{\"result\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("X-jREQ-Test", "local");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
        }
    }
}
