package com.guardtime.trace4eo.provenance.io;

import com.guardtime.trace4eo.provenance.Container;

import java.io.IOException;
import java.io.OutputStream;

public interface ContainerWriter {
    void writeTo(Container container, OutputStream out) throws IOException;
}
