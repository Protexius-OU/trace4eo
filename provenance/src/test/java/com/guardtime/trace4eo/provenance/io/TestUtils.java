package com.guardtime.trace4eo.provenance.io;

import com.guardtime.trace4eo.provenance.HashAlgorithm;
import com.guardtime.trace4eo.provenance.ProvenanceJsonMapper;
import com.guardtime.trace4eo.provenance.ProvenanceSignature;
import com.guardtime.trace4eo.provenance.record.FilesInfo;
import com.guardtime.trace4eo.provenance.record.FilesInfoBuilder;
import com.guardtime.trace4eo.provenance.record.Manifest;
import com.guardtime.trace4eo.provenance.record.ManifestBuilder;
import com.guardtime.trace4eo.provenance.record.Metadata;
import com.guardtime.trace4eo.provenance.record.ProvenanceRecord;
import com.guardtime.trace4eo.provenance.record.ProvenanceRecordBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

@SuppressWarnings("MutablePublicArray")
public final class TestUtils {

    public static final byte[] TEST_BYTES_1 = new byte[]{1, 2, 3};
    public static final byte[] TEST_BYTES_2 = new byte[]{4, 5, 6};
    public static final String TEST_FILE_1 = "src/test/resources/test1.txt";
    public static final String TEST_FILE_2 = "src/test/resources/test2.txt";

    private static final ProvenanceJsonMapper MAPPER = new ProvenanceJsonMapper();

    private TestUtils() {
    }

    private static int fixtureIndex;

    public static ProvenanceRecord createProvenanceRecord(String filePath) throws IOException {
        Metadata metadata = new Metadata("data-id", "container-type", List.of());
        HashAlgorithm hashAlgorithm = HashAlgorithm.SHA256;
        FilesInfo filesInfo = new FilesInfoBuilder(hashAlgorithm)
            .addFile(Path.of(filePath))
            .build();
        Manifest manifest = new ManifestBuilder(hashAlgorithm, MAPPER)
            .withFilesInfo(filesInfo)
            .withMetadata(metadata)
            .build();
        ProvenanceSignature signature = nextFixtureSignature();
        return new ProvenanceRecordBuilder()
            .withMetadata(metadata)
            .withFilesInfo(filesInfo)
            .withManifest(manifest)
            .withSignature(signature)
            .build();
    }

    public static ProvenanceSignature sign(byte[] data) {
        return loadFixtureSignature("signature1.json");
    }

    private static ProvenanceSignature nextFixtureSignature() {
        fixtureIndex++;
        String name = "signature" + ((fixtureIndex % 2) + 1) + ".json";
        return loadFixtureSignature(name);
    }

    public static ProvenanceSignature loadFixtureSignature(String resourceName) {
        try (InputStream is = TestUtils.class.getResourceAsStream("/" + resourceName)) {
            if (is == null) {
                throw new IllegalStateException("Test fixture not found: " + resourceName);
            }
            return MAPPER.readValue(is, ProvenanceSignature.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load test fixture: " + resourceName, e);
        }
    }

}
