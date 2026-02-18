package com.guardtime.trace4eo.signing;

import com.guardtime.trace4eo.provenance.record.FilesInfo;
import com.guardtime.trace4eo.provenance.record.Manifest;
import com.guardtime.trace4eo.provenance.record.Metadata;

public record UnsignedRecord(Metadata metadata, FilesInfo filesInfo, Manifest manifest, byte[] manifestBytes) {}
