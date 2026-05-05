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
    public String format(ProvenanceVerificationResult result) {
        StringBuilder sb = new StringBuilder();
        appendHeader(sb, result, null, null);
        appendResultLine(sb, result);
        if (!result.steps().isEmpty()) {
            appendStepsSection(sb, result.steps());
        }
        return sb.toString();
    }

    @Override
    public String format(List<ProvenanceVerificationResult> results) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            if (i > 0) {
                sb.append("\n");
            }
            ProvenanceVerificationResult result = results.get(i);
            Integer recordNum = results.size() > 1 ? i + 1 : null;
            Integer total = results.size() > 1 ? results.size() : null;
            appendHeader(sb, result, recordNum, total);
            appendResultLine(sb, result);
            if (!result.steps().isEmpty()) {
                appendStepsSection(sb, result.steps());
            }
        }
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
        String status = result.status() ? "PASSED" : "FAILED";
        if (!result.steps().isEmpty()) {
            long passed = result.steps().stream().filter(VerificationStep::status).count();
            sb.append("Result:  ").append(status)
                .append("  (").append(passed).append("/").append(result.steps().size()).append(" checks)\n");
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

    private void appendStepsSection(StringBuilder sb, List<VerificationStep> steps) {
        sb.append("\nSteps:\n");
        for (VerificationStep step : steps) {
            appendStepRow(sb, step);
        }
    }

    private void appendStepRow(StringBuilder sb, VerificationStep step) {
        String label = step.status() ? "[OK]   " : "[FAIL] ";
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
}
