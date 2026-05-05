package com.protexius.trace4eo.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class KeycloakBrokerTokenServiceTest {

    private KeycloakBrokerTokenService service;
    private HttpClient mockHttpClient;
    private HttpResponse<String> mockResponse = mock(HttpResponse.class);

    @BeforeEach
    void setUp() {
        mockHttpClient = mock(HttpClient.class);
        service = new KeycloakBrokerTokenService("http://localhost:8180", "trace4eo", mockHttpClient);  // uses @Autowired constructor
    }

    /** Creates a fake Sigstore JWT with the given exp claim (seconds since epoch). */
    private static String fakeJwt(long expSeconds) {
        String header = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("{\"alg\":\"RS256\"}".getBytes());
        String payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(("{\"exp\":" + expSeconds + ",\"email\":\"test@example.com\","
                + "\"iss\":\"https://oauth2.sigstore.dev/auth\"}").getBytes());
        return header + "." + payload + ".fakesig";
    }

    /** Creates a fake Keycloak JWT with a sub claim. */
    private static String fakeKeycloakJwt() {
        String header = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("{\"alg\":\"RS256\"}".getBytes());
        String payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("{\"sub\":\"user-123\",\"iss\":\"http://localhost:8180/realms/trace4eo\"}".getBytes());
        return header + "." + payload + ".fakesig";
    }

    private static String validJwt() {
        return fakeJwt(Instant.now().plusSeconds(3600).getEpochSecond());
    }

    private static String expiredJwt() {
        return fakeJwt(Instant.now().minusSeconds(60).getEpochSecond());
    }

    @Test
    void getSigstoreIdToken_validToken_returnsDirectly() throws Exception {
        String jwt = validJwt();
        String tokenResponse = """
            {
                "access_token": "dex-access-token",
                "id_token": "%s",
                "token_type": "Bearer"
            }
            """.formatted(jwt);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(tokenResponse);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(mockResponse);

        String result = service.getSigstoreIdToken(fakeKeycloakJwt());

        assertEquals(jwt, result);
        // Only one HTTP call (to Keycloak broker), no refresh
        verify(mockHttpClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getSigstoreIdToken_expiredToken_refreshesViaDex() throws Exception {
        String expiredToken = expiredJwt();
        String freshToken = validJwt();
        String brokerResponse = """
            {
                "access_token": "dex-access-token",
                "id_token": "%s",
                "refresh_token": "dex-refresh-token",
                "token_type": "Bearer"
            }
            """.formatted(expiredToken);
        String dexRefreshResponse = """
            {
                "access_token": "new-access-token",
                "id_token": "%s",
                "token_type": "Bearer"
            }
            """.formatted(freshToken);

        HttpResponse<String> dexResponse = mock(HttpResponse.class);
        when(dexResponse.statusCode()).thenReturn(200);
        when(dexResponse.body()).thenReturn(dexRefreshResponse);

        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(brokerResponse);

        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(mockResponse)   // first call: Keycloak broker
            .thenReturn(dexResponse);   // second call: Dex refresh

        String result = service.getSigstoreIdToken(fakeKeycloakJwt());

        assertEquals(freshToken, result);
        verify(mockHttpClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void getSigstoreIdToken_expiredToken_noRefreshToken_throwsException() throws Exception {
        String expiredToken = expiredJwt();
        String brokerResponse = """
            {
                "access_token": "dex-access-token",
                "id_token": "%s",
                "token_type": "Bearer"
            }
            """.formatted(expiredToken);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(brokerResponse);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(mockResponse);

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            service.getSigstoreIdToken(fakeKeycloakJwt())
        );

        assertTrue(exception.getMessage().contains("no refresh token available"));
    }

    @Test
    void getSigstoreIdToken_non200Status_throwsException() throws Exception {
        when(mockResponse.statusCode()).thenReturn(401);
        when(mockResponse.body()).thenReturn("Unauthorized");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(mockResponse);

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            service.getSigstoreIdToken(fakeKeycloakJwt())
        );

        assertTrue(exception.getMessage().contains("HTTP 401"));
    }

    @Test
    void getSigstoreIdToken_forbidden_throwsBrokerTokenException() throws Exception {
        when(mockResponse.statusCode()).thenReturn(403);
        when(mockResponse.body()).thenReturn("Forbidden");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(mockResponse);

        BrokerTokenException exception = assertThrows(BrokerTokenException.class, () ->
            service.getSigstoreIdToken(fakeKeycloakJwt())
        );

        assertTrue(exception.getMessage().contains("Sigstore-supported provider"));
    }

    @Test
    void getSigstoreIdToken_noIdTokenInResponse_throwsException() throws Exception {
        String tokenResponse = """
            {
                "access_token": "dex-access-token",
                "token_type": "Bearer"
            }
            """;
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(tokenResponse);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(mockResponse);

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            service.getSigstoreIdToken(fakeKeycloakJwt())
        );

        assertTrue(exception.getMessage().contains("No Sigstore ID token found"));
    }

    @Test
    void getSigstoreIdToken_networkError_throwsException() throws Exception {
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenThrow(new IOException("Connection refused"));

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            service.getSigstoreIdToken(fakeKeycloakJwt())
        );

        assertTrue(exception.getMessage().contains("Failed to retrieve Sigstore ID token"));
    }
}
