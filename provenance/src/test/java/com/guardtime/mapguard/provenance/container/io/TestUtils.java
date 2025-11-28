package com.guardtime.traceguard.provenance.container.io;

import com.guardtime.ksi.exceptions.KSIException;
import com.guardtime.ksi.hashing.HashAlgorithm;
import com.guardtime.ksi.unisignature.KSISignature;
import com.guardtime.ksi.unisignature.inmemory.InMemoryKsiSignatureFactory;
import com.guardtime.traceguard.provenance.container.model.FilesInfo;
import com.guardtime.traceguard.provenance.container.model.FilesInfoBuilder;
import com.guardtime.traceguard.provenance.container.model.Manifest;
import com.guardtime.traceguard.provenance.container.model.ManifestBuilder;
import com.guardtime.traceguard.provenance.container.model.Metadata;
import com.guardtime.traceguard.provenance.container.model.ProvenanceRecord;
import com.guardtime.traceguard.provenance.container.model.ProvenanceRecordBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class TestUtils {
    public static final String TEST_FILE_1 = "src/test/resources/test1.txt";
    public static final String TEST_FILE_2 = "src/test/resources/test2.txt";
    public static final String SIGNATURE_1 = "src/test/resources/signature1.ksig";
    public static final String SIGNATURE_2 = "src/test/resources/signature2.ksig";

    private TestUtils() {
    }

    public static ProvenanceRecord createProvenanceRecord(
        String filePath,
        String signaturePath
    ) throws IOException {
        Metadata metadata = new Metadata("data-id", "container-type", List.of());
        HashAlgorithm hashAlgorithm = HashAlgorithm.SHA2_256;
        FilesInfo filesInfo = new FilesInfoBuilder(hashAlgorithm)
            .addFile(Path.of(filePath))
            .build();
        Manifest manifest = new ManifestBuilder(hashAlgorithm)
            .withFilesInfo(filesInfo)
            .withMetadata(metadata)
            .build();
        return new ProvenanceRecordBuilder()
            .withMetadata(metadata)
            .withFilesInfo(filesInfo)
            .withManifest(manifest)
            .withSignature(readKsiSignature(signaturePath))
            .build();
    }

    public static KSISignature readKsiSignature(String path) {
        try {
            return new InMemoryKsiSignatureFactory().createSignature(Files.newInputStream(Path.of(path)));
        } catch (KSIException | IOException e) {
            throw new RuntimeException(e);
        }
    }
}
