package com.guardtime.traceguard.provenance.container.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.SequencedSet;
import java.util.UUID;

public record Container(
    @JsonProperty("head") UUID head,
    @JsonProperty("provenanceRecords") SequencedSet<ProvenanceRecord> provenanceRecords) {

    public static final String RECORDS_DIR = "records/";
    public static final String FILES_DIR = "files/";
    public static final String HEAD_FILE = "HEAD";
    public static final String META_JSON = "meta.json";
    public static final String FILES_JSON = "files.json";
    public static final String MANIFEST_JSON = "manifest.json";
    public static final String MANIFEST_SIGNATURE = "manifest.ksig";
}
