package com.protexius.trace4eo.signing;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class OidcTokenResolverTest {

    private final HttpClient mockHttpClient = mock(HttpClient.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void resolve_returnsCiToken() {
        var resolver = new OidcTokenResolver("test-token", mockHttpClient, objectMapper);

        assertEquals("test-token", resolver.resolve());
        verifyNoInteractions(mockHttpClient);
    }

    @Test
    void resolve_cachesToken() {
        var resolver = new OidcTokenResolver("test-token", mockHttpClient, objectMapper);

        String first = resolver.resolve();
        String second = resolver.resolve();

        assertEquals(first, second);
        verifyNoInteractions(mockHttpClient);
    }

    @Test
    void resolve_returnsNullWhenNoTokenAndDeviceFlowFails() {
        var resolver = new OidcTokenResolver(null, mockHttpClient, objectMapper);

        // HttpClient.send() throws because no mock response is configured — device flow fails gracefully
        assertNull(resolver.resolve());
    }
}
