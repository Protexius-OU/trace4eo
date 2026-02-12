package com.guardtime.trace4eo.verification;

import com.guardtime.trace4eo.provenance.ProvenanceJsonMapper;
import com.guardtime.trace4eo.provenance.verification.ProvenanceVerificationService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.shell.core.ShellRunner;
import org.springframework.shell.core.command.CommandExecutionException;

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

    @Bean
    public ApplicationRunner springShellApplicationRunner(ShellRunner shellRunner) {
        return args -> {
            try {
                shellRunner.run(args.getSourceArgs());
            } catch (CommandExecutionException e) {
                Throwable root = rootCause(e);
                System.err.println("Error: " + root.getMessage());
                System.exit(1);
            }
        };
    }

    private static Throwable rootCause(Throwable t) {
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t;
    }
}
