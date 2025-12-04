package com.guardtime.trace4eo.provenance.container.io;

import com.guardtime.trace4eo.provenance.container.model.FilesInfo;
import com.guardtime.trace4eo.provenance.container.model.FilesInfoBuilder;
import com.guardtime.trace4eo.provenance.container.model.HashAlgorithm;
import com.guardtime.trace4eo.provenance.container.model.Manifest;
import com.guardtime.trace4eo.provenance.container.model.ManifestBuilder;
import com.guardtime.trace4eo.provenance.container.model.Metadata;
import com.guardtime.trace4eo.provenance.container.model.ProvenanceJsonMapper;
import com.guardtime.trace4eo.provenance.container.model.ProvenanceRecord;
import com.guardtime.trace4eo.provenance.container.model.ProvenanceRecordBuilder;
import com.guardtime.trace4eo.provenance.container.model.ProvenanceSignature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@SuppressWarnings("MutablePublicArray")
public final class TestUtils {
    public static final byte[] TEST_BYTES_1 = new byte[] {1, 2, 3};
    public static final byte[] TEST_BYTES_2 = new byte[] {4, 5, 6};
    public static final String TEST_FILE_1 = "src/test/resources/test1.txt";
    public static final String TEST_FILE_2 = "src/test/resources/test2.txt";
    public static final String SIGNATURE_1 = "src/test/resources/signature1.json";
    public static final String SIGNATURE_2 = "src/test/resources/signature2.json";

    private TestUtils() {
    }

    public static ProvenanceRecord createProvenanceRecord(
        String filePath,
        String signaturePath
    ) throws IOException {
        Metadata metadata = new Metadata("data-id", "container-type", List.of());
        HashAlgorithm hashAlgorithm = HashAlgorithm.SHA256;
        FilesInfo filesInfo = new FilesInfoBuilder(hashAlgorithm)
            .addFile(Path.of(filePath))
            .build();
        Manifest manifest = new ManifestBuilder(hashAlgorithm, new ProvenanceJsonMapper())
            .withFilesInfo(filesInfo)
            .withMetadata(metadata)
            .build();
        return new ProvenanceRecordBuilder()
            .withMetadata(metadata)
            .withFilesInfo(filesInfo)
            .withManifest(manifest)
            .withSignature(readSignature(signaturePath))
            .build();
    }

    public static ProvenanceSignature readSignature(String path) {
        return new JsonMapper().readValue(Path.of(path), ProvenanceSignature.class);
    }
}
