package com.guardtime.traceguard.provenance.container.io.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guardtime.traceguard.provenance.container.io.ContainerReader;
import com.guardtime.traceguard.provenance.container.model.Container;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

public class JsonContainerReader implements ContainerReader {

    private static final Logger log = LoggerFactory.getLogger(JsonContainerReader.class);

    private final ObjectMapper objectMapper;

    public JsonContainerReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Container read(Path path) throws IOException {
        log.debug("Reading container from {}", path);
        return objectMapper.readValue(path.toFile(), Container.class);
    }
}
