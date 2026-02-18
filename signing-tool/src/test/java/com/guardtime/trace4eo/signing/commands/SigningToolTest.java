package com.guardtime.trace4eo.signing.commands;

import com.guardtime.trace4eo.provenance.HashAlgorithm;
import com.guardtime.trace4eo.provenance.ProvenanceJsonMapper;
import com.guardtime.trace4eo.provenance.ProvenanceSignature;
import com.guardtime.trace4eo.provenance.record.Predecessor;
import com.guardtime.trace4eo.provenance.signing.ProvenanceSigningService;
import com.guardtime.trace4eo.signing.RecordSigningService;
import com.guardtime.trace4eo.signing.registration.RecordRegistrationClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SigningToolTest {

    private SigningTool signingTool;
    private ProvenanceJsonMapper provenanceJsonMapper;
    private RecordRegistrationClient mockRegistrationClient;

    @BeforeEach
    void setUp() {
        provenanceJsonMapper = new ProvenanceJsonMapper();
        ProvenanceSignature testSignature = provenanceJsonMapper.readValue(
            Path.of("src/test/resources/signature.json").toFile(),
            ProvenanceSignature.class
        );

        ProvenanceSigningService mockSigningService = mock(ProvenanceSigningService.class);
        when(mockSigningService.sign(any(byte[].class), anyString()))
            .thenReturn(testSignature);

        mockRegistrationClient = mock(RecordRegistrationClient.class);

        RecordSigningService recordSigningService = new RecordSigningService(mockSigningService, provenanceJsonMapper);
        SigningInputValidator validator = new SigningInputValidator();
        signingTool = new SigningTool(validator, recordSigningService, mockRegistrationClient, "test-token");
    }

    @Test
    void createProvenanceRecord_writesZipToDefaultPath() throws IOException {
        List<String> files = List.of("src/test/resources/test.txt");
        UUID result = signingTool.createProvenanceRecord(
            files, "test", "test", List.of(), "SHA256", null, null, null, "trace4eo", null
        );
        assertNotNull(result);
        // Clean up the default output file (written to CWD as <uuid>.zip)
        Files.deleteIfExists(Path.of(result + ".zip"));
    }

    @Test
    void createProvenanceRecord_withRegistration(@TempDir Path tempDir) throws IOException {
        when(mockRegistrationClient.exchangeTokenIfConfigured(anyString(), anyString(), anyString(), anyString()))
            .thenReturn("access-token");

        UUID predecessorId = UUID.randomUUID();
        List<String> files = List.of("src/test/resources/test.txt");
        signingTool.createProvenanceRecord(
            files, "test", "test", List.of(predecessorId.toString()), "SHA256", tempDir,
            "http://localhost:8080/api/provenance", "http://localhost:8180", "trace4eo", null
        );
        verify(mockRegistrationClient).exchangeTokenIfConfigured(
            "http://localhost:8080/api/provenance", "http://localhost:8180", "trace4eo", "test-token");
        verify(mockRegistrationClient).validatePredecessorsExist(
            List.of(new Predecessor(predecessorId)), "http://localhost:8080/api/provenance", "access-token");
        verify(mockRegistrationClient).registerIfConfigured(anyList(), anyString(), anyString());
    }

    @Test
    void createProvenanceRecord_withoutRegistration_doesNotCallClient(@TempDir Path tempDir) throws IOException {
        List<String> files = List.of("src/test/resources/test.txt");
        signingTool.createProvenanceRecord(
            files, "test", "test", List.of(), "SHA256", tempDir, null, null, "trace4eo", null
        );
        verify(mockRegistrationClient, never()).exchangeTokenIfConfigured(any(), any(), any(), any());
        verify(mockRegistrationClient, never()).registerIfConfigured(anyList(), any(), any());
    }

    @Test
    void createProvenanceRecord_nullFiles_throws() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.createProvenanceRecord(null, "test", "test", List.of(), "SHA256", null, null, null, "trace4eo", null)
        );
        assertTrue(exception.getMessage().contains("--files"));
    }

    @Test
    void createProvenanceRecord_emptyFiles_throws() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.createProvenanceRecord(List.of(), "test", "test", List.of(), "SHA256", null, null, null, "trace4eo", null)
        );
        assertTrue(exception.getMessage().contains("--files"));
    }

    @Test
    void createProvenanceRecord_blankProvenanceRecordType_throws() {
        List<String> files = List.of("src/test/resources/test.txt");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.createProvenanceRecord(files, " ", "test", List.of(), "SHA256", null, null, null, "trace4eo", null)
        );
        assertTrue(exception.getMessage().contains("--provenance-record-type"));
    }

    @Test
    void createProvenanceRecord_nullProvenanceRecordType_throws() {
        List<String> files = List.of("src/test/resources/test.txt");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.createProvenanceRecord(files, null, "test", List.of(), "SHA256", null, null, null, "trace4eo", null)
        );
        assertTrue(exception.getMessage().contains("--provenance-record-type"));
    }

    @Test
    void createProvenanceRecord_blankDataId_throws() {
        List<String> files = List.of("src/test/resources/test.txt");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.createProvenanceRecord(files, "test", " ", List.of(), "SHA256", null, null, null, "trace4eo", null)
        );
        assertTrue(exception.getMessage().contains("--data-id"));
    }

    @Test
    void createProvenanceRecord_nullDataId_throws() {
        List<String> files = List.of("src/test/resources/test.txt");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.createProvenanceRecord(files, "test", null, List.of(), "SHA256", null, null, null, "trace4eo", null)
        );
        assertTrue(exception.getMessage().contains("--data-id"));
    }

    @Test
    void createProvenanceRecord_withOutputDir_writesZipToDirectory(@TempDir Path tempDir) throws IOException {
        List<String> files = List.of("src/test/resources/test.txt");
        UUID result = signingTool.createProvenanceRecord(
            files, "test", "test", List.of(), "SHA256", tempDir, null, null, "trace4eo", null
        );
        assertNotNull(result);
        Path expectedFile = tempDir.resolve(result + ".zip");
        assertTrue(Files.exists(expectedFile));
        assertTrue(Files.size(expectedFile) > 0);
    }

    @Test
    void createProvenanceRecord_withPredecessors(@TempDir Path tempDir) throws IOException {
        List<String> files = List.of("src/test/resources/test.txt");
        UUID result = signingTool.createProvenanceRecord(
            files, "test", "test",
            List.of(UUID.randomUUID().toString(), UUID.randomUUID().toString()),
            "SHA256", tempDir, null, null, "trace4eo", null
        );
        assertInstanceOf(UUID.class, result);
    }

    @Test
    void createProvenanceRecord_nullPredecessors_succeeds(@TempDir Path tempDir) throws IOException {
        List<String> files = List.of("src/test/resources/test.txt");
        UUID result = signingTool.createProvenanceRecord(
            files, "test", "test", null, "SHA256", tempDir, null, null, "trace4eo", null
        );
        assertNotNull(result);
    }

    @Test
    void createProvenanceRecord_invalidPredecessorId_throws() {
        List<String> files = List.of("src/test/resources/test.txt");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.createProvenanceRecord(
                files, "test", "test", List.of("not-a-uuid"), "SHA256", null, null, null, "trace4eo", null)
        );
        assertTrue(exception.getMessage().contains("Invalid predecessor ID"));
    }

    @ParameterizedTest
    @EnumSource(HashAlgorithm.class)
    void createProvenanceRecord_usesSpecifiedHashAlgorithm(HashAlgorithm algorithm, @TempDir Path tempDir)
        throws IOException {
        List<String> files = List.of("src/test/resources/test.txt");
        Path outputPath = tempDir.resolve("output.zip");
        UUID result = signingTool.createProvenanceRecord(
            files, "test", "test", List.of(), algorithm.name(), outputPath, null, null, "trace4eo", null
        );
        assertNotNull(result);
    }

    @Test
    void createProvenanceRecord_withOutputDir_createsDirectoryIfMissing(@TempDir Path tempDir) throws IOException {
        Path nestedDir = tempDir.resolve("nested/output");
        List<String> files = List.of("src/test/resources/test.txt");
        UUID result = signingTool.createProvenanceRecord(
            files, "test", "test", List.of(), "SHA256", nestedDir, null, null, "trace4eo", null
        );
        assertNotNull(result);
        assertTrue(Files.isDirectory(nestedDir));
        assertTrue(Files.exists(nestedDir.resolve(result + ".zip")));
    }

    @Test
    void createProvenanceRecord_nonExistentFile_throws() {
        List<String> files = List.of("nonexistent.txt");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.createProvenanceRecord(files, "test", "test", List.of(), "SHA256", null, null, null, "trace4eo", null)
        );
        assertTrue(exception.getMessage().contains("File does not exist"));
    }

    @Test
    void createProvenanceRecord_directoryAsFile_throws(@TempDir Path tempDir) {
        List<String> files = List.of(tempDir.toString());
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.createProvenanceRecord(files, "test", "test", List.of(), "SHA256", null, null, null, "trace4eo", null)
        );
        assertTrue(exception.getMessage().contains("not a regular file"));
    }

    @Test
    void createProvenanceRecord_invalidHashAlgorithm_throws() {
        List<String> files = List.of("src/test/resources/test.txt");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.createProvenanceRecord(files, "test", "test", List.of(), "INVALID", null, null, null, "trace4eo", null)
        );
        assertTrue(exception.getMessage().contains("--hash-algorithm"));
    }

    @Test
    void createProvenanceRecord_outputDirIsFile_throws(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("not-a-dir");
        Files.writeString(file, "content");
        List<String> files = List.of("src/test/resources/test.txt");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.createProvenanceRecord(files, "test", "test", List.of(), "SHA256", file, null, null, "trace4eo", null)
        );
        assertTrue(exception.getMessage().contains("--output is not a directory"));
    }

    @Test
    void createProvenanceRecord_missingPredecessors_throws(@TempDir Path tempDir) {
        UUID missingId = UUID.randomUUID();
        when(mockRegistrationClient.exchangeTokenIfConfigured(anyString(), anyString(), anyString(), anyString()))
            .thenReturn("access-token");
        org.mockito.Mockito.doThrow(new IllegalArgumentException(
            String.format("Predecessor records not found: [%s]", missingId)))
            .when(mockRegistrationClient).validatePredecessorsExist(anyList(), anyString(), anyString());

        List<String> files = List.of("src/test/resources/test.txt");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.createProvenanceRecord(
                files, "test", "test", List.of(missingId.toString()), "SHA256", tempDir,
                "http://localhost:8080/api/provenance", "http://localhost:8180", "trace4eo", null)
        );
        assertTrue(exception.getMessage().contains("Predecessor records not found"));
        assertTrue(exception.getMessage().contains(missingId.toString()));
    }

    @Test
    void createProvenanceRecord_predecessorsWithoutRegisterUrl_skipsValidation(@TempDir Path tempDir) throws IOException {
        List<String> files = List.of("src/test/resources/test.txt");
        signingTool.createProvenanceRecord(
            files, "test", "test",
            List.of(UUID.randomUUID().toString()), "SHA256", tempDir, null, null, "trace4eo", null
        );
        verify(mockRegistrationClient, never()).validatePredecessorsExist(anyList(), anyString(), any());
    }

    @Test
    void createProvenanceRecord_withPredecessorsFile_loadsPredecessors(@TempDir Path tempDir) throws IOException {
        Path predecessorsFile = tempDir.resolve("ids.txt");
        Files.writeString(predecessorsFile, UUID.randomUUID() + "\n" + UUID.randomUUID() + "\n");

        List<String> files = List.of("src/test/resources/test.txt");
        UUID result = signingTool.createProvenanceRecord(
            files, "test", "test", List.of(), "SHA256", tempDir, null, null, "trace4eo", predecessorsFile
        );
        assertNotNull(result);
    }

    @Test
    void createProvenanceRecord_predecessorsFileMergesWithInlinePredecessors(@TempDir Path tempDir) throws IOException {
        Path predecessorsFile = tempDir.resolve("ids.txt");
        Files.writeString(predecessorsFile, UUID.randomUUID() + "\n");

        List<String> files = List.of("src/test/resources/test.txt");
        UUID result = signingTool.createProvenanceRecord(
            files, "test", "test", List.of(UUID.randomUUID().toString()), "SHA256", tempDir,
            null, null, "trace4eo", predecessorsFile
        );
        assertNotNull(result);
    }

    @Test
    void createProvenanceRecord_nonExistentPredecessorsFile_throws(@TempDir Path tempDir) {
        List<String> files = List.of("src/test/resources/test.txt");
        Path missing = tempDir.resolve("missing.json");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.createProvenanceRecord(
                files, "test", "test", List.of(), "SHA256", tempDir, null, null, "trace4eo", missing)
        );
        assertTrue(exception.getMessage().contains("--predecessors-file"));
        assertTrue(exception.getMessage().contains("does not exist"));
    }

    @Test
    void createProvenanceRecord_predecessorsFileInvalidUuid_throws(@TempDir Path tempDir) throws IOException {
        Path badFile = tempDir.resolve("bad.txt");
        Files.writeString(badFile, "not-a-uuid\n");

        List<String> files = List.of("src/test/resources/test.txt");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.createProvenanceRecord(
                files, "test", "test", List.of(), "SHA256", tempDir, null, null, "trace4eo", badFile)
        );
        assertTrue(exception.getMessage().contains("Invalid predecessor ID"));
    }

    @Test
    void createProvenanceRecord_registerUrlWithoutKeycloakUrl_throws() {
        List<String> files = List.of("src/test/resources/test.txt");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.createProvenanceRecord(
                files, "test", "test", List.of(), "SHA256", null,
                "http://localhost:8080/api/provenance", null, "trace4eo", null)
        );
        assertTrue(exception.getMessage().contains("--keycloak-url"));
    }
}
