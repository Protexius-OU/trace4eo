package com.protexius.trace4eo.signing.commands;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@Component
public class MetadataInputResolver {

    public Map<String, String> resolve(List<String> inlineEntries, Path metadataFile) {
        Map<String, String> result = new LinkedHashMap<>();
        addFromFile(result, metadataFile);
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
            putUnique(target, key, value, "--metadata");
        }
    }

    private void addFromFile(Map<String, String> target, Path metadataFile) {
        if (metadataFile == null) {
            return;
        }
        Properties properties = new Properties();
        try (BufferedReader reader = Files.newBufferedReader(metadataFile)) {
            properties.load(reader);
        } catch (IOException e) {
            throw new IllegalArgumentException(
                String.format("Cannot read --metadata-file: %s", metadataFile), e);
        }
        for (String key : properties.stringPropertyNames()) {
            putUnique(target, key, properties.getProperty(key), "--metadata-file");
        }
    }

    private void putUnique(Map<String, String> target, String key, String value, String source) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException(String.format("%s key must not be blank", source));
        }
        if (target.containsKey(key)) {
            throw new IllegalArgumentException(String.format(
                "Duplicate metadata key '%s' (from %s)", key, source));
        }
        target.put(key, value);
    }
}
