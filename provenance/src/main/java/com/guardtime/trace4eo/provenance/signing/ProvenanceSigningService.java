package com.guardtime.trace4eo.provenance.signing;

import com.guardtime.trace4eo.provenance.HashAlgorithm;
import com.guardtime.trace4eo.provenance.ProvenanceSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

public class ProvenanceSigningService {

    private static final Logger log = LoggerFactory.getLogger(ProvenanceSigningService.class);

    public ProvenanceSignature sign(HashAlgorithm hashAlgorithm, byte[] bytes) {
        return sign(hashAlgorithm, new ByteArrayInputStream(bytes));
    }

    public ProvenanceSignature sign(HashAlgorithm hashAlgorithm, InputStream inputStream) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance(hashAlgorithm.name());
        } catch (NoSuchAlgorithmException e) {
            log.error("Hash algorithm {} not supported", hashAlgorithm.name(), e);
            throw new RuntimeException(e);
        }
        try (DigestInputStream digestInputStream = new DigestInputStream(inputStream, md)) {
            digestInputStream.transferTo(OutputStream.nullOutputStream());
            return new ProvenanceSignature(md.digest(), Instant.now(), hashAlgorithm);
        } catch (IOException e) {
            log.error("Failed to sign provenance signature", e);
            throw new RuntimeException(e);
        }
    }
}
