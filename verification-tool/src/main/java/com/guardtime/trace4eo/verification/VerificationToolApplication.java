package com.guardtime.trace4eo.verification;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportRuntimeHints;

@SpringBootApplication
@ImportRuntimeHints(VerificationToolApplication.Hints.class)
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
public class VerificationToolApplication {

    public static void main(String[] args) {
        SpringApplication.run(VerificationToolApplication.class, args);
    }

    static class Hints implements RuntimeHintsRegistrar {
        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            hints.resources().registerPattern("META-INF/spring.components");
        }
    }
}
