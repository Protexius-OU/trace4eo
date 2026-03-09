package com.guardtime.trace4eo.verification;

import com.guardtime.trace4eo.provenance.ProvenanceJsonMapper;
import com.guardtime.trace4eo.provenance.verification.ProvenanceVerificationService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.shell.core.ShellRunner;
import org.springframework.shell.core.command.CommandExecutionException;

import java.util.Arrays;

@Configuration
public class VerificationToolConfiguration {

    @Bean
    public ProvenanceJsonMapper provenanceJsonMapper() {
        return new ProvenanceJsonMapper();
    }

    @Bean
    public ProvenanceVerificationService provenanceVerificationService() {
        return new ProvenanceVerificationService();
    }

    @Bean("text")
    public VerificationResultFormatter textVerificationResultFormatter() {
        return new TextVerificationResultFormatter();
    }

    @Bean("json")
    public VerificationResultFormatter jsonVerificationResultFormatter(ProvenanceJsonMapper provenanceJsonMapper) {
        return new JsonVerificationResultFormatter(provenanceJsonMapper);
    }

    @Bean
    public ApplicationRunner springShellApplicationRunner(ShellRunner shellRunner) {
        return args -> {
            try {
                String[] quotedArgs = Arrays.stream(args.getSourceArgs())
                        .map(VerificationToolConfiguration::quoteIfNecessary)
                        .toArray(String[]::new);
                shellRunner.run(quotedArgs);
            } catch (CommandExecutionException e) {
                Throwable root = rootCause(e);
                System.err.println(String.format("Error: %s", root.getMessage()));
                System.exit(1);
            }
        };
    }

    static String quoteIfNecessary(String arg) {
        if (arg.chars().anyMatch(Character::isWhitespace)) {
            return "\"" + arg.replace("\"", "\\\"") + "\"";
        }
        return arg;
    }

    private static Throwable rootCause(Throwable t) {
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t;
    }
}
