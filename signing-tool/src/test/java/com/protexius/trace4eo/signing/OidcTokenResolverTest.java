package com.protexius.trace4eo.signing;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OidcTokenResolverTest {

    private final SigstoreDeviceFlowClient mockClient = mock(SigstoreDeviceFlowClient.class);

    @Test
    void resolve_returnsCiToken() {
        var resolver = new OidcTokenResolver("test-token", mockClient);

        assertEquals("test-token", resolver.resolve());
        verifyNoInteractions(mockClient);
    }

    @Test
    void resolve_cachesToken() {
        var resolver = new OidcTokenResolver("test-token", mockClient);

        String first = resolver.resolve();
        String second = resolver.resolve();

        assertEquals(first, second);
        verifyNoInteractions(mockClient);
    }

    @Test
    void resolve_returnsNullWhenNoTokenAndDeviceFlowFails() throws IOException, InterruptedException {
        when(mockClient.discoverEndpoints(any())).thenThrow(new IOException("simulated failure"));
        var resolver = new OidcTokenResolver(null, mockClient);

        assertNull(resolver.resolve());
    }
}
