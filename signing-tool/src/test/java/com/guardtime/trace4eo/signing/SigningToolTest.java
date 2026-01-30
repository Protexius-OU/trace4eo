package com.guardtime.trace4eo.signing;

import com.guardtime.trace4eo.provenance.HashAlgorithm;
import com.guardtime.trace4eo.provenance.ProvenanceJsonMapper;
import com.guardtime.trace4eo.provenance.ProvenanceSignature;
import com.guardtime.trace4eo.provenance.record.ProvenanceRecord;
import com.guardtime.trace4eo.provenance.signing.ProvenanceSigningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SigningToolTest {

    private static final Logger log = LoggerFactory.getLogger(SigningToolTest.class);

    private SigningTool signingTool;
    private ProvenanceSignature testSignature;
    private ProvenanceSigningService mockSigningService;
    private HttpClient mockHttpClient;
    @SuppressWarnings("unchecked")
    private HttpResponse<String> mockResponse = mock(HttpResponse.class);

    @BeforeEach
    void setUp() throws IOException {
        ProvenanceJsonMapper mapper = new ProvenanceJsonMapper();
        testSignature = mapper.readValue(
            Path.of("src/test/resources/signature.json").toFile(),
            ProvenanceSignature.class
        );

        AtomicInteger callCount = new AtomicInteger(0);
        mockSigningService = mock(ProvenanceSigningService.class);
        when(mockSigningService.sign(any(byte[].class), any(HashAlgorithm.class)))
            .thenAnswer(invocation -> new ProvenanceSignature(
                testSignature.bytes(),
                testSignature.signingTime().plusSeconds(callCount.getAndIncrement()),
                testSignature.hashAlgorithm()
            ));

        mockHttpClient = mock(HttpClient.class);

        signingTool = new SigningTool(mockSigningService, mockHttpClient);
    }

    @Test
    void sign() {
        String artifactPath = "src/test/resources/test.txt";
        ProvenanceSignature result = signingTool.sign(Path.of(artifactPath));
        assertNotNull(result);
        assertNotNull(result.bytes());
        assertNotNull(result.signingTime());
        assertNotNull(result.hashAlgorithm());
    }

    @Test
    void createProvenanceRecord() throws IOException {
        List<Path> files = List.of(Path.of("src/test/resources/test.txt"));
        ProvenanceRecord result = signingTool.createProvenanceRecord(files, "test", "test", List.of(), "SHA256");
        assertNotNull(result);
        assertNotNull(result.id());
        assertNotNull(result.filesInfo());
        assertNotNull(result.metadata());
        assertNotNull(result.manifest());
        log.info("Signed provenance record: {}", result);
    }

    @Test
    void batchSign_withFilesList(@TempDir Path tempDir) throws IOException {
        Path file1 = tempDir.resolve("file1.txt");
        Path file2 = tempDir.resolve("file2.txt");
        Files.writeString(file1, "content1");
        Files.writeString(file2, "content2");
        Path outputPath = tempDir.resolve("output.zip");

        BatchSigningResult result = signingTool.batchSign(
            List.of(file1, file2),
            null,
            "*",
            "test-type",
            "batch-2024",
            outputPath,
            "SHA256",
            null
        );

        assertEquals(2, result.totalFiles());
        assertEquals(2, result.successCount());
        assertEquals(0, result.failureCount());
        assertTrue(Files.exists(outputPath));
        assertTrue(Files.size(outputPath) > 0);
        log.info("Batch signing result: {}", result);
    }

    @Test
    void batchSign_withDirectory(@TempDir Path tempDir) throws IOException {
        Path inputDir = tempDir.resolve("input");
        Files.createDirectories(inputDir);
        Files.writeString(inputDir.resolve("img1.jpg"), "image1");
        Files.writeString(inputDir.resolve("img2.jpg"), "image2");
        Files.writeString(inputDir.resolve("other.txt"), "other");
        Path outputPath = tempDir.resolve("output.zip");

        BatchSigningResult result = signingTool.batchSign(
            null,
            inputDir,
            "*.jpg",
            "satellite-image",
            "acquisition-2024",
            outputPath,
            "SHA256",
            null
        );

        assertEquals(2, result.totalFiles());
        assertEquals(2, result.successCount());
        assertEquals(0, result.failureCount());
        assertTrue(Files.exists(outputPath));
    }

    @Test
    void batchSign_emptyFilesList_throws() {
        Path outputPath = Path.of("/tmp/output.zip");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.batchSign(List.of(), null, "*", "test", "test", outputPath, "SHA256", null)
        );

        assertTrue(exception.getMessage().contains("No files to sign"));
    }

    @Test
    void batchSign_tooManyFiles_throws(@TempDir Path tempDir) {
        List<Path> files = IntStream.range(0, 101)
            .mapToObj(i -> {
                Path file = tempDir.resolve("file" + i + ".txt");
                try {
                    Files.writeString(file, "content" + i);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return file;
            })
            .toList();
        Path outputPath = tempDir.resolve("output.zip");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.batchSign(files, null, "*", "test", "test", outputPath, "SHA256", null)
        );

        assertTrue(exception.getMessage().contains("Cannot sign more than 100 files"));
    }

    @Test
    void batchSign_partialFailure_continuesWithRemainingFiles(@TempDir Path tempDir) throws IOException {
        Path validFile = tempDir.resolve("valid.txt");
        Files.writeString(validFile, "valid content");
        Path nonExistentFile = tempDir.resolve("nonexistent.txt");
        Path outputPath = tempDir.resolve("output.zip");

        BatchSigningResult result = signingTool.batchSign(
            List.of(validFile, nonExistentFile),
            null,
            "*",
            "test",
            "test",
            outputPath,
            "SHA256",
            null
        );

        assertEquals(2, result.totalFiles());
        assertEquals(1, result.successCount());
        assertEquals(1, result.failureCount());
        assertTrue(result.hasFailures());
        assertTrue(Files.exists(outputPath));
    }

    @Test
    void batchSign_withRegisterUrl_registersRecords(@TempDir Path tempDir) throws IOException, InterruptedException {
        Path file1 = tempDir.resolve("file1.txt");
        Path file2 = tempDir.resolve("file2.txt");
        Files.writeString(file1, "content1");
        Files.writeString(file2, "content2");
        Path outputPath = tempDir.resolve("output.zip");

        when(mockResponse.statusCode()).thenReturn(200);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(mockResponse);

        BatchSigningResult result = signingTool.batchSign(
            List.of(file1, file2),
            null,
            "*",
            "test-type",
            "batch-2024",
            outputPath,
            "SHA256",
            "http://localhost:8080/api/records"
        );

        assertEquals(2, result.successCount());
        org.mockito.Mockito.verify(mockHttpClient, org.mockito.Mockito.times(2))
            .send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void batchSign_withRegisterUrl_handlesFailure(@TempDir Path tempDir) throws IOException, InterruptedException {
        Path file1 = tempDir.resolve("file1.txt");
        Files.writeString(file1, "content1");
        Path outputPath = tempDir.resolve("output.zip");

        when(mockResponse.statusCode()).thenReturn(500);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(mockResponse);

        BatchSigningResult result = signingTool.batchSign(
            List.of(file1),
            null,
            "*",
            "test-type",
            "batch-2024",
            outputPath,
            "SHA256",
            "http://localhost:8080/api/records"
        );

        assertEquals(1, result.successCount());
        org.mockito.Mockito.verify(mockHttpClient, org.mockito.Mockito.times(1))
            .send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void batchSign_withRegisterUrl_handlesException(@TempDir Path tempDir) throws IOException, InterruptedException {
        Path file1 = tempDir.resolve("file1.txt");
        Files.writeString(file1, "content1");
        Path outputPath = tempDir.resolve("output.zip");

        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenThrow(new IOException("Connection refused"));

        BatchSigningResult result = signingTool.batchSign(
            List.of(file1),
            null,
            "*",
            "test-type",
            "batch-2024",
            outputPath,
            "SHA256",
            "http://localhost:8080/api/records"
        );

        assertEquals(1, result.successCount());
        assertTrue(Files.exists(outputPath));
    }
}
