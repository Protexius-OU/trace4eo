package com.guardtime.trace4eo.signing;

import com.guardtime.trace4eo.provenance.ProvenanceJsonMapper;
import com.guardtime.trace4eo.provenance.signing.ProvenanceSigningService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.shell.core.command.CommandRegistry;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class SigningToolShellTest {

    @Autowired
    CommandRegistry commandRegistry;

    @MockitoBean
    ProvenanceSigningService provenanceSigningService;

    @MockitoBean
    ProvenanceJsonMapper provenanceJsonMapper;

    @Test
    void createProvenanceRecordCommandIsRegistered() {
        boolean found = commandRegistry.getCommands().stream()
            .anyMatch(cmd -> "create-provenance-record".equals(cmd.getName()));
        assertTrue(found, "create-provenance-record command should be registered");
    }
}
