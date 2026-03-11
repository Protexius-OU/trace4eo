package com.guardtime.trace4eo.verification;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.convert.support.ConfigurableConversionService;
import org.springframework.shell.core.ShellRunner;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spring Boot integration tests for {@link VerificationTool}.
 *
 * <p>These tests load the full application context and exercise the Spring Shell command dispatch
 * pipeline, including {@link PathConverter} type conversion and Spring dependency injection.
 */
@SpringBootTest
class VerificationToolApplicationTest {

    // Suppress actual shell command execution during Spring context startup.
    @MockitoBean(name = "springShellApplicationRunner")
    @SuppressWarnings("unused")
    private ApplicationRunner springShellApplicationRunner;

    @Autowired
    private VerificationTool verificationTool;

    @Autowired
    private ConfigurableConversionService conversionService;

    @Autowired
    private ShellRunner shellRunner;

    @Test
    void contextLoads() {
        assertNotNull(verificationTool);
    }

    @Test
    void pathConverterRegistersStringToPathConversion() {
        assertTrue(conversionService.canConvert(String.class, Path.class));
    }

    /**
     * Exercises the {@code verify-provenance-record} command's required {@code --file}
     * path parameter through the Spring Shell dispatch pipeline.
     */
    @Test
    void verifyProvenanceRecordCommandAcceptsFileParameter() {
        assertDoesNotThrow(() -> shellRunner.run(new String[]{
            "verify-provenance-record",
            "--file", "src/test/resources/provenance-record.json"
        }));
    }

    /**
     * Exercises the optional {@code --file-hash} string parameter. Spring Shell passes it
     * as-is (no type conversion), so this test verifies the parameter reaches the command method.
     */
    @Test
    void verifyProvenanceRecordCommandAcceptsFileHashParameter() {
        // SHA-256 of empty bytes (content of test.txt), base64-encoded
        String fileHash = "test.txt=47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU=";
        assertDoesNotThrow(() -> shellRunner.run(new String[]{
            "verify-provenance-record",
            "--file", "src/test/resources/provenance-record.json",
            "--file-hash", fileHash
        }));
    }

    /**
     * Exercises the optional {@code --file-hashes} path parameter. Spring Shell converts
     * the string argument to a {@link Path} using {@link PathConverter}.
     */
    @Test
    void verifyProvenanceRecordCommandAcceptsFileHashesParameter() {
        assertDoesNotThrow(() -> shellRunner.run(new String[]{
            "verify-provenance-record",
            "--file", "src/test/resources/provenance-record.json",
            "--file-hashes", "src/test/resources/hashes.txt"
        }));
    }
}
