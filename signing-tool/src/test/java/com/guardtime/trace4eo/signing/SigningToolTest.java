package com.guardtime.trace4eo.signing;

import com.guardtime.trace4eo.provenance.ProvenanceSignature;
import com.guardtime.trace4eo.provenance.record.ProvenanceRecord;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class SigningToolTest {

    private static final Logger log = LoggerFactory.getLogger(SigningToolTest.class);

    @Test
    void sign() {
        SigningTool signingTool = new SigningTool();
        String artifactPath = "src/test/resources/test.txt";
        ProvenanceSignature result = signingTool.sign(Path.of(artifactPath));
        assertNotNull(result);
        assertNotNull(result.bytes());
        assertNotNull(result.signingTime());
        assertNotNull(result.hashAlgorithm());
    }

    @Test
    void createProvenanceRecord() throws IOException {
        SigningTool signingTool = new SigningTool();
        List<Path> files = List.of(Path.of("src/test/resources/test.txt"));
        ProvenanceRecord result = signingTool.createProvenanceRecord(files, "test", "test",  List.of(), "SHA256");
        assertNotNull(result);
        assertNotNull(result.id());
        assertNotNull(result.filesInfo());
        assertNotNull(result.metadata());
        assertNotNull(result.manifest());
        log.info("Signed provenance record: {}", result);
    }
}
