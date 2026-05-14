package com.protexius.trace4eo.signing;

import com.protexius.trace4eo.provenance.HashAlgorithm;
import com.protexius.trace4eo.provenance.ProvenanceJsonMapper;
import com.protexius.trace4eo.provenance.ProvenanceSignature;
import com.protexius.trace4eo.provenance.record.FilesInfo;
import com.protexius.trace4eo.provenance.record.FilesInfoBuilder;
import com.protexius.trace4eo.provenance.record.Manifest;
import com.protexius.trace4eo.provenance.record.ManifestBuilder;
import com.protexius.trace4eo.provenance.record.Metadata;
import com.protexius.trace4eo.provenance.record.Predecessor;
import com.protexius.trace4eo.provenance.record.ProvenanceRecord;
import com.protexius.trace4eo.provenance.record.ProvenanceRecordBuilder;
import com.protexius.trace4eo.provenance.signing.ProvenanceSigningService;
import dev.sigstore.KeylessSigner;
import dev.sigstore.json.canonicalizer.JsonCanonicalizer;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Component
public class RecordSigningService {

    private final ProvenanceSigningService signingService;
    private final ProvenanceJsonMapper provenanceJsonMapper;

    public RecordSigningService(ProvenanceSigningService signingService, ProvenanceJsonMapper provenanceJsonMapper) {
        this.signingService = signingService;
        this.provenanceJsonMapper = provenanceJsonMapper;
    }

    public UnsignedRecord build(
        List<Path> files, String dataId, String provenanceRecordType,
        List<Predecessor> predecessors, Map<String, String> attributes, HashAlgorithm algorithm
    ) throws IOException {
        Metadata metadata = new Metadata(dataId, provenanceRecordType, predecessors, attributes);
        FilesInfo filesInfo = new FilesInfoBuilder(algorithm).addFiles(files).build();
        Manifest manifest = new ManifestBuilder(algorithm, provenanceJsonMapper)
            .withFilesInfo(filesInfo)
            .withMetadata(metadata)
            .build();
        byte[] manifestBytes = new JsonCanonicalizer(provenanceJsonMapper.writeValueAsBytes(manifest)).getEncodedUTF8();
        return new UnsignedRecord(metadata, filesInfo, manifest, manifestBytes);
    }

    public ProvenanceRecord sign(UnsignedRecord unsigned, String oidcToken) throws IOException {
        ProvenanceSignature signature = signingService.sign(unsigned.manifestBytes(), oidcToken);
        return assembleRecord(unsigned, signature);
    }

    public ProvenanceRecord sign(UnsignedRecord unsigned, KeylessSigner signer) throws IOException {
        ProvenanceSignature signature = signingService.sign(unsigned.manifestBytes(), signer);
        return assembleRecord(unsigned, signature);
    }

    public KeylessSigner buildSigner(String oidcToken) {
        return signingService.buildSigner(oidcToken);
    }

    private ProvenanceRecord assembleRecord(UnsignedRecord unsigned, ProvenanceSignature signature) throws IOException {
        return new ProvenanceRecordBuilder()
            .withMetadata(unsigned.metadata())
            .withFilesInfo(unsigned.filesInfo())
            .withManifest(unsigned.manifest())
            .withSignature(signature)
            .build();
    }
}
