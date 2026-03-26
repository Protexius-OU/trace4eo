package com.protexius.trace4eo.provenance.signing;

import dev.sigstore.oidc.client.ImmutableOidcToken;
import dev.sigstore.oidc.client.OidcClient;
import dev.sigstore.oidc.client.OidcException;
import dev.sigstore.oidc.client.OidcToken;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Base64;
import java.util.Map;

/**
 * OidcClient that extracts the {@code email} claim as the subject alternative name.
 * <p>
 * sigstore-java's {@code TokenStringOidcClient} uses the {@code sub} claim, which is an opaque
 * identifier for Dex tokens. Fulcio expects the proof-of-possession to be over the email address,
 * which is what the browser-based {@code WebOidcClient} uses. This client bridges that gap for
 * pre-obtained tokens.
 */
class EmailOidcClient implements OidcClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String idToken;

    EmailOidcClient(String idToken) {
        this.idToken = idToken;
    }

    @Override
    public boolean isEnabled(Map<String, String> env) {
        return true;
    }

    @Override
    public OidcToken getIDToken(Map<String, String> env) throws OidcException {
        try {
            String[] parts = idToken.split("\\.");
            if (parts.length < 2) {
                throw new OidcException("Invalid JWT format");
            }
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode payload = MAPPER.readTree(payloadBytes);

            String email = payload.path("email").asText(null);
            if (email == null || email.isBlank()) {
                throw new OidcException("OIDC token does not contain an email claim");
            }
            String issuer = payload.path("iss").asText(null);

            return ImmutableOidcToken.builder()
                .idToken(idToken)
                .issuer(issuer)
                .subjectAlternativeName(email)
                .build();
        } catch (OidcException e) {
            throw e;
        } catch (Exception e) {
            throw new OidcException("Failed to parse OIDC token", e);
        }
    }
}
