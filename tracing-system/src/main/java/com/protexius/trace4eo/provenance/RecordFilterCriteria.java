package com.protexius.trace4eo.provenance;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record RecordFilterCriteria(
    List<String> dataTypes,
    String dataId,
    List<String> signerIdentities,
    AttributeFilter attributes,
    Set<UUID> recordIds
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
            AttributeFilterParser.parse(rawAttributeTokens),
            null
        );
    }

    public static RecordFilterCriteria none() {
        return new RecordFilterCriteria(null, null, null, AttributeFilter.empty(), null);
    }

    public RecordFilterCriteria withRecordIds(Set<UUID> ids) {
        return new RecordFilterCriteria(dataTypes, dataId, signerIdentities, attributes, ids);
    }
}
