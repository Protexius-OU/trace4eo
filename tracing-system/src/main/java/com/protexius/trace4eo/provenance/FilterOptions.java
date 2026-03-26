package com.protexius.trace4eo.provenance;

import java.util.List;

public record FilterOptions(
    List<String> dataTypes,
    List<String> signerIdentities
) {}
