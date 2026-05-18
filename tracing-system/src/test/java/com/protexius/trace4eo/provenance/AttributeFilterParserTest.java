package com.protexius.trace4eo.provenance;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttributeFilterParserTest {

    @Test
    void parseNullReturnsEmpty() {
        AttributeFilter filter = AttributeFilterParser.parse(null);
        assertTrue(filter.isEmpty());
    }

    @Test
    void parseEmptyListReturnsEmpty() {
        AttributeFilter filter = AttributeFilterParser.parse(List.of());
        assertTrue(filter.isEmpty());
    }

    @Test
    void parseSingleToken() {
        AttributeFilter filter = AttributeFilterParser.parse(List.of("location=oslo"));
        assertEquals(1, filter.keyToValues().size());
        assertEquals(List.of("oslo"), filter.keyToValues().get("location"));
    }

    @Test
    void parseMultipleTokensSameKeyGroupsValues() {
        AttributeFilter filter = AttributeFilterParser.parse(List.of(
            "location=oslo",
            "location=bergen"
        ));
        assertEquals(1, filter.keyToValues().size());
        assertEquals(List.of("oslo", "bergen"), filter.keyToValues().get("location"));
    }

    @Test
    void parseMultipleTokensDifferentKeysProducesMultipleEntries() {
        AttributeFilter filter = AttributeFilterParser.parse(List.of(
            "location=oslo",
            "env=prod"
        ));
        assertEquals(2, filter.keyToValues().size());
        assertEquals(List.of("oslo"), filter.keyToValues().get("location"));
        assertEquals(List.of("prod"), filter.keyToValues().get("env"));
    }

    @Test
    void parseSplitsOnFirstEquals() {
        AttributeFilter filter = AttributeFilterParser.parse(List.of("key=a=b"));
        assertEquals(List.of("a=b"), filter.keyToValues().get("key"));
    }

    @Test
    void parseDuplicateKeyValueDeduplicates() {
        AttributeFilter filter = AttributeFilterParser.parse(List.of(
            "location=oslo",
            "location=oslo"
        ));
        assertEquals(List.of("oslo"), filter.keyToValues().get("location"));
    }

    @Test
    void parseEmptyKeyThrows() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> AttributeFilterParser.parse(List.of("=value")));
        assertTrue(ex.getMessage().contains("=value"));
    }

    @Test
    void parseEmptyValueThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> AttributeFilterParser.parse(List.of("key=")));
    }

    @Test
    void parseTokenWithoutEqualsThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> AttributeFilterParser.parse(List.of("noequals")));
    }

    @Test
    void parseKeyWithSpaceThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> AttributeFilterParser.parse(List.of("bad key=value")));
    }

    @Test
    void parseKeyWithTabThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> AttributeFilterParser.parse(List.of("bad\tkey=value")));
    }

    @Test
    void parseAllowsKeyWithPunctuation() {
        AttributeFilter filter = AttributeFilterParser.parse(List.of("data.set_id-1=v"));
        assertEquals(List.of("v"), filter.keyToValues().get("data.set_id-1"));
    }

    @Test
    void parseAllowsKeyWithSlashesAndOtherCharacters() {
        AttributeFilter filter = AttributeFilterParser.parse(List.of("path/to:thing=v"));
        assertEquals(List.of("v"), filter.keyToValues().get("path/to:thing"));
    }
}
