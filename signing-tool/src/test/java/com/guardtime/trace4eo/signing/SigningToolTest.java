package com.guardtime.trace4eo.signing;

import com.guardtime.trace4eo.provenance.HashAlgorithm;
import com.guardtime.trace4eo.provenance.ProvenanceJsonMapper;
import com.guardtime.trace4eo.provenance.ProvenanceSignature;
import com.guardtime.trace4eo.provenance.record.ProvenanceRecord;
import com.guardtime.trace4eo.provenance.signing.ProvenanceSigningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
        when(mockSigningService.sign(any(byte[].class), any(HashAlgorithm.class), anyString()))
            .thenReturn(testSignature);

        mockRegistrationClient = mock(RecordRegistrationClient.class);
        when(mockRegistrationClient.registerRecords(anyList(), anyString(), any()))
            .thenReturn(List.of());

        signingTool = new SigningTool(mockSigningService, provenanceJsonMapper, mockRegistrationClient, "test-token");
    }

    @Test
    void createProvenanceRecord() throws IOException {
        List<String> files = List.of("src/test/resources/test.txt");
        ProvenanceRecord result = signingTool.createProvenanceRecord(
            files, "test", "test", List.of(), "SHA256", null, null, "trace4eo"
        );
        assertNotNull(result);
        assertNotNull(result.id());
        assertNotNull(result.filesInfo());
        assertNotNull(result.metadata());
        assertNotNull(result.manifest());
    }

    @Test
    void createProvenanceRecord_withRegistration() throws IOException {
        when(mockRegistrationClient.exchangeToken(anyString(), anyString(), anyString()))
            .thenReturn("access-token");

        List<String> files = List.of("src/test/resources/test.txt");
        ProvenanceRecord result = signingTool.createProvenanceRecord(
            files, "test", "test", List.of(), "SHA256",
            "http://localhost:8080/api/provenance", "http://localhost:8180", "trace4eo"
        );
        assertNotNull(result);
        verify(mockRegistrationClient).exchangeToken("http://localhost:8180", "trace4eo", "test-token");
        verify(mockRegistrationClient).registerRecords(
            eq(List.of(result)), eq("http://localhost:8080/api/provenance"), eq("access-token"));
    }

    @Test
    void createProvenanceRecord_withoutRegistration_doesNotCallClient() throws IOException {
        List<String> files = List.of("src/test/resources/test.txt");
        signingTool.createProvenanceRecord(
            files, "test", "test", List.of(), "SHA256", null, null, "trace4eo"
        );
        verify(mockRegistrationClient, never()).exchangeToken(anyString(), anyString(), anyString());
        verify(mockRegistrationClient, never()).registerRecords(anyList(), anyString(), any());
    }

    @Test
    void createProvenanceRecord_nullFiles_throws() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.createProvenanceRecord(null, "test", "test", List.of(), "SHA256", null, null, "trace4eo")
        );
        assertTrue(exception.getMessage().contains("--files"));
    }

    @Test
    void createProvenanceRecord_emptyFiles_throws() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.createProvenanceRecord(List.of(), "test", "test", List.of(), "SHA256", null, null, "trace4eo")
        );
        assertTrue(exception.getMessage().contains("--files"));
    }

    @Test
    void createProvenanceRecord_blankProvenanceRecordType_throws() {
        List<String> files = List.of("src/test/resources/test.txt");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.createProvenanceRecord(files, " ", "test", List.of(), "SHA256", null, null, "trace4eo")
        );
        assertTrue(exception.getMessage().contains("--provenance-record-type"));
    }

    @Test
    void createProvenanceRecord_nullProvenanceRecordType_throws() {
        List<String> files = List.of("src/test/resources/test.txt");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.createProvenanceRecord(files, null, "test", List.of(), "SHA256", null, null, "trace4eo")
        );
        assertTrue(exception.getMessage().contains("--provenance-record-type"));
    }

    @Test
    void createProvenanceRecord_blankDataId_throws() {
        List<String> files = List.of("src/test/resources/test.txt");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.createProvenanceRecord(files, "test", " ", List.of(), "SHA256", null, null, "trace4eo")
        );
        assertTrue(exception.getMessage().contains("--data-id"));
    }

    @Test
    void createProvenanceRecord_nullDataId_throws() {
        List<String> files = List.of("src/test/resources/test.txt");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.createProvenanceRecord(files, "test", null, List.of(), "SHA256", null, null, "trace4eo")
        );
        assertTrue(exception.getMessage().contains("--data-id"));
    }

    @Test
    void createProvenanceRecord_registerUrlWithoutKeycloakUrl_throws() {
        List<String> files = List.of("src/test/resources/test.txt");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.createProvenanceRecord(
                files, "test", "test", List.of(), "SHA256",
                "http://localhost:8080/api/provenance", null, "trace4eo")
        );
        assertTrue(exception.getMessage().contains("--keycloak-url"));
    }
}
