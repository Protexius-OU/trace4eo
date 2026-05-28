package com.protexius.trace4eo.verification;

import com.protexius.trace4eo.provenance.verification.ProvenanceVerificationResult;

import java.util.List;

record VerificationSummary(int records, long passed, long failed, int skipped) {

    static VerificationSummary of(List<ProvenanceVerificationResult> results, VerificationFormatOptions options) {
        long passed = results.stream().filter(ProvenanceVerificationResult::status).count();
        return new VerificationSummary(results.size(), passed, results.size() - passed, options.skipped());
    }
}
