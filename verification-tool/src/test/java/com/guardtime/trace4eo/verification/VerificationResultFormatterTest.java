package com.guardtime.trace4eo.verification;

import com.guardtime.trace4eo.provenance.verification.ProvenanceVerificationError;
import com.guardtime.trace4eo.provenance.verification.ProvenanceVerificationResult;
import com.guardtime.trace4eo.provenance.verification.VerificationStep;
import com.guardtime.trace4eo.provenance.verification.VerificationStepName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VerificationResultFormatterTest {

    private final TextVerificationResultFormatter formatter = new TextVerificationResultFormatter();

    // --- Single result with no steps (signature verification) ---

    @Test
    void successNoSteps_showsSignatureVerificationHeader() {
        ProvenanceVerificationResult result = new ProvenanceVerificationResult();
        String output = formatter.format(result);
        assertTrue(output.contains("=== Signature Verification ==="));
        assertTrue(output.contains("PASSED"));
    }

    @Test
    void failureNoSteps_showsErrorDetails() {
        ProvenanceVerificationResult result = new ProvenanceVerificationResult(
            ProvenanceVerificationError.SIGNATURE_VERIFICATION_FAILED, "Signature could not be verified");
        String output = formatter.format(result);
        assertTrue(output.contains("=== Signature Verification ==="));
        assertTrue(output.contains("FAILED"));
        assertTrue(output.contains("SIGNATURE_VERIFICATION_FAILED"));
        assertTrue(output.contains("Signature could not be verified"));
    }

    // --- Single result with steps (provenance record verification) ---

    @Test
    void successWithSteps_showsProvenanceRecordHeader() {
        List<VerificationStep> steps = List.of(
            VerificationStep.success(VerificationStepName.FILES_INFO, "Files info hash matches manifest"),
            VerificationStep.success(VerificationStepName.METADATA, "Metadata hash matches manifest"),
            VerificationStep.success(VerificationStepName.FILE_CONTENTS, "1 of 1 file content hashes verified"),
            VerificationStep.success(VerificationStepName.SIGNATURE, "Signature verified against manifest")
        );
        ProvenanceVerificationResult result = new ProvenanceVerificationResult(steps);
        String output = formatter.format(result);
        assertTrue(output.contains("=== Provenance Record Verification ==="));
        assertTrue(output.contains("PASSED"));
        assertTrue(output.contains("4/4"));
        assertTrue(output.contains("[OK]"));
        assertTrue(output.contains("Files Info"));
        assertTrue(output.contains("Metadata"));
        assertTrue(output.contains("File Contents"));
        assertTrue(output.contains("Signature"));
    }

    @Test
    void failureWithMixedSteps_showsFailedStepsAndErrors() {
        List<VerificationStep> steps = List.of(
            VerificationStep.success(VerificationStepName.FILES_INFO, "Files info hash matches manifest"),
            VerificationStep.success(VerificationStepName.METADATA, "Metadata hash matches manifest"),
            VerificationStep.failure(VerificationStepName.FILE_CONTENTS, "Hash mismatch",
                "file data.csv hash does not match"),
            VerificationStep.failure(VerificationStepName.SIGNATURE, "Signature verification failed",
                "Signature could not be verified")
        );
        ProvenanceVerificationResult result = new ProvenanceVerificationResult(steps);
        String output = formatter.format(result);
        assertTrue(output.contains("FAILED"));
        assertTrue(output.contains("2/4"));
        assertTrue(output.contains("[FAIL]"));
        assertTrue(output.contains("File Contents"));
        assertTrue(output.contains("file data.csv hash does not match"));
        assertTrue(output.contains("Signature"));
        assertTrue(output.contains("Signature could not be verified"));
    }

    // --- Multiple records ---

    @Test
    void multipleRecords_showsRecordNumbers() {
        List<VerificationStep> steps = List.of(
            VerificationStep.success(VerificationStepName.SIGNATURE, "Signature verified against manifest")
        );
        ProvenanceVerificationResult r1 = new ProvenanceVerificationResult(steps);
        ProvenanceVerificationResult r2 = new ProvenanceVerificationResult(steps);
        String output = formatter.format(List.of(r1, r2));
        assertTrue(output.contains("(Record 1 of 2)"));
        assertTrue(output.contains("(Record 2 of 2)"));
    }

    @Test
    void singleRecord_showsNoRecordNumber() {
        List<VerificationStep> steps = List.of(
            VerificationStep.success(VerificationStepName.SIGNATURE, "Signature verified against manifest")
        );
        ProvenanceVerificationResult result = new ProvenanceVerificationResult(steps);
        String output = formatter.format(List.of(result));
        assertTrue(!output.contains("Record 1 of 1"));
    }
}
