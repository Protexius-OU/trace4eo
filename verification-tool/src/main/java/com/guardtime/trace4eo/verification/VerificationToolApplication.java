package com.guardtime.trace4eo.verification;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportRuntimeHints;

@SpringBootApplication
@ImportRuntimeHints(VerificationToolApplication.NativeHints.class)
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
public class VerificationToolApplication {

    public static void main(String[] args) {
        SpringApplication.run(VerificationToolApplication.class, args);
    }

    static class NativeHints implements RuntimeHintsRegistrar {
        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            hints.reflection().registerType(VerificationTool.class,
                MemberCategory.INVOKE_DECLARED_METHODS);
        }
    }
}
