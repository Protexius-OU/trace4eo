package com.protexius.trace4eo.provenance;

import java.util.List;
import java.util.Map;

public record AttributeFilter(Map<String, List<String>> keyToValues) {

    public static AttributeFilter empty() {
        return new AttributeFilter(Map.of());
    }

    public boolean isEmpty() {
        return keyToValues == null || keyToValues.isEmpty();
    }
}
