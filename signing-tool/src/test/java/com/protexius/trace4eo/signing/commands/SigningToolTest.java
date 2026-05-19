package com.protexius.trace4eo.signing.commands;

import com.protexius.trace4eo.provenance.HashAlgorithm;
import com.protexius.trace4eo.provenance.ProvenanceJsonMapper;
import com.protexius.trace4eo.provenance.ProvenanceSignature;
import com.protexius.trace4eo.provenance.record.Predecessor;
import com.protexius.trace4eo.provenance.signing.ProvenanceSigningService;
import com.protexius.trace4eo.signing.OidcTokenResolver;
import com.protexius.trace4eo.signing.OutputWriter;
import com.protexius.trace4eo.signing.RecordSigningService;
import com.protexius.trace4eo.signing.registration.RecordRegistrationClient;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SigningToolTest {

    private SigningTool signingTool;
    private ProvenanceJsonMapper provenanceJsonMapper;
    private RecordRegistrationClient mockRegistrationClient;
    private RecordSigningService recordSigningService;

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

        recordSigningService = spy(new RecordSigningService(mockSigningService, provenanceJsonMapper));
        OutputWriter outputWriter = new OutputWriter(provenanceJsonMapper);
        SigningInputValidator validator = new SigningInputValidator();
        OidcTokenResolver oidcTokenResolver = mock(OidcTokenResolver.class);
        when(oidcTokenResolver.resolve()).thenReturn("test-token");
        signingTool = new SigningTool(validator, recordSigningService, outputWriter, mockRegistrationClient,
            oidcTokenResolver, new MetadataInputResolver());
    }

    @Test
    void createProvenanceRecord_writesZipToDefaultPath() throws IOException {
        List<String> files = List.of("src/test/resources/test.txt");
        UUID result = signingTool.createProvenanceRecord(
            files, "test", "test", List.of(), "SHA256", null, null, null, "trace4eo", null, null, "*", true, null);
        Path defaultOutput = Path.of(result + ".zip");
        try {
            assertTrue(Files.exists(defaultOutput));
            assertTrue(Files.size(defaultOutput) > 0);
        } finally {
            Files.deleteIfExists(defaultOutput);
        }
    }

    @Test
    void createProvenanceRecord_withRegistration(@TempDir Path tempDir) throws IOException {
        when(mockRegistrationClient.exchangeToken(anyString(), anyString(), anyString()))
            .thenReturn("access-token");

        UUID predecessorId = UUID.randomUUID();
        List<String> files = List.of("src/test/resources/test.txt");
        signingTool.createProvenanceRecord(
            files, "test", "test", List.of(predecessorId.toString()), "SHA256", tempDir,
            "http://localhost:8080/api/provenance", "http://localhost:8180", "trace4eo", null, null, "*", true, null);
        verify(mockRegistrationClient).exchangeToken("http://localhost:8180", "trace4eo", "test-token");
        verify(mockRegistrationClient).validatePredecessorsExist(
            List.of(new Predecessor(predecessorId)), "http://localhost:8080/api/provenance", "access-token");
        verify(mockRegistrationClient).registerIfConfigured(anyList(), anyString(), anyString());
    }

    @Test
    void createProvenanceRecord_withoutRegistration_doesNotCallClient(@TempDir Path tempDir) throws IOException {
        List<String> files = List.of("src/test/resources/test.txt");
        signingTool.createProvenanceRecord(
            files, "test", "test", List.of(), "SHA256", tempDir, null, null, "trace4eo", null, null, "*", true, null);
        verify(mockRegistrationClient, never()).exchangeToken(any(), any(), any());
    }

    @Test
    void createProvenanceRecord_nullFilesAndNullDirectory_throws() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.createProvenanceRecord(
                null, "test", "test", List.of(), "SHA256", null, null, null, "trace4eo", null, null, "*", true, null)
        );
        assertTrue(exception.getMessage().contains("--files") || exception.getMessage().contains("--directory"));
    }

    @Test
    void createProvenanceRecord_emptyFilesAndNullDirectory_throws() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.createProvenanceRecord(
                List.of(), "test", "test", List.of(), "SHA256", null, null, null, "trace4eo", null, null, "*", true, null)
        );
        assertTrue(exception.getMessage().contains("--files") || exception.getMessage().contains("--directory"));
    }

    @Test
    void createProvenanceRecord_blankProvenanceRecordType_throws() {
        List<String> files = List.of("src/test/resources/test.txt");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.createProvenanceRecord(
                files, " ", "test", List.of(), "SHA256", null, null, null, "trace4eo", null, null, "*", true, null)
        );
        assertTrue(exception.getMessage().contains("--provenance-record-type"));
    }

    @Test
    void createProvenanceRecord_nullProvenanceRecordType_throws() {
        List<String> files = List.of("src/test/resources/test.txt");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.createProvenanceRecord(
                files, null, "test", List.of(), "SHA256", null, null, null, "trace4eo", null, null, "*", true, null)
        );
        assertTrue(exception.getMessage().contains("--provenance-record-type"));
    }

    @Test
    void createProvenanceRecord_blankDataId_throws() {
        List<String> files = List.of("src/test/resources/test.txt");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.createProvenanceRecord(
                files, "test", " ", List.of(), "SHA256", null, null, null, "trace4eo", null, null, "*", true, null)
        );
        assertTrue(exception.getMessage().contains("--data-id"));
    }

    @Test
    void createProvenanceRecord_nullDataId_throws() {
        List<String> files = List.of("src/test/resources/test.txt");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.createProvenanceRecord(
                files, "test", null, List.of(), "SHA256", null, null, null, "trace4eo", null, null, "*", true, null)
        );
        assertTrue(exception.getMessage().contains("--data-id"));
    }

    @Test
    void createProvenanceRecord_withOutputDir_writesZipToDirectory(@TempDir Path tempDir) throws IOException {
        List<String> files = List.of("src/test/resources/test.txt");
        UUID result = signingTool.createProvenanceRecord(
            files, "test", "test", List.of(), "SHA256", tempDir, null, null, "trace4eo", null, null, "*", true, null);
        assertNotNull(result);
        Path expectedFile = tempDir.resolve(result + ".zip");
        assertTrue(Files.exists(expectedFile));
        assertTrue(Files.size(expectedFile) > 0);
    }

    @Test
    void createProvenanceRecord_withPredecessors(@TempDir Path tempDir) throws IOException {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        List<String> files = List.of("src/test/resources/test.txt");
        signingTool.createProvenanceRecord(
            files, "test", "test",
            List.of(id1.toString(), id2.toString()),
            "SHA256", tempDir, null, null, "trace4eo", null, null, "*", true, null);
        verify(recordSigningService).build(
            anyList(), anyString(), anyString(),
            argThat(preds -> preds.size() == 2
                && preds.contains(new Predecessor(id1))
                && preds.contains(new Predecessor(id2))),
            any(), any(HashAlgorithm.class));
    }

    @Test
    void createProvenanceRecord_nullPredecessors_treatedAsEmpty(@TempDir Path tempDir) throws IOException {
        List<String> files = List.of("src/test/resources/test.txt");
        signingTool.createProvenanceRecord(
            files, "test", "test", null, "SHA256", tempDir, null, null, "trace4eo", null, null, "*", true, null);
        verify(recordSigningService).build(
            anyList(), anyString(), anyString(), eq(List.of()), any(), any(HashAlgorithm.class));
    }

    @Test
    void createProvenanceRecord_invalidPredecessorId_throws() {
        List<String> files = List.of("src/test/resources/test.txt");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.createProvenanceRecord(
                files, "test", "test", List.of("not-a-uuid"), "SHA256", null, null, null,
                "trace4eo", null, null, "*", true, null)
        );
        assertTrue(exception.getMessage().contains("Invalid predecessor ID"));
    }

    @ParameterizedTest
    @EnumSource(HashAlgorithm.class)
    void createProvenanceRecord_usesSpecifiedHashAlgorithm(HashAlgorithm algorithm, @TempDir Path tempDir)
        throws IOException {
        List<String> files = List.of("src/test/resources/test.txt");
        signingTool.createProvenanceRecord(
            files, "test", "test", List.of(), algorithm.name(), tempDir, null, null, "trace4eo", null, null, "*", true, null);
        verify(recordSigningService).build(
            anyList(), anyString(), anyString(), anyList(), any(), eq(algorithm));
    }

    @Test
    void createProvenanceRecord_withOutputDir_createsDirectoryIfMissing(@TempDir Path tempDir) throws IOException {
        Path nestedDir = tempDir.resolve("nested/output");
        List<String> files = List.of("src/test/resources/test.txt");
        UUID result = signingTool.createProvenanceRecord(
            files, "test", "test", List.of(), "SHA256", nestedDir, null, null, "trace4eo", null, null, "*", true, null);
        assertNotNull(result);
        assertTrue(Files.isDirectory(nestedDir));
        assertTrue(Files.exists(nestedDir.resolve(result + ".zip")));
    }

    @Test
    void createProvenanceRecord_nonExistentFile_throws() {
        List<String> files = List.of("nonexistent.txt");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.createProvenanceRecord(
                files, "test", "test", List.of(), "SHA256", null, null, null, "trace4eo", null, null, "*", true, null)
        );
        assertTrue(exception.getMessage().contains("File does not exist"));
    }

    @Test
    void createProvenanceRecord_directoryAsFile_throws(@TempDir Path tempDir) {
        List<String> files = List.of(tempDir.toString());
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.createProvenanceRecord(
                files, "test", "test", List.of(), "SHA256", null, null, null, "trace4eo", null, null, "*", true, null)
        );
        assertTrue(exception.getMessage().contains("not a regular file"));
    }

    @Test
    void createProvenanceRecord_invalidHashAlgorithm_throws() {
        List<String> files = List.of("src/test/resources/test.txt");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.createProvenanceRecord(
                files, "test", "test", List.of(), "INVALID", null, null, null, "trace4eo", null, null, "*", true, null)
        );
        assertTrue(exception.getMessage().contains("--hash-algorithm"));
    }

    @Test
    void createProvenanceRecord_outputDirIsFile_throws(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("not-a-dir");
        Files.writeString(file, "content");
        List<String> files = List.of("src/test/resources/test.txt");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.createProvenanceRecord(
                files, "test", "test", List.of(), "SHA256", file, null, null, "trace4eo", null, null, "*", true, null)
        );
        assertTrue(exception.getMessage().contains("--output is not a directory"));
    }

    @Test
    void createProvenanceRecord_missingPredecessors_throws(@TempDir Path tempDir) {
        UUID missingId = UUID.randomUUID();
        when(mockRegistrationClient.exchangeToken(anyString(), anyString(), anyString()))
            .thenReturn("access-token");
        org.mockito.Mockito.doThrow(new IllegalArgumentException(
            String.format("Predecessor records not found: [%s]", missingId)))
            .when(mockRegistrationClient).validatePredecessorsExist(anyList(), anyString(), anyString());

        List<String> files = List.of("src/test/resources/test.txt");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.createProvenanceRecord(
                files, "test", "test", List.of(missingId.toString()), "SHA256", tempDir,
                "http://localhost:8080/api/provenance", "http://localhost:8180", "trace4eo", null, null, "*", true, null)
        );
        assertTrue(exception.getMessage().contains("Predecessor records not found"));
        assertTrue(exception.getMessage().contains(missingId.toString()));
    }

    @Test
    void createProvenanceRecord_predecessorsWithoutRegisterUrl_skipsValidation(@TempDir Path tempDir) throws IOException {
        List<String> files = List.of("src/test/resources/test.txt");
        signingTool.createProvenanceRecord(
            files, "test", "test",
            List.of(UUID.randomUUID().toString()), "SHA256", tempDir, null, null, "trace4eo", null, null, "*", true, null);
        verify(mockRegistrationClient, never()).validatePredecessorsExist(anyList(), anyString(), any());
    }

    @Test
    void createProvenanceRecord_withPredecessorsFile_loadsPredecessors(@TempDir Path tempDir) throws IOException {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Path predecessorsFile = tempDir.resolve("ids.txt");
        Files.writeString(predecessorsFile, id1 + "\n" + id2 + "\n");

        List<String> files = List.of("src/test/resources/test.txt");
        signingTool.createProvenanceRecord(
            files, "test", "test", List.of(), "SHA256", tempDir, null, null,
            "trace4eo", predecessorsFile, null, "*", true, null);
        verify(recordSigningService).build(
            anyList(), anyString(), anyString(),
            eq(List.of(new Predecessor(id1), new Predecessor(id2))),
            any(), any(HashAlgorithm.class));
    }

    @Test
    void createProvenanceRecord_predecessorsFileMergesWithInlinePredecessors(@TempDir Path tempDir) throws IOException {
        UUID idInline = UUID.randomUUID();
        UUID idFromFile = UUID.randomUUID();
        Path predecessorsFile = tempDir.resolve("ids.txt");
        Files.writeString(predecessorsFile, idFromFile + "\n");

        List<String> files = List.of("src/test/resources/test.txt");
        signingTool.createProvenanceRecord(
            files, "test", "test", List.of(idInline.toString()), "SHA256", tempDir,
            null, null, "trace4eo", predecessorsFile, null, "*", true, null);
        verify(recordSigningService).build(
            anyList(), anyString(), anyString(),
            eq(List.of(new Predecessor(idInline), new Predecessor(idFromFile))),
            any(), any(HashAlgorithm.class));
    }

    @Test
    void createProvenanceRecord_nonExistentPredecessorsFile_throws(@TempDir Path tempDir) {
        List<String> files = List.of("src/test/resources/test.txt");
        Path missing = tempDir.resolve("missing.json");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.createProvenanceRecord(
                files, "test", "test", List.of(), "SHA256", tempDir, null, null, "trace4eo", missing, null, "*", true, null)
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
                files, "test", "test", List.of(), "SHA256", tempDir, null, null, "trace4eo", badFile, null, "*", true, null)
        );
        assertTrue(exception.getMessage().contains("Invalid predecessor ID"));
    }

    @Test
    void createProvenanceRecord_registerUrlWithoutKeycloakUrl_throws() {
        List<String> files = List.of("src/test/resources/test.txt");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.createProvenanceRecord(
                files, "test", "test", List.of(), "SHA256", null,
                "http://localhost:8080/api/provenance", null, "trace4eo", null, null, "*", true, null)
        );
        assertTrue(exception.getMessage().contains("--keycloak-url"));
    }

    @Test
    void createProvenanceRecord_duplicateInlinePredecessors_deduplicates(@TempDir Path tempDir) throws IOException {
        UUID id = UUID.randomUUID();
        List<String> files = List.of("src/test/resources/test.txt");
        signingTool.createProvenanceRecord(
            files, "test", "test", List.of(id.toString(), id.toString()), "SHA256", tempDir,
            null, null, "trace4eo", null, null, "*", true, null);
        verify(recordSigningService).build(
            anyList(), anyString(), anyString(), eq(List.of(new Predecessor(id))), any(), any(HashAlgorithm.class));
    }

    @Test
    void createProvenanceRecord_duplicatesAcrossFileAndInline_deduplicates(@TempDir Path tempDir) throws IOException {
        UUID id = UUID.randomUUID();
        Path predecessorsFile = tempDir.resolve("ids.txt");
        Files.writeString(predecessorsFile, id + "\n");

        List<String> files = List.of("src/test/resources/test.txt");
        signingTool.createProvenanceRecord(
            files, "test", "test", List.of(id.toString()), "SHA256", tempDir,
            null, null, "trace4eo", predecessorsFile, null, "*", true, null);
        verify(recordSigningService).build(
            anyList(), anyString(), anyString(), eq(List.of(new Predecessor(id))), any(), any(HashAlgorithm.class));
    }

    @Test
    void createProvenanceRecord_duplicatesInFile_deduplicates(@TempDir Path tempDir) throws IOException {
        UUID id = UUID.randomUUID();
        Path predecessorsFile = tempDir.resolve("ids.txt");
        Files.writeString(predecessorsFile, id + "\n" + id + "\n");

        List<String> files = List.of("src/test/resources/test.txt");
        signingTool.createProvenanceRecord(
            files, "test", "test", List.of(), "SHA256", tempDir,
            null, null, "trace4eo", predecessorsFile, null, "*", true, null);
        verify(recordSigningService).build(
            anyList(), anyString(), anyString(), eq(List.of(new Predecessor(id))), any(), any(HashAlgorithm.class));
    }

    // --- directory/pattern tests ---

    @Test
    void createProvenanceRecord_withDirectory_includesAllFiles(@TempDir Path tempDir) throws IOException {
        Path outputDir = tempDir.resolve("output");
        Path inputDir = tempDir.resolve("input");
        Files.createDirectory(inputDir);
        Files.writeString(inputDir.resolve("a.txt"), "content-a");
        Files.writeString(inputDir.resolve("b.txt"), "content-b");

        signingTool.createProvenanceRecord(
            null, "test", "test", List.of(), "SHA256", outputDir, null, null, "trace4eo", null, inputDir, "*", true, null);
        verify(recordSigningService).build(
            argThat(paths -> paths.size() == 2
                && paths.contains(inputDir.resolve("a.txt"))
                && paths.contains(inputDir.resolve("b.txt"))),
            anyString(), anyString(), anyList(), any(), any(HashAlgorithm.class));
    }

    @Test
    void createProvenanceRecord_withDirectoryAndPattern_includesMatchingFilesOnly(@TempDir Path tempDir)
        throws IOException {
        Path outputDir = tempDir.resolve("output");
        Path inputDir = tempDir.resolve("input");
        Files.createDirectory(inputDir);
        Files.writeString(inputDir.resolve("a.tif"), "tiff-a");
        Files.writeString(inputDir.resolve("b.tif"), "tiff-b");
        Files.writeString(inputDir.resolve("c.txt"), "text-c");

        signingTool.createProvenanceRecord(
            null, "test", "test", List.of(), "SHA256", outputDir, null, null,
            "trace4eo", null, inputDir, "*.tif", true, null);
        verify(recordSigningService).build(
            argThat(paths -> paths.size() == 2
                && paths.contains(inputDir.resolve("a.tif"))
                && paths.contains(inputDir.resolve("b.tif"))),
            anyString(), anyString(), anyList(), any(), any(HashAlgorithm.class));
    }

    @Test
    void createProvenanceRecord_filesAndDirectoryCombined_mergesAll(@TempDir Path tempDir) throws IOException {
        Path outputDir = tempDir.resolve("output");
        Path inputDir = tempDir.resolve("input");
        Files.createDirectory(inputDir);
        Path dirFile = inputDir.resolve("dir-file.txt");
        Files.writeString(dirFile, "from-dir");

        Path extraFile = tempDir.resolve("extra.txt");
        Files.writeString(extraFile, "extra");

        signingTool.createProvenanceRecord(
            List.of(extraFile.toString()), "test", "test", List.of(), "SHA256", outputDir,
            null, null, "trace4eo", null, inputDir, "*", true, null);
        verify(recordSigningService).build(
            argThat(paths -> paths.size() == 2
                && paths.contains(extraFile)
                && paths.contains(dirFile)),
            anyString(), anyString(), anyList(), any(), any(HashAlgorithm.class));
    }

    @Test
    void createProvenanceRecord_duplicateFileAcrossFilesAndDirectory_deduplicates(@TempDir Path tempDir)
        throws IOException {
        Path inputDir = tempDir.resolve("input");
        Files.createDirectory(inputDir);
        Path sharedFile = inputDir.resolve("shared.txt");
        Files.writeString(sharedFile, "content");

        signingTool.createProvenanceRecord(
            List.of(sharedFile.toString()), "test", "test", List.of(), "SHA256", tempDir,
            null, null, "trace4eo", null, inputDir, "*", true, null);
        verify(recordSigningService).build(
            argThat(paths -> paths.size() == 1 && paths.contains(sharedFile.toAbsolutePath())),
            anyString(), anyString(), anyList(), any(), any(HashAlgorithm.class));
    }

    @Test
    void createProvenanceRecord_invalidGlobPattern_throws(@TempDir Path tempDir) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.createProvenanceRecord(
                null, "test", "test", List.of(), "SHA256", null, null, null,
                "trace4eo", null, tempDir, "[invalid", true, null)
        );
        assertTrue(exception.getMessage().contains("--pattern"));
    }

    @Test
    void createProvenanceRecord_nonExistentDirectory_throws(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("no-such-dir");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.createProvenanceRecord(
                null, "test", "test", List.of(), "SHA256", null, null, null, "trace4eo", null, missing, "*", true, null)
        );
        assertTrue(exception.getMessage().contains("--directory"));
        assertTrue(exception.getMessage().contains("does not exist"));
    }

    @Test
    void createProvenanceRecord_fileAsDirectory_throws(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("not-a-dir.txt");
        Files.writeString(file, "content");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.createProvenanceRecord(
                null, "test", "test", List.of(), "SHA256", null, null, null, "trace4eo", null, file, "*", true, null)
        );
        assertTrue(exception.getMessage().contains("--directory"));
        assertTrue(exception.getMessage().contains("not a directory"));
    }

    @Test
    void createProvenanceRecord_emptyDirectoryAndNoFiles_throws(@TempDir Path tempDir) throws IOException {
        Path emptyDir = tempDir.resolve("empty");
        Files.createDirectory(emptyDir);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.createProvenanceRecord(
                null, "test", "test", List.of(), "SHA256", null, null, null, "trace4eo", null, emptyDir, "*", true, null)
        );
        assertTrue(exception.getMessage().contains("No files found"));
    }

    @Test
    void createProvenanceRecord_noSaveZip_doesNotWriteZip(@TempDir Path tempDir) throws IOException {
        List<String> files = List.of("src/test/resources/test.txt");
        UUID result = signingTool.createProvenanceRecord(
            files, "test", "test", List.of(), "SHA256", tempDir, null, null, "trace4eo", null, null, "*", false, null);
        assertNotNull(result);
        assertFalse(Files.exists(tempDir.resolve(result + ".zip")));
    }

    @Test
    void createProvenanceRecord_patternMatchesNoFiles_throws(@TempDir Path tempDir) throws IOException {
        Path inputDir = tempDir.resolve("input");
        Files.createDirectory(inputDir);
        Files.writeString(inputDir.resolve("a.txt"), "content");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.createProvenanceRecord(
                null, "test", "test", List.of(), "SHA256", null, null, null,
                "trace4eo", null, inputDir, "*.tif", true, null)
        );
        assertTrue(exception.getMessage().contains("No files found"));
    }
}
