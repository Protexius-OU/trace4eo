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
            files, "test", "test", List.of(), "SHA256", null, null, null, "trace4eo"
        );
        assertNotNull(result);
        // Clean up the default output file (written to CWD as <uuid>.zip)
        Files.deleteIfExists(Path.of(result.id() + ".zip"));
    }

    @Test
    void createProvenanceRecord_withRegistration(@TempDir Path tempDir) throws IOException {
        when(mockRegistrationClient.exchangeToken(anyString(), anyString(), anyString()))
            .thenReturn("access-token");

        List<String> files = List.of("src/test/resources/test.txt");
        Path outputPath = tempDir.resolve("output.zip");
        signingTool.createProvenanceRecord(
            files, "test", "test", List.of(), "SHA256", outputPath,
            "http://localhost:8080/api/provenance", "http://localhost:8180", "trace4eo"
        );
        verify(mockRegistrationClient).exchangeToken("http://localhost:8180", "trace4eo", "test-token");
        verify(mockRegistrationClient).registerRecords(anyList(), anyString(), anyString());
    }

    @Test
    void createProvenanceRecord_withoutRegistration_doesNotCallClient(@TempDir Path tempDir) throws IOException {
        List<String> files = List.of("src/test/resources/test.txt");
        signingTool.createProvenanceRecord(
            files, "test", "test", List.of(), "SHA256", tempDir.resolve("out.zip"), null, null, "trace4eo"
        );
        verify(mockRegistrationClient, never()).exchangeToken(anyString(), anyString(), anyString());
        verify(mockRegistrationClient, never()).registerRecords(anyList(), anyString(), any());
    }

    @Test
    void createProvenanceRecord_nullFiles_throws() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.createProvenanceRecord(null, "test", "test", List.of(), "SHA256", null, null, null, "trace4eo")
        );
        assertTrue(exception.getMessage().contains("--files"));
    }

    @Test
    void createProvenanceRecord_emptyFiles_throws() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.createProvenanceRecord(List.of(), "test", "test", List.of(), "SHA256", null, null, null, "trace4eo")
        );
        assertTrue(exception.getMessage().contains("--files"));
    }

    @Test
    void createProvenanceRecord_blankProvenanceRecordType_throws() {
        List<String> files = List.of("src/test/resources/test.txt");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.createProvenanceRecord(files, " ", "test", List.of(), "SHA256", null, null, null, "trace4eo")
        );
        assertTrue(exception.getMessage().contains("--provenance-record-type"));
    }

    @Test
    void createProvenanceRecord_nullProvenanceRecordType_throws() {
        List<String> files = List.of("src/test/resources/test.txt");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.createProvenanceRecord(files, null, "test", List.of(), "SHA256", null, null, null, "trace4eo")
        );
        assertTrue(exception.getMessage().contains("--provenance-record-type"));
    }

    @Test
    void createProvenanceRecord_blankDataId_throws() {
        List<String> files = List.of("src/test/resources/test.txt");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.createProvenanceRecord(files, "test", " ", List.of(), "SHA256", null, null, null, "trace4eo")
        );
        assertTrue(exception.getMessage().contains("--data-id"));
    }

    @Test
    void createProvenanceRecord_nullDataId_throws() {
        List<String> files = List.of("src/test/resources/test.txt");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.createProvenanceRecord(files, "test", null, List.of(), "SHA256", null, null, null, "trace4eo")
        );
        assertTrue(exception.getMessage().contains("--data-id"));
    }

    @Test
    void createProvenanceRecord_withOutput_writesZipToCustomPath(@TempDir Path tempDir) throws IOException {
        Path outputPath = tempDir.resolve("output.zip");
        List<String> files = List.of("src/test/resources/test.txt");
        ProvenanceRecord result = signingTool.createProvenanceRecord(
            files, "test", "test", List.of(), "SHA256", outputPath, null, null, "trace4eo"
        );
        assertNotNull(result);
        assertTrue(Files.exists(outputPath));
        assertTrue(Files.size(outputPath) > 0);
    }

    @Test
    void createProvenanceRecord_withPredecessors(@TempDir Path tempDir) throws IOException {
        UUID predecessorId1 = UUID.randomUUID();
        UUID predecessorId2 = UUID.randomUUID();
        List<String> files = List.of("src/test/resources/test.txt");
        Path outputPath = tempDir.resolve("output.zip");
        ProvenanceRecord result = signingTool.createProvenanceRecord(
            files, "test", "test",
            List.of(predecessorId1.toString(), predecessorId2.toString()),
            "SHA256", outputPath, null, null, "trace4eo"
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
        Path outputPath = tempDir.resolve("output.zip");
        ProvenanceRecord result = signingTool.createProvenanceRecord(
            files, "test", "test", null, "SHA256", outputPath, null, null, "trace4eo"
        );
        assertNotNull(result);
        assertTrue(result.metadata().predecessors().isEmpty());
    }

    @Test
    void createProvenanceRecord_invalidPredecessorId_throws() {
        List<String> files = List.of("src/test/resources/test.txt");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.createProvenanceRecord(
                files, "test", "test", List.of("not-a-uuid"), "SHA256", null, null, null, "trace4eo")
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
            files, "test", "test", List.of(), algorithm.name(), outputPath, null, null, "trace4eo"
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
    void createProvenanceRecord_registerUrlWithoutKeycloakUrl_throws() {
        List<String> files = List.of("src/test/resources/test.txt");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.createProvenanceRecord(
                files, "test", "test", List.of(), "SHA256", null,
                "http://localhost:8080/api/provenance", null, "trace4eo")
        );
        assertTrue(exception.getMessage().contains("--keycloak-url"));
    }
}
