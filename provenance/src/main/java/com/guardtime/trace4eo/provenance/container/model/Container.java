package com.guardtime.trace4eo.provenance.container.model;

import java.util.SequencedSet;
import java.util.UUID;

public record Container(
    UUID head,
    SequencedSet<ProvenanceRecord> provenanceRecords
) {
}
