package com.guardtime.trace4eo.signing;

import dev.sigstore.SigningConfigProvider;
import dev.sigstore.oidc.client.OidcClients;
import dev.sigstore.oidc.client.OidcToken;
import dev.sigstore.trustroot.Service;
import dev.sigstore.tuf.SigstoreTufClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class OidcTokenResolver {

    private static final Logger log = LoggerFactory.getLogger(OidcTokenResolver.class);

    private final String oidcTokenOverride;

    public OidcTokenResolver(String oidcTokenOverride) {
        this.oidcTokenOverride = oidcTokenOverride;
    }

    public String resolve() {
        if (oidcTokenOverride != null) {
            return oidcTokenOverride;
        }
        String ciToken = System.getenv("SIGSTORE_ID_TOKEN");
        if (ciToken != null && !ciToken.isBlank()) {
            log.info("Using SIGSTORE_ID_TOKEN from environment");
            return ciToken;
        }
        log.info("No SIGSTORE_ID_TOKEN set, obtaining token via browser-based OIDC login");
        return obtainBrowserToken();
    }

    private String obtainBrowserToken() {
        try {
            var tufClientBuilder = SigstoreTufClient.builder().usePublicGoodInstance();
            var signingConfig = SigningConfigProvider.from(tufClientBuilder).get();
            var oidcService = Service.select(signingConfig.getOidcProviders(), List.of(1));
            if (oidcService.isEmpty()) {
                log.warn("No suitable OIDC provider found in Sigstore signing config");
                return null;
            }
            OidcClients oidcClients = OidcClients.from(oidcService.get());
            OidcToken token = oidcClients.getIDToken();
            log.info("Obtained OIDC token via browser-based login");
            return token.getIdToken();
        } catch (Exception e) {
            log.warn("Failed to obtain OIDC token via browser-based login", e);
            return null;
        }
    }
}
