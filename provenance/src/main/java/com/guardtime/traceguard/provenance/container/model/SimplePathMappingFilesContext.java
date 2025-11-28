package com.guardtime.traceguard.provenance.container.model;

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
        return Files.newInputStream(containerToFsMapping.get(fileInfo.path()));
    }
}

