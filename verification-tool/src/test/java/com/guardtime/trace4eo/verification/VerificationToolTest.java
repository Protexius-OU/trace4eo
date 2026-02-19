package com.guardtime.trace4eo.verification;

import com.guardtime.trace4eo.provenance.Container;
import com.guardtime.trace4eo.provenance.HashAlgorithm;
import com.guardtime.trace4eo.provenance.ProvenanceJsonMapper;
import com.guardtime.trace4eo.provenance.ProvenanceSignature;
import com.guardtime.trace4eo.provenance.io.json.JsonContainerReader;
import com.guardtime.trace4eo.provenance.io.json.JsonContainerWriter;
import com.guardtime.trace4eo.provenance.io.zip.ZipContainerWriter;
import com.guardtime.trace4eo.provenance.record.FileHashInfo;
import com.guardtime.trace4eo.provenance.record.FilesInfo;
import com.guardtime.trace4eo.provenance.record.FilesInfoBuilder;
import com.guardtime.trace4eo.provenance.record.Manifest;
import com.guardtime.trace4eo.provenance.record.ManifestBuilder;
import com.guardtime.trace4eo.provenance.record.Metadata;
import com.guardtime.trace4eo.provenance.record.ProvenanceRecord;
import com.guardtime.trace4eo.provenance.record.ProvenanceRecordBuilder;
import com.guardtime.trace4eo.provenance.verification.ProvenanceVerificationResult;
import com.guardtime.trace4eo.provenance.verification.ProvenanceVerificationService;
import com.guardtime.trace4eo.provenance.verification.VerificationStepName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.SequencedSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerificationToolTest {

    private static final String TEST_FILE = "src/test/resources/test.txt";
    private static final String OTHER_FILE = "src/test/resources/other.txt";
    private static final String SIGNATURE_FILE = "src/test/resources/signature.json";
    private static final String PROVENANCE_RECORD_FILE = "src/test/resources/provenance-record.json";
    private static final String INVALID_SIGNATURE_PROVENANCE_RECORD_FILE = "src/test/resources/invalid-signature-provenance-record.json";
    private static final String INVALID_CONTENTS_PROVENANCE_RECORD_FILE = "src/test/resources/invalid-contents-provenance-record.json";
    private static final String HASHES_FILE = "src/test/resources/hashes.txt";
    private static final String WRONG_HASHES_FILE = "src/test/resources/wrong-hashes.txt";
    private static final String INVALID_HASHES_FILE = "src/test/resources/invalid-hashes.txt";

    private final ProvenanceJsonMapper provenanceJsonMapper = new ProvenanceJsonMapper();
    private final ProvenanceVerificationService verificationService = new ProvenanceVerificationService();
    private final VerificationTool verificationTool = new VerificationTool(verificationService, provenanceJsonMapper);

    @Test
    void verifyValidSignature() {
        ProvenanceVerificationResult result = verificationTool.verify(Path.of(TEST_FILE), Path.of(SIGNATURE_FILE));
        assertTrue(result.status());
    }

    @Test
    void verifyInvalidSignature() {
        ProvenanceVerificationResult result = verificationTool.verify(Path.of(OTHER_FILE), Path.of(SIGNATURE_FILE));
        assertFalse(result.status());
    }

    // SHA-256 of empty bytes (content of test.txt in provenance-record.json), base64-encoded
    private static final String EMPTY_SHA256_B64 = "47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU=";
    // base64 of 32 zero bytes
    private static final String WRONG_SHA256_B64 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    @Test
    void verifyProvenanceRecord() {
        List<ProvenanceVerificationResult> results = verificationTool.verify(Path.of(PROVENANCE_RECORD_FILE), null, null);
        assertEquals(1, results.size());
        ProvenanceVerificationResult result = results.getFirst();
        assertTrue(result.status(), () -> String.format(
            "Verification failed: %s - %s", result.error(), result.errorMessage()));
    }

    @Test
    void verifyProvenanceRecordZip(@TempDir Path tempDir) throws IOException {
        Container container = new JsonContainerReader(provenanceJsonMapper)
            .read(Path.of(PROVENANCE_RECORD_FILE));
        Path zipPath = tempDir.resolve("provenance-record.zip");
        new ZipContainerWriter(provenanceJsonMapper)
            .writeTo(container, Files.newOutputStream(zipPath));

        List<ProvenanceVerificationResult> results = verificationTool.verify(zipPath, null, null);
        assertEquals(1, results.size());
        ProvenanceVerificationResult result = results.getFirst();
        assertTrue(result.status(), () -> String.format(
            "Verification failed: %s - %s", result.error(), result.errorMessage()));
    }

    @Test
    void verifyProvenanceRecordWithInvalidSignature() {
        List<ProvenanceVerificationResult> results = verificationTool.verify(
            Path.of(INVALID_SIGNATURE_PROVENANCE_RECORD_FILE), null, null);
        assertEquals(1, results.size());
        assertFalse(results.getFirst().status());
        assertTrue(results.getFirst().steps().stream()
            .anyMatch(s -> s.name() == VerificationStepName.SIGNATURE
                && !s.status()),
            "Expected a failed SIGNATURE verification step");
    }

    @Test
    void verifyProvenanceRecordWithInvalidContents() {
        List<ProvenanceVerificationResult> results = verificationTool.verify(
            Path.of(INVALID_CONTENTS_PROVENANCE_RECORD_FILE), null, null);
        assertEquals(1, results.size());
        assertFalse(results.getFirst().status());
        assertTrue(results.getFirst().steps().stream()
            .anyMatch(s -> s.name() == VerificationStepName.FILES_INFO
                && !s.status()),
            "Expected a failed FILES_INFO verification step");
    }

    @Test
    void verifyProvenanceRecordWithCorrectInlineHash() {
        List<ProvenanceVerificationResult> results = verificationTool.verify(
            Path.of(PROVENANCE_RECORD_FILE), "test.txt=" + EMPTY_SHA256_B64, null);
        assertEquals(1, results.size());
        ProvenanceVerificationResult result = results.getFirst();
        assertTrue(result.status(), () -> String.format(
            "Verification failed: %s - %s", result.error(), result.errorMessage()));
        assertTrue(result.steps().stream()
            .anyMatch(s -> s.name() == VerificationStepName.FILE_CONTENTS && s.status()),
            "Expected a passing FILE_CONTENTS step");
    }

    @Test
    void verifyProvenanceRecordWithWrongInlineHash() {
        List<ProvenanceVerificationResult> results = verificationTool.verify(
            Path.of(PROVENANCE_RECORD_FILE), "test.txt=" + WRONG_SHA256_B64, null);
        assertEquals(1, results.size());
        ProvenanceVerificationResult result = results.getFirst();
        assertFalse(result.status());
        assertTrue(result.steps().stream()
            .anyMatch(s -> s.name() == VerificationStepName.FILE_CONTENTS && !s.status()),
            "Expected a failed FILE_CONTENTS step");
    }

    @Test
    void verifyProvenanceRecordWithCorrectHashFile() {
        List<ProvenanceVerificationResult> results = verificationTool.verify(
            Path.of(PROVENANCE_RECORD_FILE), null, Path.of(HASHES_FILE));
        assertEquals(1, results.size());
        ProvenanceVerificationResult result = results.getFirst();
        assertTrue(result.status(), () -> String.format(
            "Verification failed: %s - %s", result.error(), result.errorMessage()));
        assertTrue(result.steps().stream()
            .anyMatch(s -> s.name() == VerificationStepName.FILE_CONTENTS && s.status()),
            "Expected a passing FILE_CONTENTS step");
    }

    @Test
    void verifyProvenanceRecordWithWrongHashFile() {
        List<ProvenanceVerificationResult> results = verificationTool.verify(
            Path.of(PROVENANCE_RECORD_FILE), null, Path.of(WRONG_HASHES_FILE));
        assertEquals(1, results.size());
        ProvenanceVerificationResult result = results.getFirst();
        assertFalse(result.status());
        assertTrue(result.steps().stream()
            .anyMatch(s -> s.name() == VerificationStepName.FILE_CONTENTS && !s.status()),
            "Expected a failed FILE_CONTENTS step");
    }

    @Test
    void verifyProvenanceRecordWithInlineAndHashFile() {
        List<ProvenanceVerificationResult> results = verificationTool.verify(
            Path.of(PROVENANCE_RECORD_FILE), "test.txt=" + EMPTY_SHA256_B64, Path.of(HASHES_FILE));
        assertEquals(1, results.size());
        ProvenanceVerificationResult result = results.getFirst();
        assertTrue(result.status(), () -> String.format(
            "Verification failed: %s - %s", result.error(), result.errorMessage()));
    }

    @Test
    void verifyProvenanceRecordSkipsUnrecognizedEntriesInHashFile() {
        // hashes.txt has test.txt (in the record) and data.txt (not in the record).
        // Only test.txt should be verified; data.txt should be silently skipped.
        List<ProvenanceVerificationResult> results = verificationTool.verify(
            Path.of(PROVENANCE_RECORD_FILE), null, Path.of(HASHES_FILE));
        assertEquals(1, results.size());
        assertTrue(results.getFirst().steps().stream()
            .filter(s -> s.name() == VerificationStepName.FILE_CONTENTS)
            .anyMatch(s -> s.status() && s.description().contains("1 of 1")),
            "Expected '1 of 1 file content hashes verified' (data.txt skipped)");
    }

    @Test
    void verifyProvenanceRecordFailsForInvalidEntryInHashFile() {
        // invalid-hashes.txt has two valid entries followed by one with invalid base64.
        assertThrows(IllegalArgumentException.class,
            () -> verificationTool.verify(Path.of(PROVENANCE_RECORD_FILE), null, Path.of(INVALID_HASHES_FILE)));
    }

    @Test
    void verifyProvenanceRecordWithInvalidHashFormat() {
        assertThrows(IllegalArgumentException.class,
            () -> verificationTool.verify(Path.of(PROVENANCE_RECORD_FILE), "test.txt", null));
    }

    @Test
    void verifyFailsForNonExistentTextFile() {
        assertThrows(IllegalArgumentException.class,
            () -> verificationTool.verify(Path.of("nonexistent.txt"), Path.of(SIGNATURE_FILE)));
    }

    @Test
    void verifyProvenanceRecordFailsForNonExistentFile() {
        assertThrows(IllegalArgumentException.class,
            () -> verificationTool.verify(Path.of("nonexistent.json"), null, null));
    }

    @Test
    void verifyProvenanceRecordFailsForNonExistentHashManifest(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("missing.txt");
        assertThrows(IllegalArgumentException.class,
            () -> verificationTool.verify(Path.of(PROVENANCE_RECORD_FILE), null, missing));
    }

    @Test
    void verifyProvenanceRecordFailsForInvalidBase64InHash() {
        assertThrows(IllegalArgumentException.class,
            () -> verificationTool.verify(Path.of(PROVENANCE_RECORD_FILE), "test.txt=not!valid!!base64", null));
    }

    @Test
    void verifyProvenanceRecordFailsForBlankPathInHash() {
        assertThrows(IllegalArgumentException.class,
            () -> verificationTool.verify(Path.of(PROVENANCE_RECORD_FILE), "=" + EMPTY_SHA256_B64, null));
    }

    @Test
    void verifyProvenanceRecordWithNonDefaultAlgorithm(@TempDir Path tempDir) throws IOException {
        Metadata metadata = new Metadata("data-id", "container-type", List.of());
        FilesInfo filesInfo = new FilesInfoBuilder(HashAlgorithm.SHA512)
            .addFile(Path.of(TEST_FILE))
            .build();
        Manifest manifest = new ManifestBuilder(HashAlgorithm.SHA512, provenanceJsonMapper)
            .withFilesInfo(filesInfo)
            .withMetadata(metadata)
            .build();
        ProvenanceSignature signature = provenanceJsonMapper.readValue(
            Path.of(SIGNATURE_FILE), ProvenanceSignature.class);
        ProvenanceRecord record = new ProvenanceRecordBuilder()
            .withMetadata(metadata)
            .withFilesInfo(filesInfo)
            .withManifest(manifest)
            .withSignature(signature)
            .build();

        Path jsonPath = tempDir.resolve("sha512-record.json");
        SequencedSet<ProvenanceRecord> records = new LinkedHashSet<>();
        records.add(record);
        new JsonContainerWriter(provenanceJsonMapper)
            .writeTo(new Container(record.id(), records), Files.newOutputStream(jsonPath));

        FileHashInfo fileHashInfo = record.filesInfo().files().iterator().next();
        String fileHash = fileHashInfo.path() + "=" + Base64.getEncoder().encodeToString(fileHashInfo.hashValue());

        List<ProvenanceVerificationResult> results = verificationTool.verify(jsonPath, fileHash, null);
        assertEquals(1, results.size());
        assertTrue(results.getFirst().steps().stream()
            .anyMatch(s -> s.name() == VerificationStepName.FILE_CONTENTS && s.status()),
            "Expected FILE_CONTENTS to pass for SHA-512 record");
    }

}
