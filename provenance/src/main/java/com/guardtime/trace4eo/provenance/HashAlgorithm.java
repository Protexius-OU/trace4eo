package com.guardtime.trace4eo.provenance;

public enum HashAlgorithm {
    MD5("MD5"),
    SHA1("SHA-1"),
    SHA256("SHA-256"),
    SHA384("SHA-384"),
    SHA512("SHA-512");

    private final String name;

    HashAlgorithm(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
