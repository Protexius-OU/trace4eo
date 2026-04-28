package com.protexius.trace4eo.signing.commands;

import com.protexius.trace4eo.provenance.HashAlgorithm;
import com.protexius.trace4eo.provenance.ProvenanceJsonMapper;
import com.protexius.trace4eo.provenance.ProvenanceSignature;
import com.protexius.trace4eo.provenance.record.Predecessor;
import com.protexius.trace4eo.provenance.record.ProvenanceRecord;
import com.protexius.trace4eo.provenance.signing.ProvenanceSigningService;
import com.protexius.trace4eo.signing.OidcTokenResolver;
import com.protexius.trace4eo.signing.OutputWriter;
import com.protexius.trace4eo.signing.RecordSigningService;
import com.protexius.trace4eo.signing.UnsignedRecord;
import com.protexius.trace4eo.signing.registration.RecordRegistrationClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegisterRecordsToolTest {

    private RegisterRecordsTool tool;
    private RecordRegistrationClient mockRegistrationClient;
    private OidcTokenResolver mockOidcTokenResolver;
    private RecordSigningService recordSigningService;
    private OutputWriter outputWriter;
    private ProvenanceJsonMapper provenanceJsonMapper;

    @BeforeEach
    void setUp() {
        provenanceJsonMapper = new ProvenanceJsonMapper();
        ProvenanceSignature stubSignature = provenanceJsonMapper.readValue(
            Path.of("src/test/resources/signature.json").toFile(),
            ProvenanceSignature.class
        );
        AtomicLong signingTimeCounter = new AtomicLong();
        ProvenanceSigningService mockSigningService = mock(ProvenanceSigningService.class);
        when(mockSigningService.sign(any(byte[].class), anyString())).thenAnswer(invocation ->
            new ProvenanceSignature(
                stubSignature.bytes(),
                stubSignature.signingTime().plusSeconds(signingTimeCounter.getAndIncrement()),
                stubSignature.hashAlgorithm(),
                stubSignature.details()
            ));
        recordSigningService = new RecordSigningService(mockSigningService, provenanceJsonMapper);
        outputWriter = new OutputWriter(provenanceJsonMapper);

        mockRegistrationClient = mock(RecordRegistrationClient.class);
        when(mockRegistrationClient.exchangeToken(anyString(), anyString(), anyString())).thenReturn("access-token");
        when(mockRegistrationClient.registerRecords(anyList(), anyString(), anyString())).thenReturn(List.of());

        mockOidcTokenResolver = mock(OidcTokenResolver.class);
        when(mockOidcTokenResolver.resolve()).thenReturn("oidc-token");

        SigningInputValidator validator = new SigningInputValidator();
        tool = new RegisterRecordsTool(validator, mockRegistrationClient, mockOidcTokenResolver, provenanceJsonMapper);
    }

    @Test
    void registerRecords_loadsZipsFromDirectoryAndPostsEach(@TempDir Path tempDir) throws IOException {
        ProvenanceRecord recordA = signAndSaveRecord(tempDir, "data-a", List.of());
        ProvenanceRecord recordB = signAndSaveRecord(tempDir, "data-b", List.of());

        List<UUID> registered = tool.registerRecords(
            null, tempDir, "*.zip",
            "http://localhost:8080/api/provenance", "http://localhost:8180", "trace4eo"
        );

        assertEquals(Set.of(recordA.id(), recordB.id()), Set.copyOf(registered));
        verify(mockRegistrationClient).exchangeToken("http://localhost:8180", "trace4eo", "oidc-token");
        verify(mockRegistrationClient).checkSignerAccess("http://localhost:8080/api/provenance", "access-token");
        verify(mockRegistrationClient, never()).findMissingPredecessors(anyList(), anyString(), anyString());
        verify(mockRegistrationClient).registerRecords(
            argThat(list -> list.size() == 2
                && list.stream().map(ProvenanceRecord::id).collect(Collectors.toSet())
                    .equals(Set.of(recordA.id(), recordB.id()))),
            anyString(), anyString());
    }

    @Test
    void registerRecords_explicitRecordsList(@TempDir Path tempDir) throws IOException {
        ProvenanceRecord recordA = signAndSaveRecord(tempDir, "data-a", List.of());
        Path zipA = tempDir.resolve(recordA.id() + ".zip");

        List<UUID> registered = tool.registerRecords(
            List.of(zipA.toString()), null, "*.zip",
            "http://localhost:8080/api/provenance", "http://localhost:8180", "trace4eo"
        );

        assertEquals(List.of(recordA.id()), registered);
    }

    @Test
    void registerRecords_validatesExternalPredecessorsOnly(@TempDir Path tempDir) throws IOException {
        UUID externalPredecessorId = UUID.randomUUID();
        ProvenanceRecord recordA = signAndSaveRecord(tempDir, "data-a", List.of());
        ProvenanceRecord recordB = signAndSaveRecord(
            tempDir, "data-b", List.of(new Predecessor(recordA.id()), new Predecessor(externalPredecessorId)));

        when(mockRegistrationClient.findMissingPredecessors(anyList(), anyString(), anyString())).thenReturn(List.of());

        tool.registerRecords(
            null, tempDir, "*.zip",
            "http://localhost:8080/api/provenance", "http://localhost:8180", "trace4eo"
        );

        verify(mockRegistrationClient).findMissingPredecessors(
            eq(List.of(externalPredecessorId)), eq("http://localhost:8080/api/provenance"), eq("access-token"));
        verify(mockRegistrationClient).registerRecords(
            argThat(list -> list.size() == 2
                && list.stream().map(ProvenanceRecord::id).collect(Collectors.toSet())
                    .equals(Set.of(recordA.id(), recordB.id()))),
            anyString(), anyString());
    }

    @Test
    void registerRecords_missingExternalPredecessor_throws(@TempDir Path tempDir) throws IOException {
        UUID missingId = UUID.randomUUID();
        signAndSaveRecord(tempDir, "data-a", List.of(new Predecessor(missingId)));
        when(mockRegistrationClient.findMissingPredecessors(anyList(), anyString(), anyString()))
            .thenReturn(List.of(missingId));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            tool.registerRecords(
                null, tempDir, "*.zip",
                "http://localhost:8080/api/provenance", "http://localhost:8180", "trace4eo")
        );

        assertTrue(exception.getMessage().contains(missingId.toString()));
        verify(mockRegistrationClient, never()).registerRecords(anyList(), anyString(), anyString());
    }

    @Test
    void registerRecords_perRecordFailureReportedAtEnd(@TempDir Path tempDir) throws IOException {
        signAndSaveRecord(tempDir, "data-a", List.of());
        signAndSaveRecord(tempDir, "data-b", List.of());

        when(mockRegistrationClient.registerRecords(anyList(), anyString(), anyString()))
            .thenReturn(List.of("HTTP 500"));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
            tool.registerRecords(
                null, tempDir, "*.zip",
                "http://localhost:8080/api/provenance", "http://localhost:8180", "trace4eo")
        );

        assertTrue(exception.getMessage().contains("HTTP 500"));
        verify(mockRegistrationClient).registerRecords(anyList(), anyString(), anyString());
    }

    @Test
    void registerRecords_noOidcToken_throws(@TempDir Path tempDir) throws IOException {
        signAndSaveRecord(tempDir, "data-a", List.of());
        when(mockOidcTokenResolver.resolve()).thenReturn(null);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
            tool.registerRecords(
                null, tempDir, "*.zip",
                "http://localhost:8080/api/provenance", "http://localhost:8180", "trace4eo")
        );

        assertTrue(exception.getMessage().contains("OIDC token"));
        verify(mockRegistrationClient, never()).exchangeToken(anyString(), anyString(), anyString());
    }

    @Test
    void registerRecords_nullRecordsAndDirectory_throws() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            tool.registerRecords(
                null, null, "*.zip",
                "http://localhost:8080/api/provenance", "http://localhost:8180", "trace4eo")
        );
        assertTrue(exception.getMessage().contains("--records") || exception.getMessage().contains("--directory"));
    }

    @Test
    void registerRecords_blankRegisterUrl_throws(@TempDir Path tempDir) throws IOException {
        signAndSaveRecord(tempDir, "data-a", List.of());
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            tool.registerRecords(null, tempDir, "*.zip", " ", "http://localhost:8180", "trace4eo")
        );
        assertTrue(exception.getMessage().contains("--register-url"));
    }

    @Test
    void registerRecords_blankKeycloakUrl_throws(@TempDir Path tempDir) throws IOException {
        signAndSaveRecord(tempDir, "data-a", List.of());
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            tool.registerRecords(
                null, tempDir, "*.zip", "http://localhost:8080/api/provenance", " ", "trace4eo")
        );
        assertTrue(exception.getMessage().contains("--keycloak-url"));
    }

    @Test
    void registerRecords_emptyDirectory_throws(@TempDir Path tempDir) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            tool.registerRecords(
                null, tempDir, "*.zip",
                "http://localhost:8080/api/provenance", "http://localhost:8180", "trace4eo")
        );
        assertTrue(exception.getMessage().contains("No record files found"));
    }

    @Test
    void registerRecords_nonExistentDirectory_throws(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("missing");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            tool.registerRecords(
                null, missing, "*.zip",
                "http://localhost:8080/api/provenance", "http://localhost:8180", "trace4eo")
        );
        assertTrue(exception.getMessage().contains("--directory"));
    }

    @Test
    void registerRecords_duplicateAcrossSources_skipsSecond(@TempDir Path tempDir) throws IOException {
        ProvenanceRecord recordA = signAndSaveRecord(tempDir, "data-a", List.of());
        Path original = tempDir.resolve(recordA.id() + ".zip");
        Path copy = tempDir.resolve("copy.zip");
        Files.copy(original, copy);

        List<UUID> registered = tool.registerRecords(
            List.of(original.toString(), copy.toString()), null, "*.zip",
            "http://localhost:8080/api/provenance", "http://localhost:8180", "trace4eo"
        );

        assertEquals(List.of(recordA.id()), registered);
        verify(mockRegistrationClient).registerRecords(
            argThat(list -> list.size() == 1 && list.get(0).id().equals(recordA.id())),
            anyString(), anyString());
    }

    private ProvenanceRecord signAndSaveRecord(Path outputDir, String dataId, List<Predecessor> predecessors)
        throws IOException {
        Path inputFile = outputDir.resolve(dataId + ".txt");
        Files.writeString(inputFile, "content-" + dataId);
        UnsignedRecord unsigned = recordSigningService.build(
            new ArrayList<>(List.of(inputFile)), dataId, "test", predecessors, HashAlgorithm.SHA256);
        ProvenanceRecord record = recordSigningService.sign(unsigned, "oidc-token");
        outputWriter.saveRecord(record, outputDir);
        return record;
    }
}
