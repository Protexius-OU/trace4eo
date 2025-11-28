package com.guardtime.traceguard.provenance.container.io.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guardtime.traceguard.provenance.container.io.ContainerWriter;
import com.guardtime.traceguard.provenance.container.model.Container;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;

public class JsonContainerWriter implements ContainerWriter {

    private static final Logger log = LoggerFactory.getLogger(JsonContainerWriter.class);

    private final ObjectMapper objectMapper;

    public JsonContainerWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void writeTo(Container container, OutputStream outputStream) throws IOException {
        log.debug("Writing container to output stream: {}", container);
        objectMapper.writeValue(outputStream, container);
    }
}
