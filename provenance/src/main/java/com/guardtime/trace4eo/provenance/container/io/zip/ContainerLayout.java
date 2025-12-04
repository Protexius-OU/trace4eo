package com.guardtime.trace4eo.provenance.container.io.zip;

import java.io.File;

final class ContainerLayout {
    private ContainerLayout() {
        // prevent instantiation
    }

    public static final String RECORDS_DIR_NAME = "records" + File.separator;
    public static final String FILES_DIR_NAME = "files" + File.separator;
    public static final String HEAD_FILE_NAME = "HEAD";
    public static final String METADATA_FILE_NAME = "meta.json";
    public static final String FILES_FILE_NAME = "files.json";
    public static final String MANIFEST_FILE_NAME = "manifest.json";
    public static final String MANIFEST_SIGNATURE_FILE_NAME = "manifest.ksig";
}
