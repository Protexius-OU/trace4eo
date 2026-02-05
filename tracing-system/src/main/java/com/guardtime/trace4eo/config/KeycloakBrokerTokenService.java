package com.guardtime.trace4eo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

/**
 * Service for retrieving the Google ID token from Keycloak's broker token endpoint.
 * When a user authenticates via Google through Keycloak's identity brokering,
 * Keycloak stores the Google tokens (if storeToken=true is configured).
 * This service retrieves that stored Google ID token for Sigstore/Fulcio signing.
 */
@Service
@Profile("!test")
public class KeycloakBrokerTokenService {

    private static final Logger log = LoggerFactory.getLogger(KeycloakBrokerTokenService.class);

    private final String keycloakBaseUrl;
    private final String realm;
    private final HttpClient httpClient;

    public KeycloakBrokerTokenService(
        @Value("${keycloak.base-url:http://localhost:8180}") String keycloakBaseUrl,
        @Value("${keycloak.realm:trace4eo}") String realm
    ) {
        this.keycloakBaseUrl = keycloakBaseUrl;
        this.realm = realm;
        this.httpClient = HttpClient.newHttpClient();
    }

    // Test constructor
    KeycloakBrokerTokenService(String keycloakBaseUrl, String realm, HttpClient httpClient) {
        this.keycloakBaseUrl = keycloakBaseUrl;
        this.realm = realm;
        this.httpClient = httpClient;
    }

    /**
     * Retrieves the Google ID token from Keycloak's broker token endpoint.
     *
     * @param keycloakAccessToken the user's Keycloak access token
     * @return the Google ID token stored by Keycloak
     * @throws RuntimeException if the token cannot be retrieved
     */
    public String getGoogleIdToken(String keycloakAccessToken) {
        String url = keycloakBaseUrl + "/realms/" + realm + "/broker/google/token";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + keycloakAccessToken)
            .GET()
            .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException(
                    "Failed to retrieve broker token from Keycloak: HTTP "
                    + response.statusCode() + " - " + response.body());
            }

            return extractIdToken(response.body());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve Google ID token from Keycloak", e);
        }
    }

    private String extractIdToken(String responseBody) {
        // Keycloak broker/token endpoint returns the raw token response from Google.
        // For Google OAuth2 with openid scope, this is a JSON containing:
        // { "access_token": "...", "id_token": "...", "token_type": "Bearer", ... }
        try {
            Map<String, Object> tokenResponse = new ObjectMapper().readValue(
                responseBody, new TypeReference<>() {});
            String idToken = (String) tokenResponse.get("id_token");
            if (idToken == null || idToken.isBlank()) {
                log.error("Keycloak broker token response did not contain id_token. Response: {}", responseBody);
                throw new RuntimeException(
                    "No Google ID token found. Ensure the user authenticated via Google and the Google IdP is configured with 'openid' scope.");
            }
            log.debug("Successfully retrieved Google ID token from Keycloak broker");
            return idToken;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse broker token response: " + responseBody, e);
        }
    }
}
