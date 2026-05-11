package com.protexius.trace4eo.provenance.sentinel2;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.nio.file.Path;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class Sentinel2TraceResponse {

    private Sentinel2TraceResponse() {
    }

    record TraceDto(
            String id,
            String event,
            @JsonProperty("hash_algorithm") String hashAlgorithm,
            TracingSignature signature
    ) {}

    public record Trace(
            String id,
            String event,
            String hashAlgorithm,
            Product product,
            TracingSignature signature
    ) {
        Optional<ProductEntry> getEntry(String fileName) {
            return product.contents().stream()
                    .filter(e -> e.path().getFileName().toString().equals(fileName))
                    .findFirst();
        }

        public boolean matchesProductFile(String fileName) {
            return fileName != null && fileName.equals(product.name());
        }
    }

    public record TracingSignature(
            String signature,
            String algorithm,
            String certificate,
            String message
    ) {
        public TracingSignature {
            Objects.requireNonNull(signature);
            Objects.requireNonNull(algorithm);
            Objects.requireNonNull(certificate);
            Objects.requireNonNull(message);
        }

        public byte[] signatureBytes() {
            return Base64.getDecoder().decode(signature);
        }
        public byte[] certificateBytes() {
            return Base64.getDecoder().decode(certificate);
        }
    }

    record ProductDto(String name, String event, String hash, List<ProductEntry> contents) {}
    public record Product(String name, String hash, List<ProductEntry> contents) {}

    public record ProductEntry(Path path, String hash) {
        public byte[] hashBytes() {
            return HexFormat.of().parseHex(hash);
        }
    }
}
