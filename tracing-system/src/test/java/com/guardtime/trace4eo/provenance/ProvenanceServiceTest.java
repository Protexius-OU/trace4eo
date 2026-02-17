package com.guardtime.trace4eo.provenance;

import com.guardtime.trace4eo.provenance.record.FilesInfo;
import com.guardtime.trace4eo.provenance.record.Manifest;
import com.guardtime.trace4eo.provenance.record.Metadata;
import com.guardtime.trace4eo.provenance.record.ProvenanceRecord;
import com.guardtime.trace4eo.provenance.verification.ProvenanceVerificationResult;
import com.guardtime.trace4eo.provenance.verification.ProvenanceVerificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProvenanceServiceTest {

    @Mock
    private ProvenanceRegistry provenanceRegistry;

    @Mock
    private ProvenanceJsonMapper provenanceJsonMapper;

    @Mock
    private ProvenanceVerificationService provenanceVerificationService;

    private ProvenanceService provenanceService;

    @BeforeEach
    void setUp() {
        provenanceService = new ProvenanceService(provenanceRegistry, provenanceJsonMapper, provenanceVerificationService);
    }

    @Test
    void getDelegatesToRegistry() {
        UUID id = UUID.randomUUID();
        ProvenanceRecord expectedRecord = createTestRecord(id);
        when(provenanceRegistry.get(id)).thenReturn(Optional.of(expectedRecord));

        Optional<ProvenanceRecord> result = provenanceService.get(id);

        assertTrue(result.isPresent());
        assertSame(expectedRecord, result.get());
        verify(provenanceRegistry).get(id);
    }

    @Test
    void getReturnsEmptyWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(provenanceRegistry.get(id)).thenReturn(Optional.empty());

        Optional<ProvenanceRecord> result = provenanceService.get(id);

        assertTrue(result.isEmpty());
    }

    @Test
    void saveSignatureSerializesAndCallsRegistry() {
        UUID id = UUID.randomUUID();
        Instant signingTime = Instant.parse("2024-01-15T10:30:00Z");
        byte[] signatureBytes = new byte[]{1, 2, 3, 4, 5};
        ProvenanceRecord record = createTestRecordWithSignature(id, signingTime, signatureBytes);
        String signatureJson = """
            {"bytes":"AQIDBAU=","signingTime":"2024-01-15T10:30:00Z","hashAlgorithm":"SHA256"}""";

        when(provenanceJsonMapper.writeValueAsString(record.signature())).thenReturn(signatureJson);

        provenanceService.saveSignature(record);

        verify(provenanceRegistry).addSignature(eq(id), eq(signingTime), eq(signatureJson.getBytes(java.nio.charset.StandardCharsets.UTF_8)), eq((String) null));
    }

    @Test
    void saveProvenanceRecordSerializesAndCallsRegistry() {
        UUID id = UUID.randomUUID();
        Instant signingTime = Instant.parse("2024-01-15T10:30:00Z");
        ProvenanceRecord record = createTestRecordWithSignature(id, signingTime, new byte[]{1});

        when(provenanceJsonMapper.writeValueAsString(record.manifest())).thenReturn("{\"manifest\":true}");
        when(provenanceJsonMapper.writeValueAsString(record.metadata())).thenReturn("{\"metadata\":true}");
        when(provenanceJsonMapper.writeValueAsString(record.filesInfo())).thenReturn("{\"files\":true}");

        provenanceService.saveProvenanceRecord(record);

        verify(provenanceRegistry).addProvenanceRecord(
            eq(id),
            eq("{\"manifest\":true}"),
            eq("{\"metadata\":true}"),
            eq("{\"files\":true}"),
            eq(signingTime)
        );
    }

    @Test
    void saveProvenanceRecordHandlesNullFilesInfo() {
        UUID id = UUID.randomUUID();
        Instant signingTime = Instant.now();
        ProvenanceRecord record = createTestRecordWithNullFilesInfo(id, signingTime);

        when(provenanceJsonMapper.writeValueAsString(record.manifest())).thenReturn("{\"manifest\":true}");
        when(provenanceJsonMapper.writeValueAsString(record.metadata())).thenReturn("{\"metadata\":true}");
        when(provenanceJsonMapper.writeValueAsString(null)).thenReturn(null);

        provenanceService.saveProvenanceRecord(record);

        verify(provenanceRegistry).addProvenanceRecord(
            eq(id),
            eq("{\"manifest\":true}"),
            eq("{\"metadata\":true}"),
            eq((String) null),
            eq(signingTime)
        );
    }

    @Test
    void findAllReturnsPagedResponse() {
        List<String> dataTypes = List.of("type-a");
        String dataId = "satellite";
        List<String> signerIdentities = List.of("user@example.com");
        List<ProvenanceRecord> records = List.of(createTestRecord(UUID.randomUUID()));

        when(provenanceRegistry.findAll(0, 20, dataTypes, dataId, signerIdentities)).thenReturn(records);
        when(provenanceRegistry.count(dataTypes, dataId, signerIdentities)).thenReturn(45L);

        PagedResponse<ProvenanceRecord> result = provenanceService.findAll(0, 20, dataTypes, dataId, signerIdentities);

        assertEquals(records, result.content());
        assertEquals(45L, result.totalElements());
        assertEquals(3, result.totalPages());
        assertEquals(0, result.page());
        assertEquals(20, result.size());
    }

    @Test
    void findAllReturnsEmptyPage() {
        when(provenanceRegistry.findAll(0, 20, null, null, null)).thenReturn(List.of());
        when(provenanceRegistry.count(null, null, null)).thenReturn(0L);

        PagedResponse<ProvenanceRecord> result = provenanceService.findAll(0, 20, null, null, null);

        assertTrue(result.content().isEmpty());
        assertEquals(0, result.totalElements());
        assertEquals(0, result.totalPages());
    }

    @Test
    void verifyProvenanceRecordCallsVerificationServiceAndLogsResult() {
        UUID id = UUID.randomUUID();
        ProvenanceRecord record = createTestRecord(id);
        ProvenanceVerificationResult expectedResult = new ProvenanceVerificationResult();

        when(provenanceVerificationService.verify(record)).thenReturn(expectedResult);

        ProvenanceVerificationResult result = provenanceService.verifyProvenanceRecord(record);

        assertSame(expectedResult, result);
        verify(provenanceVerificationService).verify(record);
    }

    @Test
    void verifyProvenanceRecordAddsVerificationLog() {
        UUID id = UUID.randomUUID();
        ProvenanceRecord record = createTestRecord(id);
        ProvenanceVerificationResult verificationResult = new ProvenanceVerificationResult();

        when(provenanceVerificationService.verify(record)).thenReturn(verificationResult);

        provenanceService.verifyProvenanceRecord(record);

        ArgumentCaptor<Instant> instantCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(provenanceRegistry).addVerificationLog(eq(id), instantCaptor.capture(), eq(true));

        Instant capturedInstant = instantCaptor.getValue();
        assertTrue(capturedInstant.isBefore(Instant.now().plusSeconds(1)));
        assertTrue(capturedInstant.isAfter(Instant.now().minusSeconds(5)));
    }

    @Test
    void verifyProvenanceRecordLogsFailedStatus() {
        UUID id = UUID.randomUUID();
        ProvenanceRecord record = createTestRecord(id);
        ProvenanceVerificationResult verificationResult = new ProvenanceVerificationResult(null, "Test failure");

        when(provenanceVerificationService.verify(record)).thenReturn(verificationResult);

        provenanceService.verifyProvenanceRecord(record);

        verify(provenanceRegistry).addVerificationLog(eq(id), any(Instant.class), eq(false));
    }

    private ProvenanceRecord createTestRecord(UUID id) {
        return createTestRecordWithSignature(id, Instant.now(), new byte[]{1, 2, 3});
    }

    private ProvenanceRecord createTestRecordWithSignature(UUID id, Instant signingTime, byte[] signatureBytes) {
        Metadata metadata = new Metadata("data-id", "test-type", Collections.emptyList());
        Manifest manifest = new Manifest("1", null, null);
        FilesInfo filesInfo = new FilesInfo(null, null);
        ProvenanceSignature signature = new ProvenanceSignature(signatureBytes, signingTime, HashAlgorithm.SHA256);
        return new ProvenanceRecordImpl(id, metadata, filesInfo, manifest, signature);
    }

    private ProvenanceRecord createTestRecordWithNullFilesInfo(UUID id, Instant signingTime) {
        Metadata metadata = new Metadata("data-id", "test-type", Collections.emptyList());
        Manifest manifest = new Manifest("1", null, null);
        ProvenanceSignature signature = new ProvenanceSignature(new byte[]{1}, signingTime, HashAlgorithm.SHA256);
        return new ProvenanceRecordImpl(id, metadata, null, manifest, signature);
    }
}
