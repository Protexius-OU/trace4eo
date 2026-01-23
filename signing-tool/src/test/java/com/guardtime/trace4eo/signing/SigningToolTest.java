package com.guardtime.trace4eo.signing;

import com.guardtime.trace4eo.provenance.ProvenanceSignature;
import com.guardtime.trace4eo.provenance.record.ProvenanceRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SigningToolTest {

    private static final Logger log = LoggerFactory.getLogger(SigningToolTest.class);

    @Test
    void sign() {
        SigningTool signingTool = new SigningTool();
        String artifactPath = "src/test/resources/test.txt";
        ProvenanceSignature result = signingTool.sign(Path.of(artifactPath));
        assertNotNull(result);
        assertNotNull(result.bytes());
        assertNotNull(result.signingTime());
        assertNotNull(result.hashAlgorithm());
    }

    @Test
    void createProvenanceRecord() throws IOException {
        SigningTool signingTool = new SigningTool();
        List<Path> files = List.of(Path.of("src/test/resources/test.txt"));
        ProvenanceRecord result = signingTool.createProvenanceRecord(files, "test", "test",  List.of(), "SHA256");
        assertNotNull(result);
        assertNotNull(result.id());
        assertNotNull(result.filesInfo());
        assertNotNull(result.metadata());
        assertNotNull(result.manifest());
        log.info("Signed provenance record: {}", result);
    }

    @Test
    void batchSign_withFilesList(@TempDir Path tempDir) throws IOException {
        Path file1 = tempDir.resolve("file1.txt");
        Path file2 = tempDir.resolve("file2.txt");
        Files.writeString(file1, "content1");
        Files.writeString(file2, "content2");
        Path outputPath = tempDir.resolve("output.zip");

        SigningTool signingTool = new SigningTool();
        BatchSigningResult result = signingTool.batchSign(
            List.of(file1, file2),
            null,
            "*",
            "test-type",
            "batch-2024",
            outputPath,
            "SHA256"
        );

        assertEquals(2, result.totalFiles());
        assertEquals(2, result.successCount());
        assertEquals(0, result.failureCount());
        assertTrue(Files.exists(outputPath));
        assertTrue(Files.size(outputPath) > 0);
        log.info("Batch signing result: {}", result);
    }

    @Test
    void batchSign_withDirectory(@TempDir Path tempDir) throws IOException {
        Path inputDir = tempDir.resolve("input");
        Files.createDirectories(inputDir);
        Files.writeString(inputDir.resolve("img1.jpg"), "image1");
        Files.writeString(inputDir.resolve("img2.jpg"), "image2");
        Files.writeString(inputDir.resolve("other.txt"), "other");
        Path outputPath = tempDir.resolve("output.zip");

        SigningTool signingTool = new SigningTool();
        BatchSigningResult result = signingTool.batchSign(
            null,
            inputDir,
            "*.jpg",
            "satellite-image",
            "acquisition-2024",
            outputPath,
            "SHA256"
        );

        assertEquals(2, result.totalFiles());
        assertEquals(2, result.successCount());
        assertEquals(0, result.failureCount());
        assertTrue(Files.exists(outputPath));
    }

    @Test
    void batchSign_emptyFilesList_throws() {
        SigningTool signingTool = new SigningTool();
        Path outputPath = Path.of("/tmp/output.zip");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.batchSign(List.of(), null, "*", "test", "test", outputPath, "SHA256")
        );

        assertTrue(exception.getMessage().contains("No files to sign"));
    }

    @Test
    void batchSign_tooManyFiles_throws(@TempDir Path tempDir) throws IOException {
        List<Path> files = IntStream.range(0, 101)
            .mapToObj(i -> {
                Path file = tempDir.resolve("file" + i + ".txt");
                try {
                    Files.writeString(file, "content" + i);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return file;
            })
            .toList();
        Path outputPath = tempDir.resolve("output.zip");

        SigningTool signingTool = new SigningTool();
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            signingTool.batchSign(files, null, "*", "test", "test", outputPath, "SHA256")
        );

        assertTrue(exception.getMessage().contains("Cannot sign more than 100 files"));
    }

    @Test
    void batchSign_partialFailure_continuesWithRemainingFiles(@TempDir Path tempDir) throws IOException {
        Path validFile = tempDir.resolve("valid.txt");
        Files.writeString(validFile, "valid content");
        Path nonExistentFile = tempDir.resolve("nonexistent.txt");
        Path outputPath = tempDir.resolve("output.zip");

        SigningTool signingTool = new SigningTool();
        BatchSigningResult result = signingTool.batchSign(
            List.of(validFile, nonExistentFile),
            null,
            "*",
            "test",
            "test",
            outputPath,
            "SHA256"
        );

        assertEquals(2, result.totalFiles());
        assertEquals(1, result.successCount());
        assertEquals(1, result.failureCount());
        assertTrue(result.hasFailures());
        assertTrue(Files.exists(outputPath));
    }
}
