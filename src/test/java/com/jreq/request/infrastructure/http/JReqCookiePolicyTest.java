package com.jreq.request.infrastructure.http;

import org.junit.jupiter.api.Test;

import java.net.HttpCookie;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class JReqCookiePolicyTest {
    private final JReqCookiePolicy policy = new JReqCookiePolicy();

    @Test
    void acceptsCookiesOnlyFromTheirOriginDomain() {
        HttpCookie valid = new HttpCookie("session", "secret");
        valid.setDomain("example.com");
        HttpCookie foreign = new HttpCookie("session", "secret");
        foreign.setDomain("attacker.example");
        HttpCookie topLevel = new HttpCookie("session", "secret");
        topLevel.setDomain("com");

        assertThat(policy.shouldAccept(URI.create("https://api.example.com/login"), valid)).isTrue();
        assertThat(policy.shouldAccept(URI.create("https://api.example.com/login"), foreign)).isFalse();
        assertThat(policy.shouldAccept(URI.create("https://api.example.com/login"), topLevel)).isFalse();
    }

    @Test
    void enforcesSecureCookiePrefixes() {
        HttpCookie secure = new HttpCookie("__Secure-token", "secret");
        secure.setSecure(true);
        HttpCookie insecure = new HttpCookie("__Secure-token", "secret");
        HttpCookie host = new HttpCookie("__Host-session", "secret");
        host.setSecure(true);
        host.setPath("/");
        HttpCookie hostWithDomain = new HttpCookie("__Host-session", "secret");
        hostWithDomain.setSecure(true);
        hostWithDomain.setPath("/");
        hostWithDomain.setDomain("example.com");

        assertThat(policy.shouldAccept(URI.create("https://example.com"), secure)).isTrue();
        assertThat(policy.shouldAccept(URI.create("http://example.com"), secure)).isFalse();
        assertThat(policy.shouldAccept(URI.create("https://example.com"), insecure)).isFalse();
        assertThat(policy.shouldAccept(URI.create("https://example.com"), host)).isTrue();
        assertThat(policy.shouldAccept(URI.create("https://example.com"), hostWithDomain)).isFalse();
    }
}
