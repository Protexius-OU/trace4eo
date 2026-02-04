package com.guardtime.trace4eo.provenance.verification;

import com.guardtime.trace4eo.provenance.ProvenanceSignature;
import com.guardtime.trace4eo.provenance.io.TestUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.guardtime.trace4eo.provenance.io.TestUtils.TEST_BYTES_1;
import static com.guardtime.trace4eo.provenance.io.TestUtils.TEST_BYTES_2;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProvenanceVerificationServiceTest {

    private static ProvenanceSignature signature1;
    private static ProvenanceSignature signature2;

    private final ProvenanceVerificationService verificationService = new ProvenanceVerificationService();

    @BeforeAll
    static void loadFixtures() {
        signature1 = TestUtils.loadFixtureSignature("signature1.json");
        signature2 = TestUtils.loadFixtureSignature("signature2.json");
    }

    @Test
    void verifyValidSignature() {
        ProvenanceVerificationResult result = verificationService.verify(signature1, TEST_BYTES_1);
        assertTrue(result.status());
        assertNull(result.error());
    }

    @Test
    void verifyValidSignatureWithDifferentData() {
        ProvenanceVerificationResult result = verificationService.verify(signature2, TEST_BYTES_2);
        assertTrue(result.status());
        assertNull(result.error());
    }

    @Test
    void verifyFailsWhenDataDoesNotMatchSignature() {
        ProvenanceVerificationResult result = verificationService.verify(signature1, TEST_BYTES_2);
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
