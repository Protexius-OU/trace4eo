package com.guardtime.trace4eo.provenance.signing;

import com.guardtime.trace4eo.provenance.HashAlgorithm;
import com.guardtime.trace4eo.provenance.ProvenanceSignature;
import dev.sigstore.KeylessSigner;
import dev.sigstore.bundle.Bundle;
import dev.sigstore.json.canonicalizer.JsonCanonicalizer;
import dev.sigstore.rekor.client.RekorEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

public class ProvenanceSigningService {

    private static final Logger log = LoggerFactory.getLogger(ProvenanceSigningService.class);

    private final KeylessSigner signer;

    public ProvenanceSigningService() {
        try {
            this.signer = KeylessSigner.builder().sigstorePublicDefaults().build();
        } catch (Exception e) {
            log.error("Failed to initialize provenance signing service", e);
            throw new RuntimeException(e);
        }
    }

    public ProvenanceSignature sign(byte[] bytes, HashAlgorithm hashAlgorithm) {
        return sign(new ByteArrayInputStream(bytes), hashAlgorithm);
    }

    public ProvenanceSignature sign(InputStream inputStream, HashAlgorithm hashAlgorithm) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance(hashAlgorithm.name());
        } catch (NoSuchAlgorithmException e) {
            log.error("Hash algorithm {} not supported", hashAlgorithm.name(), e);
            throw new IllegalArgumentException(e);
        }
        try (DigestInputStream digestInputStream = new DigestInputStream(inputStream, md)) {
            digestInputStream.transferTo(OutputStream.nullOutputStream());
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
            return new ProvenanceSignature(bundleBytes, timestamp, hashAlgorithm);
        } catch (Exception e) {
            log.error("Failed to sign provenance signature", e);
            throw new RuntimeException(e);
        }
    }
}
