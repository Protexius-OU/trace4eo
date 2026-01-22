package com.guardtime.trace4eo.signing;

import com.guardtime.trace4eo.provenance.HashAlgorithm;
import com.guardtime.trace4eo.provenance.ProvenanceJsonMapper;
import com.guardtime.trace4eo.provenance.ProvenanceSignature;
import com.guardtime.trace4eo.provenance.record.FilesInfo;
import com.guardtime.trace4eo.provenance.record.FilesInfoBuilder;
import com.guardtime.trace4eo.provenance.record.Manifest;
import com.guardtime.trace4eo.provenance.record.ManifestBuilder;
import com.guardtime.trace4eo.provenance.record.Metadata;
import com.guardtime.trace4eo.provenance.record.Predecessor;
import com.guardtime.trace4eo.provenance.record.ProvenanceRecord;
import com.guardtime.trace4eo.provenance.record.ProvenanceRecordBuilder;
import com.guardtime.trace4eo.provenance.signing.ProvenanceSigningService;
import dev.sigstore.json.canonicalizer.JsonCanonicalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

public class SigningTool {

    private static final Logger log = LoggerFactory.getLogger(SigningTool.class);

    @Command(name = "sign", description = "Sign input data")
    public ProvenanceSignature sign(
        @Option(longName = "file", description = "Path to input file") Path file
    ) {
        byte[] inputBytes = resolveInput(file, null, null);
        ProvenanceSigningService signingService = new ProvenanceSigningService();
        return signingService.sign(inputBytes, HashAlgorithm.SHA256);
    }

    @Command(name = "create-provenance-record", description = "Create and sign provenance record")
    public ProvenanceRecord createProvenanceRecord(
        @Option(longName = "files", description = "Files to be included in provenance record") List<Path> files,
        @Option(longName = "provenance-record-type", description = "Provenance record type") String provenanceRecordType,
        @Option(longName = "data-id", description = "Provenance record data ID") String dataId,
        @Option(longName = "predecessors", description = "Provenance record predecessors") List<Predecessor> predecessors,
        @Option(longName = "hash-algorithm", description = "Hash algorithm", defaultValue = "SHA-256") String hashAlgorithm
    ) throws IOException {
        Metadata metadata = new Metadata(dataId, provenanceRecordType, predecessors);
        FilesInfo filesInfo = new FilesInfoBuilder(HashAlgorithm.valueOf(hashAlgorithm))
            .addFiles(files)
            .build();
        ProvenanceJsonMapper provenanceJsonMapper = new ProvenanceJsonMapper();
        Manifest manifest = new ManifestBuilder(HashAlgorithm.valueOf(hashAlgorithm), provenanceJsonMapper)
            .withFilesInfo(filesInfo)
            .withMetadata(metadata)
            .build();
        byte[] manifestBytes = new JsonCanonicalizer(provenanceJsonMapper.writeValueAsBytes(manifest)).getEncodedUTF8();
        ProvenanceSigningService signingService = new ProvenanceSigningService();
        ProvenanceSignature provenanceSignature = signingService.sign(manifestBytes, HashAlgorithm.valueOf(hashAlgorithm));
        return new ProvenanceRecordBuilder()
            .withMetadata(metadata)
            .withFilesInfo(filesInfo)
            .withManifest(manifest)
            .withSignature(provenanceSignature)
            .build();
    }

    private byte[] resolveInput(Path file, String hex, String base64) {
        if (file != null) {
            try {
                return Files.readAllBytes(file);
            } catch (IOException e) {
                log.warn("Failed to read file {}", file, e);
                throw new RuntimeException(e);
            }
        }
        if (hex != null) {
            return HexFormat.of().parseHex(hex);
        }
        if (base64 != null) {
            return Base64.getDecoder().decode(base64);
        }
        throw new IllegalArgumentException("Input data was missing");
    }
}
