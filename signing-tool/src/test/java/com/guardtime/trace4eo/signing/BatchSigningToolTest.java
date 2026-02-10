package com.guardtime.trace4eo.signing;

import com.guardtime.trace4eo.provenance.HashAlgorithm;
import com.guardtime.trace4eo.provenance.ProvenanceJsonMapper;
import com.guardtime.trace4eo.provenance.ProvenanceSignature;
import com.guardtime.trace4eo.provenance.signing.ProvenanceSigningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BatchSigningToolTest {

    private BatchSigningTool batchSigningTool;
    private HttpClient mockHttpClient;
    @SuppressWarnings("unchecked")
    private final HttpResponse<String> mockResponse = mock(HttpResponse.class);

    @BeforeEach
    void setUp() {
        ProvenanceJsonMapper provenanceJsonMapper = new ProvenanceJsonMapper();
        ProvenanceSignature testSignature = provenanceJsonMapper.readValue(
            Path.of("src/test/resources/signature.json").toFile(),
            ProvenanceSignature.class
        );

        AtomicInteger callCount = new AtomicInteger(0);
        ProvenanceSigningService mockSigningService = mock(ProvenanceSigningService.class);
        when(mockSigningService.sign(any(byte[].class), any(HashAlgorithm.class), anyString()))
            .thenAnswer(invocation -> new ProvenanceSignature(
                testSignature.bytes(),
                testSignature.signingTime().plusSeconds(callCount.getAndIncrement()),
                testSignature.hashAlgorithm()
            ));

        mockHttpClient = mock(HttpClient.class);
        RecordRegistrationClient registrationClient = new RecordRegistrationClient(mockHttpClient, provenanceJsonMapper);

        batchSigningTool = new BatchSigningTool(mockSigningService, provenanceJsonMapper, registrationClient, "test-token");
    }

    @Test
    void batchSign_withFilesList(@TempDir Path tempDir) throws IOException {
        Path file1 = tempDir.resolve("file1.txt");
        Path file2 = tempDir.resolve("file2.txt");
        Files.writeString(file1, "content1");
        Files.writeString(file2, "content2");
        Path outputPath = tempDir.resolve("output.zip");

        BatchSigningResult result = batchSigningTool.batchSign(
            List.of(file1.toString(), file2.toString()),
            null,
            "*",
            "test-type",
            "batch-2024",
            outputPath,
            "SHA256",
            null,
            null, "trace4eo"
        );

        assertEquals(2, result.totalFiles());
        assertEquals(2, result.successCount());
        assertEquals(0, result.failureCount());
        assertTrue(Files.exists(outputPath));
        assertTrue(Files.size(outputPath) > 0);
    }

    @Test
    void batchSign_withDirectory(@TempDir Path tempDir) throws IOException {
        Path inputDir = tempDir.resolve("input");
        Files.createDirectories(inputDir);
        Files.writeString(inputDir.resolve("img1.jpg"), "image1");
        Files.writeString(inputDir.resolve("img2.jpg"), "image2");
        Files.writeString(inputDir.resolve("other.txt"), "other");
        Path outputPath = tempDir.resolve("output.zip");

        BatchSigningResult result = batchSigningTool.batchSign(
            null,
            inputDir,
            "*.jpg",
            "satellite-image",
            "acquisition-2024",
            outputPath,
            "SHA256",
            null,
            null, "trace4eo"
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
            batchSigningTool.batchSign(
                List.of(), null, "*", "test", "test", outputPath, "SHA256", null,
                null, "trace4eo"
            )
        );

        assertTrue(exception.getMessage().contains("No files to sign"));
    }

    @Test
    void batchSign_partialFailure_continuesWithRemainingFiles(@TempDir Path tempDir) throws IOException {
        Path validFile = tempDir.resolve("valid.txt");
        Files.writeString(validFile, "valid content");
        Path nonExistentFile = tempDir.resolve("nonexistent.txt");
        Path outputPath = tempDir.resolve("output.zip");

        BatchSigningResult result = batchSigningTool.batchSign(
            List.of(validFile.toString(), nonExistentFile.toString()),
            null,
            "*",
            "test",
            "test",
            outputPath,
            "SHA256",
            null,
            null, "trace4eo"
        );

        assertEquals(2, result.totalFiles());
        assertEquals(1, result.successCount());
        assertEquals(1, result.failureCount());
        assertTrue(result.hasFailures());
        assertTrue(Files.exists(outputPath));
    }

    @SuppressWarnings("unchecked")
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

        BatchSigningResult result = batchSigningTool.batchSign(
            List.of(file1.toString(), file2.toString()),
            null,
            "*",
            "test-type",
            "batch-2024",
            outputPath,
            "SHA256",
            "http://localhost:8080/api/records",
            null, "trace4eo"
        );

        assertEquals(2, result.successCount());
        verify(mockHttpClient, times(2))
            .send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    void batchSign_withRegisterUrl_handlesFailure(@TempDir Path tempDir) throws IOException, InterruptedException {
        Path file1 = tempDir.resolve("file1.txt");
        Files.writeString(file1, "content1");
        Path outputPath = tempDir.resolve("output.zip");

        when(mockResponse.statusCode()).thenReturn(500);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(mockResponse);

        BatchSigningResult result = batchSigningTool.batchSign(
            List.of(file1.toString()),
            null,
            "*",
            "test-type",
            "batch-2024",
            outputPath,
            "SHA256",
            "http://localhost:8080/api/records",
            null, "trace4eo"
        );

        assertEquals(1, result.successCount());
        verify(mockHttpClient, times(1))
            .send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    void batchSign_withRegisterUrl_handlesException(@TempDir Path tempDir) throws IOException, InterruptedException {
        Path file1 = tempDir.resolve("file1.txt");
        Files.writeString(file1, "content1");
        Path outputPath = tempDir.resolve("output.zip");

        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenThrow(new IOException("Connection refused"));

        BatchSigningResult result = batchSigningTool.batchSign(
            List.of(file1.toString()),
            null,
            "*",
            "test-type",
            "batch-2024",
            outputPath,
            "SHA256",
            "http://localhost:8080/api/records",
            null, "trace4eo"
        );

        assertEquals(1, result.successCount());
        assertTrue(Files.exists(outputPath));
    }

    @Test
    void batchSign_blankProvenanceRecordType_throws() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            batchSigningTool.batchSign(
                List.of("file.txt"), null, "*", " ", "test",
                Path.of("/tmp/out.zip"), "SHA256", null, null, "trace4eo"
            )
        );
        assertTrue(exception.getMessage().contains("--provenance-record-type"));
    }

    @Test
    void batchSign_nullProvenanceRecordType_throws() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            batchSigningTool.batchSign(
                List.of("file.txt"), null, "*", null, "test",
                Path.of("/tmp/out.zip"), "SHA256", null, null, "trace4eo"
            )
        );
        assertTrue(exception.getMessage().contains("--provenance-record-type"));
    }

    @Test
    void batchSign_blankDataId_throws() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            batchSigningTool.batchSign(
                List.of("file.txt"), null, "*", "test", " ",
                Path.of("/tmp/out.zip"), "SHA256", null, null, "trace4eo"
            )
        );
        assertTrue(exception.getMessage().contains("--data-id"));
    }

    @Test
    void batchSign_nullDataId_throws() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            batchSigningTool.batchSign(
                List.of("file.txt"), null, "*", "test", null,
                Path.of("/tmp/out.zip"), "SHA256", null, null, "trace4eo"
            )
        );
        assertTrue(exception.getMessage().contains("--data-id"));
    }

    @Test
    void batchSign_nullOutput_throws() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            batchSigningTool.batchSign(
                List.of("file.txt"), null, "*", "test", "test",
                null, "SHA256", null, null, "trace4eo"
            )
        );
        assertTrue(exception.getMessage().contains("--output"));
    }

    @Test
    void batchSign_nonExistentDirectory_throws() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            batchSigningTool.batchSign(
                null, Path.of("/nonexistent/path"), "*", "test", "test",
                Path.of("/tmp/out.zip"), "SHA256", null, null, "trace4eo"
            )
        );
        assertTrue(exception.getMessage().contains("--directory"));
    }

    @Test
    void batchSign_pathIsNotDirectory_throws(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("not-a-dir.txt");
        Files.writeString(file, "content");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            batchSigningTool.batchSign(
                null, file, "*", "test", "test",
                Path.of("/tmp/out.zip"), "SHA256", null, null, "trace4eo"
            )
        );
        assertTrue(exception.getMessage().contains("--directory"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void batchSign_withKeycloakUrl_exchangesTokenBeforeRegistration(@TempDir Path tempDir)
        throws IOException, InterruptedException {
        Path file1 = tempDir.resolve("file1.txt");
        Files.writeString(file1, "content1");
        Path outputPath = tempDir.resolve("output.zip");

        HttpResponse<String> tokenResponse = mock(HttpResponse.class);
        when(tokenResponse.statusCode()).thenReturn(200);
        when(tokenResponse.body()).thenReturn("{\"access_token\":\"exchanged-token\"}");

        HttpResponse<String> registerResponse = mock(HttpResponse.class);
        when(registerResponse.statusCode()).thenReturn(200);

        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(tokenResponse)
            .thenReturn(registerResponse);

        BatchSigningResult result = batchSigningTool.batchSign(
            List.of(file1.toString()),
            null,
            "*",
            "test-type",
            "batch-2024",
            outputPath,
            "SHA256",
            "http://localhost:8080/api/records",
            "http://localhost:8180", "trace4eo"
        );

        assertEquals(1, result.successCount());
        // Token exchange + record registration = 2 HTTP calls
        verify(mockHttpClient, times(2))
            .send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }
}
