package com.guardtime.trace4eo.verification;

import com.guardtime.trace4eo.provenance.verification.ProvenanceVerificationResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerificationToolTest {

    @Test
    void verify() {
        VerificationTool verificationTool = new VerificationTool();
        String artifactPath = "src/test/resources/test.txt";
        String signaturePath = "src/test/resources/provenance-signature.json";
        ProvenanceVerificationResult result = verificationTool.verify(Path.of(artifactPath), Path.of(signaturePath));
        assertTrue(result.status());
    }

    @Test
    void verifyInvalid() {
        VerificationTool verificationTool = new VerificationTool();
        String unsignedArtifactPath = "src/test/resources/other.txt";
        String signaturePath = "src/test/resources/provenance-signature.json";
        ProvenanceVerificationResult result = verificationTool.verify(Path.of(unsignedArtifactPath), Path.of(signaturePath));
        assertFalse(result.status());
    }

    @Test
    void verifyProvenanceRecord() {
        VerificationTool verificationTool = new VerificationTool();
        String provenanceRecordPath = "src/test/resources/provenance-record.json";
        List<ProvenanceVerificationResult> result = verificationTool.verify(Path.of(provenanceRecordPath));
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
        assertTrue(result.getFirst().status());
    }
}
