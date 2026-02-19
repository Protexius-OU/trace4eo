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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class VerificationTool {

    private static final Logger log = LoggerFactory.getLogger(VerificationTool.class);

    private final ProvenanceVerificationService verificationService;
    private final ProvenanceJsonMapper provenanceJsonMapper;
    private final Map<String, VerificationResultFormatter> formatters;

    public VerificationTool(
        ProvenanceVerificationService verificationService,
        ProvenanceJsonMapper provenanceJsonMapper,
        Map<String, VerificationResultFormatter> formatters
    ) {
        this.verificationService = verificationService;
        this.provenanceJsonMapper = provenanceJsonMapper;
        this.formatters = formatters;
    }

    @Command(name = "verify", description = "Verify input data against signature")
    public String verify(
        @Option(longName = "text", description = "Path to input file", required = true) Path file,
        @Option(longName = "signature", description = "Path to signature file", required = true) Path signaturePath,
        @Option(longName = "format", description = "Output format: text (default) or json", defaultValue = "text") String format
    ) {
        byte[] inputBytes = resolveInput(file);
        ProvenanceSignature signature = provenanceJsonMapper.readValue(signaturePath, ProvenanceSignature.class);
        ProvenanceVerificationResult result = verificationService.verify(signature, inputBytes);
        return resolveFormatter(format).format(result);
    }

    @Command(name = "verify-provenance-record", description = "Verify provenance record")
    public String verify(
        @Option(longName = "file", description = "Path to provenance record", required = true) Path provenanceRecordPath,
        @Option(longName = "file-hash", description = "File hash as <path>=<base64>") String fileHash,
        @Option(longName = "file-hashes", description = "Path to hash file with one <path>=<base64> per line")
            Path fileHashesPath,
        @Option(longName = "format", description = "Output format: text (default) or json", defaultValue = "text") String format
    ) {
        Map<String, byte[]> hashes = resolveFileHashes(fileHash, fileHashesPath);
        Container container = readContainer(provenanceRecordPath);
        List<ProvenanceVerificationResult> results = verifyAll(container.provenanceRecords(), hashes);
        return resolveFormatter(format).format(results);
    }

    private VerificationResultFormatter resolveFormatter(String format) {
        VerificationResultFormatter formatter = formatters.get(format);
        if (formatter == null) {
            throw new IllegalArgumentException("Unknown output format: " + format);
        }
        return formatter;
    }

    private Map<String, byte[]> resolveFileHashes(String fileHash, Path fileHashesPath) {
        Map<String, byte[]> hashes = new LinkedHashMap<>();
        if (fileHash != null) {
            Map.Entry<String, byte[]> entry = parseInlineHash(fileHash);
            hashes.put(entry.getKey(), entry.getValue());
        }
        if (fileHashesPath != null) {
            hashes.putAll(parseHashManifest(fileHashesPath));
        }
        return hashes;
    }

    private Map.Entry<String, byte[]> parseInlineHash(String entry) {
        int eqIdx = entry.indexOf('=');
        if (eqIdx < 0) {
            throw new IllegalArgumentException(
                String.format("Invalid hash entry '%s': expected format <path>=<base64>", entry));
        }
        String path = entry.substring(0, eqIdx);
        if (path.isBlank()) {
            throw new IllegalArgumentException(
                String.format("Invalid hash entry '%s': path component must not be blank", entry));
        }
        String base64 = entry.substring(eqIdx + 1);
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                String.format("Invalid base64 hash for '%s': %s", path, e.getMessage()), e);
        }
        return Map.entry(path, bytes);
    }

    private Map<String, byte[]> parseHashManifest(Path path) {
        if (Files.notExists(path)) {
            throw new IllegalArgumentException("Hash file not found: " + path);
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(path);
        } catch (IOException e) {
            log.error("Failed to read hash file {}", path, e);
            throw new RuntimeException("Failed to read hash file: " + path, e);
        }
        Map<String, byte[]> hashes = new LinkedHashMap<>();
        for (String line : lines) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            Map.Entry<String, byte[]> entry = parseInlineHash(line);
            hashes.put(entry.getKey(), entry.getValue());
        }
        return hashes;
    }

    private Container readContainer(Path provenanceRecordPath) {
        if (Files.notExists(provenanceRecordPath)) {
            throw new IllegalArgumentException("Provenance record file not found: " + provenanceRecordPath);
        }
        try {
            ContainerReader reader = provenanceRecordPath.toString().endsWith(".zip")
                ? new ZipContainerReader(provenanceJsonMapper)
                : new JsonContainerReader(provenanceJsonMapper);
            return reader.read(provenanceRecordPath);
        } catch (IOException e) {
            log.error("Failed to read container", e);
            throw new RuntimeException(e);
        }
    }

    private List<ProvenanceVerificationResult> verifyAll(Collection<ProvenanceRecord> records, Map<String, byte[]> hashes) {
        List<ProvenanceVerificationResult> results = new ArrayList<>();
        for (ProvenanceRecord record : records) {
            if (hashes.isEmpty()) {
                results.add(verificationService.verify(record));
            } else {
                results.add(verificationService.verifyWithFileHashes(record, hashes));
            }
        }
        return results;
    }

    private byte[] resolveInput(Path filePath) {
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
}
