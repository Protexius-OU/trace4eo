package com.guardtime.traceguard.provenance.container.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guardtime.ksi.hashing.HashAlgorithm;
import com.guardtime.traceguard.provenance.container.io.FileHasher;
import org.erdtman.jcs.JsonCanonicalizer;

import java.io.IOException;

public sealed interface RecordComponent permits Manifest, Metadata, FilesInfo {

    // TODO - serialization should probably be separate from data model
    default byte[] toCanonicalBytes() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        byte[] bytes = objectMapper.writeValueAsBytes(this);
        return new JsonCanonicalizer(bytes).getEncodedUTF8();
    }

    default FileHashInfo toFileHashInfo(HashAlgorithm hashAlgorithm) throws IOException {
        byte[] jsonBytes = toCanonicalBytes();
        byte[] hashBytes = FileHasher.hashBytes(hashAlgorithm, jsonBytes);
        return new FileHashInfo(hashAlgorithm, hashBytes);
    }
}
