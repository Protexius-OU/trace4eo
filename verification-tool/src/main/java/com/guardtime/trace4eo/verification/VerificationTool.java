package com.guardtime.trace4eo.verification;

import com.guardtime.trace4eo.provenance.Container;
import com.guardtime.trace4eo.provenance.ProvenanceJsonMapper;
import com.guardtime.trace4eo.provenance.ProvenanceSignature;
import com.guardtime.trace4eo.provenance.io.ContainerReader;
import com.guardtime.trace4eo.provenance.io.json.JsonContainerReader;
import com.guardtime.trace4eo.provenance.io.zip.ZipContainerReader;
import com.guardtime.trace4eo.provenance.record.ProvenanceRecord;
import com.guardtime.trace4eo.provenance.verification.ProvenanceVerificationResult;
import com.guardtime.trace4eo.provenance.verification.ProvenanceVerificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

@Component
public class VerificationTool {

    private static final Logger log = LoggerFactory.getLogger(VerificationTool.class);

    @Command(name = "verify", description = "Verify input data against signature")
    public ProvenanceVerificationResult verify(
        @Option(longName = "text", description = "Path to input file") Path file,
        @Option(longName = "signature", description = "Path to signature file") Path signaturePath
    ) {
        byte[] inputBytes = resolveInput(file, null, null);
        ProvenanceVerificationService verificationService = new ProvenanceVerificationService();
        ProvenanceSignature signature = new ProvenanceJsonMapper().readValue(signaturePath, ProvenanceSignature.class);
        return verificationService.verify(signature, inputBytes);
    }

    @Command(name = "verify-provenance-record", description = "Verify provenance record")
    public List<ProvenanceVerificationResult> verify(
        @Option(longName = "file", description = "Path to provenance record") Path provenanceRecordPath
    ) {
        try {
            ProvenanceJsonMapper provenanceJsonMapper = new ProvenanceJsonMapper();
            ContainerReader reader = provenanceRecordPath.toString().endsWith(".zip")
                ? new ZipContainerReader(provenanceJsonMapper)
                : new JsonContainerReader(provenanceJsonMapper);
            Container container = reader.read(provenanceRecordPath);
            ProvenanceVerificationService verificationService = new ProvenanceVerificationService();
            List<ProvenanceVerificationResult> results = new ArrayList<>();
            for (ProvenanceRecord provenanceRecord : container.provenanceRecords()) {
                results.add(verificationService.verify(provenanceRecord));
            }
            return results;
        } catch (IOException e) {
            log.error("Failed to read container", e);
            throw new RuntimeException(e);
        }
    }

    private byte[] resolveInput(Path filePath, String hex, String base64) {
        if (filePath != null) {
            if (Files.notExists(filePath)) {
                throw new IllegalArgumentException("File does not exist");
            }
            try {
                return Files.readAllBytes(filePath);
            } catch (IOException e) {
                log.warn("Failed to read file {}", filePath, e);
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
