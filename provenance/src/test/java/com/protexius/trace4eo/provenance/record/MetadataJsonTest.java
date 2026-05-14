package com.protexius.trace4eo.provenance.record;

import com.protexius.trace4eo.provenance.ProvenanceJsonMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class MetadataJsonTest {

    private final ProvenanceJsonMapper mapper = new ProvenanceJsonMapper();

    @Test
    void nullAttributesAreOmittedFromJson() {
        Metadata metadata = new Metadata("data-id", "data-type", List.of(), null);

        String json = mapper.writeValueAsString(metadata);

        assertFalse(json.contains("attributes"),
            "JSON for legacy records (null attributes) must not contain the 'attributes' key: " + json);
    }

    @Test
    void populatedAttributesRoundTrip() {
        Map<String, String> input = Map.of("env", "prod", "owner", "alice");
        Metadata metadata = new Metadata("data-id", "data-type", List.of(), input);

        String json = mapper.writeValueAsString(metadata);
        Metadata roundTripped = mapper.readValue(json, Metadata.class);

        assertEquals(input, roundTripped.attributes());
    }

    @Test
    void missingAttributesFieldDeserializesAsNull() {
        String legacyJson = "{\"dataId\":\"data-id\",\"dataType\":\"data-type\",\"predecessors\":[]}";

        Metadata metadata = mapper.readValue(legacyJson, Metadata.class);

        assertNull(metadata.attributes());
    }
}
