package com.guardtime.trace4eo.signing;

import com.guardtime.trace4eo.signing.commands.BatchSigningTool;
import com.guardtime.trace4eo.signing.commands.SigningTool;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportRuntimeHints;

@SpringBootApplication
@ImportRuntimeHints(SigningToolApplication.NativeHints.class)
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
public class SigningToolApplication {

    public static void main(String[] args) {
        SpringApplication.run(SigningToolApplication.class, args);
    }

    static class NativeHints implements RuntimeHintsRegistrar {
        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            hints.reflection().registerType(SigningTool.class,
                MemberCategory.INVOKE_DECLARED_METHODS);
            hints.reflection().registerType(BatchSigningTool.class,
                MemberCategory.INVOKE_DECLARED_METHODS);
        }
    }
}
