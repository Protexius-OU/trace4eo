package com.guardtime.trace4eo.signing;

import com.guardtime.trace4eo.provenance.ProvenanceJsonMapper;
import com.guardtime.trace4eo.provenance.signing.ProvenanceSigningService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.shell.core.ShellRunner;
import org.springframework.shell.core.command.CommandExecutionException;

import java.net.http.HttpClient;
import java.util.Arrays;

@Configuration
public class SigningToolConfiguration {

    @Bean
    public ProvenanceJsonMapper provenanceJsonMapper() {
        return new ProvenanceJsonMapper();
    }

    @Bean
    public ProvenanceSigningService provenanceSigningService() {
        return new ProvenanceSigningService();
    }

    @Bean
    public HttpClient httpClient() {
        return HttpClient.newHttpClient();
    }

    @Bean
    public ApplicationRunner springShellApplicationRunner(ShellRunner shellRunner) {
        return args -> {
            try {
                String[] quotedArgs = Arrays.stream(args.getSourceArgs())
                        .map(SigningToolConfiguration::quoteIfNecessary)
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
        if (arg.contains(" ")) {
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
