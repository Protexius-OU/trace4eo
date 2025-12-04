package com.guardtime.trace4eo.provenance.container.io;

import com.guardtime.trace4eo.provenance.container.model.Container;

import java.io.IOException;
import java.nio.file.Path;

public interface ContainerReader {
    Container read(Path path) throws IOException;
}
