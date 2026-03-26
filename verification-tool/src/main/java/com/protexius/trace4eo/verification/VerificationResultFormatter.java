package com.protexius.trace4eo.verification;

import com.protexius.trace4eo.provenance.verification.ProvenanceVerificationResult;

import java.util.List;

public interface VerificationResultFormatter {
    String format(ProvenanceVerificationResult result);
    String format(List<ProvenanceVerificationResult> results);
}
