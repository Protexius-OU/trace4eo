package com.guardtime.trace4eo.provenance.container.model;

import java.io.IOException;
import java.io.InputStream;

public interface FilesContext {

    InputStream getFileContents(FileHashInfo fileInfo) throws IOException;

}
