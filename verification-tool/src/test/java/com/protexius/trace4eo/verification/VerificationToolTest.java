package com.protexius.trace4eo.verification;

import com.protexius.trace4eo.provenance.Container;
import com.protexius.trace4eo.provenance.HashAlgorithm;
import com.protexius.trace4eo.provenance.ProvenanceJsonMapper;
import com.protexius.trace4eo.provenance.ProvenanceSignature;
import com.protexius.trace4eo.provenance.io.json.JsonContainerReader;
import com.protexius.trace4eo.provenance.io.json.JsonContainerWriter;
import com.protexius.trace4eo.provenance.io.zip.ZipContainerWriter;
import com.protexius.trace4eo.provenance.record.FileHashInfo;
import com.protexius.trace4eo.provenance.record.FilesInfo;
import com.protexius.trace4eo.provenance.record.FilesInfoBuilder;
import com.protexius.trace4eo.provenance.record.Manifest;
import com.protexius.trace4eo.provenance.record.ManifestBuilder;
import com.protexius.trace4eo.provenance.record.Metadata;
import com.protexius.trace4eo.provenance.record.ProvenanceRecord;
import com.protexius.trace4eo.provenance.record.ProvenanceRecordBuilder;
import com.protexius.trace4eo.provenance.verification.ProvenanceVerificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SequencedSet;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerificationToolTest {

    private static final String TEST_FILE = "src/test/resources/test.txt";
    private static final String SIGNATURE_FILE = "src/test/resources/signature.json";
    private static final String PROVENANCE_RECORD_FILE = "src/test/resources/provenance-record.json";
    private static final String INVALID_SIGNATURE_PROVENANCE_RECORD_FILE = "src/test/resources/invalid-signature-provenance-record.json";
    private static final String INVALID_CONTENTS_PROVENANCE_RECORD_FILE = "src/test/resources/invalid-contents-provenance-record.json";
    private static final String HASHES_FILE = "src/test/resources/hashes.txt";
    private static final String WRONG_HASHES_FILE = "src/test/resources/wrong-hashes.txt";
    private static final String INVALID_HASHES_FILE = "src/test/resources/invalid-hashes.txt";

    private final ProvenanceJsonMapper provenanceJsonMapper = new ProvenanceJsonMapper();
    private final ProvenanceVerificationService verificationService = new ProvenanceVerificationService();
    private final Map<String, VerificationResultFormatter> formatters = Map.of(
        "text", new TextVerificationResultFormatter(),
        "json", new JsonVerificationResultFormatter(provenanceJsonMapper)
    );
    private final VerificationTool verificationTool = new VerificationTool(verificationService, provenanceJsonMapper, formatters);

    private String verify(Path file, String fileHash, Path fileHashes, String format) {
        return verificationTool.verify(file, fileHash, fileHashes, format, false, null);
    }

    // SHA-256 of empty bytes (content of test.txt in provenance-record.json), base64-encoded
    private static final String EMPTY_SHA256_B64 = "47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU=";
    // base64 of 32 zero bytes
    private static final String WRONG_SHA256_B64 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    @Test
    void verifyProvenanceRecord() {
        String result = verify(Path.of(PROVENANCE_RECORD_FILE), null, null, "text");
        assertTrue(result.contains("PASSED"), () -> "Expected PASSED, got: " + result);
    }

    @Test
    void verifyProvenanceRecordZip(@TempDir Path tempDir) throws IOException {
        Container container = new JsonContainerReader(provenanceJsonMapper)
            .read(Path.of(PROVENANCE_RECORD_FILE));
        Path zipPath = tempDir.resolve("provenance-record.zip");
        new ZipContainerWriter(provenanceJsonMapper)
            .writeTo(container, Files.newOutputStream(zipPath));

        String result = verify(zipPath, null, null, "text");
        assertTrue(result.contains("PASSED"), () -> "Expected PASSED, got: " + result);
    }

    @Test
    void verifyProvenanceRecordWithInvalidSignature() {
        String result = verify(
            Path.of(INVALID_SIGNATURE_PROVENANCE_RECORD_FILE), null, null, "text");
        assertTrue(result.contains("FAILED"));
        assertTrue(result.contains("[FAIL]"));
        assertTrue(result.contains("Signature"));
    }

    @Test
    void verifyProvenanceRecordWithInvalidContents() {
        String result = verify(
            Path.of(INVALID_CONTENTS_PROVENANCE_RECORD_FILE), null, null, "text");
        assertTrue(result.contains("FAILED"));
        assertTrue(result.contains("[FAIL]"));
        assertTrue(result.contains("Files Info"));
    }

    @Test
    void verifyProvenanceRecordWithCorrectInlineHash() {
        String result = verify(
            Path.of(PROVENANCE_RECORD_FILE), "test.txt=" + EMPTY_SHA256_B64, null, "text");
        assertTrue(result.contains("PASSED"), () -> "Expected PASSED, got: " + result);
        assertTrue(result.contains("[OK]"));
        assertTrue(result.contains("File Contents"));
    }

    @Test
    void verifyProvenanceRecordWithWrongInlineHash() {
        String result = verify(
            Path.of(PROVENANCE_RECORD_FILE), "test.txt=" + WRONG_SHA256_B64, null, "text");
        assertTrue(result.contains("FAILED"));
        assertTrue(result.contains("[FAIL]"));
        assertTrue(result.contains("File Contents"));
    }

    @Test
    void verifyProvenanceRecordWithCorrectHashFile() {
        String result = verify(
            Path.of(PROVENANCE_RECORD_FILE), null, Path.of(HASHES_FILE), "text");
        assertTrue(result.contains("PASSED"), () -> "Expected PASSED, got: " + result);
        assertTrue(result.contains("[OK]"));
        assertTrue(result.contains("File Contents"));
    }

    @Test
    void verifyProvenanceRecordWithWrongHashFile() {
        String result = verify(
            Path.of(PROVENANCE_RECORD_FILE), null, Path.of(WRONG_HASHES_FILE), "text");
        assertTrue(result.contains("FAILED"));
        assertTrue(result.contains("[FAIL]"));
        assertTrue(result.contains("File Contents"));
    }

    @Test
    void verifyProvenanceRecordWithInlineAndHashFile() {
        String result = verify(
            Path.of(PROVENANCE_RECORD_FILE), "test.txt=" + EMPTY_SHA256_B64, Path.of(HASHES_FILE), "text");
        assertTrue(result.contains("PASSED"), () -> "Expected PASSED, got: " + result);
    }

    @Test
    void verifyProvenanceRecordSkipsUnrecognizedEntriesInHashFile() {
        // hashes.txt has test.txt (in the record) and data.txt (not in the record).
        // Only test.txt should be verified; data.txt should be silently skipped.
        String result = verify(
            Path.of(PROVENANCE_RECORD_FILE), null, Path.of(HASHES_FILE), "text");
        assertTrue(result.contains("1 of 1"), "Expected '1 of 1 file content hashes verified' (data.txt skipped)");
    }

    @Test
    void verifyProvenanceRecordFailsForInvalidEntryInHashFile() {
        // invalid-hashes.txt has two valid entries followed by one with invalid base64.
        assertThrows(IllegalArgumentException.class,
            () -> verify(Path.of(PROVENANCE_RECORD_FILE), null, Path.of(INVALID_HASHES_FILE), "text"));
    }

    @Test
    void verifyProvenanceRecordWithInvalidHashFormat() {
        assertThrows(IllegalArgumentException.class,
            () -> verify(Path.of(PROVENANCE_RECORD_FILE), "test.txt", null, "text"));
    }

    @Test
    void verifyProvenanceRecordFailsForNonExistentFile() {
        assertThrows(IllegalArgumentException.class,
            () -> verify(Path.of("nonexistent.json"), null, null, "text"));
    }

    @Test
    void verifyProvenanceRecordFailsForNonExistentHashManifest(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("missing.txt");
        assertThrows(IllegalArgumentException.class,
            () -> verify(Path.of(PROVENANCE_RECORD_FILE), null, missing, "text"));
    }

    @Test
    void verifyProvenanceRecordFailsForInvalidBase64InHash() {
        assertThrows(IllegalArgumentException.class,
            () -> verify(Path.of(PROVENANCE_RECORD_FILE), "test.txt=not!valid!!base64", null, "text"));
    }

    @Test
    void verifyProvenanceRecordFailsForBlankPathInHash() {
        assertThrows(IllegalArgumentException.class,
            () -> verify(Path.of(PROVENANCE_RECORD_FILE), "=" + EMPTY_SHA256_B64, null, "text"));
    }

    @Test
    void verifyProvenanceRecordFailsForUnknownFormat() {
        assertThrows(IllegalArgumentException.class,
            () -> verify(Path.of(PROVENANCE_RECORD_FILE), null, null, "xml"));
    }

    @Test
    void verifyProvenanceRecordWithNonDefaultAlgorithm(@TempDir Path tempDir) throws IOException {
        Metadata metadata = new Metadata("data-id", "container-type", List.of(), null);
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

        String result = verify(jsonPath, fileHash, null, "text");
        assertTrue(result.contains("[OK]"));
        assertTrue(result.contains("File Contents"));
    }

    // --- New behavior: summary, --silent, --data-id ---

    @Test
    void textOutput_alwaysIncludesSummary() {
        String result = verify(Path.of(PROVENANCE_RECORD_FILE), null, null, "text");
        assertTrue(result.contains("=== Summary ==="), () -> "Expected summary header, got: " + result);
        assertTrue(result.contains("Records verified:  1"));
        assertTrue(result.contains("Passed:            1"));
        assertTrue(result.contains("Failed:            0"));
    }

    @Test
    void textOutput_summaryDoesNotMentionSkippedWhenNoneSkipped() {
        String result = verify(Path.of(PROVENANCE_RECORD_FILE), null, null, "text");
        assertTrue(!result.contains("Skipped"), () -> "Did not expect Skipped line, got: " + result);
    }

    @Test
    void silentMode_suppressesPassingRecordButKeepsSummary() {
        String result = verificationTool.verify(
            Path.of(PROVENANCE_RECORD_FILE), null, null, "text", true, null);
        assertTrue(!result.contains("=== Provenance Record Verification ==="),
            () -> "Expected passing record to be hidden in silent mode, got: " + result);
        assertTrue(result.contains("=== Summary ==="));
        assertTrue(result.contains("Records verified:  1"));
        assertTrue(result.contains("Passed:            1"));
    }

    @Test
    void silentMode_keepsFailingRecord() {
        String result = verificationTool.verify(
            Path.of(INVALID_SIGNATURE_PROVENANCE_RECORD_FILE), null, null, "text", true, null);
        assertTrue(result.contains("=== Provenance Record Verification ==="),
            () -> "Expected failing record to be shown in silent mode, got: " + result);
        assertTrue(result.contains("[FAIL]"));
        assertTrue(result.contains("=== Summary ==="));
        assertTrue(result.contains("Failed:            1"));
    }

    @Test
    void jsonOutput_includesSummaryAndResults() {
        String result = verify(Path.of(PROVENANCE_RECORD_FILE), null, null, "json");
        assertTrue(result.contains("\"summary\""));
        assertTrue(result.contains("\"records\" : 1"));
        assertTrue(result.contains("\"passed\" : 1"));
        assertTrue(result.contains("\"failed\" : 0"));
        assertTrue(result.contains("\"skipped\" : 0"));
        assertTrue(result.contains("\"results\""));
    }

    @Test
    void jsonOutput_silentFiltersResultsButKeepsSummary() {
        String result = verificationTool.verify(
            Path.of(PROVENANCE_RECORD_FILE), null, null, "json", true, null);
        assertTrue(result.contains("\"results\" : [ ]"),
            () -> "Expected empty results array in silent mode, got: " + result);
        assertTrue(result.contains("\"passed\" : 1"));
    }

    @Test
    void dataId_unknownValue_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> verificationTool.verify(Path.of(PROVENANCE_RECORD_FILE), null, null, "text", false, "no-such-id"));
    }

    @Test
    void dataId_selectsOnlyMatchingRecord(@TempDir Path tempDir) throws IOException {
        Path otherFile = tempDir.resolve("other.txt");
        Files.writeString(otherFile, "different content");

        ProvenanceRecord recordA = buildRecord("product-a", Path.of(TEST_FILE));
        ProvenanceRecord recordB = buildRecord("product-b", otherFile);

        Path jsonPath = tempDir.resolve("two-records.json");
        SequencedSet<ProvenanceRecord> records = new LinkedHashSet<>();
        records.add(recordA);
        records.add(recordB);
        new JsonContainerWriter(provenanceJsonMapper)
            .writeTo(new Container(recordA.id(), records), Files.newOutputStream(jsonPath));

        String full = verificationTool.verify(jsonPath, null, null, "text", false, null);
        assertTrue(full.contains("Records verified:  2"),
            () -> "Expected both records to be verified without --data-id, got: " + full);
        assertTrue(!full.contains("Skipped"),
            () -> "Did not expect Skipped line without --data-id, got: " + full);

        String filtered = verificationTool.verify(jsonPath, null, null, "text", false, "product-a");
        assertTrue(filtered.contains("Records verified:  1"),
            () -> "Expected only the matching record to be verified, got: " + filtered);
        assertTrue(filtered.contains("Skipped:           1"),
            () -> "Expected skipped count in summary, got: " + filtered);
    }

    private ProvenanceRecord buildRecord(String dataId, Path file) throws IOException {
        Metadata metadata = new Metadata(dataId, "container-type", List.of(), null);
        FilesInfo filesInfo = new FilesInfoBuilder(HashAlgorithm.SHA256)
            .addFile(file)
            .build();
        Manifest manifest = new ManifestBuilder(HashAlgorithm.SHA256, provenanceJsonMapper)
            .withFilesInfo(filesInfo)
            .withMetadata(metadata)
            .build();
        ProvenanceSignature signature = provenanceJsonMapper.readValue(
            Path.of(SIGNATURE_FILE), ProvenanceSignature.class);
        return new ProvenanceRecordBuilder()
            .withMetadata(metadata)
            .withFilesInfo(filesInfo)
            .withManifest(manifest)
            .withSignature(signature)
            .build();
    }

}
