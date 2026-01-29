package com.guardtime.trace4eo.provenance.verification;

import java.util.List;

public record ProvenanceVerificationResult(
    boolean status,
    ProvenanceVerificationError error,
    String errorMessage,
    List<VerificationStep> steps
) {
    public ProvenanceVerificationResult() {
        this(true, null, null, List.of());
    }

    public ProvenanceVerificationResult(ProvenanceVerificationError error, String errorMessage) {
        this(false, error, errorMessage, List.of());
    }

    public ProvenanceVerificationResult(List<VerificationStep> steps) {
        this(steps.stream().allMatch(VerificationStep::status), null, null, steps);
    }

    public ProvenanceVerificationResult(List<VerificationStep> steps, ProvenanceVerificationError error, String errorMessage) {
        this(false, error, errorMessage, steps);
    }
}
