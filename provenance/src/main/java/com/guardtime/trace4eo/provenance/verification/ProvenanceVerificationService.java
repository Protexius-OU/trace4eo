package com.guardtime.trace4eo.provenance.verification;

import com.guardtime.trace4eo.provenance.HashAlgorithm;
import com.guardtime.trace4eo.provenance.ProvenanceJsonMapper;
import com.guardtime.trace4eo.provenance.ProvenanceSignature;
import com.guardtime.trace4eo.provenance.record.FileHashInfo;
import com.guardtime.trace4eo.provenance.record.FilesInfo;
import com.guardtime.trace4eo.provenance.record.Manifest;
import com.guardtime.trace4eo.provenance.record.Metadata;
import com.guardtime.trace4eo.provenance.record.ProvenanceRecord;
import dev.sigstore.KeylessVerifier;
import dev.sigstore.VerificationOptions;
import dev.sigstore.bundle.Bundle;
import dev.sigstore.bundle.BundleParseException;
import dev.sigstore.json.canonicalizer.JsonCanonicalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ProvenanceVerificationService {

    private static final Logger log = LoggerFactory.getLogger(ProvenanceVerificationService.class);

    private final KeylessVerifier verifier;

    public ProvenanceVerificationService() {
        try {
            this.verifier = KeylessVerifier.builder().sigstorePublicDefaults().build();
        } catch (Exception e) {
            log.error("Failed to initialize provenance verification service", e);
            throw new RuntimeException(e);
        }
    }

    public ProvenanceVerificationResult verify(ProvenanceSignature signature, byte[] bytes) {
        return verify(signature, new ByteArrayInputStream(bytes));
    }

    public ProvenanceVerificationResult verify(ProvenanceSignature signature, InputStream inputStream) {
        HashAlgorithm hashAlgorithm = signature.hashAlgorithm();
        MessageDigest md;
        try {
            md = MessageDigest.getInstance(hashAlgorithm.name());
        } catch (NoSuchAlgorithmException e) {
            return new ProvenanceVerificationResult(ProvenanceVerificationError.UNSUPPORTED_HASH_ALGORITHM,
                String.format("Hash algorithm %s is not supported.", hashAlgorithm.name()));
        }
        try (DigestInputStream digestInputStream = new DigestInputStream(inputStream, md)) {
            digestInputStream.transferTo(OutputStream.nullOutputStream());
            byte[] digest = md.digest();

            Bundle bundle = parseBundle(signature.bytes());
            verifier.verify(digest, bundle, VerificationOptions.empty());
            return new ProvenanceVerificationResult();
        } catch (Exception e) {
            log.error("Failed to verify provenance signature", e);
            return new ProvenanceVerificationResult(ProvenanceVerificationError.SIGNATURE_VERIFICATION_FAILED,
                "Signature verification failed: " + e.getMessage());
        }
    }

    private Bundle parseBundle(byte[] bundleBytes) throws IOException {
        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(bundleBytes), StandardCharsets.UTF_8)) {
            return Bundle.from(reader);
        } catch (BundleParseException e) {
            throw new IOException("Failed to parse bundle", e);
        }
    }

    public ProvenanceVerificationResult verify(ProvenanceRecord provenanceRecord) {
        List<VerificationStep> steps = new ArrayList<>();

        // Step 1: Verify FilesInfo hash
        ProvenanceVerificationResult filesInfoResult = verifyFilesInfo(provenanceRecord.manifest(),
            provenanceRecord.filesInfo());
        if (filesInfoResult.status()) {
            steps.add(VerificationStep.success(VerificationStepName.FILES_INFO, "Files info hash matches manifest"));
        } else {
            steps.add(VerificationStep.failure(VerificationStepName.FILES_INFO, "Files info hash verification", filesInfoResult.errorMessage()));
        }

        // Step 2: Verify Metadata hash
        ProvenanceVerificationResult metadataResult = verifyMetadata(provenanceRecord.manifest(),
            provenanceRecord.metadata());
        if (metadataResult.status()) {
            steps.add(VerificationStep.success(VerificationStepName.METADATA, "Metadata hash matches manifest"));
        } else {
            steps.add(VerificationStep.failure(VerificationStepName.METADATA, "Metadata hash verification", metadataResult.errorMessage()));
        }

        // Step 3: Verify file content hashes (if available)
        ProvenanceVerificationResult filesResult = verifyFiles(provenanceRecord.filesInfo());
        if (filesResult.status()) {
            if (provenanceRecord.filesInfo().filesContext() != null) {
                steps.add(VerificationStep.success(VerificationStepName.FILE_CONTENTS, "All file content hashes verified"));
            } else {
                steps.add(VerificationStep.success(VerificationStepName.FILE_CONTENTS, "File content verification skipped (files not available)"));
            }
        } else {
            steps.add(VerificationStep.failure(VerificationStepName.FILE_CONTENTS, "File content hash verification", filesResult.errorMessage()));
        }

        // Step 4: Verify signature against manifest
        ProvenanceVerificationResult signatureResult = verifySignature(provenanceRecord);
        if (signatureResult.status()) {
            steps.add(VerificationStep.success(VerificationStepName.SIGNATURE, "Signature verified against manifest"));
        } else {
            steps.add(VerificationStep.failure(VerificationStepName.SIGNATURE, "Signature verification", signatureResult.errorMessage()));
        }

        return new ProvenanceVerificationResult(steps);
    }

    private ProvenanceVerificationResult verifySignature(ProvenanceRecord provenanceRecord) {
        byte[] manifestBytes;
        try {
            manifestBytes = new JsonCanonicalizer(new ProvenanceJsonMapper().writeValueAsBytes(provenanceRecord.manifest())).getEncodedUTF8();
        } catch (IOException e) {
            log.warn("Failed to canonicalize Manifest for signature verification", e);
            throw new IllegalStateException(e);
        }
        return verify(provenanceRecord.signature(), manifestBytes);
    }

    private ProvenanceVerificationResult verifyFilesInfo(Manifest manifest, FilesInfo filesInfo) {
        byte[] filesInfoBytes;
        try {
            filesInfoBytes = new JsonCanonicalizer(new ProvenanceJsonMapper().writeValueAsBytes(filesInfo)).getEncodedUTF8();
        } catch (IOException e) {
            log.warn("Failed to canonicalize FilesInfo", e);
            throw new IllegalStateException(e);
        }
        FileHashInfo filesHashInfo = manifest.filesHashInfo();
        MessageDigest md;
        try {
            md = MessageDigest.getInstance(filesHashInfo.hashAlgorithm().getName());
        } catch (NoSuchAlgorithmException e) {
            return new ProvenanceVerificationResult(ProvenanceVerificationError.UNSUPPORTED_HASH_ALGORITHM,
                String.format("Hash algorithm %s is not supported.", filesHashInfo.hashAlgorithm().name()));
        }
        md.update(filesInfoBytes);
        if (!Arrays.equals(md.digest(), filesHashInfo.hashValue())) {
            String msg = "FilesInfo hash mismatch";
            return new ProvenanceVerificationResult(ProvenanceVerificationError.HASH_MISMATCH, msg);
        }
        return new ProvenanceVerificationResult();
    }

    private ProvenanceVerificationResult verifyMetadata(Manifest manifest, Metadata metadata) {
        byte[] metadataBytes;
        try {
            metadataBytes = new JsonCanonicalizer(new ProvenanceJsonMapper().writeValueAsBytes(metadata)).getEncodedUTF8();
        } catch (IOException e) {
            log.warn("Failed to canonicalize Metadata", e);
            throw new IllegalStateException(e);
        }
        FileHashInfo metadataHashInfo = manifest.metadataHashInfo();
        MessageDigest md;
        try {
            md = MessageDigest.getInstance(metadataHashInfo.hashAlgorithm().getName());
        } catch (NoSuchAlgorithmException e) {
            return new ProvenanceVerificationResult(ProvenanceVerificationError.UNSUPPORTED_HASH_ALGORITHM,
                String.format("Hash algorithm %s is not supported.", metadataHashInfo.hashAlgorithm().name()));
        }
        md.update(metadataBytes);
        if (!Arrays.equals(md.digest(), metadataHashInfo.hashValue())) {
            String msg = "Metadata hash mismatch";
            return new ProvenanceVerificationResult(ProvenanceVerificationError.HASH_MISMATCH, msg);
        }
        return new ProvenanceVerificationResult();
    }

    // TODO: revisit file content verification when filesContext is null - should this be an error or warning?
    private ProvenanceVerificationResult verifyFiles(FilesInfo filesInfo) {
        if (filesInfo.filesContext() == null) {
            log.debug("Skipping file content verification - no filesContext available");
            return new ProvenanceVerificationResult();
        }
        for (FileHashInfo file : filesInfo.files()) {
            log.info("Verifying file {}", file);
            MessageDigest md;
            try {
                md = MessageDigest.getInstance(file.hashAlgorithm().getName());
            } catch (NoSuchAlgorithmException e) {
                return new ProvenanceVerificationResult(ProvenanceVerificationError.UNSUPPORTED_HASH_ALGORITHM,
                    String.format("Hash algorithm %s is not supported.", file.hashAlgorithm().name()));
            }
            try (InputStream fileContentsStream = filesInfo.filesContext().getFileContents(file)) {
                try (DigestInputStream digestInputStream = new DigestInputStream(fileContentsStream, md)) {
                    digestInputStream.transferTo(OutputStream.nullOutputStream());
                    if (!Arrays.equals(md.digest(), file.hashValue())) {
                        String msg = String.format("File %s hash mismatch", file.path());
                        return new ProvenanceVerificationResult(ProvenanceVerificationError.HASH_MISMATCH, msg);
                    }
                }
            } catch (IOException e) {
                String msg = String.format("Failed to verify file %s", file.path());
                log.error(msg, e);
                return new ProvenanceVerificationResult(ProvenanceVerificationError.UNKNOWN_ERROR, msg);
            }
        }
        return new ProvenanceVerificationResult();
    }
}
