package com.guardtime.trace4eo.verification;

import com.guardtime.trace4eo.provenance.ProvenanceJsonMapper;
import com.guardtime.trace4eo.provenance.verification.ProvenanceVerificationResult;

import java.util.List;

public class JsonVerificationResultFormatter implements VerificationResultFormatter {

    private final ProvenanceJsonMapper mapper;

    public JsonVerificationResultFormatter(ProvenanceJsonMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String format(ProvenanceVerificationResult result) {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize result to JSON", e);
        }
    }

    @Override
    public String format(List<ProvenanceVerificationResult> results) {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(results);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize results to JSON", e);
        }
    }
}
