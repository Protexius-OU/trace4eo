package com.guardtime.trace4eo.verification;

import com.guardtime.trace4eo.provenance.verification.ProvenanceVerificationError;
import com.guardtime.trace4eo.provenance.verification.ProvenanceVerificationResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerificationToolTest {

    private static final String TEST_FILE = "src/test/resources/test.txt";
    private static final String OTHER_FILE = "src/test/resources/other.txt";
    private static final String SIGNATURE_FILE = "src/test/resources/signature.json";
    private static final String PROVENANCE_RECORD_FILE = "src/test/resources/provenance-record.json";
    private static final String INVALID_SIGNATURE_PROVENANCE_RECORD_FILE = "src/test/resources/invalid-signature-provenance-record.json";
    private static final String INVALID_CONTENTS_PROVENANCE_RECORD_FILE = "src/test/resources/invalid-contents-provenance-record.json";

    @Test
    void verifyValidSignature() {
        VerificationTool verificationTool = new VerificationTool();
        ProvenanceVerificationResult result = verificationTool.verify(Path.of(TEST_FILE), Path.of(SIGNATURE_FILE));
        assertTrue(result.status());
    }

    @Test
    void verifyInvalidSignature() {
        VerificationTool verificationTool = new VerificationTool();
        ProvenanceVerificationResult result = verificationTool.verify(Path.of(OTHER_FILE), Path.of(SIGNATURE_FILE));
        assertFalse(result.status());
    }

    @Test
    void verifyProvenanceRecord() {
        VerificationTool verificationTool = new VerificationTool();
        List<ProvenanceVerificationResult> results = verificationTool.verify(Path.of(PROVENANCE_RECORD_FILE));
        assertEquals(2, results.size());
        for (ProvenanceVerificationResult result : results) {
            assertTrue(result.status(), () -> String.format("Verification failed: %s - %s", result.error(), result.errorMessage()));
        }
    }

    @Test
    void verifyProvenanceRecordWithInvalidSignature() {
        VerificationTool verificationTool = new VerificationTool();
        List<ProvenanceVerificationResult> results = verificationTool.verify(Path.of(INVALID_SIGNATURE_PROVENANCE_RECORD_FILE));
        assertEquals(2, results.size());

        long signatureFailureCount = results.stream()
            .filter(r -> r.error() == ProvenanceVerificationError.SIGNATURE_VERIFICATION_FAILED)
            .count();
        assertEquals(1, signatureFailureCount,
            String.format("Expected exactly one result with %s error", ProvenanceVerificationError.SIGNATURE_VERIFICATION_FAILED));
    }

    @Test
    void verifyProvenanceRecordWithInvalidContents() {
        VerificationTool verificationTool = new VerificationTool();
        List<ProvenanceVerificationResult> results = verificationTool.verify(Path.of(INVALID_CONTENTS_PROVENANCE_RECORD_FILE));
        assertEquals(2, results.size());

        long hashMismatchCount = results.stream()
            .filter(r -> r.error() == ProvenanceVerificationError.HASH_MISMATCH)
            .count();
        assertEquals(1, hashMismatchCount,
            String.format("Expected exactly one result with %s error", ProvenanceVerificationError.HASH_MISMATCH));
    }

}
