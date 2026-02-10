package com.guardtime.trace4eo.provenance.signing;

import com.guardtime.trace4eo.provenance.HashAlgorithm;
import com.guardtime.trace4eo.provenance.ProvenanceSignature;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static com.guardtime.trace4eo.provenance.io.TestUtils.TEST_BYTES_1;
import static com.guardtime.trace4eo.provenance.io.TestUtils.TEST_BYTES_2;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Disabled("Requires interactive Sigstore OIDC authentication")
class ProvenanceSigningServiceTest {

    private final ProvenanceSigningService provenanceSigningService = new ProvenanceSigningService();

    @Test
    void sign() {
        String oidcToken = System.getenv("SIGSTORE_ID_TOKEN");
        HashAlgorithm hashAlgorithm = HashAlgorithm.SHA256;
        ProvenanceSignature signature1 = provenanceSigningService.sign(TEST_BYTES_1, hashAlgorithm, oidcToken);
        assertNotNull(signature1);
        ProvenanceSignature signature2 = provenanceSigningService.sign(TEST_BYTES_2, hashAlgorithm, oidcToken);
        assertNotNull(signature2);
        assertFalse(Arrays.equals(signature1.bytes(), signature2.bytes()));
        assertEquals(hashAlgorithm, signature1.hashAlgorithm());
    }
}
