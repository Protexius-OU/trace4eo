package com.protexius.trace4eo.signing.commands;

import com.protexius.trace4eo.provenance.Container;
import com.protexius.trace4eo.provenance.ProvenanceJsonMapper;
import com.protexius.trace4eo.provenance.io.ContainerReader;
import com.protexius.trace4eo.provenance.io.json.JsonContainerReader;
import com.protexius.trace4eo.provenance.io.zip.ZipContainerReader;
import com.protexius.trace4eo.provenance.record.Predecessor;
import com.protexius.trace4eo.provenance.record.ProvenanceRecord;
import com.protexius.trace4eo.signing.OidcTokenResolver;
import com.protexius.trace4eo.signing.registration.RecordRegistrationClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class RegisterRecordsTool {

    private static final Logger log = LoggerFactory.getLogger(RegisterRecordsTool.class);

    private final SigningInputValidator validator;
    private final RecordRegistrationClient registrationClient;
    private final OidcTokenResolver oidcTokenResolver;
    private final ProvenanceJsonMapper provenanceJsonMapper;

    public RegisterRecordsTool(
        SigningInputValidator validator,
        RecordRegistrationClient registrationClient,
        OidcTokenResolver oidcTokenResolver,
        ProvenanceJsonMapper provenanceJsonMapper
    ) {
        this.validator = validator;
        this.registrationClient = registrationClient;
        this.oidcTokenResolver = oidcTokenResolver;
        this.provenanceJsonMapper = provenanceJsonMapper;
    }

    @Command(name = "register-records", description = "Register previously-signed provenance records to the tracing system")
    public List<UUID> registerRecords(
        @Option(longName = "records", description = "Paths to provenance record files (zip or json)") List<String> records,
        @Option(longName = "directory", description = "Directory containing provenance record files") Path directory,
        @Option(longName = "pattern",
            description = "Glob pattern for files in --directory",
            defaultValue = "*.zip") String pattern,
        @Option(longName = "register-url",
            description = "Tracing backend URL to register provenance records",
            required = true) String registerUrl,
        @Option(longName = "keycloak-url",
            description = "Keycloak server URL (for registration auth)",
            required = true) String keycloakUrl,
        @Option(longName = "realm", description = "Keycloak realm", defaultValue = "trace4eo") String realm
    ) throws IOException {
        List<Path> sources = validateAndResolveSources(records, directory, pattern, registerUrl, keycloakUrl);
        String accessToken = exchangeToken(keycloakUrl, realm);
        registrationClient.checkSignerAccess(registerUrl, accessToken);
        List<ProvenanceRecord> loaded = loadRecords(sources);
        if (loaded.isEmpty()) {
            throw new IllegalArgumentException("No provenance records found in input files");
        }
        validateExternalPredecessors(loaded, registerUrl, accessToken);
        return post(loaded, registerUrl, accessToken);
    }

    private List<Path> validateAndResolveSources(
        List<String> records, Path directory, String pattern, String registerUrl, String keycloakUrl
    ) throws IOException {
        if ((records == null || records.isEmpty()) && directory == null) {
            throw new IllegalArgumentException("At least one of --records or --directory must be provided");
        }
        if (registerUrl == null || registerUrl.isBlank()) {
            throw new IllegalArgumentException("--register-url must not be null or blank");
        }
        if (keycloakUrl == null || keycloakUrl.isBlank()) {
            throw new IllegalArgumentException("--keycloak-url must not be null or blank");
        }
        if (directory != null) {
            validator.validateInputDirectory(directory);
            validator.validateGlobPattern(pattern);
        }
        List<Path> sources = resolveSources(records, directory, pattern);
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("No record files found matching the given --records or --directory/--pattern");
        }
        validator.validateFilesExist(sources);
        return sources;
    }

    private List<Path> resolveSources(List<String> records, Path directory, String pattern) throws IOException {
        List<Path> result = new ArrayList<>();
        if (records != null) {
            for (String record : records) {
                result.add(Path.of(record));
            }
        }
        if (directory != null) {
            List<Path> fromDirectory = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, pattern)) {
                for (Path path : stream) {
                    if (Files.isRegularFile(path)) {
                        fromDirectory.add(path);
                    }
                }
            }
            fromDirectory.sort(Path::compareTo);
            result.addAll(fromDirectory);
        }
        return result.stream().map(Path::toAbsolutePath).distinct().toList();
    }

    private List<ProvenanceRecord> loadRecords(List<Path> sources) throws IOException {
        List<ProvenanceRecord> loaded = new ArrayList<>();
        Map<UUID, Path> seenIds = new LinkedHashMap<>();
        for (Path source : sources) {
            Container container = readContainer(source);
            for (ProvenanceRecord record : container.provenanceRecords()) {
                Path previous = seenIds.put(record.id(), source);
                if (previous != null) {
                    log.warn("Skipping duplicate record {} from {} (already loaded from {})",
                        record.id(), source, previous);
                    continue;
                }
                loaded.add(record);
            }
        }
        log.info("Loaded {} provenance record(s) from {} source file(s)", loaded.size(), sources.size());
        return loaded;
    }

    private Container readContainer(Path source) throws IOException {
        ContainerReader reader = source.toString().toLowerCase(Locale.ROOT).endsWith(".zip")
            ? new ZipContainerReader(provenanceJsonMapper)
            : new JsonContainerReader(provenanceJsonMapper);
        try {
            return reader.read(source);
        } catch (IOException e) {
            throw new IOException(String.format("Failed to read provenance record from %s: %s", source, e.getMessage()), e);
        }
    }

    private String exchangeToken(String keycloakUrl, String realm) {
        String oidcToken = oidcTokenResolver.resolve();
        if (oidcToken == null) {
            throw new IllegalStateException(
                "No OIDC token available. Set SIGSTORE_ID_TOKEN or enable browser-based login.");
        }
        return registrationClient.exchangeToken(keycloakUrl, realm, oidcToken);
    }

    private void validateExternalPredecessors(
        List<ProvenanceRecord> loaded, String registerUrl, String accessToken
    ) {
        Set<UUID> loadedIds = loaded.stream().map(ProvenanceRecord::id).collect(Collectors.toSet());
        List<UUID> external = loaded.stream()
            .flatMap(record -> record.metadata().predecessors().stream())
            .map(Predecessor::id)
            .filter(id -> !loadedIds.contains(id))
            .distinct()
            .toList();
        if (external.isEmpty()) {
            return;
        }
        List<UUID> missing = registrationClient.findMissingPredecessors(external, registerUrl, accessToken);
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(String.format(
                "Predecessor records not found in tracing system: %s. "
                    + "Register them first or include them in --records/--directory.", missing));
        }
    }

    private List<UUID> post(List<ProvenanceRecord> loaded, String registerUrl, String accessToken) {
        log.info("Registering {} provenance record(s) to tracing system at {}...", loaded.size(), registerUrl);
        List<String> failures = registrationClient.registerRecords(loaded, registerUrl, accessToken);
        int successCount = loaded.size() - failures.size();
        log.info("Registered {}/{} provenance record(s)", successCount, loaded.size());
        if (!failures.isEmpty()) {
            throw new IllegalStateException(String.format(
                "Failed to register %d of %d provenance record(s): %s",
                failures.size(), loaded.size(), String.join("; ", failures)));
        }
        return loaded.stream().map(ProvenanceRecord::id).toList();
    }
}
