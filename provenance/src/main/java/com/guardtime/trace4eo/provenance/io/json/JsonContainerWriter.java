package com.guardtime.trace4eo.provenance.io.json;

import com.guardtime.trace4eo.provenance.Container;
import com.guardtime.trace4eo.provenance.ProvenanceJsonMapper;
import com.guardtime.trace4eo.provenance.io.ContainerWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;

public class JsonContainerWriter implements ContainerWriter {

    private static final Logger log = LoggerFactory.getLogger(JsonContainerWriter.class);

    private final ProvenanceJsonMapper provenanceJsonMapper;

    public JsonContainerWriter(ProvenanceJsonMapper provenanceJsonMapper) {
        this.provenanceJsonMapper = provenanceJsonMapper;
    }

    @Override
    public void writeTo(Container container, OutputStream outputStream) {
        log.debug("Writing container to output stream: {}", container);
        provenanceJsonMapper.writeValue(outputStream, container);
    }
}
