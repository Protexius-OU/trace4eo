package com.guardtime.trace4eo.signing;

import com.guardtime.trace4eo.signing.commands.BatchSigningTool;
import com.guardtime.trace4eo.signing.commands.SigningTool;
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
            registerProvenanceReflection(hints, classLoader);
            registerProtobufReflection(hints, classLoader);
            registerBouncyCastleReflection(hints, classLoader);
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
        // 3. Gson deserialization + proto Builder methods: sigstore-java uses Gson to deserialize
        //    TUF target files (e.g. signing config, trust root) downloaded at runtime. Gson
        //    accesses fields directly via reflection. Additionally, protobuf JsonFormat calls
        //    getter methods (e.g. getMediaType()) on dev.sigstore Builder classes via reflection.
        //    We register DECLARED_FIELDS, constructors, and methods for the whole dev.sigstore
        //    package to avoid per-class whack-a-mole.
        private static void registerProtobufReflection(RuntimeHints hints, ClassLoader classLoader) {
            hints.reflection().registerTypeIfPresent(classLoader,
                    "com.google.protobuf.DescriptorProtos$FeatureSet",
                    MemberCategory.INVOKE_DECLARED_METHODS);
            hints.reflection().registerTypeIfPresent(classLoader,
                    "com.google.protobuf.DescriptorProtos$FeatureSet$Builder",
                    MemberCategory.INVOKE_DECLARED_METHODS);
            registerProtocolMessageEnums(hints, classLoader,
                    "com.google.protobuf", "com.google.api", "com.google.rpc", "dev.sigstore",
                    "io.intoto");
            registerForGsonDeserialization(hints, classLoader, "com.google.protobuf", "dev.sigstore", "io.intoto");
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
                            MemberCategory.ACCESS_DECLARED_FIELDS,
                            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                            MemberCategory.INVOKE_DECLARED_METHODS);
                }
            }
        }

        // Provenance record and result types are Java records. GraalVM requires all record
        // component accessor methods to be registered for reflection so that
        // Class.getRecordComponents() works at runtime (used by Jackson and internal serializers).
        // Scanning the whole com.guardtime.trace4eo.provenance package covers all current and
        // future record types without per-class maintenance.
        private static void registerProvenanceReflection(
                RuntimeHints hints, ClassLoader classLoader) {
            ClassPathScanningCandidateComponentProvider scanner =
                    new ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter((metadataReader, factory) -> true);
            for (BeanDefinition bd : scanner.findCandidateComponents(
                    "com.guardtime.trace4eo.provenance")) {
                hints.reflection().registerTypeIfPresent(classLoader,
                        bd.getBeanClassName(),
                        MemberCategory.ACCESS_DECLARED_FIELDS,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS);
            }
        }

        // BouncyCastle registers JCA service implementations (KeyFactory, Signature, etc.) by
        // string class name. GraalVM static analysis cannot trace these string references, so the
        // implementation classes are excluded from the image and throw ClassNotFoundException at
        // runtime. Scanning the whole org.bouncycastle package and registering all classes for
        // construction ensures every provider implementation is reachable.
        private static void registerBouncyCastleReflection(
                RuntimeHints hints, ClassLoader classLoader) {
            ClassPathScanningCandidateComponentProvider scanner =
                    new ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter((metadataReader, factory) -> true);
            for (BeanDefinition bd : scanner.findCandidateComponents("org.bouncycastle")) {
                hints.reflection().registerTypeIfPresent(classLoader,
                        bd.getBeanClassName(),
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS);
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
