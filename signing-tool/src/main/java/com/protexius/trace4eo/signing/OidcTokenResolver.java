package com.protexius.trace4eo.signing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OidcTokenResolver {

    private static final Logger log = LoggerFactory.getLogger(OidcTokenResolver.class);
    private static final String SCOPE = "openid email";

    private final String ciToken;
    private final SigstoreDeviceFlowClient deviceFlowClient;

    private String cachedToken;
    private boolean resolved;

    public OidcTokenResolver(String ciToken, SigstoreDeviceFlowClient deviceFlowClient) {
        this.ciToken = ciToken;
        this.deviceFlowClient = deviceFlowClient;
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
        try {
            var endpoints = deviceFlowClient.discoverEndpoints(null);
            return deviceFlowClient.runDeviceFlow(endpoints, SCOPE).idToken();
        } catch (Exception e) {
            log.warn("Failed to obtain OIDC token via device flow", e);
            return null;
        }
    }
}
