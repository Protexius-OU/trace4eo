package com.protexius.trace4eo.provenance.record;

import java.util.List;
import java.util.Map;

public record Metadata(
    String dataId,
    String dataType,
    List<Predecessor> predecessors,
    Map<String, String> attributes
) implements RecordComponent {
    public Metadata {
        if (attributes != null) {
            attributes = Map.copyOf(attributes);
        }
    }
}
