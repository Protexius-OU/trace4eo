package com.protexius.trace4eo.provenance.record;

import java.util.List;

public record Metadata(
    String dataId,
    String dataType,
    List<Predecessor> predecessors
) implements RecordComponent {
}
