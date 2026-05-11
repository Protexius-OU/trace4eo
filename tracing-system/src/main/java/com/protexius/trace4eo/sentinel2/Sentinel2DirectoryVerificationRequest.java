package com.protexius.trace4eo.sentinel2;

import java.util.List;

public record Sentinel2DirectoryVerificationRequest(List<Sentinel2FileVerificationRequest> files) {
}
