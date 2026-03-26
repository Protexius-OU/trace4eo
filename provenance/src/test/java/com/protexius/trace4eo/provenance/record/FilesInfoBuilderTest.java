package com.protexius.trace4eo.provenance.record;

import com.protexius.trace4eo.provenance.HashAlgorithm;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FilesInfoBuilderTest {

    @TempDir
    private Path tempDir;

    @Test
    void addFile_hashesFileContents() throws IOException, NoSuchAlgorithmException {
        byte[] content = "hello world".getBytes(StandardCharsets.UTF_8);
        Path file = tempDir.resolve("test.txt");
        Files.write(file, content);

        FilesInfo filesInfo = new FilesInfoBuilder(HashAlgorithm.SHA256)
            .addFile(file)
            .build();

        assertEquals(1, filesInfo.files().size());
        FileHashInfo fileHashInfo = filesInfo.files().iterator().next();
        assertEquals(HashAlgorithm.SHA256, fileHashInfo.hashAlgorithm());
        assertEquals("test.txt", fileHashInfo.path());

        byte[] expectedHash = MessageDigest.getInstance("SHA-256").digest(content);
        assertArrayEquals(expectedHash, fileHashInfo.hashValue());
    }

    @Test
    void addFile_hashIsNotEmptyStringHash() throws IOException, NoSuchAlgorithmException {
        Path file = tempDir.resolve("nonempty.txt");
        Files.writeString(file, "some data");

        FilesInfo filesInfo = new FilesInfoBuilder(HashAlgorithm.SHA256)
            .addFile(file)
            .build();

        byte[] emptyHash = MessageDigest.getInstance("SHA-256").digest(new byte[0]);
        FileHashInfo fileHashInfo = filesInfo.files().iterator().next();

        String emptyHashBase64 = Base64.getEncoder().encodeToString(emptyHash);
        String actualHashBase64 = Base64.getEncoder().encodeToString(fileHashInfo.hashValue());
        assertEquals("47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU=", emptyHashBase64,
            "Sanity check: this is the known SHA-256 of empty input");
        if (actualHashBase64.equals(emptyHashBase64)) {
            throw new AssertionError("File hash equals empty-string hash — file contents were not read");
        }
    }

    @Test
    void addFile_emptyFileProducesEmptyHash() throws IOException, NoSuchAlgorithmException {
        Path file = tempDir.resolve("empty.txt");
        Files.createFile(file);

        FilesInfo filesInfo = new FilesInfoBuilder(HashAlgorithm.SHA256)
            .addFile(file)
            .build();

        byte[] expectedHash = MessageDigest.getInstance("SHA-256").digest(new byte[0]);
        FileHashInfo fileHashInfo = filesInfo.files().iterator().next();
        assertArrayEquals(expectedHash, fileHashInfo.hashValue());
    }

    @Test
    void addFile_nonExistentFileThrows() {
        Path nonExistent = tempDir.resolve("missing.txt");
        FilesInfoBuilder builder = new FilesInfoBuilder(HashAlgorithm.SHA256);
        assertThrows(IOException.class, () -> builder.addFile(nonExistent));
    }

    @Test
    void addFile_nullPathThrows() {
        FilesInfoBuilder builder = new FilesInfoBuilder(HashAlgorithm.SHA256);
        assertThrows(IOException.class, () -> builder.addFile(null));
    }

    @Test
    void addFile_withCustomDestinationPath() throws IOException, NoSuchAlgorithmException {
        byte[] content = "destination test".getBytes(StandardCharsets.UTF_8);
        Path file = tempDir.resolve("source.txt");
        Files.write(file, content);

        FilesInfo filesInfo = new FilesInfoBuilder(HashAlgorithm.SHA256)
            .addFile(file, "custom/dest.txt")
            .build();

        FileHashInfo fileHashInfo = filesInfo.files().iterator().next();
        assertEquals("custom/dest.txt", fileHashInfo.path());

        byte[] expectedHash = MessageDigest.getInstance("SHA-256").digest(content);
        assertArrayEquals(expectedHash, fileHashInfo.hashValue());
    }

    @Test
    void addFiles_hashesMultipleFiles() throws IOException {
        Path file1 = tempDir.resolve("a.txt");
        Path file2 = tempDir.resolve("b.txt");
        Files.writeString(file1, "aaa");
        Files.writeString(file2, "bbb");

        FilesInfo filesInfo = new FilesInfoBuilder(HashAlgorithm.SHA256)
            .addFiles(List.of(file1, file2))
            .build();

        assertEquals(2, filesInfo.files().size());
    }

    @Test
    void addFiles_differentFilesProduceDifferentHashes() throws IOException {
        Path file1 = tempDir.resolve("a.txt");
        Path file2 = tempDir.resolve("b.txt");
        Files.writeString(file1, "content A");
        Files.writeString(file2, "content B");

        FilesInfo filesInfo = new FilesInfoBuilder(HashAlgorithm.SHA256)
            .addFiles(List.of(file1, file2))
            .build();

        var iterator = filesInfo.files().iterator();
        byte[] hash1 = iterator.next().hashValue();
        byte[] hash2 = iterator.next().hashValue();
        assertFalse(Arrays.equals(hash1, hash2),
            "Different file contents must produce different hashes");
    }
}
