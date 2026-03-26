package com.protexius.trace4eo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Retrieves the Sigstore OIDC token from Keycloak's broker token endpoint.
 * When a user authenticates via the Sigstore identity provider (Dex),
 * Keycloak stores the tokens (storeToken=true). This service retrieves the
 * stored ID token for Sigstore/Fulcio signing, refreshing it via Dex if expired.
 */
@Service
@Profile("!test")
public class KeycloakBrokerTokenService {

    private static final Logger log = LoggerFactory.getLogger(KeycloakBrokerTokenService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEX_TOKEN_URL = "https://oauth2.sigstore.dev/auth/token";
    private static final String DEX_CLIENT_ID = "sigstore";

    private final String keycloakBaseUrl;
    private final String realm;
    private final HttpClient httpClient;
    private final ConcurrentHashMap<String, TokenPair> tokenCache = new ConcurrentHashMap<>();

    private record TokenPair(String idToken, String refreshToken) {}

    @Autowired
    public KeycloakBrokerTokenService(
        @Value("${keycloak.base-url:http://localhost:8180}") String keycloakBaseUrl,
        @Value("${keycloak.realm:trace4eo}") String realm,
        HttpClient httpClient
    ) {
        this.keycloakBaseUrl = keycloakBaseUrl;
        this.realm = realm;
        this.httpClient = httpClient;
    }

    /**
     * Retrieves the Sigstore ID token from Keycloak's broker token endpoint.
     * If the stored token is expired, refreshes it using the Dex refresh token.
     * Refreshed tokens are cached per user to handle Dex refresh token rotation,
     * since Keycloak only stores the original tokens from login time.
     *
     * @param keycloakAccessToken the user's Keycloak access token
     * @return the Sigstore-compatible ID token
     */
    public String getSigstoreIdToken(String keycloakAccessToken) {
        String userSub = extractSubject(keycloakAccessToken);

        TokenPair cached = tokenCache.get(userSub);
        if (cached != null && !isExpired(cached.idToken())) {
            log.debug("Using cached Sigstore ID token for user {}", userSub);
            return cached.idToken();
        }

        Map<String, Object> brokerResponse = fetchBrokerTokens(keycloakAccessToken);

        String idToken = (String) brokerResponse.get("id_token");
        if (idToken == null || idToken.isBlank()) {
            log.error("Keycloak broker token response did not contain id_token");
            throw new RuntimeException(
                "No Sigstore ID token found. Ensure the user authenticated via the Sigstore identity provider.");
        }

        if (!isExpired(idToken)) {
            log.debug("Sigstore ID token from broker is still valid");
            String refreshToken = (String) brokerResponse.get("refresh_token");
            tokenCache.put(userSub, new TokenPair(idToken, refreshToken));
            return idToken;
        }

        log.info("Sigstore ID token expired, refreshing via Dex");
        String refreshToken = cached != null ? cached.refreshToken()
            : (String) brokerResponse.get("refresh_token");
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new RuntimeException(
                "Sigstore ID token expired and no refresh token available. Please log in again.");
        }

        TokenPair refreshed = refreshTokenPair(refreshToken);
        tokenCache.put(userSub, refreshed);
        return refreshed.idToken();
    }

    private String extractSubject(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 3) {
                throw new IllegalArgumentException(String.format("Invalid JWT format: expected 3 parts, got %d", parts.length));
            }
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            Map<String, Object> claims = MAPPER.readValue(payload, new TypeReference<>() {});
            return (String) claims.get("sub");
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract subject from Keycloak token", e);
        }
    }

    private Map<String, Object> fetchBrokerTokens(String keycloakAccessToken) {
        String url = keycloakBaseUrl + "/realms/" + realm + "/broker/sigstore/token";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + keycloakAccessToken)
            .GET()
            .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 403) {
                throw new BrokerTokenException(
                    "Not authorized to retrieve Sigstore token. "
                    + "Sign in with a Sigstore-supported provider (Google, GitHub, Microsoft) to use signing.");
            }
            if (response.statusCode() != 200) {
                throw new RuntimeException(
                    String.format("Failed to retrieve broker token from Keycloak: HTTP %d - %s",
                        response.statusCode(), response.body()));
            }

            return MAPPER.readValue(response.body(), new TypeReference<>() {});
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve Sigstore ID token from Keycloak", e);
        }
    }

    private boolean isExpired(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) {
                return true;
            }
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            Map<String, Object> claims = MAPPER.readValue(payload, new TypeReference<>() {});
            Object exp = claims.get("exp");
            if (exp instanceof Number expNum) {
                return Instant.ofEpochSecond(expNum.longValue()).isBefore(Instant.now());
            }
            return true;
        } catch (Exception e) {
            log.warn("Failed to check token expiry, assuming expired", e);
            return true;
        }
    }

    private TokenPair refreshTokenPair(String refreshToken) {
        String body = "grant_type=refresh_token"
            + "&refresh_token=" + refreshToken
            + "&client_id=" + DEX_CLIENT_ID;

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(DEX_TOKEN_URL))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException(String.format("Failed to refresh Sigstore token via Dex: HTTP %d - %s. Please log in again.",
                    response.statusCode(), response.body()));
            }

            Map<String, Object> tokenResponse = MAPPER.readValue(
                response.body(), new TypeReference<>() {});
            String idToken = (String) tokenResponse.get("id_token");
            if (idToken == null || idToken.isBlank()) {
                throw new RuntimeException("Dex refresh response did not contain id_token. Please log in again.");
            }

            String newRefreshToken = (String) tokenResponse.get("refresh_token");
            if (newRefreshToken == null) {
                newRefreshToken = refreshToken;
            }

            log.info("Successfully refreshed Sigstore ID token via Dex");
            return new TokenPair(idToken, newRefreshToken);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to refresh Sigstore ID token", e);
        }
    }
}
