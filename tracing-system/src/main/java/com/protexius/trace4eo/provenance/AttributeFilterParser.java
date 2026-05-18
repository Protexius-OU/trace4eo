package com.protexius.trace4eo.provenance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

final class AttributeFilterParser {

    private static final Pattern WHITESPACE = Pattern.compile("\\s");

    private AttributeFilterParser() {
    }

    static AttributeFilter parse(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return AttributeFilter.empty();
        }
        Map<String, LinkedHashSet<String>> grouped = new LinkedHashMap<>();
        for (String token : tokens) {
            Map.Entry<String, String> pair = splitToken(token);
            grouped.computeIfAbsent(pair.getKey(), k -> new LinkedHashSet<>()).add(pair.getValue());
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        grouped.forEach((key, values) -> result.put(key, new ArrayList<>(values)));
        return new AttributeFilter(result);
    }

    private static Map.Entry<String, String> splitToken(String token) {
        if (token == null) {
            throw new IllegalArgumentException("Invalid attribute filter token: null");
        }
        int firstEquals = token.indexOf('=');
        if (firstEquals < 0) {
            throw new IllegalArgumentException("Invalid attribute filter token: " + token);
        }
        String key = token.substring(0, firstEquals);
        String value = token.substring(firstEquals + 1);
        if (key.isEmpty() || WHITESPACE.matcher(key).find()) {
            throw new IllegalArgumentException("Invalid attribute filter token: " + token);
        }
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Invalid attribute filter token: " + token);
        }
        return Map.entry(key, value);
    }
}
