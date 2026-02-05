package com.guardtime.trace4eo.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KeycloakBrokerTokenServiceTest {

    private KeycloakBrokerTokenService service;
    private HttpClient mockHttpClient;
    @SuppressWarnings("unchecked")
    private HttpResponse<String> mockResponse = mock(HttpResponse.class);

    @BeforeEach
    void setUp() {
        mockHttpClient = mock(HttpClient.class);
        service = new KeycloakBrokerTokenService("http://localhost:8180", "trace4eo", mockHttpClient);
    }

    @Test
    void getGoogleIdToken_success() throws Exception {
        String tokenResponse = """
            {
                "access_token": "google-access-token",
                "id_token": "google-id-token-jwt",
                "token_type": "Bearer",
                "expires_in": 3600
            }
            """;
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(tokenResponse);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(mockResponse);

        String result = service.getGoogleIdToken("keycloak-access-token");

        assertEquals("google-id-token-jwt", result);
        verify(mockHttpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void getGoogleIdToken_non200Status_throwsException() throws Exception {
        when(mockResponse.statusCode()).thenReturn(401);
        when(mockResponse.body()).thenReturn("Unauthorized");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(mockResponse);

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            service.getGoogleIdToken("invalid-token")
        );

        assertTrue(exception.getMessage().contains("HTTP 401"));
    }

    @Test
    void getGoogleIdToken_noIdTokenInResponse_throwsException() throws Exception {
        String tokenResponse = """
            {
                "access_token": "google-access-token",
                "token_type": "Bearer"
            }
            """;
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(tokenResponse);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(mockResponse);

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            service.getGoogleIdToken("keycloak-access-token")
        );

        assertTrue(exception.getMessage().contains("No Google ID token found"));
    }

    @Test
    void getGoogleIdToken_networkError_throwsException() throws Exception {
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenThrow(new IOException("Connection refused"));

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            service.getGoogleIdToken("keycloak-access-token")
        );

        assertTrue(exception.getMessage().contains("Failed to retrieve Google ID token"));
    }
}
