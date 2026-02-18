package com.guardtime.trace4eo.signing.registration;

import com.guardtime.trace4eo.provenance.ProvenanceJsonMapper;
import com.guardtime.trace4eo.provenance.record.Predecessor;
import com.guardtime.trace4eo.provenance.record.ProvenanceRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class RecordRegistrationClient {

    private static final Logger log = LoggerFactory.getLogger(RecordRegistrationClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient httpClient;
    private final ProvenanceJsonMapper provenanceJsonMapper;

    public RecordRegistrationClient(HttpClient httpClient, ProvenanceJsonMapper provenanceJsonMapper) {
        this.httpClient = httpClient;
        this.provenanceJsonMapper = provenanceJsonMapper;
    }

    public String exchangeToken(String keycloakUrl, String realm, String sigstoreOidcToken) {
        String tokenUrl = keycloakUrl + "/realms/" + realm + "/protocol/openid-connect/token";
        String body = "grant_type=" + URLEncoder.encode("urn:ietf:params:oauth:grant-type:token-exchange", StandardCharsets.UTF_8)
            + "&subject_token=" + URLEncoder.encode(sigstoreOidcToken, StandardCharsets.UTF_8)
            + "&subject_token_type=" + URLEncoder.encode("urn:ietf:params:oauth:token-type:id_token", StandardCharsets.UTF_8)
            + "&subject_issuer=sigstore"
            + "&client_id=trace4eo-ui";

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RegistrationException(
                    String.format("Keycloak token exchange failed: HTTP %d - %s", response.statusCode(), response.body()));
            }

            Map<String, Object> tokenResponse = MAPPER.readValue(
                response.body(), new TypeReference<>() {});
            String accessToken = (String) tokenResponse.get("access_token");
            if (accessToken == null || accessToken.isBlank()) {
                throw new RegistrationException("Keycloak token exchange response did not contain an access_token.");
            }
            log.info("Exchanged Sigstore OIDC token for Keycloak access token");
            return accessToken;
        } catch (RegistrationException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RegistrationException("Token exchange with Keycloak interrupted", e);
        } catch (IOException e) {
            throw new RegistrationException(String.format("Failed to exchange token with Keycloak at %s", tokenUrl), e);
        }
    }

    public List<UUID> findMissingPredecessors(List<UUID> ids, String registerUrl, String accessToken) {
        String url = registerUrl + "/validate-predecessors";
        try {
            String json = MAPPER.writeValueAsString(ids);
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));

            if (accessToken != null) {
                requestBuilder.header("Authorization", "Bearer " + accessToken);
            }

            HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RegistrationException(
                    String.format("Predecessor validation failed: HTTP %d - %s", response.statusCode(), response.body()));
            }

            return MAPPER.readValue(response.body(), new TypeReference<List<UUID>>() {});
        } catch (RegistrationException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RegistrationException("Predecessor validation interrupted", e);
        } catch (IOException e) {
            throw new RegistrationException(String.format("Failed to validate predecessors at %s", url), e);
        }
    }

    public String exchangeTokenIfConfigured(String registerUrl, String keycloakUrl, String realm, String oidcToken) {
        if (registerUrl == null || registerUrl.isBlank()) return null;
        if (oidcToken != null) return exchangeToken(keycloakUrl, realm, oidcToken);
        log.warn("No OIDC token available for token exchange. Attempting registration without auth.");
        return null;
    }

    public void validatePredecessorsExist(List<Predecessor> predecessors, String registerUrl, String accessToken) {
        if (registerUrl == null || registerUrl.isBlank() || predecessors.isEmpty()) return;
        List<UUID> ids = predecessors.stream().map(Predecessor::id).toList();
        List<UUID> missing = findMissingPredecessors(ids, registerUrl, accessToken);
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(String.format("Predecessor records not found: %s", missing));
        }
    }

    public void registerIfConfigured(List<ProvenanceRecord> records, String registerUrl, String accessToken) {
        if (registerUrl == null || registerUrl.isBlank()) return;
        log.info("Registering {} provenance record(s) to tracing system at {}...", records.size(), registerUrl);
        List<String> failures = registerRecords(records, registerUrl, accessToken);
        if (!failures.isEmpty()) {
            int successCount = records.size() - failures.size();
            log.warn("Registered {}/{} provenance records to tracing system.", successCount, records.size());
            return;
        }
        log.info("Successfully registered {} provenance record(s) to tracing system.", records.size());
    }

    public List<String> registerRecords(List<ProvenanceRecord> records, String registerUrl, String accessToken) {
        List<String> failures = new ArrayList<>();
        for (ProvenanceRecord record : records) {
            try {
                String json = provenanceJsonMapper.writeValueAsString(record);
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(registerUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json));

                if (accessToken != null) {
                    requestBuilder.header("Authorization", "Bearer " + accessToken);
                }

                HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    String msg = String.format("Failed to register record %s: HTTP %d - %s",
                        record.id(), response.statusCode(), response.body());
                    log.warn(msg);
                    failures.add(msg);
                }
            } catch (Exception e) {
                String msg = String.format("Failed to register record %s: %s", record.id(), e.getMessage());
                log.error(msg, e);
                failures.add(msg);
            }
        }
        return failures;
    }
}
