package com.protexius.trace4eo.signing.commands;

import com.protexius.trace4eo.provenance.HashAlgorithm;
import org.springframework.stereotype.Component;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.PatternSyntaxException;

@Component
public class SigningInputValidator {

    public HashAlgorithm validateHashAlgorithm(String hashAlgorithm) {
        try {
            return HashAlgorithm.valueOf(hashAlgorithm);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(String.format(
                "Invalid --hash-algorithm: %s. Supported values: %s",
                hashAlgorithm, Arrays.stream(HashAlgorithm.values()).map(Enum::name).toList()));
        }
    }

    public void validateFilesExist(List<Path> files) {
        for (Path file : files) {
            if (!Files.exists(file)) {
                throw new IllegalArgumentException(String.format("File does not exist: %s", file));
            }
            if (!Files.isRegularFile(file)) {
                throw new IllegalArgumentException(String.format("Path is not a regular file: %s", file));
            }
            if (!Files.isReadable(file)) {
                throw new IllegalArgumentException(String.format("File is not readable: %s", file));
            }
        }
    }

    public void validateOutputDirectory(Path outputDir) {
        if (outputDir == null) {
            return;
        }
        if (Files.exists(outputDir)) {
            if (!Files.isDirectory(outputDir)) {
                throw new IllegalArgumentException(String.format("--output is not a directory: %s", outputDir));
            }
            if (!Files.isWritable(outputDir)) {
                throw new IllegalArgumentException(String.format("--output directory is not writable: %s", outputDir));
            }
        } else {
            Path ancestor = outputDir.toAbsolutePath().getParent();
            while (ancestor != null && !Files.exists(ancestor)) {
                ancestor = ancestor.getParent();
            }
            if (ancestor != null && !Files.isWritable(ancestor)) {
                throw new IllegalArgumentException(String.format(
                    "Cannot create --output %s: ancestor directory %s is not writable", outputDir, ancestor));
            }
        }
    }

    public void validateProvenanceRecordType(String provenanceRecordType) {
        if (provenanceRecordType == null || provenanceRecordType.isBlank()) {
            throw new IllegalArgumentException("--provenance-record-type must not be null or blank");
        }
    }

    public void validateDataId(String dataId) {
        if (dataId == null || dataId.isBlank()) {
            throw new IllegalArgumentException("--data-id must not be null or blank");
        }
    }

    public void validateRegistrationConfig(String registerUrl, String keycloakUrl) {
        if (registerUrl != null && !registerUrl.isBlank()
            && (keycloakUrl == null || keycloakUrl.isBlank())) {
            throw new IllegalArgumentException("--keycloak-url is required when --register-url is provided");
        }
    }

    public void validatePredecessorsFile(Path predecessorsFile) {
        if (predecessorsFile == null) {
            return;
        }
        if (!Files.exists(predecessorsFile)) {
            throw new IllegalArgumentException(
                String.format("--predecessors-file does not exist: %s", predecessorsFile));
        }
        if (!Files.isRegularFile(predecessorsFile)) {
            throw new IllegalArgumentException(
                String.format("--predecessors-file is not a regular file: %s", predecessorsFile));
        }
        if (!Files.isReadable(predecessorsFile)) {
            throw new IllegalArgumentException(
                String.format("--predecessors-file is not readable: %s", predecessorsFile));
        }
    }

    public void validateInputDirectory(Path directory) {
        if (!Files.exists(directory)) {
            throw new IllegalArgumentException(String.format("--directory does not exist: %s", directory));
        }
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException(String.format("--directory is not a directory: %s", directory));
        }
        if (!Files.isReadable(directory)) {
            throw new IllegalArgumentException(String.format("--directory is not readable: %s", directory));
        }
    }

    public void validateGlobPattern(String pattern) {
        try {
            FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException(String.format("Invalid --pattern: %s", pattern));
        }
    }
}
