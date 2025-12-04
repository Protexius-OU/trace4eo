package com.guardtime.trace4eo.provenance.container.io.json;

import com.guardtime.trace4eo.provenance.container.io.ContainerReader;
import com.guardtime.trace4eo.provenance.container.model.Container;
import com.guardtime.trace4eo.provenance.container.model.ProvenanceJsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class JsonContainerReader implements ContainerReader {

    private static final Logger log = LoggerFactory.getLogger(JsonContainerReader.class);

    private final ProvenanceJsonMapper provenanceJsonMapper;

    public JsonContainerReader(ProvenanceJsonMapper provenanceJsonMapper) {
        this.provenanceJsonMapper = provenanceJsonMapper;
    }

    @Override
    public Container read(Path path) {
        log.debug("Reading container from {}", path);
        return provenanceJsonMapper.readValue(path.toFile(), Container.class);
    }
}
