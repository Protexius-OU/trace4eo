package com.guardtime.trace4eo.verification;

import com.guardtime.trace4eo.provenance.verification.ProvenanceVerificationResult;

import java.util.List;

public interface VerificationResultFormatter {
    String format(ProvenanceVerificationResult result);
    String format(List<ProvenanceVerificationResult> results);
}
