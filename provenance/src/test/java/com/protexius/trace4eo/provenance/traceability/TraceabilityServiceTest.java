package com.protexius.trace4eo.provenance.traceability;

import com.protexius.trace4eo.provenance.ProvenanceJsonMapper;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jcajce.provider.digest.Blake3.Blake3_256;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.math.BigInteger;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.security.auth.x500.X500Principal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TraceabilityServiceTest {

    private static final String IMAGE_ID = "S2A_MSIL1C_20250101T000000_TEST";
    private static final String PRODUCT_NAME = IMAGE_ID + ".SAFE.zip";

    private final JsonMapper jsonMapper = new ProvenanceJsonMapper();
    private TracingClient tracingClient;
    private TraceabilityService service;

    @TempDir
    private Path tempDir;
    private Path productFile;
    private byte[] productBlake3;
    private TestSigningIdentity identity;

    @BeforeEach
    void setUp() throws Exception {
        tracingClient = mock(TracingClient.class);
        service = new TraceabilityService(tracingClient);

        productFile = tempDir.resolve(PRODUCT_NAME);
        Files.writeString(productFile, "synthetic sentinel-2 product payload");
        productBlake3 = blake3(Files.readAllBytes(productFile));
        identity = TestSigningIdentity.generate();
    }

    @Test
    void verify_validTrace_returnsOk() throws Exception {
        TraceResponse.Trace trace = newTrace(productBlake3, "BLAKE3", "RSA-SHA256", false);
        when(tracingClient.getProductCreateEventTrace(eq(IMAGE_ID)))
            .thenReturn(Optional.of(trace));

        VerificationResult result = service.verify(IMAGE_ID, productFile);

        assertEquals(VerificationResult.Status.OK, result.status());
        assertTrue(result.trace().isPresent());
        assertEquals(IMAGE_ID, result.localFile().imageId());
        assertEquals(productFile, result.localFile().path());
    }

    @Test
    void verify_fileContentTampered_returnsHashMismatch() throws Exception {
        TraceResponse.Trace trace = newTrace(productBlake3, "BLAKE3", "RSA-SHA256", false);
        when(tracingClient.getProductCreateEventTrace(eq(IMAGE_ID)))
            .thenReturn(Optional.of(trace));
        // Mutate file contents *after* the trace was signed.
        Files.writeString(productFile, "tampered payload");

        VerificationResult result = service.verify(IMAGE_ID, productFile);

        assertEquals(VerificationResult.Status.HASH_MISMATCH, result.status());
        assertTrue(result.localFile().hashHex() != null && !result.localFile().hashHex().isEmpty());
    }

    @Test
    void verify_corruptedSignature_returnsSignatureError() throws Exception {
        TraceResponse.Trace trace = newTrace(productBlake3, "BLAKE3", "RSA-SHA256", true);
        when(tracingClient.getProductCreateEventTrace(eq(IMAGE_ID)))
            .thenReturn(Optional.of(trace));

        VerificationResult result = service.verify(IMAGE_ID, productFile);

        assertEquals(VerificationResult.Status.SIGNATURE_ERROR, result.status());
    }

    @Test
    void verify_unsupportedHashAlgorithm_throws() throws Exception {
        TraceResponse.Trace trace = newTrace(productBlake3, "SHA256", "RSA-SHA256", false);
        when(tracingClient.getProductCreateEventTrace(eq(IMAGE_ID)))
            .thenReturn(Optional.of(trace));

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> service.verify(IMAGE_ID, productFile));
        assertTrue(ex.getMessage().contains("Unsupported hash algorithm"));
    }

    @Test
    void verify_unsupportedSignatureAlgorithm_throws() throws Exception {
        TraceResponse.Trace trace = newTrace(productBlake3, "BLAKE3", "ECDSA-SHA256", false);
        when(tracingClient.getProductCreateEventTrace(eq(IMAGE_ID)))
            .thenReturn(Optional.of(trace));

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> service.verify(IMAGE_ID, productFile));
        assertTrue(ex.getMessage().contains("Unsupported signature algorithm"));
    }

    @Test
    void verifyTrace_validSignature_returnsOk() throws Exception {
        TraceResponse.Trace trace = newTrace(productBlake3, "BLAKE3", "RSA-SHA256", false);
        when(tracingClient.getProductCreateEventTrace(eq(IMAGE_ID)))
            .thenReturn(Optional.of(trace));

        TraceVerificationResult result = service.verifyTrace(IMAGE_ID);

        assertEquals(TraceVerificationResult.Status.OK, result.status());
        assertTrue(result.trace().isPresent());
        assertEquals(IMAGE_ID, result.imageId());
    }

    @Test
    void verifyTrace_traceNotFound_returnsTraceNotFound() throws Exception {
        when(tracingClient.getProductCreateEventTrace(eq(IMAGE_ID)))
            .thenReturn(Optional.empty());

        TraceVerificationResult result = service.verifyTrace(IMAGE_ID);

        assertEquals(TraceVerificationResult.Status.TRACE_NOT_FOUND, result.status());
        assertTrue(result.trace().isEmpty());
    }

    @Test
    void verifyTraceWithFileHash_matchesEntry_returnsOk() throws Exception {
        TraceResponse.Trace trace = newTrace(productBlake3, "BLAKE3", "RSA-SHA256", false);
        when(tracingClient.getProductCreateEventTrace(eq(IMAGE_ID)))
            .thenReturn(Optional.of(trace));

        Sentinel2FileHashCheckResult result = service.verifyTraceWithFileHash(
            IMAGE_ID, PRODUCT_NAME, HexFormat.of().formatHex(productBlake3));

        assertEquals(Sentinel2FileHashCheckResult.Status.OK, result.status());
        assertEquals(HexFormat.of().formatHex(productBlake3), result.expectedHash());
    }

    @Test
    void verifyTraceWithFileHash_wrongHash_returnsHashMismatch() throws Exception {
        TraceResponse.Trace trace = newTrace(productBlake3, "BLAKE3", "RSA-SHA256", false);
        when(tracingClient.getProductCreateEventTrace(eq(IMAGE_ID)))
            .thenReturn(Optional.of(trace));

        Sentinel2FileHashCheckResult result = service.verifyTraceWithFileHash(
            IMAGE_ID, PRODUCT_NAME, "00".repeat(32));

        assertEquals(Sentinel2FileHashCheckResult.Status.HASH_MISMATCH, result.status());
        assertEquals("00".repeat(32), result.providedHash());
        assertEquals(HexFormat.of().formatHex(productBlake3), result.expectedHash());
    }

    @Test
    void verifyTraceWithFileHash_unknownFilename_returnsFileNotInTrace() throws Exception {
        TraceResponse.Trace trace = newTrace(productBlake3, "BLAKE3", "RSA-SHA256", false);
        when(tracingClient.getProductCreateEventTrace(eq(IMAGE_ID)))
            .thenReturn(Optional.of(trace));

        Sentinel2FileHashCheckResult result = service.verifyTraceWithFileHash(
            IMAGE_ID, "not-in-product.txt", HexFormat.of().formatHex(productBlake3));

        assertEquals(Sentinel2FileHashCheckResult.Status.FILE_NOT_IN_TRACE, result.status());
    }

    @Test
    void verifyTrace_corruptedSignature_returnsSignatureError() throws Exception {
        TraceResponse.Trace trace = newTrace(productBlake3, "BLAKE3", "RSA-SHA256", true);
        when(tracingClient.getProductCreateEventTrace(eq(IMAGE_ID)))
            .thenReturn(Optional.of(trace));

        TraceVerificationResult result = service.verifyTrace(IMAGE_ID);

        assertEquals(TraceVerificationResult.Status.SIGNATURE_ERROR, result.status());
        assertTrue(result.trace().isPresent());
    }

    /**
     * Real-world end-to-end check: parses an actual response from the Copernicus Traceability API
     * (saved as a fixture) and runs RSA-SHA256 verification against the real CDSE certificate.
     * The on-disk file we present is synthetic, so the BLAKE3 hash check is expected to fail —
     * the value of this test is that signature verification reaches that step at all, proving the
     * signature path works against unmodified, real CDSE bytes.
     */
    @Test
    void verify_realCopernicusTrace_signatureVerifiesAndHashMismatches() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(Files.newInputStream(
            Path.of("src/test/resources/sentinel2-trace-create-event.json")));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(response);

        TraceabilityService realService = new TraceabilityService(new TracingClient(jsonMapper, httpClient));

        // manifest.safe matches an entry in the real signed message, so the hash-comparison path
        // runs (rather than throwing "File entry not found"); the synthetic content guarantees a
        // hash mismatch.
        Path manifestSafe = tempDir.resolve("manifest.safe");
        Files.writeString(manifestSafe, "synthetic content (not the real Sentinel-2 product)");

        String imageId = "S2A_MSIL1C_20230420T100021_N0509_R122_T33UVP_20230420T120027";
        VerificationResult result = realService.verify(imageId, manifestSafe);

        assertEquals(VerificationResult.Status.HASH_MISMATCH, result.status());
        assertTrue(result.trace().isPresent());
        assertEquals("BLAKE3", result.trace().get().hashAlgorithm());
        assertEquals("RSA-SHA256", result.trace().get().signature().algorithm());
    }

    /**
     * Build a trace whose signed message claims the given hash for the product file. When
     * {@code corruptSignature} is true the signature bytes are flipped so verification fails while
     * the message and certificate stay otherwise valid.
     */
    private TraceResponse.Trace newTrace(
        byte[] hashBytes, String hashAlgorithm, String signatureAlgorithm, boolean corruptSignature
    ) throws Exception {
        String message = jsonMapper.writeValueAsString(Map.of(
            "name", PRODUCT_NAME,
            "event", "CREATE",
            "contents", List.of(Map.of(
                "path", PRODUCT_NAME,
                "hash", HexFormat.of().formatHex(hashBytes)
            ))
        ));
        byte[] signatureBytes = identity.sign(message.getBytes(StandardCharsets.UTF_8));
        if (corruptSignature) {
            signatureBytes[0] ^= (byte) 0xFF;
        }
        TraceResponse.TracingSignature signature = new TraceResponse.TracingSignature(
            Base64.getEncoder().encodeToString(signatureBytes),
            signatureAlgorithm,
            Base64.getEncoder().encodeToString(identity.certificateBytes()),
            message
        );
        return new TraceResponse.Trace(
            "trace-id",
            "CREATE",
            hashAlgorithm,
            new TraceResponse.Product(PRODUCT_NAME, HexFormat.of().formatHex(hashBytes), List.of(
                new TraceResponse.ProductEntry(Path.of(PRODUCT_NAME), HexFormat.of().formatHex(hashBytes))
            )),
            signature
        );
    }

    private static byte[] blake3(byte[] data) {
        MessageDigest digest = new Blake3_256();
        digest.update(data);
        return digest.digest();
    }

    /** Holds a self-signed RSA certificate + private key for synthesising trace signatures. */
    private record TestSigningIdentity(KeyPair keyPair, X509Certificate certificate) {

        static TestSigningIdentity generate() throws Exception {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            X500Principal subject = new X500Principal("CN=trace4eo-test");
            Instant now = Instant.now();
            Date notBefore = Date.from(now);
            Date notAfter = Date.from(now.plus(Duration.ofDays(1)));
            JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                subject, BigInteger.ONE, notBefore, notAfter, subject, keyPair.getPublic());
            ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
            X509Certificate certificate = new JcaX509CertificateConverter()
                .getCertificate(builder.build(signer));
            return new TestSigningIdentity(keyPair, certificate);
        }

        byte[] sign(byte[] message) throws Exception {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(keyPair.getPrivate());
            signature.update(message);
            return signature.sign();
        }

        byte[] certificateBytes() throws Exception {
            return certificate.getEncoded();
        }
    }
}
