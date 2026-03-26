package com.protexius.trace4eo.provenance.record;

import java.io.IOException;
import java.io.InputStream;

public interface FilesContext {

    InputStream getFileContents(FileHashInfo fileInfo) throws IOException;

}
