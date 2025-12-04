package com.guardtime.trace4eo.provenance.container.model;

import java.util.List;

public record Metadata(
    String dataId,
    String dataType,
    List<Predecessor> predecessors
) implements RecordComponent {
}
