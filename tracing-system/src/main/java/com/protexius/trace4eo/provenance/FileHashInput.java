package com.protexius.trace4eo.provenance;

import java.util.Base64;

public record FileHashInput(String filename, String hashValue) {

    public byte[] decodedHash() {
        return Base64.getDecoder().decode(hashValue);
    }
}
