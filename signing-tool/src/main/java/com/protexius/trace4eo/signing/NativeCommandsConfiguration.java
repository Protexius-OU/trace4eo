package com.protexius.trace4eo.signing;

import com.protexius.trace4eo.signing.commands.BatchSigningTool;
import com.protexius.trace4eo.signing.commands.SigningTool;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.shell.core.command.CommandRegistry;
import org.springframework.shell.core.command.annotation.support.CommandFactoryBean;
import org.springframework.shell.core.utils.Utils;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Provides a {@link CommandRegistry} bean to suppress
 * {@code CommandRegistryAutoConfiguration}'s classpath scanning, which is
 * incompatible with GraalVM native images. Commands are registered explicitly
 * via {@link CommandFactoryBean} {@code @Bean} methods so Spring AOT can
 * generate code for them without requiring runtime class-file access.
 */
@Configuration(proxyBeanMethods = false)
class NativeCommandsConfiguration {

    @Bean
    CommandRegistry commandRegistry() {
        CommandRegistry registry = new CommandRegistry();
        registry.registerCommand(Utils.QUIT_COMMAND);
        return registry;
    }

    @Bean
    CommandFactoryBean createProvenanceRecordCommand() {
        return commandBean(SigningTool.class, "createProvenanceRecord");
    }

    @Bean
    CommandFactoryBean batchSignCommand() {
        return commandBean(BatchSigningTool.class, "batchSign");
    }

    @Bean
    CommandFactoryBean getOidcTokenCommand() {
        return commandBean(SigningTool.class, "getOidcToken");
    }

    private CommandFactoryBean commandBean(Class<?> cls, String methodName) {
        Method method = Arrays.stream(cls.getDeclaredMethods())
            .filter(m -> m.getName().equals(methodName))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "Command method not found: " + cls.getSimpleName() + "." + methodName));
        return new CommandFactoryBean(method);
    }
}
