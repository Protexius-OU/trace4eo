package com.protexius.trace4eo.signing;

import com.protexius.trace4eo.provenance.record.FilesInfo;
import com.protexius.trace4eo.provenance.record.Manifest;
import com.protexius.trace4eo.provenance.record.Metadata;

// Internal value passed within a single sign() invocation; manifestBytes is never mutated.
@SuppressWarnings("ArrayRecordComponent")
public record UnsignedRecord(Metadata metadata, FilesInfo filesInfo, Manifest manifest, byte[] manifestBytes) {}
