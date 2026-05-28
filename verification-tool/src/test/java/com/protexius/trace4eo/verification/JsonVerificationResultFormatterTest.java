package com.protexius.trace4eo.verification;

import com.protexius.trace4eo.provenance.ProvenanceJsonMapper;
import com.protexius.trace4eo.provenance.verification.ProvenanceVerificationError;
import com.protexius.trace4eo.provenance.verification.ProvenanceVerificationResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonVerificationResultFormatterTest {

    private final JsonVerificationResultFormatter formatter =
        new JsonVerificationResultFormatter(new ProvenanceJsonMapper());

    private String format(ProvenanceVerificationResult result) {
        return formatter.format(List.of(result), VerificationFormatOptions.defaults());
    }

    @Test
    void successNoSteps_producesJsonWithStatusTrue() {
        String output = format(new ProvenanceVerificationResult());
        assertTrue(output.contains("\"status\" : true"));
    }

    @Test
    void failureNoSteps_producesJsonWithErrorFields() {
        String output = format(new ProvenanceVerificationResult(
            ProvenanceVerificationError.SIGNATURE_VERIFICATION_FAILED, "Could not verify"));
        assertTrue(output.contains("\"status\" : false"));
        assertTrue(output.contains("SIGNATURE_VERIFICATION_FAILED"));
        assertTrue(output.contains("Could not verify"));
    }

    @Test
    void listResultsWithOptions_wrapsInSummaryAndResults() {
        ProvenanceVerificationResult r1 = new ProvenanceVerificationResult();
        ProvenanceVerificationResult r2 = new ProvenanceVerificationResult(
            ProvenanceVerificationError.HASH_MISMATCH, "hash mismatch");
        String output = formatter.format(List.of(r1, r2), new VerificationFormatOptions(false, 3));
        assertTrue(output.contains("\"summary\""));
        assertTrue(output.contains("\"records\" : 2"));
        assertTrue(output.contains("\"passed\" : 1"));
        assertTrue(output.contains("\"failed\" : 1"));
        assertTrue(output.contains("\"skipped\" : 3"));
        assertTrue(output.contains("\"results\""));
        assertTrue(output.contains("\"status\" : true"));
        assertTrue(output.contains("\"status\" : false"));
    }

    @Test
    void listResultsWithOptions_silentDropsPassingResults() {
        ProvenanceVerificationResult r1 = new ProvenanceVerificationResult();
        ProvenanceVerificationResult r2 = new ProvenanceVerificationResult(
            ProvenanceVerificationError.HASH_MISMATCH, "hash mismatch");
        String output = formatter.format(List.of(r1, r2), new VerificationFormatOptions(true, 0));
        assertTrue(output.contains("\"status\" : false"));
        assertTrue(!output.contains("\"status\" : true"),
            () -> "Did not expect passing results to be serialized in silent mode, got: " + output);
        assertTrue(output.contains("\"passed\" : 1"));
        assertTrue(output.contains("\"failed\" : 1"));
    }
}
