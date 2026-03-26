package com.protexius.trace4eo.provenance;

import com.protexius.trace4eo.provenance.record.ProvenanceRecord;

import java.util.SequencedSet;
import java.util.UUID;

public record Container(
    UUID head,
    SequencedSet<ProvenanceRecord> provenanceRecords
) {
}
