package com.guardtime.traceguard.provenance.container.io;

import com.guardtime.ksi.hashing.DataHasher;
import com.guardtime.ksi.hashing.HashAlgorithm;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FileHasher {
    private FileHasher() {
    }

    public static byte[] hashFile(HashAlgorithm hashAlgorithm, Path filePath) throws IOException {
        if (!Files.exists(filePath)) {
            throw new IOException("File does not exist: " + filePath);
        }
        if (!Files.isReadable(filePath)) {
            throw new IOException("File is not readable: " + filePath);
        }
        if (!Files.isRegularFile(filePath)) {
            throw new IOException("Path is not a regular file: " + filePath);
        }

        DataHasher dh = new DataHasher(hashAlgorithm);

        try (InputStream inputStream = Files.newInputStream(filePath)) {
            dh.addData(inputStream);
        } catch (IOException e) {
            throw new IOException(String.format("Failed to hash file: %s", filePath), e);
        }

        return dh.getHash().getValue();
    }

    public static byte[] hashBytes(HashAlgorithm hashAlgorithm, byte[] bytes) {
        DataHasher dh = new DataHasher(hashAlgorithm);
        dh.addData(bytes);
        return dh.getHash().getValue();
    }
}
