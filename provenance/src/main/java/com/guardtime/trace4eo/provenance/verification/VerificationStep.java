package com.guardtime.trace4eo.provenance.verification;

public record VerificationStep(
    String name,
    String description,
    boolean status,
    String errorMessage
) {
    public static VerificationStep success(String name, String description) {
        return new VerificationStep(name, description, true, null);
    }

    public static VerificationStep failure(String name, String description, String errorMessage) {
        return new VerificationStep(name, description, false, errorMessage);
    }
}
