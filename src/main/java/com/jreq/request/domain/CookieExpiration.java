package com.jreq.request.domain;

import java.time.Instant;
import java.util.Objects;

public sealed interface CookieExpiration permits CookieExpiration.Session, CookieExpiration.At {
    static CookieExpiration session() {
        return new Session();
    }

    static CookieExpiration at(Instant instant) {
        return new At(instant);
    }

    record Session() implements CookieExpiration {
    }

    record At(Instant instant) implements CookieExpiration {
        public At {
            Objects.requireNonNull(instant, "instant");
        }
    }
}
