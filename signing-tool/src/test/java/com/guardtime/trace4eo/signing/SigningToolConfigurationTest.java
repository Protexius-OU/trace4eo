package com.guardtime.trace4eo.signing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.shell.core.ShellRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SigningToolConfigurationTest {

    private final SigningToolConfiguration config = new SigningToolConfiguration();

    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @CsvSource({
        // space in file name
        "/path/my file.txt,              \"/path/my file.txt\"",
        // space in directory
        "/my dir/file.txt,               \"/my dir/file.txt\"",
        // space in both directory and file name
        "/my dir/my file.txt,            \"/my dir/my file.txt\"",
        // no space -> unchanged
        "/path/file.txt,                 /path/file.txt",
        // no space, option-like value -> unchanged
        "satellite-imagery,              satellite-imagery"
    })
    void applicationRunner_quotesArgsContainingSpaces(String input, String expected) throws Exception {
        ShellRunner mockShellRunner = mock(ShellRunner.class);
        var runner = config.springShellApplicationRunner(mockShellRunner);

        runner.run(new DefaultApplicationArguments(
            "create-provenance-record", "--files", input,
            "--provenance-record-type", "satellite-imagery", "--data-id", "test"
        ));

        ArgumentCaptor<String[]> captor = ArgumentCaptor.forClass(String[].class);
        verify(mockShellRunner).run(captor.capture());
        assertThat(captor.getValue()).contains(expected.strip());
    }

    @Test
    void applicationRunner_quotesArgsContainingTab() throws Exception {
        String pathWithTab = "/path/my\tfile.txt";
        ShellRunner mockShellRunner = mock(ShellRunner.class);
        var runner = config.springShellApplicationRunner(mockShellRunner);

        runner.run(new DefaultApplicationArguments(
            "create-provenance-record", "--files", pathWithTab,
            "--provenance-record-type", "satellite-imagery", "--data-id", "test"
        ));

        ArgumentCaptor<String[]> captor = ArgumentCaptor.forClass(String[].class);
        verify(mockShellRunner).run(captor.capture());
        assertThat(captor.getValue()).contains("\"" + pathWithTab + "\"");
    }
}
