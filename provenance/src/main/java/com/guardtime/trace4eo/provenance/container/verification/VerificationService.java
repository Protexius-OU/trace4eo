package com.guardtime.trace4eo.provenance.container.verification;

import com.guardtime.ksi.PublicationsHandler;
import com.guardtime.ksi.Verifier;
import com.guardtime.ksi.exceptions.KSIException;
import com.guardtime.ksi.hashing.DataHasher;
import com.guardtime.ksi.hashing.HashAlgorithm;
import com.guardtime.ksi.unisignature.KSISignature;
import com.guardtime.ksi.unisignature.verifier.VerificationResult;
import com.guardtime.ksi.unisignature.verifier.policies.ContextAwarePolicy;
import com.guardtime.ksi.unisignature.verifier.policies.ContextAwarePolicyAdapter;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

public class VerificationService {
    private final Verifier verifier;
    private final PublicationsHandler publicationsHandler;

    public VerificationService(Verifier verifier, PublicationsHandler publicationsHandler) {
        this.verifier = verifier;
        this.publicationsHandler = publicationsHandler;
    }

    public VerificationResult verify(KSISignature signature, byte[] bytes) throws KSIException {
        return verify(signature, new ByteArrayInputStream(bytes));
    }

    public VerificationResult verify(KSISignature signature, InputStream inputStream) throws KSIException {
        ContextAwarePolicy publicationsFilePolicy =
            ContextAwarePolicyAdapter.createPublicationsFilePolicy(publicationsHandler);
        ContextAwarePolicy keyPolicy = ContextAwarePolicyAdapter.createKeyPolicy(publicationsHandler);
        publicationsFilePolicy.setFallbackPolicy(keyPolicy);
        HashAlgorithm hashAlgorithm = signature.getInputHash().getAlgorithm();
        DataHasher dataHasher = new DataHasher(hashAlgorithm);
        dataHasher.addData(inputStream);
        return verifier.verify(signature, dataHasher.getHash(), publicationsFilePolicy);
    }
}
