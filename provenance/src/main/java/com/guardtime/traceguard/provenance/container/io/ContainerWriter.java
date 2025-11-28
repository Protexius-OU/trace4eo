package com.guardtime.traceguard.provenance.container.io;

import com.guardtime.traceguard.provenance.container.model.Container;

import java.io.IOException;
import java.io.OutputStream;

public interface ContainerWriter {
    void writeTo(Container container, OutputStream out) throws IOException;
}
