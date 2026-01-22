package com.guardtime.trace4eo.provenance.verification;

import com.guardtime.trace4eo.provenance.HashAlgorithm;
import com.guardtime.trace4eo.provenance.ProvenanceSignature;
import com.guardtime.trace4eo.provenance.signing.ProvenanceSigningService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProvenanceVerificationServiceTest {

    private static final byte[] TEST_DATA_1 = new byte[]{1, 2, 3};
    private static final byte[] TEST_DATA_2 = new byte[]{4, 5, 6};

    private static ProvenanceSignature signature1;
    private static ProvenanceSignature signature2;

    private final ProvenanceVerificationService verificationService = new ProvenanceVerificationService();

    @BeforeAll
    static void signTestData() {
        ProvenanceSigningService signingService = new ProvenanceSigningService();
        signature1 = signingService.sign(TEST_DATA_1, HashAlgorithm.SHA256);
        signature2 = signingService.sign(TEST_DATA_2, HashAlgorithm.SHA256);
    }

    @Test
    void verifyValidSignature() {
        ProvenanceVerificationResult result = verificationService.verify(signature1, TEST_DATA_1);
        assertTrue(result.status());
        assertNull(result.error());
    }

    @Test
    void verifyValidSignatureWithDifferentData() {
        ProvenanceVerificationResult result = verificationService.verify(signature2, TEST_DATA_2);
        assertTrue(result.status());
        assertNull(result.error());
    }

    @Test
    void verifyFailsWhenDataDoesNotMatchSignature() {
        ProvenanceVerificationResult result = verificationService.verify(signature1, TEST_DATA_2);
        assertFalse(result.status());
        assertEquals(ProvenanceVerificationError.SIGNATURE_VERIFICATION_FAILED, result.error());
    }

    @Test
    void verifyFailsWithEmptyData() {
        ProvenanceVerificationResult result = verificationService.verify(signature1, new byte[]{});
        assertFalse(result.status());
        assertEquals(ProvenanceVerificationError.SIGNATURE_VERIFICATION_FAILED, result.error());
    }
}
