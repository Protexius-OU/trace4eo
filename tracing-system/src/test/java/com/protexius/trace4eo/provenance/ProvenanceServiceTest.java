package com.protexius.trace4eo.provenance;

import com.protexius.trace4eo.provenance.record.FileHashInfo;
import com.protexius.trace4eo.provenance.record.FilesInfo;
import com.protexius.trace4eo.provenance.record.Manifest;
import com.protexius.trace4eo.provenance.record.Metadata;
import com.protexius.trace4eo.provenance.record.Predecessor;
import com.protexius.trace4eo.provenance.record.ProvenanceRecord;
import com.protexius.trace4eo.provenance.verification.ProvenanceVerificationResult;
import com.protexius.trace4eo.provenance.verification.ProvenanceVerificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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

        provenanceService.saveProvenanceRecord(record, "uploader@example.com");

        verify(provenanceRegistry).addProvenanceRecord(
            eq(id),
            eq("{\"manifest\":true}"),
            eq("{\"metadata\":true}"),
            eq("{\"files\":true}"),
            eq(signingTime),
            eq("uploader@example.com")
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

        provenanceService.saveProvenanceRecord(record, null);

        verify(provenanceRegistry).addProvenanceRecord(
            eq(id),
            eq("{\"manifest\":true}"),
            eq("{\"metadata\":true}"),
            eq((String) null),
            eq(signingTime),
            eq((String) null)
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

    @Test
    void saveWithNullIdThrows() {
        ProvenanceRecord record = new ProvenanceRecordImpl(null,
            new Metadata("data-id", "test-type", Collections.emptyList(), null),
            new FilesInfo(null, null), new Manifest("1", null, null),
            new ProvenanceSignature(new byte[]{1}, Instant.now(), HashAlgorithm.SHA256), null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> provenanceService.save(record, null));
        assertEquals("Record ID must not be null", ex.getMessage());
    }

    @Test
    void saveWithNullMetadataThrows() {
        ProvenanceRecord record = new ProvenanceRecordImpl(UUID.randomUUID(),
            null, new FilesInfo(null, null), new Manifest("1", null, null),
            new ProvenanceSignature(new byte[]{1}, Instant.now(), HashAlgorithm.SHA256), null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> provenanceService.save(record, null));
        assertEquals("Metadata must not be null", ex.getMessage());
    }

    @Test
    void saveWithNullDataIdThrows() {
        ProvenanceRecord record = new ProvenanceRecordImpl(UUID.randomUUID(),
            new Metadata(null, "test-type", Collections.emptyList(), null),
            new FilesInfo(null, null), new Manifest("1", null, null),
            new ProvenanceSignature(new byte[]{1}, Instant.now(), HashAlgorithm.SHA256), null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> provenanceService.save(record, null));
        assertEquals("dataId must not be null or blank", ex.getMessage());
    }

    @Test
    void saveWithBlankDataTypeThrows() {
        ProvenanceRecord record = new ProvenanceRecordImpl(UUID.randomUUID(),
            new Metadata("data-id", "  ", Collections.emptyList(), null),
            new FilesInfo(null, null), new Manifest("1", null, null),
            new ProvenanceSignature(new byte[]{1}, Instant.now(), HashAlgorithm.SHA256), null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> provenanceService.save(record, null));
        assertEquals("dataType must not be null or blank", ex.getMessage());
    }

    @Test
    void saveWithNullSignatureThrows() {
        ProvenanceRecord record = new ProvenanceRecordImpl(UUID.randomUUID(),
            new Metadata("data-id", "test-type", Collections.emptyList(), null),
            new FilesInfo(null, null), new Manifest("1", null, null), null, null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> provenanceService.save(record, null));
        assertEquals("Signature must not be null", ex.getMessage());
    }

    @Test
    void saveWithNullManifestThrows() {
        ProvenanceRecord record = new ProvenanceRecordImpl(UUID.randomUUID(),
            new Metadata("data-id", "test-type", Collections.emptyList(), null),
            new FilesInfo(null, null), null,
            new ProvenanceSignature(new byte[]{1}, Instant.now(), HashAlgorithm.SHA256), null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> provenanceService.save(record, null));
        assertEquals("Manifest must not be null", ex.getMessage());
    }

    @Test
    void saveWithDuplicateIdThrows() {
        UUID id = UUID.randomUUID();
        ProvenanceRecord record = createTestRecord(id);
        when(provenanceRegistry.get(id)).thenReturn(Optional.of(record));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> provenanceService.save(record, null));
        assertTrue(ex.getMessage().contains(id.toString()));
        verify(provenanceRegistry, never()).addSignature(any(), any(), any(), any());
    }

    @Test
    void saveWithNullPredecessorsSkipsValidation() {
        UUID id = UUID.randomUUID();
        Metadata metadata = new Metadata("data-id", "test-type", null, null);
        Manifest manifest = new Manifest("1", null, null);
        FilesInfo filesInfo = new FilesInfo(null, null);
        ProvenanceSignature signature = new ProvenanceSignature(new byte[]{1, 2, 3}, Instant.now(), HashAlgorithm.SHA256);
        ProvenanceRecord record = new ProvenanceRecordImpl(id, metadata, filesInfo, manifest, signature, null);
        when(provenanceRegistry.get(id)).thenReturn(Optional.empty());
        when(provenanceJsonMapper.writeValueAsString(any())).thenReturn("{}");

        provenanceService.save(record, null);

        verify(provenanceRegistry, never()).findMissing(any());
        verify(provenanceRegistry).addSignature(any(), any(), any(), any());
        verify(provenanceRegistry).addProvenanceRecord(any(), any(), any(), any(), any(), any());
    }

    @Test
    void saveWithNoPredecessorsSkipsValidation() {
        UUID id = UUID.randomUUID();
        ProvenanceRecord record = createTestRecord(id);
        when(provenanceRegistry.get(id)).thenReturn(Optional.empty());
        when(provenanceJsonMapper.writeValueAsString(any())).thenReturn("{}");

        provenanceService.save(record, null);

        verify(provenanceRegistry, never()).findMissing(any());
        verify(provenanceRegistry).addSignature(any(), any(), any(), any());
        verify(provenanceRegistry).addProvenanceRecord(any(), any(), any(), any(), any(), any());
    }

    @Test
    void saveWithValidPredecessorsDelegatesToRegistry() {
        UUID id = UUID.randomUUID();
        UUID predecessorId = UUID.randomUUID();
        ProvenanceRecord record = createTestRecordWithPredecessors(id, List.of(new Predecessor(predecessorId)));
        when(provenanceRegistry.get(id)).thenReturn(Optional.empty());
        when(provenanceRegistry.findMissing(List.of(predecessorId))).thenReturn(List.of());
        when(provenanceJsonMapper.writeValueAsString(any())).thenReturn("{}");

        provenanceService.save(record, "uploader@example.com");

        verify(provenanceRegistry).findMissing(List.of(predecessorId));
        verify(provenanceRegistry).addSignature(any(), any(), any(), any());
        verify(provenanceRegistry).addProvenanceRecord(any(), any(), any(), any(), any(), eq("uploader@example.com"));
    }

    @Test
    void saveWithNonExistingPredecessorsThrowsIllegalArgumentException() {
        UUID id = UUID.randomUUID();
        UUID missingId = UUID.randomUUID();
        ProvenanceRecord record = createTestRecordWithPredecessors(id, List.of(new Predecessor(missingId)));
        when(provenanceRegistry.get(id)).thenReturn(Optional.empty());
        when(provenanceRegistry.findMissing(List.of(missingId))).thenReturn(List.of(missingId));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> provenanceService.save(record, null));

        assertTrue(ex.getMessage().contains(missingId.toString()));
        verify(provenanceRegistry, never()).addSignature(any(), any(), any(), any());
        verify(provenanceRegistry, never()).addProvenanceRecord(any(), any(), any(), any(), any(), any());
    }

    private ProvenanceRecord createTestRecordWithPredecessors(UUID id, List<Predecessor> predecessors) {
        Metadata metadata = new Metadata("data-id", "test-type", predecessors, null);
        Manifest manifest = new Manifest("1", null, null);
        FilesInfo filesInfo = new FilesInfo(null, null);
        ProvenanceSignature signature = new ProvenanceSignature(new byte[]{1, 2, 3}, Instant.now(), HashAlgorithm.SHA256);
        return new ProvenanceRecordImpl(id, metadata, filesInfo, manifest, signature, null);
    }

    private ProvenanceRecord createTestRecord(UUID id) {
        return createTestRecordWithSignature(id, Instant.now(), new byte[]{1, 2, 3});
    }

    private ProvenanceRecord createTestRecordWithSignature(UUID id, Instant signingTime, byte[] signatureBytes) {
        Metadata metadata = new Metadata("data-id", "test-type", Collections.emptyList(), null);
        Manifest manifest = new Manifest("1", null, null);
        FilesInfo filesInfo = new FilesInfo(null, null);
        ProvenanceSignature signature = new ProvenanceSignature(signatureBytes, signingTime, HashAlgorithm.SHA256);
        return new ProvenanceRecordImpl(id, metadata, filesInfo, manifest, signature, null);
    }

    @Test
    void verifyFileHashesMatchedWhenHashMatches() {
        byte[] knownHash = {1, 2, 3, 4, 5, 6, 7, 8};
        FileHashInfo fileHashInfo = new FileHashInfo("image.tif", HashAlgorithm.SHA256, knownHash);
        ProvenanceRecord record = createTestRecordWithFiles(UUID.randomUUID(), fileHashInfo);
        FileHashInput input = new FileHashInput("image.tif", Base64.getEncoder().encodeToString(knownHash));
        when(provenanceVerificationService.verifyWithFileHashes(any(), any())).thenReturn(new ProvenanceVerificationResult());

        FileVerificationResponse response = provenanceService.verifyFileHashes(record, List.of(input));

        assertEquals(1, response.fileResults().size());
        FileCheckResult result = response.fileResults().getFirst();
        assertEquals("image.tif", result.filename());
        assertEquals("image.tif", result.recordPath());
        assertEquals(FileCheckStatus.MATCHED, result.status());
    }

    @Test
    void verifyFileHashesMatchesBySuffixPath() {
        byte[] knownHash = {10, 20, 30};
        FileHashInfo fileHashInfo = new FileHashInfo("data/subdir/image.tif", HashAlgorithm.SHA256, knownHash);
        ProvenanceRecord record = createTestRecordWithFiles(UUID.randomUUID(), fileHashInfo);
        FileHashInput input = new FileHashInput("image.tif", Base64.getEncoder().encodeToString(knownHash));
        when(provenanceVerificationService.verifyWithFileHashes(any(), any())).thenReturn(new ProvenanceVerificationResult());

        FileVerificationResponse response = provenanceService.verifyFileHashes(record, List.of(input));

        FileCheckResult result = response.fileResults().getFirst();
        assertEquals("data/subdir/image.tif", result.recordPath());
        assertEquals(FileCheckStatus.MATCHED, result.status());
    }

    @Test
    void verifyFileHashesMismatchWhenHashDiffers() {
        byte[] storedHash = {1, 2, 3, 4};
        byte[] wrongHash = {9, 8, 7, 6};
        FileHashInfo fileHashInfo = new FileHashInfo("image.tif", HashAlgorithm.SHA256, storedHash);
        ProvenanceRecord record = createTestRecordWithFiles(UUID.randomUUID(), fileHashInfo);
        FileHashInput input = new FileHashInput("image.tif", Base64.getEncoder().encodeToString(wrongHash));
        when(provenanceVerificationService.verifyWithFileHashes(any(), any())).thenReturn(new ProvenanceVerificationResult());

        FileVerificationResponse response = provenanceService.verifyFileHashes(record, List.of(input));

        FileCheckResult result = response.fileResults().getFirst();
        assertEquals("image.tif", result.recordPath());
        assertEquals(FileCheckStatus.MISMATCH, result.status());
    }

    @Test
    void verifyFileHashesNotInRecordWhenFilenameUnmatched() {
        FileHashInfo fileHashInfo = new FileHashInfo("other.tif", HashAlgorithm.SHA256, new byte[]{1});
        ProvenanceRecord record = createTestRecordWithFiles(UUID.randomUUID(), fileHashInfo);
        FileHashInput input = new FileHashInput("missing.tif", Base64.getEncoder().encodeToString(new byte[]{1}));
        when(provenanceVerificationService.verifyWithFileHashes(any(), any())).thenReturn(new ProvenanceVerificationResult());

        FileVerificationResponse response = provenanceService.verifyFileHashes(record, List.of(input));

        FileCheckResult result = response.fileResults().getFirst();
        assertEquals("missing.tif", result.filename());
        assertSame(null, result.recordPath());
        assertEquals(FileCheckStatus.NOT_IN_RECORD, result.status());
    }

    @Test
    void verifyFileHashesWithNullFilesInfoReturnsNotInRecord() {
        ProvenanceRecord record = createTestRecordWithNullFilesInfo(UUID.randomUUID(), Instant.now());
        FileHashInput input = new FileHashInput("image.tif", Base64.getEncoder().encodeToString(new byte[]{1}));
        when(provenanceVerificationService.verifyWithFileHashes(any(), any())).thenReturn(new ProvenanceVerificationResult());

        FileVerificationResponse response = provenanceService.verifyFileHashes(record, List.of(input));

        assertEquals(1, response.fileResults().size());
        assertEquals(FileCheckStatus.NOT_IN_RECORD, response.fileResults().getFirst().status());
    }

    @Test
    void verifyFileHashesWithEmptyFilesCollectionReturnsNotInRecord() {
        Metadata metadata = new Metadata("data-id", "test-type", Collections.emptyList(), null);
        Manifest manifest = new Manifest("1", null, null);
        FilesInfo filesInfo = new FilesInfo(null, null);
        ProvenanceSignature signature = new ProvenanceSignature(new byte[]{1}, Instant.now(), HashAlgorithm.SHA256);
        ProvenanceRecord record = new ProvenanceRecordImpl(UUID.randomUUID(), metadata, filesInfo, manifest, signature, null);
        FileHashInput input = new FileHashInput("image.tif", Base64.getEncoder().encodeToString(new byte[]{1}));
        when(provenanceVerificationService.verifyWithFileHashes(any(), any())).thenReturn(new ProvenanceVerificationResult());

        FileVerificationResponse response = provenanceService.verifyFileHashes(record, List.of(input));

        assertEquals(FileCheckStatus.NOT_IN_RECORD, response.fileResults().getFirst().status());
    }

    private ProvenanceRecord createTestRecordWithFiles(UUID id, FileHashInfo... files) {
        Metadata metadata = new Metadata("data-id", "test-type", Collections.emptyList(), null);
        Manifest manifest = new Manifest("1", null, null);
        FilesInfo filesInfo = new FilesInfo(new LinkedHashSet<>(List.of(files)), null);
        ProvenanceSignature signature = new ProvenanceSignature(new byte[]{1}, Instant.now(), HashAlgorithm.SHA256);
        return new ProvenanceRecordImpl(id, metadata, filesInfo, manifest, signature, null);
    }

    private ProvenanceRecord createTestRecordWithNullFilesInfo(UUID id, Instant signingTime) {
        Metadata metadata = new Metadata("data-id", "test-type", Collections.emptyList(), null);
        Manifest manifest = new Manifest("1", null, null);
        ProvenanceSignature signature = new ProvenanceSignature(new byte[]{1}, signingTime, HashAlgorithm.SHA256);
        return new ProvenanceRecordImpl(id, metadata, null, manifest, signature, null);
    }
}
