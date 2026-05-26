package com.protexius.trace4eo.signing.commands;

import com.protexius.trace4eo.signing.SigstoreDeviceFlowClient;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class SigstoreTokenDaemonTest {

    private final SigstoreTokenDaemon daemon =
        new SigstoreTokenDaemon(mock(SigstoreDeviceFlowClient.class), new ObjectMapper());

    @Test
    void jwtExp_returnsExpClaim() {
        String token = jwt("{\"alg\":\"RS256\"}", "{\"exp\":1700000000}");

        assertEquals(1700000000L, daemon.jwtExp(token));
    }

    @Test
    void jwtExp_handlesPayloadRequiringPadding() {
        String token = jwt("{\"alg\":\"RS256\"}", "{\"exp\":1700000000,\"sub\":\"a\"}");

        assertEquals(1700000000L, daemon.jwtExp(token));
    }

    @Test
    void jwtExp_throwsOnMalformedToken() {
        assertThrows(IllegalArgumentException.class, () -> daemon.jwtExp("not-a-jwt"));
    }

    @Test
    void jwtExp_throwsWhenExpClaimMissing() {
        String token = jwt("{\"alg\":\"RS256\"}", "{\"sub\":\"alice\"}");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> daemon.jwtExp(token));
        assertEquals("JWT has no exp claim", ex.getMessage());
    }

    private static String jwt(String header, String payload) {
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        return enc.encodeToString(header.getBytes(StandardCharsets.UTF_8))
            + "." + enc.encodeToString(payload.getBytes(StandardCharsets.UTF_8))
            + ".sig";
    }
}
