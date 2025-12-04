package com.guardtime.trace4eo.provenance.container.model;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class FileHasher {
    private FileHasher() {
    }

    public static byte[] hashFile(HashAlgorithm hashAlgorithm, Path filePath)
        throws IOException, NoSuchAlgorithmException {
        if (!Files.exists(filePath)) {
            throw new IOException("File does not exist: " + filePath);
        }
        if (!Files.isReadable(filePath)) {
            throw new IOException("File is not readable: " + filePath);
        }
        if (!Files.isRegularFile(filePath)) {
            throw new IOException("Path is not a regular file: " + filePath);
        }
        MessageDigest md = MessageDigest.getInstance(hashAlgorithm.name());
        try (InputStream inputStream = Files.newInputStream(filePath);
             DigestInputStream digestInputStream = new DigestInputStream(inputStream, md)
        ) {
            return digestInputStream.getMessageDigest().digest();
        }
    }

    public static byte[] hashBytes(HashAlgorithm hashAlgorithm, byte[] bytes) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance(hashAlgorithm.getName());
        md.update(bytes);
        return md.digest();
    }
}
