package com.guardtime.trace4eo.verification;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ImportRuntimeHints;

import java.util.List;

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
            // NativeCommandsConfiguration calls getDeclaredMethods() on this class
            // and CommandFactoryBean invokes the @Command method via reflection.
            hints.reflection().registerType(VerificationTool.class, MemberCategory.INVOKE_DECLARED_METHODS);
            hints.resources().registerPattern("dev/sigstore/**");
            registerProtobufReflection(hints, classLoader);
        }

        // Protobuf 4.x makes two categories of reflective calls at runtime that are invisible
        // to GraalVM's static analysis:
        //
        // 1. Descriptor loading: every proto class's static initializer calls
        //    DescriptorProtos$FeatureSet.getXxx() to resolve edition features. Registering
        //    all declared methods on FeatureSet covers every proto file in one shot.
        //
        // 2. Enum valueOf: when processing a message field of an enum type, protobuf calls
        //    SomeEnum.valueOf(EnumValueDescriptor) via reflection. This is done for every
        //    distinct enum type in the sigstore/googleapis protos. Rather than fixing these
        //    one by one, we scan for every ProtocolMessageEnum implementation at AOT time
        //    and register them all upfront.
        //
        // 3. Gson deserialization: sigstore-java uses Gson to deserialize TUF target files
        //    (e.g. signing config, trust root) downloaded at runtime. Gson accesses fields
        //    directly via reflection, so every dev.sigstore class that participates in JSON
        //    deserialization needs DECLARED_FIELDS. We scan the whole package to avoid
        //    per-class whack-a-mole.
        private static void registerProtobufReflection(RuntimeHints hints, ClassLoader classLoader) {
            hints.reflection().registerTypeIfPresent(classLoader,
                    "com.google.protobuf.DescriptorProtos$FeatureSet",
                    MemberCategory.INVOKE_DECLARED_METHODS);
            hints.reflection().registerTypeIfPresent(classLoader,
                    "com.google.protobuf.DescriptorProtos$FeatureSet$Builder",
                    MemberCategory.INVOKE_DECLARED_METHODS);
            registerProtocolMessageEnums(hints, classLoader,
                    "com.google.protobuf", "com.google.api", "com.google.rpc", "dev.sigstore");
            registerForGsonDeserialization(hints, classLoader, "dev.sigstore");
        }

        private static void registerForGsonDeserialization(
                RuntimeHints hints, ClassLoader classLoader, String... packages) {
            ClassPathScanningCandidateComponentProvider scanner =
                    new ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter((metadataReader, factory) -> true);
            for (String pkg : List.of(packages)) {
                for (BeanDefinition bd : scanner.findCandidateComponents(pkg)) {
                    hints.reflection().registerTypeIfPresent(classLoader,
                            bd.getBeanClassName(),
                            MemberCategory.DECLARED_FIELDS,
                            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
                }
            }
        }

        private static void registerProtocolMessageEnums(
                RuntimeHints hints, ClassLoader classLoader, String... packages) {
            ClassPathScanningCandidateComponentProvider scanner =
                    new ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter((metadataReader, factory) -> {
                for (String iface : metadataReader.getClassMetadata().getInterfaceNames()) {
                    if ("com.google.protobuf.ProtocolMessageEnum".equals(iface)) {
                        return true;
                    }
                }
                return false;
            });
            for (String pkg : List.of(packages)) {
                for (BeanDefinition bd : scanner.findCandidateComponents(pkg)) {
                    hints.reflection().registerTypeIfPresent(classLoader,
                            bd.getBeanClassName(), MemberCategory.INVOKE_DECLARED_METHODS);
                }
            }
        }
    }
}
