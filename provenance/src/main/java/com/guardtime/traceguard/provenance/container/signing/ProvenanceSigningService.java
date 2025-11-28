package com.guardtime.traceguard.provenance.container.signing;

import com.guardtime.ksi.Signer;
import com.guardtime.ksi.exceptions.KSIException;
import com.guardtime.ksi.hashing.DataHasher;
import com.guardtime.ksi.hashing.HashAlgorithm;
import com.guardtime.ksi.unisignature.KSISignature;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

public class ProvenanceSigningService {
    private final Signer signer;

    public ProvenanceSigningService(Signer signer) {
        this.signer = signer;
    }

    public KSISignature sign(HashAlgorithm hashAlgorithm, byte[] bytes) throws KSIException {
        return sign(hashAlgorithm, new ByteArrayInputStream(bytes));
    }

    public KSISignature sign(HashAlgorithm hashAlgorithm, InputStream inputStream) throws KSIException {
        DataHasher dh = new DataHasher(hashAlgorithm);
        dh.addData(inputStream);
        return signer.sign(dh.getHash());
    }
}
