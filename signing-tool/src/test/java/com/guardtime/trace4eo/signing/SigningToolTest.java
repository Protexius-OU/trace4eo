package com.guardtime.trace4eo.signing;

import com.guardtime.trace4eo.provenance.HashAlgorithm;
import com.guardtime.trace4eo.provenance.ProvenanceJsonMapper;
import com.guardtime.trace4eo.provenance.ProvenanceSignature;
import com.guardtime.trace4eo.provenance.record.FileHashInfo;
import com.guardtime.trace4eo.provenance.record.Predecessor;
import com.guardtime.trace4eo.provenance.record.ProvenanceRecord;
import com.guardtime.trace4eo.provenance.signing.ProvenanceSigningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        when(mockRegistrationClient.registerRecords(anyList(), anyString(), any()))
            .thenReturn(List.of());

        signingTool = new SigningTool(mockSigningService, provenanceJsonMapper, mockRegistrationClient, "test-token");
    }

    @Test
    void createProvenanceRecord_writesZipToDefaultPath() throws IOException {
        List<String> files = List.of("src/test/resources/test.txt");
        ProvenanceRecord result = signingTool.createProvenanceRecord(
            files, "test", "test", List.of(), "SHA256", null, null, null, "trace4eo", null
        );
        assertNotNull(result);
        // Clean up the default output file (written to CWD as <uuid>.zip)
        Files.deleteIfExists(Path.of(result.id() + ".zip"));
    }

    @Test
    void createProvenanceRecord_withRegistration(@TempDir Path tempDir) throws IOException {
        when(mockRegistrationClient.exchangeToken(anyString(), anyString(), anyString()))
            .thenReturn("access-token");
        when(mockRegistrationClient.findMissingPredecessors(anyList(), anyString(), any()))
            .thenReturn(List.of());

        UUID predecessorId = UUID.randomUUID();
        List<String> files = List.of("src/test/resources/test.txt");
        signingTool.createProvenanceRecord(
            files, "test", "test", List.of(predecessorId.toString()), "SHA256", tempDir,
            "http://localhost:8080/api/provenance", "http://localhost:8180", "trace4eo", null
        );
        verify(mockRegistrationClient).exchangeToken("http://localhost:8180", "trace4eo", "test-token");
        verify(mockRegistrationClient).findMissingPredecessors(
            List.of(predecessorId), "http://localhost:8080/api/provenance", "access-token");
        verify(mockRegistrationClient).registerRecords(anyList(), anyString(), anyString());
    }

    @Test
    void createProvenanceRecord_withoutRegistration_doesNotCallClient(@TempDir Path tempDir) throws IOException {
        List<String> files = List.of("src/test/resources/test.txt");
        signingTool.createProvenanceRecord(
            files, "test", "test", List.of(), "SHA256", tempDir, null, null, "trace4eo", null
        );
        verify(mockRegistrationClient, never()).exchangeToken(anyString(), anyString(), anyString());
        verify(mockRegistrationClient, never()).registerRecords(anyList(), anyString(), any());
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
        ProvenanceRecord result = signingTool.createProvenanceRecord(
            files, "test", "test", List.of(), "SHA256", tempDir, null, null, "trace4eo", null
        );
        assertNotNull(result);
        Path expectedFile = tempDir.resolve(result.id() + ".zip");
        assertTrue(Files.exists(expectedFile));
        assertTrue(Files.size(expectedFile) > 0);
    }

    @Test
    void createProvenanceRecord_withPredecessors(@TempDir Path tempDir) throws IOException {
        UUID predecessorId1 = UUID.randomUUID();
        UUID predecessorId2 = UUID.randomUUID();
        List<String> files = List.of("src/test/resources/test.txt");
        ProvenanceRecord result = signingTool.createProvenanceRecord(
            files, "test", "test",
            List.of(predecessorId1.toString(), predecessorId2.toString()),
            "SHA256", tempDir, null, null, "trace4eo", null
        );
        assertNotNull(result);
        List<Predecessor> predecessors = result.metadata().predecessors();
        assertEquals(2, predecessors.size());
        assertEquals(predecessorId1, predecessors.get(0).id());
        assertEquals(predecessorId2, predecessors.get(1).id());
    }

    @Test
    void createProvenanceRecord_nullPredecessors_createsEmptyList(@TempDir Path tempDir) throws IOException {
        List<String> files = List.of("src/test/resources/test.txt");
        ProvenanceRecord result = signingTool.createProvenanceRecord(
            files, "test", "test", null, "SHA256", tempDir, null, null, "trace4eo", null
        );
        assertNotNull(result);
        assertTrue(result.metadata().predecessors().isEmpty());
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
        throws IOException, NoSuchAlgorithmException {
        int expectedLength = MessageDigest.getInstance(algorithm.getName()).getDigestLength();
        List<String> files = List.of("src/test/resources/test.txt");
        Path outputPath = tempDir.resolve("output.zip");
        ProvenanceRecord result = signingTool.createProvenanceRecord(
            files, "test", "test", List.of(), algorithm.name(), outputPath, null, null, "trace4eo", null
        );

        FileHashInfo fileHash = result.filesInfo().files().iterator().next();
        assertEquals(algorithm, fileHash.hashAlgorithm());
        assertEquals(expectedLength, fileHash.hashValue().length);

        assertEquals(algorithm, result.manifest().filesHashInfo().hashAlgorithm());
        assertEquals(expectedLength, result.manifest().filesHashInfo().hashValue().length);
        assertEquals(algorithm, result.manifest().metadataHashInfo().hashAlgorithm());
        assertEquals(expectedLength, result.manifest().metadataHashInfo().hashValue().length);
    }

    @Test
    void createProvenanceRecord_withOutputDir_createsDirectoryIfMissing(@TempDir Path tempDir) throws IOException {
        Path nestedDir = tempDir.resolve("nested/output");
        List<String> files = List.of("src/test/resources/test.txt");
        ProvenanceRecord result = signingTool.createProvenanceRecord(
            files, "test", "test", List.of(), "SHA256", nestedDir, null, null, "trace4eo", null
        );
        assertNotNull(result);
        assertTrue(Files.isDirectory(nestedDir));
        assertTrue(Files.exists(nestedDir.resolve(result.id() + ".zip")));
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
        when(mockRegistrationClient.exchangeToken(anyString(), anyString(), anyString()))
            .thenReturn("access-token");
        when(mockRegistrationClient.findMissingPredecessors(anyList(), anyString(), any()))
            .thenReturn(List.of(missingId));

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
        verify(mockRegistrationClient, never()).findMissingPredecessors(anyList(), anyString(), any());
    }

    @Test
    void createProvenanceRecord_withPredecessorsFile_loadsPredecessors(@TempDir Path tempDir) throws IOException {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Path predecessorsFile = tempDir.resolve("ids.txt");
        Files.writeString(predecessorsFile, id1 + "\n" + id2 + "\n");

        List<String> files = List.of("src/test/resources/test.txt");
        ProvenanceRecord result = signingTool.createProvenanceRecord(
            files, "test", "test", List.of(), "SHA256", tempDir, null, null, "trace4eo", predecessorsFile
        );

        List<Predecessor> predecessors = result.metadata().predecessors();
        assertEquals(2, predecessors.size());
        assertEquals(id1, predecessors.get(0).id());
        assertEquals(id2, predecessors.get(1).id());
    }

    @Test
    void createProvenanceRecord_predecessorsFileMergesWithInlinePredecessors(@TempDir Path tempDir) throws IOException {
        UUID idFromFile = UUID.randomUUID();
        UUID idInline = UUID.randomUUID();
        Path predecessorsFile = tempDir.resolve("ids.txt");
        Files.writeString(predecessorsFile, idFromFile + "\n");

        List<String> files = List.of("src/test/resources/test.txt");
        ProvenanceRecord result = signingTool.createProvenanceRecord(
            files, "test", "test", List.of(idInline.toString()), "SHA256", tempDir,
            null, null, "trace4eo", predecessorsFile
        );

        List<Predecessor> predecessors = result.metadata().predecessors();
        assertEquals(2, predecessors.size());
        assertEquals(idInline, predecessors.get(0).id());
        assertEquals(idFromFile, predecessors.get(1).id());
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
