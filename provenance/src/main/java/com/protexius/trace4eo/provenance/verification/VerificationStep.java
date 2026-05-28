package com.protexius.trace4eo.provenance.verification;

public record VerificationStep(
    VerificationStepName name,
    String description,
    boolean status,
    boolean partial,
    String errorMessage
) {
    public static VerificationStep success(VerificationStepName name, String description) {
        return new VerificationStep(name, description, true, false, null);
    }

    public static VerificationStep partial(VerificationStepName name, String description) {
        return new VerificationStep(name, description, true, true, null);
    }

    public static VerificationStep failure(VerificationStepName name, String description, String errorMessage) {
        return new VerificationStep(name, description, false, false, errorMessage);
    }
}
