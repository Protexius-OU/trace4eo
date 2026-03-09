package com.guardtime.trace4eo.signing;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OidcTokenResolverTest {

    @Test
    void resolve_withOverride_returnsSameTokenOnMultipleCalls() {
        OidcTokenResolver resolver = new OidcTokenResolver("my-token");

        assertEquals("my-token", resolver.resolve());
        assertEquals("my-token", resolver.resolve());
    }

    @Test
    void resolve_withNullBrowserResult_returnsNullAndDoesNotRetry() {
        var callCount = new AtomicInteger(0);
        OidcTokenResolver resolver = new OidcTokenResolver(null, () -> {
            callCount.incrementAndGet();
            return null;
        });

        assertNull(resolver.resolve());
        assertNull(resolver.resolve());
        assertEquals(1, callCount.get());
    }

    @Test
    void resolve_callsBrowserLoginOnlyOnce_forMultipleCalls() {
        var callCount = new AtomicInteger(0);
        OidcTokenResolver resolver = new OidcTokenResolver(null, () -> {
            callCount.incrementAndGet();
            return "browser-token";
        });

        assertEquals("browser-token", resolver.resolve());
        assertEquals("browser-token", resolver.resolve());
        assertEquals("browser-token", resolver.resolve());
        assertEquals(1, callCount.get());
    }
}
