package com.protexius.trace4eo.sentinel2;

import java.util.List;

public record Sentinel2HashCheckRequest(List<Sentinel2HashCheckFileEntry> files) {
}
