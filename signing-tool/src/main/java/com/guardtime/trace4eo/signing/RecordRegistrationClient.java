package com.guardtime.trace4eo.signing;

import com.guardtime.trace4eo.provenance.ProvenanceJsonMapper;
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

    public String authenticateWithDirectGrant(String keycloakUrl, String realm, String username, String password) {
        String tokenUrl = keycloakUrl + "/realms/" + realm + "/protocol/openid-connect/token";
        String body = "grant_type=password"
            + "&client_id=trace4eo-ui"
            + "&username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
            + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8)
            + "&scope=openid+email";

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RegistrationException(
                    "Keycloak authentication failed: HTTP " + response.statusCode() + " - " + response.body());
            }

            Map<String, Object> tokenResponse = MAPPER.readValue(
                response.body(), new TypeReference<>() {});
            String accessToken = (String) tokenResponse.get("access_token");
            if (accessToken == null || accessToken.isBlank()) {
                throw new RegistrationException("Keycloak response did not contain an access_token.");
            }
            log.info("Authenticated with Keycloak as {}", username);
            return accessToken;
        } catch (RegistrationException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            throw new RegistrationException("Failed to authenticate with Keycloak at " + tokenUrl, e);
        }
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

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    log.info("Registered record {} at {}", record.id(), registerUrl);
                } else {
                    String msg = "Failed to register record " + record.id() + ": HTTP " + response.statusCode();
                    log.warn(msg);
                    failures.add(msg);
                }
            } catch (Exception e) {
                String msg = "Failed to register record " + record.id() + ": " + e.getMessage();
                log.error(msg, e);
                failures.add(msg);
            }
        }
        return failures;
    }
}
