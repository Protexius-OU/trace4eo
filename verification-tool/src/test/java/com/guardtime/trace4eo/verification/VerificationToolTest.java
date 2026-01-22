package com.guardtime.trace4eo.verification;

import com.guardtime.trace4eo.provenance.Container;
import com.guardtime.trace4eo.provenance.HashAlgorithm;
import com.guardtime.trace4eo.provenance.ProvenanceJsonMapper;
import com.guardtime.trace4eo.provenance.ProvenanceSignature;
import com.guardtime.trace4eo.provenance.io.json.JsonContainerWriter;
import com.guardtime.trace4eo.provenance.record.FilesInfo;
import com.guardtime.trace4eo.provenance.record.FilesInfoBuilder;
import com.guardtime.trace4eo.provenance.record.Manifest;
import com.guardtime.trace4eo.provenance.record.ManifestBuilder;
import com.guardtime.trace4eo.provenance.record.Metadata;
import com.guardtime.trace4eo.provenance.record.ProvenanceRecord;
import com.guardtime.trace4eo.provenance.record.ProvenanceRecordBuilder;
import com.guardtime.trace4eo.provenance.signing.ProvenanceSigningService;
import com.guardtime.trace4eo.provenance.verification.ProvenanceVerificationResult;
import dev.sigstore.json.canonicalizer.JsonCanonicalizer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerificationToolTest {

    private static final String TEST_FILE = "src/test/resources/test.txt";
    private static final String OTHER_FILE = "src/test/resources/other.txt";

    @TempDir
    private static Path tempDir;

    private static Path signaturePath;
    private static Path containerPath;

    @BeforeAll
    static void createTestSignatures() throws IOException {
        ProvenanceSigningService signingService = new ProvenanceSigningService();
        ProvenanceJsonMapper jsonMapper = new ProvenanceJsonMapper();

        byte[] testFileContent = Files.readAllBytes(Path.of(TEST_FILE));
        ProvenanceSignature signature = signingService.sign(testFileContent, HashAlgorithm.SHA256);

        signaturePath = tempDir.resolve("signature.json");
        Files.write(signaturePath, jsonMapper.writeValueAsBytes(signature));

        ProvenanceRecord record = createProvenanceRecord(TEST_FILE, signingService, jsonMapper);
        Container container = new Container(record.id(), new LinkedHashSet<>(List.of(record)));
        containerPath = tempDir.resolve("container.json");
        new JsonContainerWriter(jsonMapper).writeTo(container, Files.newOutputStream(containerPath));
    }

    private static ProvenanceRecord createProvenanceRecord(
            String filePath,
            ProvenanceSigningService signingService,
            ProvenanceJsonMapper jsonMapper) throws IOException {
        Metadata metadata = new Metadata("data-id", "container-type", List.of());
        HashAlgorithm hashAlgorithm = HashAlgorithm.SHA256;
        FilesInfo filesInfo = new FilesInfoBuilder(hashAlgorithm)
            .addFile(Path.of(filePath))
            .build();
        Manifest manifest = new ManifestBuilder(hashAlgorithm, jsonMapper)
            .withFilesInfo(filesInfo)
            .withMetadata(metadata)
            .build();
        byte[] manifestBytes = new JsonCanonicalizer(jsonMapper.writeValueAsBytes(manifest)).getEncodedUTF8();
        ProvenanceSignature signature = signingService.sign(manifestBytes, hashAlgorithm);
        return new ProvenanceRecordBuilder()
            .withMetadata(metadata)
            .withFilesInfo(filesInfo)
            .withManifest(manifest)
            .withSignature(signature)
            .build();
    }

    @Test
    void verifyValidSignature() {
        VerificationTool verificationTool = new VerificationTool();
        ProvenanceVerificationResult result = verificationTool.verify(Path.of(TEST_FILE), signaturePath);
        assertTrue(result.status());
    }

    @Test
    void verifyInvalidSignature() {
        VerificationTool verificationTool = new VerificationTool();
        ProvenanceVerificationResult result = verificationTool.verify(Path.of(OTHER_FILE), signaturePath);
        assertFalse(result.status());
    }

    @Test
    void verifyProvenanceRecord() {
        VerificationTool verificationTool = new VerificationTool();
        List<ProvenanceVerificationResult> results = verificationTool.verify(containerPath);
        assertEquals(2, results.size());
        for (ProvenanceVerificationResult result : results) {
            assertTrue(result.status(), () -> String.format("Verification failed: %s - %s", result.error(), result.errorMessage()));
        }
    }

}
