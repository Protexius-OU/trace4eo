package com.protexius.trace4eo.provenance.sentinel2;

import org.bouncycastle.jcajce.provider.digest.Blake3.Blake3_256;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Sentinel2TraceabilityService {

    private static final String SUPPORTED_HASH_ALGORITHM = "BLAKE3";
    private static final String SUPPORTED_SIGNATURE_ALGORITHM = "RSA-SHA256";

    private final Sentinel2TracingClient tracingClient;

    public Sentinel2TraceabilityService(Sentinel2TracingClient tracingClient) {
        this.tracingClient = tracingClient;
    }

    /**
     * Full verification: fetches the trace for {@code imageId}, verifies its signature, and checks
     * that the BLAKE3 hash of {@code filePath} matches the matching entry in the signed message.
     */
    public Sentinel2VerificationResult verify(String imageId, Path filePath) throws Exception {
        if (!Files.isRegularFile(filePath)) {
            throw new RuntimeException("File not found: " + filePath);
        }

        Sentinel2TraceResponse.Trace trace = tracingClient.getProductCreateEventTrace(imageId)
                .orElse(null);
        if (trace == null) {
            return Sentinel2VerificationResult.traceNotFound(imageId, filePath);
        }
        return new VerificationContext(imageId, filePath, trace).verify();
    }

    /**
     * Trace-only verification: fetches the trace for {@code imageId} and verifies its signature.
     * Does not require a local file. Use when the product file isn't available (e.g. server-side
     * verification of an uploaded provenance record).
     */
    public Sentinel2TraceVerificationResult verifyTrace(String imageId) throws Exception {
        Sentinel2TraceResponse.Trace trace = tracingClient.getProductCreateEventTrace(imageId)
                .orElse(null);
        if (trace == null) {
            return Sentinel2TraceVerificationResult.traceNotFound(imageId);
        }
        if (!SUPPORTED_HASH_ALGORITHM.equals(trace.hashAlgorithm())) {
            throw new RuntimeException("Unsupported hash algorithm: " + trace.hashAlgorithm());
        }
        if (!SUPPORTED_SIGNATURE_ALGORITHM.equals(trace.signature().algorithm())) {
            throw new RuntimeException("Unsupported signature algorithm: " + trace.signature().algorithm());
        }
        if (!verifySignature(trace.signature())) {
            return Sentinel2TraceVerificationResult.signatureError(trace, imageId);
        }
        return Sentinel2TraceVerificationResult.ok(trace, imageId);
    }

    /**
     * Verify a precomputed BLAKE3 hash against the signed Copernicus trace for {@code imageId}.
     * The {@code filename} is matched first against the trace's top-level product name
     * (whole-product check, e.g. {@code S2A_..._SAFE.zip}); if that doesn't match, it falls back
     * to a basename match against the per-file entries in the signed message.
     */
    public Sentinel2FileHashCheckResult verifyTraceWithFileHash(
            String imageId, String filename, String providedHashHex
    ) throws Exception {
        Sentinel2TraceResponse.Trace trace = tracingClient.getProductCreateEventTrace(imageId)
                .orElse(null);
        if (trace == null) {
            return Sentinel2FileHashCheckResult.traceNotFound(imageId, filename, providedHashHex);
        }
        if (!SUPPORTED_HASH_ALGORITHM.equals(trace.hashAlgorithm())) {
            throw new RuntimeException("Unsupported hash algorithm: " + trace.hashAlgorithm());
        }
        if (!SUPPORTED_SIGNATURE_ALGORITHM.equals(trace.signature().algorithm())) {
            throw new RuntimeException("Unsupported signature algorithm: " + trace.signature().algorithm());
        }
        if (!verifySignature(trace.signature())) {
            return Sentinel2FileHashCheckResult.signatureError(trace, imageId, filename, providedHashHex);
        }
        String expectedHash = lookupExpectedHash(trace, filename);
        if (expectedHash == null) {
            return Sentinel2FileHashCheckResult.fileNotInTrace(trace, imageId, filename, providedHashHex);
        }
        if (!expectedHash.equalsIgnoreCase(providedHashHex)) {
            return Sentinel2FileHashCheckResult.hashMismatch(trace, imageId, filename, providedHashHex, expectedHash);
        }
        return Sentinel2FileHashCheckResult.ok(trace, imageId, filename, providedHashHex, expectedHash);
    }

    private static String lookupExpectedHash(Sentinel2TraceResponse.Trace trace, String filename) {
        if (trace.matchesProductFile(filename) && trace.product().hash() != null) {
            return trace.product().hash();
        }
        return trace.getEntry(filename).map(Sentinel2TraceResponse.ProductEntry::hash).orElse(null);
    }

    /**
     * Verify many precomputed BLAKE3 file hashes against a single Copernicus trace. Fetches the
     * trace and verifies its signature once, then classifies each input as OK / HASH_MISMATCH /
     * FILE_NOT_IN_TRACE.
     */
    public Sentinel2DirectoryHashCheckResult verifyTraceWithFileHashes(
            String imageId, List<FileHashEntry> entries
    ) throws Exception {
        Sentinel2TraceResponse.Trace trace = tracingClient.getProductCreateEventTrace(imageId)
                .orElse(null);
        if (trace == null) {
            return Sentinel2DirectoryHashCheckResult.traceNotFound(imageId);
        }
        if (!SUPPORTED_HASH_ALGORITHM.equals(trace.hashAlgorithm())) {
            throw new RuntimeException("Unsupported hash algorithm: " + trace.hashAlgorithm());
        }
        if (!SUPPORTED_SIGNATURE_ALGORITHM.equals(trace.signature().algorithm())) {
            throw new RuntimeException("Unsupported signature algorithm: " + trace.signature().algorithm());
        }
        if (!verifySignature(trace.signature())) {
            return Sentinel2DirectoryHashCheckResult.signatureError(trace, imageId);
        }
        List<Sentinel2DirectoryHashCheckResult.FileResult> fileResults = new ArrayList<>();
        for (FileHashEntry entry : entries) {
            String expectedHash = lookupExpectedHash(trace, entry.filename());
            if (expectedHash == null) {
                fileResults.add(new Sentinel2DirectoryHashCheckResult.FileResult(
                    entry.filename(),
                    Sentinel2DirectoryHashCheckResult.FileStatus.FILE_NOT_IN_TRACE,
                    entry.hashHex(),
                    null));
            } else if (expectedHash.equalsIgnoreCase(entry.hashHex())) {
                fileResults.add(new Sentinel2DirectoryHashCheckResult.FileResult(
                    entry.filename(),
                    Sentinel2DirectoryHashCheckResult.FileStatus.OK,
                    entry.hashHex(),
                    expectedHash));
            } else {
                fileResults.add(new Sentinel2DirectoryHashCheckResult.FileResult(
                    entry.filename(),
                    Sentinel2DirectoryHashCheckResult.FileStatus.HASH_MISMATCH,
                    entry.hashHex(),
                    expectedHash));
            }
        }
        return Sentinel2DirectoryHashCheckResult.ok(trace, imageId, List.copyOf(fileResults));
    }

    public record FileHashEntry(String filename, String hashHex) {}

    static boolean verifySignature(Sentinel2TraceResponse.TracingSignature signature) throws Exception {
        X509Certificate cert = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(signature.certificateBytes()));
        // TODO - verify certificate using root certificate: https://ca.cloudferro.com/certs/ca.crt
        // TODO - check that signature certificate and root certificate have not been revoked
        //  using certificate revocation list: https://ca.cloudferro.com/certs/cdse-ca.crl

        byte[] msgBytes = signature.message().getBytes(StandardCharsets.UTF_8);

        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(cert.getPublicKey());
        verifier.update(msgBytes);
        return verifier.verify(signature.signatureBytes());
    }

    record VerificationContext(String imageId, Path file, Sentinel2TraceResponse.Trace trace) {
        Sentinel2VerificationResult verify() {
            if (!SUPPORTED_HASH_ALGORITHM.equals(trace.hashAlgorithm())) {
                throw new RuntimeException("Unsupported hash algorithm: " + trace.hashAlgorithm());
            }
            if (!SUPPORTED_SIGNATURE_ALGORITHM.equals(trace.signature().algorithm())) {
                throw new RuntimeException("Unsupported signature algorithm: " + trace.signature().algorithm());
            }
            try {
                if (!verifySignature(trace.signature())) {
                    return Sentinel2VerificationResult.signatureError(trace, imageId, file);
                }
                HashComparison hashComparison = verifyFileHash();
                if (!hashComparison.match()) {
                    return Sentinel2VerificationResult.hashMismatch(trace, imageId, file, hashComparison.actual());
                }
                return Sentinel2VerificationResult.ok(trace, imageId, file);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        HashComparison verifyFileHash() throws IOException {
            String fileName = file.getFileName().toString();
            Sentinel2TraceResponse.ProductEntry entry = trace.getEntry(fileName)
                    // TODO - consider separate Sentinel2VerificationResult.Status for this case
                    .orElseThrow(() -> new IllegalStateException("File entry not found in trace. fileName=" + fileName));
            byte[] expectedHash = entry.hashBytes();
            byte[] actualHash = computeBlake3(file);
            return new HashComparison(
                    Arrays.equals(expectedHash, actualHash),
                    entry.hashBytes(),
                    actualHash
            );
        }

        @SuppressWarnings("ArrayRecordComponent")
        record HashComparison(boolean match, byte[] expected, byte[] actual) {}

        byte[] computeBlake3(Path file) throws IOException {
            MessageDigest digest = new Blake3_256();
            byte[] buffer = new byte[8192];
            try (var in = Files.newInputStream(file)) {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return digest.digest();
        }
    }
}
