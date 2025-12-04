package com.guardtime.trace4eo.provenance.container.verification;

import com.guardtime.trace4eo.provenance.container.model.ProvenanceSignature;
import org.junit.jupiter.api.Test;

import static com.guardtime.trace4eo.provenance.container.io.TestUtils.SIGNATURE_1;
import static com.guardtime.trace4eo.provenance.container.io.TestUtils.SIGNATURE_2;
import static com.guardtime.trace4eo.provenance.container.io.TestUtils.TEST_BYTES_1;
import static com.guardtime.trace4eo.provenance.container.io.TestUtils.TEST_BYTES_2;
import static com.guardtime.trace4eo.provenance.container.io.TestUtils.readSignature;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProvenanceVerificationServiceTest {

    private final ProvenanceVerificationService provenanceVerificationService = new ProvenanceVerificationService();

    @Test
    void verifySuccess() {
        ProvenanceSignature signature1 = readSignature(SIGNATURE_1);
        ProvenanceVerificationResult result1 = provenanceVerificationService.verify(signature1, TEST_BYTES_1);
        assertTrue(result1.status());
        assertNull(result1.error());

        ProvenanceSignature signature2 = readSignature(SIGNATURE_2);
        ProvenanceVerificationResult result2 = provenanceVerificationService.verify(signature2, TEST_BYTES_2);
        assertTrue(result2.status());
        assertNull(result2.error());
    }

    @Test
    void verifyFailure() {
        ProvenanceSignature signature1 = readSignature(SIGNATURE_1);
        ProvenanceVerificationResult result1 = provenanceVerificationService.verify(signature1, new byte[]{6, 6, 6});
        assertFalse(result1.status());
        assertEquals(ProvenanceVerificationError.HASH_MISMATCH, result1.error());

        ProvenanceVerificationResult result2 = provenanceVerificationService.verify(signature1, new byte[]{});
        assertFalse(result2.status());
        assertEquals(ProvenanceVerificationError.HASH_MISMATCH, result2.error());
    }
}
