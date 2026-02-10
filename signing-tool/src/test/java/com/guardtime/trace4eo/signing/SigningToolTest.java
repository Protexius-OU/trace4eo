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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SigningToolTest {

    private SigningTool signingTool;
    private ProvenanceJsonMapper provenanceJsonMapper;

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

        signingTool = new SigningTool(mockSigningService, provenanceJsonMapper, "test-token");
    }

    @Test
    void createProvenanceRecord() throws IOException {
        List<String> files = List.of("src/test/resources/test.txt");
        ProvenanceRecord result = signingTool.createProvenanceRecord(
            files, "test", "test", List.of(), "SHA256"
        );
        assertNotNull(result);
        assertNotNull(result.id());
        assertNotNull(result.filesInfo());
        assertNotNull(result.metadata());
        assertNotNull(result.manifest());
    }

    @Test
    void createProvenanceRecord_nullFiles_throws() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.createProvenanceRecord(null, "test", "test", List.of(), "SHA256")
        );
        assertTrue(exception.getMessage().contains("--files"));
    }

    @Test
    void createProvenanceRecord_emptyFiles_throws() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.createProvenanceRecord(List.of(), "test", "test", List.of(), "SHA256")
        );
        assertTrue(exception.getMessage().contains("--files"));
    }

    @Test
    void createProvenanceRecord_blankProvenanceRecordType_throws() {
        List<String> files = List.of("src/test/resources/test.txt");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.createProvenanceRecord(files, " ", "test", List.of(), "SHA256")
        );
        assertTrue(exception.getMessage().contains("--provenance-record-type"));
    }

    @Test
    void createProvenanceRecord_nullProvenanceRecordType_throws() {
        List<String> files = List.of("src/test/resources/test.txt");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.createProvenanceRecord(files, null, "test", List.of(), "SHA256")
        );
        assertTrue(exception.getMessage().contains("--provenance-record-type"));
    }

    @Test
    void createProvenanceRecord_blankDataId_throws() {
        List<String> files = List.of("src/test/resources/test.txt");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.createProvenanceRecord(files, "test", " ", List.of(), "SHA256")
        );
        assertTrue(exception.getMessage().contains("--data-id"));
    }

    @Test
    void createProvenanceRecord_nullDataId_throws() {
        List<String> files = List.of("src/test/resources/test.txt");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.createProvenanceRecord(files, "test", null, List.of(), "SHA256")
        );
        assertTrue(exception.getMessage().contains("--data-id"));
    }
}
