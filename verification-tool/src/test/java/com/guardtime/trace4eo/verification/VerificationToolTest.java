package com.guardtime.trace4eo.verification;

import com.guardtime.trace4eo.provenance.Container;
import com.guardtime.trace4eo.provenance.ProvenanceJsonMapper;
import com.guardtime.trace4eo.provenance.io.json.JsonContainerReader;
import com.guardtime.trace4eo.provenance.io.zip.ZipContainerWriter;
import com.guardtime.trace4eo.provenance.verification.ProvenanceVerificationResult;
import com.guardtime.trace4eo.provenance.verification.ProvenanceVerificationService;
import com.guardtime.trace4eo.provenance.verification.VerificationStepName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
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

    private final ProvenanceJsonMapper provenanceJsonMapper = new ProvenanceJsonMapper();
    private final ProvenanceVerificationService verificationService = new ProvenanceVerificationService();
    private final VerificationTool verificationTool = new VerificationTool(verificationService, provenanceJsonMapper);

    @Test
    void verifyValidSignature() {
        ProvenanceVerificationResult result = verificationTool.verify(Path.of(TEST_FILE), Path.of(SIGNATURE_FILE));
        assertTrue(result.status());
    }

    @Test
    void verifyInvalidSignature() {
        ProvenanceVerificationResult result = verificationTool.verify(Path.of(OTHER_FILE), Path.of(SIGNATURE_FILE));
        assertFalse(result.status());
    }

    @Test
    void verifyProvenanceRecord() {
        List<ProvenanceVerificationResult> results = verificationTool.verify(Path.of(PROVENANCE_RECORD_FILE));
        assertEquals(1, results.size());
        ProvenanceVerificationResult result = results.getFirst();
        assertTrue(result.status(), () -> String.format(
            "Verification failed: %s - %s", result.error(), result.errorMessage()));
    }

    @Test
    void verifyProvenanceRecordZip(@TempDir Path tempDir) throws IOException {
        Container container = new JsonContainerReader(provenanceJsonMapper)
            .read(Path.of(PROVENANCE_RECORD_FILE));
        Path zipPath = tempDir.resolve("provenance-record.zip");
        new ZipContainerWriter(provenanceJsonMapper)
            .writeTo(container, Files.newOutputStream(zipPath));

        List<ProvenanceVerificationResult> results = verificationTool.verify(zipPath);
        assertEquals(1, results.size());
        ProvenanceVerificationResult result = results.getFirst();
        assertTrue(result.status(), () -> String.format(
            "Verification failed: %s - %s", result.error(), result.errorMessage()));
    }

    @Test
    void verifyProvenanceRecordWithInvalidSignature() {
        List<ProvenanceVerificationResult> results = verificationTool.verify(
            Path.of(INVALID_SIGNATURE_PROVENANCE_RECORD_FILE));
        assertEquals(1, results.size());
        assertFalse(results.getFirst().status());
        assertTrue(results.getFirst().steps().stream()
            .anyMatch(s -> s.name() == VerificationStepName.SIGNATURE
                && !s.status()),
            "Expected a failed SIGNATURE verification step");
    }

    @Test
    void verifyProvenanceRecordWithInvalidContents() {
        List<ProvenanceVerificationResult> results = verificationTool.verify(
            Path.of(INVALID_CONTENTS_PROVENANCE_RECORD_FILE));
        assertEquals(1, results.size());
        assertFalse(results.getFirst().status());
        assertTrue(results.getFirst().steps().stream()
            .anyMatch(s -> s.name() == VerificationStepName.FILES_INFO
                && !s.status()),
            "Expected a failed FILES_INFO verification step");
    }

}
