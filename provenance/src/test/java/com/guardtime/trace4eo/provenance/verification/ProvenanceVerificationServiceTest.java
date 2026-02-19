package com.guardtime.trace4eo.provenance.verification;

import com.guardtime.trace4eo.provenance.HashAlgorithm;
import com.guardtime.trace4eo.provenance.ProvenanceSignature;
import com.guardtime.trace4eo.provenance.io.TestUtils;
import com.guardtime.trace4eo.provenance.record.FileHashInfo;
import com.guardtime.trace4eo.provenance.record.ProvenanceRecord;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static com.guardtime.trace4eo.provenance.io.TestUtils.TEST_BYTES_1;
import static com.guardtime.trace4eo.provenance.io.TestUtils.TEST_BYTES_2;
import static com.guardtime.trace4eo.provenance.io.TestUtils.TEST_FILE_1;
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

    @Test
    void verifyWithFileHashesPassesForCorrectHash() throws IOException {
        ProvenanceRecord record = TestUtils.createProvenanceRecord(TEST_FILE_1);
        FileHashInfo fileHashInfo = record.filesInfo().files().iterator().next();
        Map<String, byte[]> hashes = Map.of(fileHashInfo.path(), fileHashInfo.hashValue());

        ProvenanceVerificationResult result = verificationService.verifyWithFileHashes(record, hashes);

        assertTrue(result.steps().stream()
            .anyMatch(s -> s.name() == VerificationStepName.FILE_CONTENTS && s.status()),
            "Expected a passing FILE_CONTENTS step");
    }

    @Test
    void verifyWithFileHashesFailsForWrongHash() throws IOException {
        ProvenanceRecord record = TestUtils.createProvenanceRecord(TEST_FILE_1);
        FileHashInfo fileHashInfo = record.filesInfo().files().iterator().next();
        Map<String, byte[]> hashes = Map.of(fileHashInfo.path(), new byte[32]);

        ProvenanceVerificationResult result = verificationService.verifyWithFileHashes(record, hashes);

        assertFalse(result.status());
        assertTrue(result.steps().stream()
            .anyMatch(s -> s.name() == VerificationStepName.FILE_CONTENTS && !s.status()),
            "Expected a failed FILE_CONTENTS step");
        assertTrue(result.steps().stream()
            .filter(s -> s.name() == VerificationStepName.FILE_CONTENTS)
            .anyMatch(s -> s.errorMessage() != null && s.errorMessage().contains("hash mismatch")),
            "Expected 'hash mismatch' in FILE_CONTENTS error message");
    }

    @Test
    void verifyWithFileHashesFailsForWrongHashLength() throws IOException {
        ProvenanceRecord record = TestUtils.createProvenanceRecord(TEST_FILE_1);
        FileHashInfo fileHashInfo = record.filesInfo().files().iterator().next();
        Map<String, byte[]> hashes = Map.of(fileHashInfo.path(), new byte[64]); // SHA-512 size against SHA-256 record

        ProvenanceVerificationResult result = verificationService.verifyWithFileHashes(record, hashes);

        assertFalse(result.status());
        assertTrue(result.steps().stream()
            .filter(s -> s.name() == VerificationStepName.FILE_CONTENTS)
            .anyMatch(s -> !s.status() && s.errorMessage() != null
                && s.errorMessage().contains("SHA-256")
                && s.errorMessage().contains("64 bytes")),
            "Expected error message to name the algorithm and the wrong length");
    }

    @Test
    void verifyWithFileHashesPassesForNonDefaultAlgorithm() throws IOException {
        ProvenanceRecord record = TestUtils.createProvenanceRecord(TEST_FILE_1, HashAlgorithm.SHA512);
        FileHashInfo fileHashInfo = record.filesInfo().files().iterator().next();
        Map<String, byte[]> hashes = Map.of(fileHashInfo.path(), fileHashInfo.hashValue());

        ProvenanceVerificationResult result = verificationService.verifyWithFileHashes(record, hashes);

        assertTrue(result.steps().stream()
            .anyMatch(s -> s.name() == VerificationStepName.FILE_CONTENTS && s.status()),
            "Expected FILE_CONTENTS to pass for SHA-512 record");
    }

    @Test
    void verifyWithFileHashesSkipsWhenMapIsEmpty() throws IOException {
        ProvenanceRecord record = TestUtils.createProvenanceRecord(TEST_FILE_1);

        ProvenanceVerificationResult result = verificationService.verifyWithFileHashes(record, Map.of());

        assertTrue(result.steps().stream()
            .anyMatch(s -> s.name() == VerificationStepName.FILE_CONTENTS
                && s.status()
                && s.description().contains("no hashes provided")),
            "Expected FILE_CONTENTS step to be skipped with 'no hashes provided'");
    }
}
