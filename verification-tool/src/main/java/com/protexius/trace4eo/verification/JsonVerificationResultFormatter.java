package com.protexius.trace4eo.verification;

import com.protexius.trace4eo.provenance.ProvenanceJsonMapper;
import com.protexius.trace4eo.provenance.verification.ProvenanceVerificationResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JsonVerificationResultFormatter implements VerificationResultFormatter {

    private final ProvenanceJsonMapper mapper;

    public JsonVerificationResultFormatter(ProvenanceJsonMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String format(List<ProvenanceVerificationResult> results, VerificationFormatOptions options) {
        List<ProvenanceVerificationResult> visible = options.silent()
            ? results.stream().filter(r -> !r.status()).toList()
            : results;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", VerificationSummary.of(results, options));
        payload.put("results", visible);
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize results to JSON", e);
        }
    }
}
