package com.guardtime.trace4eo.provenance.record;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class SimplePathMappingFilesContext implements FilesContext {

    private final Map<Path, Path> containerToFsMapping = new HashMap<>();

    public void addFileMapping(FileHashInfo source, Path destination) {
        containerToFsMapping.put(source.path(), destination);
    }

    @Override
    public InputStream getFileContents(FileHashInfo fileInfo) throws IOException {
        Path filePath = containerToFsMapping.get(fileInfo.path());
        if (filePath == null) {
            throw new IOException("No file mapping found for: " + fileInfo.path());
        }
        return Files.newInputStream(filePath);
    }
}
