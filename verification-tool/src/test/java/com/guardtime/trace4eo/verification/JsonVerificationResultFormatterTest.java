package com.guardtime.trace4eo.verification;

import com.guardtime.trace4eo.provenance.ProvenanceJsonMapper;
import com.guardtime.trace4eo.provenance.verification.ProvenanceVerificationError;
import com.guardtime.trace4eo.provenance.verification.ProvenanceVerificationResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonVerificationResultFormatterTest {

    private final JsonVerificationResultFormatter formatter =
        new JsonVerificationResultFormatter(new ProvenanceJsonMapper());

    @Test
    void successNoSteps_producesJsonWithStatusTrue() {
        ProvenanceVerificationResult result = new ProvenanceVerificationResult();
        String output = formatter.format(result);
        assertTrue(output.contains("\"status\" : true"));
    }

    @Test
    void failureNoSteps_producesJsonWithErrorFields() {
        ProvenanceVerificationResult result = new ProvenanceVerificationResult(
            ProvenanceVerificationError.SIGNATURE_VERIFICATION_FAILED, "Could not verify");
        String output = formatter.format(result);
        assertTrue(output.contains("\"status\" : false"));
        assertTrue(output.contains("SIGNATURE_VERIFICATION_FAILED"));
        assertTrue(output.contains("Could not verify"));
    }

    @Test
    void listResults_producesJsonArray() {
        ProvenanceVerificationResult r1 = new ProvenanceVerificationResult();
        ProvenanceVerificationResult r2 = new ProvenanceVerificationResult(
            ProvenanceVerificationError.HASH_MISMATCH, "hash mismatch");
        String output = formatter.format(List.of(r1, r2));
        assertTrue(output.startsWith("["));
        assertTrue(output.contains("\"status\" : true"));
        assertTrue(output.contains("\"status\" : false"));
    }
}
