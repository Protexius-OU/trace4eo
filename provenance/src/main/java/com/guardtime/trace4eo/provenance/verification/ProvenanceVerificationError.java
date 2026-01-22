package com.guardtime.trace4eo.provenance.verification;

public enum ProvenanceVerificationError {
    UNSUPPORTED_HASH_ALGORITHM,
    HASH_MISMATCH,
    SIGNATURE_VERIFICATION_FAILED,
    UNKNOWN_ERROR
}
