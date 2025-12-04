package com.guardtime.trace4eo.provenance.container.verification;

public record ProvenanceVerificationResult(
    boolean status,
    ProvenanceVerificationError error,
    String errorMessage
) {
    public ProvenanceVerificationResult() {
        this(true, null, null);
    }

    public ProvenanceVerificationResult(ProvenanceVerificationError error, String errorMessage) {
        this(false, error, errorMessage);
    }
}
