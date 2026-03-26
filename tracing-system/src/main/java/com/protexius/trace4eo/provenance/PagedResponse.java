package com.protexius.trace4eo.provenance;

import java.util.List;

public record PagedResponse<T>(
    List<T> content,
    long totalElements,
    int totalPages,
    int page,
    int size
) {
    public static <T> PagedResponse<T> of(List<T> content, long totalElements, int page, int size) {
        int totalPages = (int) Math.ceil((double) totalElements / size);
        return new PagedResponse<>(content, totalElements, totalPages, page, size);
    }
}
