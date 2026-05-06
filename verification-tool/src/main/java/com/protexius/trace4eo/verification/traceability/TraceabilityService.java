package com.protexius.trace4eo.verification.traceability;

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
import java.util.Arrays;

public class TraceabilityService {

    private final TracingClient tracingClient;

    public TraceabilityService(TracingClient tracingClient) {
        this.tracingClient = tracingClient;
    }

    public VerificationResult verify(String imageId, Path filePath) throws Exception {
        if (!Files.isRegularFile(filePath)) {
            throw new RuntimeException("File not found: " + filePath);
        }

        TraceResponse.Trace trace = tracingClient.getProductCreateEventTrace(imageId)
                .orElse(null);
        if (trace == null) {
            return VerificationResult.traceNotFound(imageId, filePath);
        }
        return new VerificationContext(imageId, filePath, trace).verify();
    }

    record VerificationContext(String imageId, Path file, TraceResponse.Trace trace) {
        VerificationResult verify() {
            if (!"BLAKE3".equals(trace.hashAlgorithm())) {
                throw new RuntimeException("Unsupported hash algorithm: " + trace.hashAlgorithm());
            }
            if (!"RSA-SHA256".equals(trace.signature().algorithm())) {
                throw new RuntimeException("Unsupported signature algorithm: " + trace.signature().algorithm());
            }
            try {
                if (!verifySignature()) {
                    return VerificationResult.signatureError(trace, imageId, file);
                }
                HashComparison hashComparison = verifyFileHash();
                if (!hashComparison.match()) {
                    return VerificationResult.hashMismatch(trace, imageId, file, hashComparison.actual());
                }
                return VerificationResult.ok(trace, imageId, file);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        boolean verifySignature() throws Exception {
            TraceResponse.TracingSignature signature = trace.signature();
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

        HashComparison verifyFileHash() throws IOException {
            String fileName = file.getFileName().toString();
            TraceResponse.ProductEntry entry = trace.getEntry(fileName)
                    // TODO - consider separate VerificationResult.Status for this case
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
