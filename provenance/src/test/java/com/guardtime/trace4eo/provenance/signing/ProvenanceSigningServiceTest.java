package com.guardtime.trace4eo.provenance.signing;

import com.guardtime.trace4eo.provenance.HashAlgorithm;
import com.guardtime.trace4eo.provenance.ProvenanceSignature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Arrays;

import static com.guardtime.trace4eo.provenance.io.TestUtils.TEST_BYTES_1;
import static com.guardtime.trace4eo.provenance.io.TestUtils.TEST_BYTES_2;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@EnabledIfEnvironmentVariable(named = "SIGSTORE_ID_TOKEN", matches = ".+")
class ProvenanceSigningServiceTest {

    private final ProvenanceSigningService provenanceSigningService = new ProvenanceSigningService();

    @Test
    void sign() {
        String oidcToken = System.getenv("SIGSTORE_ID_TOKEN");
        ProvenanceSignature signature1 = provenanceSigningService.sign(TEST_BYTES_1, oidcToken);
        assertNotNull(signature1);
        ProvenanceSignature signature2 = provenanceSigningService.sign(TEST_BYTES_2, oidcToken);
        assertNotNull(signature2);
        assertFalse(Arrays.equals(signature1.bytes(), signature2.bytes()));
        assertEquals(HashAlgorithm.SHA256, signature1.hashAlgorithm());
    }
}
