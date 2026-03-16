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
@ImportRuntimeHints(SigningToolApplication.Hints.class)
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
public class SigningToolApplication {

    public static void main(String[] args) {
        SpringApplication.run(SigningToolApplication.class, args);
    }

    static class Hints implements RuntimeHintsRegistrar {
        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            // NativeCommandsConfiguration calls getDeclaredMethods() on these classes
            // and CommandFactoryBean invokes the @Command methods via reflection.
            hints.reflection().registerType(SigningTool.class, MemberCategory.INVOKE_DECLARED_METHODS);
            hints.reflection().registerType(BatchSigningTool.class, MemberCategory.INVOKE_DECLARED_METHODS);
            hints.resources().registerPattern("dev/sigstore/**");
            // Sigstore's gRPC layer calls valueOf(Descriptors$EnumValueDescriptor) via reflection
            // on protobuf enums from proto-google-common-protos at runtime.
            hints.reflection().registerTypeIfPresent(classLoader, "com.google.api.FieldBehavior",
                    MemberCategory.INVOKE_DECLARED_METHODS);
        }
    }
}
