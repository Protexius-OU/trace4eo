package com.protexius.trace4eo.verification;

public record VerificationFormatOptions(boolean silent, int skipped) {

    public static VerificationFormatOptions defaults() {
        return new VerificationFormatOptions(false, 0);
    }
}
