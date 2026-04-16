package com.protexius.trace4eo.provenance;

import com.protexius.trace4eo.provenance.verification.ProvenanceVerificationError;
import com.protexius.trace4eo.provenance.verification.ProvenanceVerificationResult;
import com.protexius.trace4eo.provenance.verification.VerificationStep;

import java.util.List;

public record FileVerificationResponse(
    boolean status,
    ProvenanceVerificationError error,
    String errorMessage,
    List<VerificationStep> steps,
    List<FileCheckResult> fileResults
) {
    static FileVerificationResponse from(ProvenanceVerificationResult result, List<FileCheckResult> fileResults) {
        return new FileVerificationResponse(
            result.status(), result.error(), result.errorMessage(), result.steps(), fileResults
        );
    }
}
