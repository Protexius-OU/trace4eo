package com.guardtime.trace4eo.provenance.signing;

import com.guardtime.trace4eo.provenance.HashAlgorithm;
import com.guardtime.trace4eo.provenance.ProvenanceSignature;
import dev.sigstore.AlgorithmRegistry;
import dev.sigstore.KeylessSigner;
import dev.sigstore.bundle.Bundle;
import dev.sigstore.json.canonicalizer.JsonCanonicalizer;
import dev.sigstore.oidc.client.OidcClients;
import dev.sigstore.rekor.client.RekorEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.List;

public class ProvenanceSigningService {

    private static final Logger log = LoggerFactory.getLogger(ProvenanceSigningService.class);

    /** Sigstore only supports SHA-256 digests for all available signing algorithms. */
    private static final HashAlgorithm SIGNING_HASH_ALGORITHM = HashAlgorithm.SHA256;

    private volatile KeylessSigner browserSigner;

    /** Uses Sigstore public good instance. */
    public ProvenanceSigningService() {
    }

    /** Sign with an explicit OIDC token (e.g. from Google via Keycloak broker). */
    public ProvenanceSignature sign(byte[] bytes, String oidcToken) {
        return sign(new ByteArrayInputStream(bytes), oidcToken);
    }

    public ProvenanceSignature sign(InputStream inputStream, String oidcToken) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance(SIGNING_HASH_ALGORITHM.getName());
        } catch (NoSuchAlgorithmException e) {
            log.error("Hash algorithm {} not supported", SIGNING_HASH_ALGORITHM.getName(), e);
            throw new IllegalArgumentException(e);
        }
        try (DigestInputStream digestInputStream = new DigestInputStream(inputStream, md)) {
            digestInputStream.transferTo(OutputStream.nullOutputStream());
            KeylessSigner signer = oidcToken != null
                ? buildSigner(oidcToken)
                : getBrowserSigner();
            Bundle bundle = signer.sign(md.digest());
            Bundle.MessageSignature messageSignature = bundle.getMessageSignature().orElse(null);
            if (messageSignature == null) {
                throw new IllegalStateException("Signature is null");
            }
            RekorEntry rekorEntry = bundle.getEntries().getFirst();
            if (rekorEntry == null) {
                throw new IllegalStateException("Timestamp is null");
            }
            Instant timestamp = rekorEntry.getIntegratedTimeInstant();
            byte[] bundleBytes = new JsonCanonicalizer(bundle.toJson()).getEncodedUTF8();

            SignatureDetails details = extractSignatureDetails(bundle, rekorEntry, timestamp);

            return new ProvenanceSignature(bundleBytes, timestamp, SIGNING_HASH_ALGORITHM, details);
        } catch (Exception e) {
            log.error("Failed to sign provenance signature", e);
            throw new RuntimeException(e);
        }
    }

    private KeylessSigner buildSigner(String oidcToken) {
        try {
            return KeylessSigner.builder()
                .sigstorePublicDefaults()
                .signingAlgorithm(AlgorithmRegistry.SigningAlgorithm.PKIX_RSA_PKCS1V15_4096_SHA256)
                .forceCredentialProviders(OidcClients.of(new EmailOidcClient(oidcToken)))
                .build();
        } catch (Exception e) {
            log.error("Failed to build signer", e);
            throw new RuntimeException(e);
        }
    }

    private synchronized KeylessSigner getBrowserSigner() {
        if (browserSigner == null) {
            browserSigner = buildBrowserSigner();
        }
        return browserSigner;
    }

    private KeylessSigner buildBrowserSigner() {
        try {
            return KeylessSigner.builder()
                .sigstorePublicDefaults()
                .build();
        } catch (Exception e) {
            log.error("Failed to build browser-based signer", e);
            throw new RuntimeException(e);
        }
    }

    private SignatureDetails extractSignatureDetails(Bundle bundle, RekorEntry rekorEntry, Instant timestamp) {
        String rekorLogIndex = String.valueOf(rekorEntry.getLogIndex());
        String signerIdentity = "Unknown";
        String certificateIssuer = "Unknown";

        try {
            List<X509Certificate> certChain = bundle.getCertPath().getCertificates()
                .stream()
                .filter(c -> c instanceof X509Certificate)
                .map(c -> (X509Certificate) c)
                .toList();

            if (!certChain.isEmpty()) {
                X509Certificate signingCert = certChain.getFirst();
                certificateIssuer = signingCert.getIssuerX500Principal().getName();

                // Extract SAN (Subject Alternative Name) for email/identity
                if (signingCert.getSubjectAlternativeNames() != null) {
                    for (List<?> san : signingCert.getSubjectAlternativeNames()) {
                        Integer type = (Integer) san.get(0);
                        if (type == 1) { // RFC822Name (email)
                            signerIdentity = (String) san.get(1);
                            break;
                        } else if (type == 6) { // URI
                            signerIdentity = (String) san.get(1);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract certificate details", e);
        }

        return new SignatureDetails(timestamp, rekorLogIndex, signerIdentity, certificateIssuer);
    }
}
