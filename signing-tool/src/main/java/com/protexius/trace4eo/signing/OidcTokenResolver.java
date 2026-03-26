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
import java.time.Instant;
import java.util.Base64;
import java.util.List;

public class OidcTokenResolver {

    private static final Logger log = LoggerFactory.getLogger(OidcTokenResolver.class);
    private static final String CLIENT_ID = "sigstore";

    private final String ciToken;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    private String cachedToken;
    private boolean resolved;

    public OidcTokenResolver(String ciToken, HttpClient httpClient, ObjectMapper mapper) {
        this.ciToken = ciToken;
        this.httpClient = httpClient;
        this.mapper = mapper;
    }

    public String resolve() {
        if (!resolved) {
            cachedToken = resolveOnce();
            resolved = true;
        }
        return cachedToken;
    }

    private String resolveOnce() {
        if (ciToken != null && !ciToken.isBlank()) {
            log.info("Using SIGSTORE_ID_TOKEN from environment");
            return ciToken;
        }
        log.info("No SIGSTORE_ID_TOKEN set, obtaining token via device flow");
        return obtainDeviceFlowToken();
    }

    private String obtainDeviceFlowToken() {
        try {
            var tufClientBuilder = SigstoreTufClient.builder().usePublicGoodInstance();
            var signingConfig = SigningConfigProvider.from(tufClientBuilder).get();
            var oidcService = Service.select(signingConfig.getOidcProviders(), List.of(1));
            if (oidcService.isEmpty()) {
                log.warn("No suitable OIDC provider found in Sigstore signing config");
                return null;
            }
            String issuer = oidcService.get().getUrl().toString();
            OidcEndpoints endpoints = fetchOidcEndpoints(issuer);
            String codeVerifier = generateCodeVerifier();
            String codeChallenge = computeCodeChallenge(codeVerifier);
            DeviceCodeResponse deviceCode = requestDeviceCode(endpoints.deviceAuthorizationEndpoint(), codeChallenge);
            printDeviceCodePrompt(deviceCode);
            return pollForToken(endpoints.tokenEndpoint(), deviceCode, codeVerifier);
        } catch (Exception e) {
            log.warn("Failed to obtain OIDC token via device flow", e);
            return null;
        }
    }

    private OidcEndpoints fetchOidcEndpoints(String issuer) throws IOException, InterruptedException {
        String discoveryUrl = issuer.replaceAll("/+$", "") + "/.well-known/openid-configuration";
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(discoveryUrl)).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("OIDC discovery failed: HTTP " + response.statusCode() + " — " + response.body());
        }
        JsonNode json = mapper.readTree(response.body());
        return new OidcEndpoints(
                requireText(json, "device_authorization_endpoint", response.body()),
                requireText(json, "token_endpoint", response.body())
        );
    }

    private DeviceCodeResponse requestDeviceCode(String deviceAuthorizationEndpoint, String codeChallenge)
            throws IOException, InterruptedException {
        String body = "client_id=" + encode(CLIENT_ID)
                + "&scope=" + encode("openid email")
                + "&code_challenge_method=S256"
                + "&code_challenge=" + encode(codeChallenge);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(deviceAuthorizationEndpoint))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                    "Device authorization request failed: HTTP " + response.statusCode() + " — " + response.body());
        }
        JsonNode json = mapper.readTree(response.body());
        String verificationUri = json.has("verification_uri_complete")
                ? json.get("verification_uri_complete").asText()
                : requireText(json, "verification_uri", response.body());
        return new DeviceCodeResponse(
                requireText(json, "device_code", response.body()),
                requireText(json, "user_code", response.body()),
                verificationUri,
                json.get("expires_in").asInt(),
                json.has("interval") ? json.get("interval").asInt() : 5
        );
    }

    private static String requireText(JsonNode json, String field, String rawResponse) {
        JsonNode node = json.get(field);
        if (node == null) {
            throw new IllegalStateException("Missing field '" + field + "' in response: " + rawResponse);
        }
        return node.asText();
    }

    private static void printDeviceCodePrompt(DeviceCodeResponse deviceCode) {
        log.info("To authenticate, open the following URL in any browser:\n\n  {}\n", deviceCode.verificationUriComplete());
        log.info("Or visit the URL and enter code: {}", deviceCode.userCode());
        log.info("Waiting for authorization...");
    }

    private String pollForToken(String tokenEndpoint, DeviceCodeResponse deviceCode, String codeVerifier)
            throws IOException, InterruptedException {
        Instant deadline = Instant.now().plusSeconds(deviceCode.expiresIn());
        int interval = deviceCode.intervalSeconds();
        while (Instant.now().isBefore(deadline)) {
            Thread.sleep(interval * 1000L);
            String body = "grant_type=" + encode("urn:ietf:params:oauth:grant-type:device_code")
                    + "&device_code=" + encode(deviceCode.deviceCode())
                    + "&client_id=" + encode(CLIENT_ID)
                    + "&code_verifier=" + encode(codeVerifier);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(tokenEndpoint))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = mapper.readTree(response.body());
            if (json.has("id_token")) {
                log.info("Authorization successful");
                return json.get("id_token").asText();
            }
            if (!json.has("error")) {
                throw new IllegalStateException("Unexpected token response (no id_token and no error): " + response.body());
            }
            String error = json.get("error").asText();
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

    private static String generateCodeVerifier() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String computeCodeChallenge(String verifier) throws NoSuchAlgorithmException {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record OidcEndpoints(String deviceAuthorizationEndpoint, String tokenEndpoint) {}

    private record DeviceCodeResponse(
            String deviceCode,
            String userCode,
            String verificationUriComplete,
            int expiresIn,
            int intervalSeconds
    ) {}
}
