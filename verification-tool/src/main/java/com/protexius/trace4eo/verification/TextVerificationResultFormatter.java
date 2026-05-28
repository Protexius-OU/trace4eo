package com.protexius.trace4eo.verification;

import com.protexius.trace4eo.provenance.verification.ProvenanceVerificationResult;
import com.protexius.trace4eo.provenance.verification.VerificationStep;
import com.protexius.trace4eo.provenance.verification.VerificationStepName;

import java.util.List;
import java.util.Map;

public class TextVerificationResultFormatter implements VerificationResultFormatter {

    private static final Map<VerificationStepName, String> STEP_LABELS = Map.of(
        VerificationStepName.FILES_INFO, "Files Info",
        VerificationStepName.METADATA, "Metadata",
        VerificationStepName.FILE_CONTENTS, "File Contents",
        VerificationStepName.SIGNATURE, "Signature"
    );

    @Override
    public String format(List<ProvenanceVerificationResult> results, VerificationFormatOptions options) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            ProvenanceVerificationResult result = results.get(i);
            if (options.silent() && result.status()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("\n");
            }
            Integer recordNum = results.size() > 1 ? i + 1 : null;
            Integer total = results.size() > 1 ? results.size() : null;
            appendHeader(sb, result, recordNum, total);
            appendResultLine(sb, result);
            if (!result.steps().isEmpty()) {
                appendStepsSection(sb, result.steps());
            }
        }
        appendSummary(sb, VerificationSummary.of(results, options));
        return sb.toString();
    }

    private void appendHeader(StringBuilder sb, ProvenanceVerificationResult result, Integer recordNum, Integer total) {
        String title = result.steps().isEmpty() ? "Signature Verification" : "Provenance Record Verification";
        sb.append("=== ").append(title).append(" ===");
        if (recordNum != null) {
            sb.append("  (Record ").append(recordNum).append(" of ").append(total).append(")");
        }
        sb.append("\n");
    }

    private void appendResultLine(StringBuilder sb, ProvenanceVerificationResult result) {
        sb.append("\n");
        String status = resultLabel(result);
        if (!result.steps().isEmpty()) {
            long passed = result.steps().stream().filter(s -> s.status() && !s.partial()).count();
            long partial = result.steps().stream().filter(VerificationStep::partial).count();
            sb.append("Result:  ").append(status)
                .append("  (").append(passed).append("/").append(result.steps().size()).append(" checks");
            if (partial > 0) {
                sb.append(", ").append(partial).append(" partial");
            }
            sb.append(")\n");
        } else {
            sb.append("Result:  ").append(status).append("\n");
            if (!result.status()) {
                if (result.error() != null) {
                    sb.append("Error:   ").append(result.error()).append("\n");
                }
                if (result.errorMessage() != null) {
                    sb.append("         ").append(result.errorMessage()).append("\n");
                }
            }
        }
    }

    private String resultLabel(ProvenanceVerificationResult result) {
        if (!result.status()) {
            return "FAILED";
        }
        if (result.steps().stream().anyMatch(VerificationStep::partial)) {
            return "PARTIAL";
        }
        return "PASSED";
    }

    private void appendStepsSection(StringBuilder sb, List<VerificationStep> steps) {
        sb.append("\nSteps:\n");
        for (VerificationStep step : steps) {
            appendStepRow(sb, step);
        }
    }

    private void appendStepRow(StringBuilder sb, VerificationStep step) {
        String label;
        if (!step.status()) {
            label = "[FAIL] ";
        } else if (step.partial()) {
            label = "[?]    ";
        } else {
            label = "[OK]   ";
        }
        String name = STEP_LABELS.getOrDefault(step.name(), step.name().name());
        sb.append("  ").append(label).append(String.format("%-16s", name)).append(step.description()).append("\n");
        if (!step.status() && step.errorMessage() != null) {
            appendErrorSection(sb, step.errorMessage());
        }
    }

    private void appendErrorSection(StringBuilder sb, String errorMessage) {
        String[] lines = errorMessage.split("\n", -1);
        sb.append("           Error: ").append(lines[0]).append("\n");
        for (int i = 1; i < lines.length; i++) {
            sb.append("                  ").append(lines[i]).append("\n");
        }
    }

    private void appendSummary(StringBuilder sb, VerificationSummary summary) {
        if (sb.length() > 0) {
            sb.append("\n");
        }
        sb.append("=== Summary ===\n");
        sb.append("Records verified:  ").append(summary.records()).append("\n");
        sb.append("Passed:            ").append(summary.passed()).append("\n");
        sb.append("Failed:            ").append(summary.failed()).append("\n");
        if (summary.skipped() > 0) {
            sb.append("Skipped:           ").append(summary.skipped()).append("\n");
        }
    }
}
