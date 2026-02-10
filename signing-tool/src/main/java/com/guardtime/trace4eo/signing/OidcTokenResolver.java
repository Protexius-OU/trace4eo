package com.guardtime.trace4eo.signing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class OidcTokenResolver {

    private static final Logger log = LoggerFactory.getLogger(OidcTokenResolver.class);

    private final String oidcTokenOverride;

    OidcTokenResolver(String oidcTokenOverride) {
        this.oidcTokenOverride = oidcTokenOverride;
    }

    String resolve() {
        if (oidcTokenOverride != null) {
            return oidcTokenOverride;
        }
        String ciToken = System.getenv("SIGSTORE_ID_TOKEN");
        if (ciToken != null && !ciToken.isBlank()) {
            log.info("Using SIGSTORE_ID_TOKEN from environment");
            return ciToken;
        }
        throw new IllegalStateException(
            "No OIDC token available. Set SIGSTORE_ID_TOKEN environment variable (e.g., from 'gcloud auth print-identity-token').");
    }
}
