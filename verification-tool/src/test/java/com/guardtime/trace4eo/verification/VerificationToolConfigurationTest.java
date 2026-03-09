package com.guardtime.trace4eo.verification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.shell.core.ShellRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class VerificationToolConfigurationTest {

    private final VerificationToolConfiguration config = new VerificationToolConfiguration();

    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @CsvSource({
        // space in file name
        "/path/my file.json,             \"/path/my file.json\"",
        // space in directory
        "/my dir/file.json,              \"/my dir/file.json\"",
        // space in both directory and file name
        "/my dir/my file.json,           \"/my dir/my file.json\"",
        // no space -> unchanged
        "/path/file.json,                /path/file.json"
    })
    void applicationRunner_quotesArgsContainingSpaces(String input, String expected) throws Exception {
        ShellRunner mockShellRunner = mock(ShellRunner.class);
        var runner = config.springShellApplicationRunner(mockShellRunner);

        runner.run(new DefaultApplicationArguments(
            "verify-provenance-record", "--file", input
        ));

        ArgumentCaptor<String[]> captor = ArgumentCaptor.forClass(String[].class);
        verify(mockShellRunner).run(captor.capture());
        assertThat(captor.getValue()).contains(expected.strip());
    }

    @Test
    void applicationRunner_quotesArgsContainingTab() throws Exception {
        String pathWithTab = "/path/my\tfile.json";
        ShellRunner mockShellRunner = mock(ShellRunner.class);
        var runner = config.springShellApplicationRunner(mockShellRunner);

        runner.run(new DefaultApplicationArguments(
            "verify-provenance-record", "--file", pathWithTab
        ));

        ArgumentCaptor<String[]> captor = ArgumentCaptor.forClass(String[].class);
        verify(mockShellRunner).run(captor.capture());
        assertThat(captor.getValue()).contains("\"" + pathWithTab + "\"");
    }
}
