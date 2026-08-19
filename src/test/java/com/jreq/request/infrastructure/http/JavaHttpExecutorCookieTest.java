package com.jreq.request.infrastructure.http;

import com.jreq.request.application.HttpResponseSuccess;
import com.jreq.request.application.HttpTimeout;
import com.jreq.request.domain.CookieJarMode;
import com.jreq.request.domain.HttpMethod;
import com.jreq.request.domain.HttpRequestDefinition;
import com.jreq.request.domain.KeyValueEntry;
import com.jreq.request.domain.RequestAuthentication;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JavaHttpExecutorCookieTest {
    private HttpServer server;
    private java.util.concurrent.ExecutorService serverExecutor;
    private JavaHttpExecutor executor;
    private ManagedCookieStore cookieStore;
    private AtomicReference<String> receivedCookie;
    private List<String> redirectCookies;

    @BeforeEach
    void startServer() throws IOException {
        receivedCookie = new AtomicReference<>("");
        redirectCookies = new CopyOnWriteArrayList<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/login", this::login);
        server.createContext("/me", this::captureCookies);
        server.createContext("/redirect", this::redirect);
        server.createContext("/redirect-target", this::captureRedirectCookies);
        server.createContext("/cross-host", this::crossHostRedirect);
        server.createContext("/cross-target", this::captureRedirectCookies);
        server.createContext("/invalid", this::invalidCookie);
        serverExecutor = Executors.newCachedThreadPool();
        server.setExecutor(serverExecutor);
        server.start();
        cookieStore = new ManagedCookieStore();
        executor = new JavaHttpExecutor(HttpTimeout.of(Duration.ofSeconds(3)), cookieStore);
    }

    @AfterEach
    void stopServer() {
        if (executor != null) {
            executor.close();
        }
        if (server != null) {
            server.stop(0);
        }
        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
        }
    }

    @Test
    void capturesMultipleResponseCookiesAndSendsThemOnTheNextRequest() throws Exception {
        success(executor.execute(request("/login", CookieJarMode.ENABLED, List.of())));

        success(executor.execute(request("/me", CookieJarMode.ENABLED, List.of())));

        assertThat(receivedCookie.get()).contains("session=server-secret", "theme=dark");
        assertThat(cookieStore.snapshot()).extracting(cookie -> cookie.name())
                .containsExactlyInAnyOrder("session", "theme");
    }

    @Test
    void makesRedirectCookiesAvailableToTheRedirectedRequest() throws Exception {
        success(executor.execute(request("/redirect", CookieJarMode.ENABLED, List.of())));

        assertThat(redirectCookies).singleElement().asString().contains("redirect-token=step-one");
    }

    @Test
    void neverLeaksOriginCookiesAcrossARedirectToAnotherHostName() throws Exception {
        success(executor.execute(request("/login", CookieJarMode.ENABLED, List.of())));

        success(executor.execute(request("/cross-host", CookieJarMode.ENABLED, List.of())));

        assertThat(redirectCookies).singleElement().asString()
                .doesNotContain("session=", "theme=");
    }

    @Test
    void disabledJarNeitherCapturesNorSendsAutomaticCookies() throws Exception {
        success(executor.execute(request("/login", CookieJarMode.DISABLED, List.of())));

        success(executor.execute(request("/me", CookieJarMode.ENABLED, List.of())));

        assertThat(cookieStore.snapshot()).isEmpty();
        assertThat(receivedCookie.get()).isEmpty();
    }

    @Test
    void disabledJarStillSendsAnExplicitManualCookie() throws Exception {
        KeyValueEntry manual = new KeyValueEntry(
                UUID.randomUUID(), "Cookie", "manual-session=user-choice", true);

        success(executor.execute(request("/me", CookieJarMode.DISABLED, List.of(manual))));

        assertThat(receivedCookie.get()).contains("manual-session=user-choice");
        assertThat(cookieStore.snapshot()).isEmpty();
    }

    @Test
    void manualCookieOverridesTheSameJarNameAndKeepsOtherAutomaticCookies() throws Exception {
        success(executor.execute(request("/login", CookieJarMode.ENABLED, List.of())));
        KeyValueEntry manual = new KeyValueEntry(
                UUID.randomUUID(), "Cookie", "session=manual-value", true);

        success(executor.execute(request("/me", CookieJarMode.ENABLED, List.of(manual))));

        assertThat(receivedCookie.get())
                .contains("session=manual-value", "theme=dark")
                .doesNotContain("session=server-secret");
    }

    @Test
    void ignoresMalformedSetCookieWithoutFailingTheResponse() throws Exception {
        HttpResponseSuccess response = success(
                executor.execute(request("/invalid", CookieJarMode.ENABLED, List.of())));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(cookieStore.snapshot()).isEmpty();
    }

    @Test
    void capturesCookiesThatOnlyDeclareExpires() throws Exception {
        server.createContext("/expires-only", exchange -> {
            exchange.getResponseHeaders().add(
                    "Set-Cookie",
                    "remember=token; Path=/; Expires=Wed, 18 Aug 2027 12:00:00 GMT");
            respond(exchange, 200, "ok");
        });

        success(executor.execute(request("/expires-only", CookieJarMode.ENABLED, List.of())));

        assertThat(cookieStore.snapshot()).singleElement()
                .extracting(cookie -> cookie.name(), cookie -> cookie.value())
                .containsExactly("remember", "token");
    }

    @Test
    void closeIsIdempotentAndPreventsNewExecutions() {
        executor.close();
        executor.close();

        assertThatThrownBy(() -> executor.execute(request("/me", CookieJarMode.ENABLED, List.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
    }

    private HttpResponseSuccess success(
            java.util.concurrent.CompletableFuture<com.jreq.request.application.HttpResponseResult> future
    ) throws Exception {
        return (HttpResponseSuccess) future.get(4, TimeUnit.SECONDS);
    }

    private HttpRequestDefinition request(
            String path,
            CookieJarMode mode,
            List<KeyValueEntry> headers
    ) {
        return new HttpRequestDefinition(
                UUID.randomUUID(), "Cookie test", HttpMethod.GET,
                "http://127.0.0.1:" + server.getAddress().getPort() + path,
                List.of(), headers, RequestBody.none(), RequestAuthentication.none(), mode);
    }

    private void login(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Set-Cookie", "session=server-secret; Path=/; HttpOnly");
        exchange.getResponseHeaders().add("Set-Cookie", "theme=dark; Path=/; Max-Age=3600");
        respond(exchange, 200, "logged-in");
    }

    private void captureCookies(HttpExchange exchange) throws IOException {
        receivedCookie.set(String.join("; ", exchange.getRequestHeaders()
                .getOrDefault("Cookie", List.of())));
        respond(exchange, 200, "ok");
    }

    private void redirect(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Set-Cookie", "redirect-token=step-one; Path=/");
        exchange.getResponseHeaders().add("Location", "/redirect-target");
        respond(exchange, 302, "redirect");
    }

    private void crossHostRedirect(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add(
                "Location", "http://localhost:" + server.getAddress().getPort() + "/cross-target");
        respond(exchange, 302, "redirect");
    }

    private void captureRedirectCookies(HttpExchange exchange) throws IOException {
        redirectCookies.add(String.join("; ", exchange.getRequestHeaders()
                .getOrDefault("Cookie", List.of())));
        respond(exchange, 200, "redirected");
    }

    private void invalidCookie(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Set-Cookie", "not a valid cookie;;;;");
        respond(exchange, 200, "ok");
    }

    private void respond(HttpExchange exchange, int status, String content) throws IOException {
        try (exchange) {
            byte[] body = content.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
        }
    }
}
