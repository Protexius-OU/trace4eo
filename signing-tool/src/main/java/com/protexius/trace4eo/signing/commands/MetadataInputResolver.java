package com.protexius.trace4eo.signing.commands;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class MetadataInputResolver {

    public Map<String, String> resolve(List<String> inlineEntries) {
        Map<String, String> result = new LinkedHashMap<>();
        addFromInline(result, inlineEntries);
        return result.isEmpty() ? null : Map.copyOf(result);
    }

    private void addFromInline(Map<String, String> target, List<String> entries) {
        if (entries == null) {
            return;
        }
        for (String entry : entries) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            int eq = entry.indexOf('=');
            if (eq < 0) {
                throw new IllegalArgumentException(String.format(
                    "--metadata entry must be in key=value form: %s", entry));
            }
            String key = entry.substring(0, eq).trim();
            String value = entry.substring(eq + 1);
            putUnique(target, key, value);
        }
    }

    private void putUnique(Map<String, String> target, String key, String value) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("--metadata key must not be blank");
        }
        if (target.containsKey(key)) {
            throw new IllegalArgumentException(String.format(
                "Duplicate --metadata key '%s'", key));
        }
        target.put(key, value);
    }
}
