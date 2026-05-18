package com.protexius.trace4eo.provenance;

import java.util.List;

public record RecordFilterCriteria(
    List<String> dataTypes,
    String dataId,
    List<String> signerIdentities,
    AttributeFilter attributes
) {

    public static RecordFilterCriteria of(
        List<String> dataTypes,
        String dataId,
        List<String> signerIdentities,
        List<String> rawAttributeTokens
    ) {
        return new RecordFilterCriteria(
            dataTypes,
            dataId,
            signerIdentities,
            AttributeFilterParser.parse(rawAttributeTokens)
        );
    }

    public static RecordFilterCriteria none() {
        return new RecordFilterCriteria(null, null, null, AttributeFilter.empty());
    }
}
