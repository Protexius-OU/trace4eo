package com.protexius.trace4eo.signing;

import dev.sigstore.SigningConfigProvider;
import dev.sigstore.trustroot.Service;
import dev.sigstore.tuf.SigstoreTufClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

public class SigstoreDeviceFlowClient {

    private static final Logger log = LoggerFactory.getLogger(SigstoreDeviceFlowClient.class);
    private static final String CLIENT_ID = "sigstore";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public SigstoreDeviceFlowClient(HttpClient httpClient, ObjectMapper mapper) {
        this.httpClient = httpClient;
        this.mapper = mapper;
    }

    public OidcEndpoints discoverEndpoints(String issuerOverride) throws IOException, InterruptedException {
        String issuer = issuerOverride != null && !issuerOverride.isBlank()
            ? issuerOverride
            : discoverIssuerFromTuf();
        return fetchOidcEndpoints(issuer);
    }

    public TokenResponse runDeviceFlow(OidcEndpoints endpoints, String scope)
            throws IOException, InterruptedException {
        String verifier = randomBase64UrlBytes(32);
        String challenge = sha256Base64Url(verifier);
        DeviceCodeResponse deviceCode = requestDeviceCode(endpoints.deviceAuthorizationEndpoint(), scope, challenge);
        printDeviceCodePrompt(deviceCode);
        return pollForToken(endpoints.tokenEndpoint(), deviceCode, verifier);
    }

    public TokenResponse refresh(OidcEndpoints endpoints, String refreshToken)
            throws IOException, InterruptedException {
        String body = "grant_type=refresh_token"
            + "&refresh_token=" + encode(refreshToken)
            + "&client_id=" + encode(CLIENT_ID);
        HttpResponse<String> response = postForm(endpoints.tokenEndpoint(), body);
        JsonNode json = mapper.readTree(response.body());
        if (json.has("id_token")) {
            String newRefresh = json.has("refresh_token") ? json.get("refresh_token").asString() : null;
            return new TokenResponse(json.get("id_token").asString(), newRefresh);
        }
        log.warn("Refresh failed: HTTP {} — {}", response.statusCode(), response.body());
        return null;
    }

    private String discoverIssuerFromTuf() {
        try {
            var tufClientBuilder = SigstoreTufClient.builder().usePublicGoodInstance();
            var signingConfig = SigningConfigProvider.from(tufClientBuilder).get();
            var oidcService = Service.select(signingConfig.getOidcProviders(), List.of(1));
            if (oidcService.isEmpty()) {
                throw new IllegalStateException("No suitable OIDC provider found in Sigstore signing config");
            }
            return oidcService.get().getUrl().toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to discover Sigstore OIDC issuer via TUF", e);
        }
    }

    private OidcEndpoints fetchOidcEndpoints(String issuer) throws IOException, InterruptedException {
        String discoveryUrl = issuer.replaceAll("/+$", "") + "/.well-known/openid-configuration";
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(discoveryUrl)).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                "OIDC discovery failed: HTTP " + response.statusCode() + " — " + response.body());
        }
        JsonNode json = mapper.readTree(response.body());
        return new OidcEndpoints(
            requireText(json, "device_authorization_endpoint", response.body()),
            requireText(json, "token_endpoint", response.body())
        );
    }

    private DeviceCodeResponse requestDeviceCode(String endpoint, String scope, String challenge)
            throws IOException, InterruptedException {
        String body = "client_id=" + encode(CLIENT_ID)
            + "&scope=" + encode(scope)
            + "&code_challenge_method=S256"
            + "&code_challenge=" + encode(challenge);
        HttpResponse<String> response = postForm(endpoint, body);
        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                "Device authorization failed: HTTP " + response.statusCode() + " — " + response.body());
        }
        JsonNode json = mapper.readTree(response.body());
        String verificationUri = json.has("verification_uri_complete")
            ? json.get("verification_uri_complete").asString()
            : requireText(json, "verification_uri", response.body());
        return new DeviceCodeResponse(
            requireText(json, "device_code", response.body()),
            requireText(json, "user_code", response.body()),
            verificationUri,
            json.get("expires_in").asInt(),
            json.has("interval") ? json.get("interval").asInt() : 5
        );
    }

    private TokenResponse pollForToken(String tokenEndpoint, DeviceCodeResponse deviceCode, String codeVerifier)
            throws IOException, InterruptedException {
        long deadlineMs = System.currentTimeMillis() + deviceCode.expiresIn() * 1000L;
        int interval = deviceCode.intervalSeconds();
        while (System.currentTimeMillis() < deadlineMs) {
            Thread.sleep(interval * 1000L);
            String body = "grant_type=" + encode("urn:ietf:params:oauth:grant-type:device_code")
                + "&device_code=" + encode(deviceCode.deviceCode())
                + "&client_id=" + encode(CLIENT_ID)
                + "&code_verifier=" + encode(codeVerifier);
            HttpResponse<String> response = postForm(tokenEndpoint, body);
            JsonNode json = mapper.readTree(response.body());
            if (json.has("id_token")) {
                log.info("Authorization successful");
                String refreshToken = json.has("refresh_token") ? json.get("refresh_token").asString() : null;
                return new TokenResponse(json.get("id_token").asString(), refreshToken);
            }
            if (!json.has("error")) {
                throw new IllegalStateException(
                    "Unexpected token response (no id_token and no error): " + response.body());
            }
            String error = json.get("error").asString();
            switch (error) {
                case "authorization_pending" -> { /* keep polling */ }
                case "slow_down" -> interval += 5;
                case "access_denied" -> throw new IllegalStateException("Authorization denied by user");
                case "expired_token" -> throw new IllegalStateException("Device code expired — please retry");
                default -> throw new IllegalStateException("Device flow failed: " + error);
            }
        }
        throw new IllegalStateException("Device flow timed out — authorization was not completed in time");
    }

    private HttpResponse<String> postForm(String url, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static void printDeviceCodePrompt(DeviceCodeResponse deviceCode) {
        log.info("To authenticate, open the following URL in any browser:\n\n  {}\n",
            deviceCode.verificationUriComplete());
        log.info("User code: {}", deviceCode.userCode());
        log.info("Waiting for Sigstore authorization...");
    }

    private static String requireText(JsonNode json, String field, String rawResponse) {
        JsonNode node = json.get(field);
        if (node == null) {
            throw new IllegalStateException("Missing field '" + field + "' in response: " + rawResponse);
        }
        return node.asString();
    }

    private static String randomBase64UrlBytes(int byteLength) {
        byte[] bytes = new byte[byteLength];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Base64Url(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public record OidcEndpoints(String deviceAuthorizationEndpoint, String tokenEndpoint) {}

    public record TokenResponse(String idToken, String refreshToken) {}

    private record DeviceCodeResponse(
        String deviceCode, String userCode, String verificationUriComplete,
        int expiresIn, int intervalSeconds
    ) {}
}
