package com.guardtime.trace4eo.signing.commands;

import com.guardtime.trace4eo.provenance.ProvenanceJsonMapper;
import com.guardtime.trace4eo.provenance.ProvenanceSignature;
import com.guardtime.trace4eo.provenance.signing.ProvenanceSigningService;
import com.guardtime.trace4eo.signing.RecordSigningService;
import com.guardtime.trace4eo.signing.SigningInputValidator;
import com.guardtime.trace4eo.signing.registration.RecordRegistrationClient;
import dev.sigstore.KeylessSigner;
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        KeylessSigner mockSigner = mock(KeylessSigner.class);
        when(mockSigningService.buildTokenSigner(anyString())).thenReturn(mockSigner);
        when(mockSigningService.sign(any(byte[].class), any(KeylessSigner.class)))
            .thenAnswer(invocation -> new ProvenanceSignature(
                testSignature.bytes(),
                testSignature.signingTime().plusSeconds(callCount.getAndIncrement()),
                testSignature.hashAlgorithm()
            ));

        mockHttpClient = mock(HttpClient.class);
        RecordRegistrationClient registrationClient = new RecordRegistrationClient(mockHttpClient, provenanceJsonMapper);
        RecordSigningService recordSigningService = new RecordSigningService(mockSigningService, provenanceJsonMapper);
        SigningInputValidator validator = new SigningInputValidator();

        batchSigningTool = new BatchSigningTool(validator, recordSigningService, registrationClient, "test-token");
    }

    @Test
    void batchSign_withFilesList(@TempDir Path tempDir) throws IOException {
        Path file1 = tempDir.resolve("file1.txt");
        Path file2 = tempDir.resolve("file2.txt");
        Files.writeString(file1, "content1");
        Files.writeString(file2, "content2");
        Path outputDir = tempDir.resolve("out");

        List<UUID> result = batchSigningTool.batchSign(
            List.of(file1.toString(), file2.toString()),
            null,
            "*",
            "test-type",
            "batch-2024",
            outputDir,
            "SHA256",
            null,
            null, "trace4eo", false
        );

        assertEquals(2, result.size());
        Path expectedFile = outputDir.resolve("batch-2024.zip");
        assertTrue(Files.exists(expectedFile));
        assertTrue(Files.size(expectedFile) > 0);
    }

    @Test
    void batchSign_withDirectory(@TempDir Path tempDir) throws IOException {
        Path inputDir = tempDir.resolve("input");
        Files.createDirectories(inputDir);
        Files.writeString(inputDir.resolve("img1.jpg"), "image1");
        Files.writeString(inputDir.resolve("img2.jpg"), "image2");
        Files.writeString(inputDir.resolve("other.txt"), "other");
        Path outputDir = tempDir.resolve("out");

        List<UUID> result = batchSigningTool.batchSign(
            null,
            inputDir,
            "*.jpg",
            "satellite-image",
            "acquisition-2024",
            outputDir,
            "SHA256",
            null,
            null, "trace4eo", false
        );

        assertEquals(2, result.size());
        assertTrue(Files.exists(outputDir.resolve("acquisition-2024.zip")));
    }

    @Test
    void batchSign_withDefaultOutput(@TempDir Path tempDir) throws IOException {
        Path file1 = tempDir.resolve("file1.txt");
        Files.writeString(file1, "content1");

        // Change working directory effect: pass null for output, file gets created in CWD
        List<UUID> result = batchSigningTool.batchSign(
            List.of(file1.toString()),
            null,
            "*",
            "test-type",
            "my-data-id",
            null,
            "SHA256",
            null,
            null, "trace4eo", false
        );

        assertEquals(1, result.size());
        assertTrue(Files.exists(Path.of("my-data-id.zip")));
        // Clean up default output file
        Files.deleteIfExists(Path.of("my-data-id.zip"));
    }

    @Test
    void batchSign_withOutputDir_createsDirectoryIfMissing(@TempDir Path tempDir) throws IOException {
        Path file1 = tempDir.resolve("file1.txt");
        Files.writeString(file1, "content1");
        Path nestedDir = tempDir.resolve("nested/output");

        List<UUID> result = batchSigningTool.batchSign(
            List.of(file1.toString()),
            null,
            "*",
            "test-type",
            "batch-2024",
            nestedDir,
            "SHA256",
            null,
            null, "trace4eo", false
        );

        assertEquals(1, result.size());
        assertTrue(Files.isDirectory(nestedDir));
        assertTrue(Files.exists(nestedDir.resolve("batch-2024.zip")));
    }

    @Test
    void batchSign_emptyFilesList_throws() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            batchSigningTool.batchSign(
                List.of(), null, "*", "test", "test", null, "SHA256", null,
                null, "trace4eo", false
            )
        );

        assertTrue(exception.getMessage().contains("--files or --directory"));
    }

    @Test
    void batchSign_nonExistentFile_throwsBeforeSigning() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            batchSigningTool.batchSign(
                List.of("nonexistent.txt"),
                null,
                "*",
                "test",
                "test",
                null,
                "SHA256",
                null,
                null, "trace4eo", false
            )
        );
        assertTrue(exception.getMessage().contains("File does not exist"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void batchSign_withRegisterUrl_registersRecords(@TempDir Path tempDir) throws IOException, InterruptedException {
        Path file1 = tempDir.resolve("file1.txt");
        Path file2 = tempDir.resolve("file2.txt");
        Files.writeString(file1, "content1");
        Files.writeString(file2, "content2");

        HttpResponse<String> tokenResponse = mock(HttpResponse.class);
        when(tokenResponse.statusCode()).thenReturn(200);
        when(tokenResponse.body()).thenReturn("{\"access_token\":\"exchanged-token\"}");

        when(mockResponse.statusCode()).thenReturn(200);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(tokenResponse)
            .thenReturn(mockResponse)
            .thenReturn(mockResponse);

        List<UUID> result = batchSigningTool.batchSign(
            List.of(file1.toString(), file2.toString()),
            null,
            "*",
            "test-type",
            "batch-2024",
            tempDir,
            "SHA256",
            "http://localhost:8080/api/records",
            "http://localhost:8180", "trace4eo", false
        );

        assertEquals(2, result.size());
        // Token exchange + 2 record registrations = 3 HTTP calls
        verify(mockHttpClient, times(3))
            .send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    void batchSign_withRegisterUrl_handlesFailure(@TempDir Path tempDir) throws IOException, InterruptedException {
        Path file1 = tempDir.resolve("file1.txt");
        Files.writeString(file1, "content1");

        HttpResponse<String> tokenResponse = mock(HttpResponse.class);
        when(tokenResponse.statusCode()).thenReturn(200);
        when(tokenResponse.body()).thenReturn("{\"access_token\":\"exchanged-token\"}");

        when(mockResponse.statusCode()).thenReturn(500);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(tokenResponse)
            .thenReturn(mockResponse);

        List<UUID> result = batchSigningTool.batchSign(
            List.of(file1.toString()),
            null,
            "*",
            "test-type",
            "batch-2024",
            tempDir,
            "SHA256",
            "http://localhost:8080/api/records",
            "http://localhost:8180", "trace4eo", false
        );

        assertEquals(1, result.size());
        // Token exchange + 1 registration attempt = 2 HTTP calls
        verify(mockHttpClient, times(2))
            .send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    void batchSign_withRegisterUrl_handlesException(@TempDir Path tempDir) throws IOException, InterruptedException {
        Path file1 = tempDir.resolve("file1.txt");
        Files.writeString(file1, "content1");

        HttpResponse<String> tokenResponse = mock(HttpResponse.class);
        when(tokenResponse.statusCode()).thenReturn(200);
        when(tokenResponse.body()).thenReturn("{\"access_token\":\"exchanged-token\"}");

        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(tokenResponse)
            .thenThrow(new IOException("Connection refused"));

        List<UUID> result = batchSigningTool.batchSign(
            List.of(file1.toString()),
            null,
            "*",
            "test-type",
            "batch-2024",
            tempDir,
            "SHA256",
            "http://localhost:8080/api/records",
            "http://localhost:8180", "trace4eo", false
        );

        assertEquals(1, result.size());
        assertTrue(Files.exists(tempDir.resolve("batch-2024.zip")));
    }

    @Test
    void batchSign_registerUrlWithoutKeycloakUrl_throws(@TempDir Path tempDir) throws IOException {
        Path file1 = tempDir.resolve("file1.txt");
        Files.writeString(file1, "content1");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            batchSigningTool.batchSign(
                List.of(file1.toString()), null, "*", "test", "test",
                null, "SHA256",
                "http://localhost:8080/api/records", null, "trace4eo", false
            )
        );
        assertTrue(exception.getMessage().contains("--keycloak-url"));
    }

    @Test
    void batchSign_blankProvenanceRecordType_throws() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            batchSigningTool.batchSign(
                List.of("file.txt"), null, "*", " ", "test",
                null, "SHA256", null, null, "trace4eo", false
            )
        );
        assertTrue(exception.getMessage().contains("--provenance-record-type"));
    }

    @Test
    void batchSign_nullProvenanceRecordType_throws() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            batchSigningTool.batchSign(
                List.of("file.txt"), null, "*", null, "test",
                null, "SHA256", null, null, "trace4eo", false
            )
        );
        assertTrue(exception.getMessage().contains("--provenance-record-type"));
    }

    @Test
    void batchSign_blankDataId_throws() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            batchSigningTool.batchSign(
                List.of("file.txt"), null, "*", "test", " ",
                null, "SHA256", null, null, "trace4eo", false
            )
        );
        assertTrue(exception.getMessage().contains("--data-id"));
    }

    @Test
    void batchSign_nullDataId_throws() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            batchSigningTool.batchSign(
                List.of("file.txt"), null, "*", "test", null,
                null, "SHA256", null, null, "trace4eo", false
            )
        );
        assertTrue(exception.getMessage().contains("--data-id"));
    }

    @Test
    void batchSign_nonExistentDirectory_throws() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            batchSigningTool.batchSign(
                null, Path.of("/nonexistent/path"), "*", "test", "test",
                null, "SHA256", null, null, "trace4eo", false
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
                null, "SHA256", null, null, "trace4eo", false
            )
        );
        assertTrue(exception.getMessage().contains("--directory"));
    }

    @Test
    void batchSign_invalidHashAlgorithm_throws(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("file.txt");
        Files.writeString(file, "content");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            batchSigningTool.batchSign(
                List.of(file.toString()), null, "*", "test", "test",
                null, "INVALID", null, null, "trace4eo", false
            )
        );
        assertTrue(exception.getMessage().contains("--hash-algorithm"));
    }

    @Test
    void batchSign_directoryAsFile_throws(@TempDir Path tempDir) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            batchSigningTool.batchSign(
                List.of(tempDir.toString()), null, "*", "test", "test",
                null, "SHA256", null, null, "trace4eo", false
            )
        );
        assertTrue(exception.getMessage().contains("not a regular file"));
    }

    @Test
    void batchSign_outputDirIsFile_throws(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("file.txt");
        Files.writeString(file, "content");
        Path notADir = tempDir.resolve("not-a-dir");
        Files.writeString(notADir, "content");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            batchSigningTool.batchSign(
                List.of(file.toString()), null, "*", "test", "test",
                notADir, "SHA256", null, null, "trace4eo", false
            )
        );
        assertTrue(exception.getMessage().contains("--output is not a directory"));
    }

    @Test
    void batchSign_invalidGlobPattern_throws(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("file.txt");
        Files.writeString(file, "content");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            batchSigningTool.batchSign(
                null, tempDir, "[invalid", "test", "test",
                null, "SHA256", null, null, "trace4eo", false
            )
        );
        assertTrue(exception.getMessage().contains("--pattern"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void batchSign_withKeycloakUrl_exchangesTokenBeforeRegistration(@TempDir Path tempDir)
        throws IOException, InterruptedException {
        Path file1 = tempDir.resolve("file1.txt");
        Files.writeString(file1, "content1");

        HttpResponse<String> tokenResponse = mock(HttpResponse.class);
        when(tokenResponse.statusCode()).thenReturn(200);
        when(tokenResponse.body()).thenReturn("{\"access_token\":\"exchanged-token\"}");

        HttpResponse<String> registerResponse = mock(HttpResponse.class);
        when(registerResponse.statusCode()).thenReturn(200);

        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(tokenResponse)
            .thenReturn(registerResponse);

        List<UUID> result = batchSigningTool.batchSign(
            List.of(file1.toString()),
            null,
            "*",
            "test-type",
            "batch-2024",
            tempDir,
            "SHA256",
            "http://localhost:8080/api/records",
            "http://localhost:8180", "trace4eo", false
        );

        assertEquals(1, result.size());
        // Token exchange + record registration = 2 HTTP calls
        verify(mockHttpClient, times(2))
            .send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void batchSign_withCreateRecordIdsFile_writesTextFile(@TempDir Path tempDir) throws IOException {
        Path file1 = tempDir.resolve("file1.txt");
        Path file2 = tempDir.resolve("file2.txt");
        Files.writeString(file1, "content1");
        Files.writeString(file2, "content2");
        Path outputDir = tempDir.resolve("out");

        List<UUID> result = batchSigningTool.batchSign(
            List.of(file1.toString(), file2.toString()),
            null,
            "*",
            "test-type",
            "batch-2024",
            outputDir,
            "SHA256",
            null,
            null, "trace4eo", true
        );

        assertEquals(2, result.size());
        List<Path> textFiles;
        try (var stream = Files.list(outputDir)) {
            textFiles = stream.filter(p -> p.getFileName().toString().startsWith("record-ids-")
                && p.getFileName().toString().endsWith(".txt")).toList();
        }
        assertEquals(1, textFiles.size());
        List<String> ids = Files.readAllLines(textFiles.get(0)).stream()
            .filter(l -> !l.isBlank()).toList();
        assertEquals(2, ids.size());
    }

    @Test
    void batchSign_withCreateRecordIdsFile_noOutputDir_writesToCwd(@TempDir Path tempDir) throws IOException {
        Path file1 = tempDir.resolve("file1.txt");
        Files.writeString(file1, "content1");

        List<UUID> result = batchSigningTool.batchSign(
            List.of(file1.toString()),
            null, "*", "test-type", "cwd-record-ids-test",
            null, "SHA256", null, null, "trace4eo", true
        );

        assertEquals(1, result.size());
        List<Path> recordIdsFiles;
        try (var stream = Files.list(Path.of("."))) {
            recordIdsFiles = stream.filter(p -> p.getFileName().toString().startsWith("record-ids-")
                && p.getFileName().toString().endsWith(".txt")).toList();
        }
        assertFalse(recordIdsFiles.isEmpty());
        List<String> ids = Files.readAllLines(recordIdsFiles.get(0)).stream().filter(l -> !l.isBlank()).toList();
        assertEquals(1, ids.size());
        for (Path f : recordIdsFiles) Files.deleteIfExists(f);
        Files.deleteIfExists(Path.of("cwd-record-ids-test.zip"));
    }

    @Test
    void batchSign_withCreateRecordIdsFile_false_noExtraFile(@TempDir Path tempDir) throws IOException {
        Path file1 = tempDir.resolve("file1.txt");
        Files.writeString(file1, "content1");

        List<UUID> result = batchSigningTool.batchSign(
            List.of(file1.toString()),
            null,
            "*",
            "test-type",
            "batch-2024",
            tempDir,
            "SHA256",
            null,
            null, "trace4eo", false
        );

        assertEquals(1, result.size());
        long fileCount;
        try (var stream = Files.list(tempDir)) {
            fileCount = stream.filter(p -> p.getFileName().toString().startsWith("record-ids-")).count();
        }
        assertEquals(0, fileCount);
    }
}
