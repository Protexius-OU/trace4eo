package com.guardtime.trace4eo.provenance.container.verification;

import com.guardtime.trace4eo.provenance.container.model.HashAlgorithm;
import com.guardtime.trace4eo.provenance.container.model.ProvenanceSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class ProvenanceVerificationService {

    private static final Logger log = LoggerFactory.getLogger(ProvenanceVerificationService.class);

    public ProvenanceVerificationResult verify(ProvenanceSignature signature, byte[] bytes) {
        return verify(signature, new ByteArrayInputStream(bytes));
    }

    public ProvenanceVerificationResult verify(ProvenanceSignature signature, InputStream inputStream) {
        HashAlgorithm hashAlgorithm = signature.hashAlgorithm();
        MessageDigest md;
        try {
            md = MessageDigest.getInstance(hashAlgorithm.name());
        } catch (NoSuchAlgorithmException e) {
            return new ProvenanceVerificationResult(ProvenanceVerificationError.UNSUPPORTED_HASH_ALGORITHM,
                String.format("Hash algorithm %s is not supported.", hashAlgorithm.name()));
        }
        try (DigestInputStream digestInputStream = new DigestInputStream(inputStream, md)) {
            byte[] expectedSignatureBytes = signature.bytes();
            digestInputStream.transferTo(OutputStream.nullOutputStream());
            if (Arrays.equals(expectedSignatureBytes, md.digest())) {
                return new ProvenanceVerificationResult();
            }
            return new ProvenanceVerificationResult(ProvenanceVerificationError.HASH_MISMATCH,
                "Hash of data didn't match provided signature.");
        } catch (IOException e) {
            log.error("Failed to verify provenance signature", e);
            return new ProvenanceVerificationResult(ProvenanceVerificationError.UNKNOWN_ERROR, e.getMessage());
        }
    }
}
