package com.guardtime.trace4eo.provenance.container.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashSet;
import java.util.List;

public class FilesInfoBuilder {
    private final HashAlgorithm hashAlgorithm;
    private final FilesInfo filesInfo;
    private final SimplePathMappingFilesContext filesContext = new SimplePathMappingFilesContext();

    public FilesInfoBuilder(HashAlgorithm hashAlgorithm) {
        this.hashAlgorithm = hashAlgorithm;
        this.filesInfo = new FilesInfo(new LinkedHashSet<>(), filesContext);
    }

    public FilesInfo build() {
        return this.filesInfo;
    }

    public FilesInfoBuilder addFiles(List<Path> filePaths) throws IOException {
        for (Path filePath : filePaths) {
            addFile(filePath);
        }
        return this;
    }

    public FilesInfoBuilder addFile(Path filePath) throws IOException {
        validateFilePath(filePath);
        return addFile(filePath, filePath.getFileName());
    }

    public FilesInfoBuilder addFile(Path filePath, Path destinationPath) throws IOException {
        validateFilePath(filePath);
        byte[] hashBytes;
        try {
            hashBytes = FileHasher.hashFile(this.hashAlgorithm, filePath);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        FileHashInfo fileHashInfo = new FileHashInfo(destinationPath, this.hashAlgorithm, hashBytes);
        filesContext.addFileMapping(fileHashInfo, filePath);
        this.filesInfo.files().add(fileHashInfo);
        return this;
    }

    private void validateFilePath(Path filePath) throws IOException {
        if (filePath == null) {
            throw new IOException("File path cannot be null");
        }
        if (!Files.exists(filePath)) {
            throw new IOException("File does not exist: " + filePath);
        }
    }
}
