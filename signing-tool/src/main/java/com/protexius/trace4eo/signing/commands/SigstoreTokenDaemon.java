package com.protexius.trace4eo.signing.commands;

import com.protexius.trace4eo.signing.SigstoreDeviceFlowClient;
import com.protexius.trace4eo.signing.SigstoreDeviceFlowClient.OidcEndpoints;
import com.protexius.trace4eo.signing.SigstoreDeviceFlowClient.TokenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Base64;
import java.util.Set;

@Component
public class SigstoreTokenDaemon {

    private static final Logger log = LoggerFactory.getLogger(SigstoreTokenDaemon.class);
    private static final String DEFAULT_TOKEN_FILENAME = ".sigstore-id-token";
    private static final String SCOPE_OFFLINE = "openid email offline_access";
    private static final String SCOPE_BARE = "openid email";

    private final SigstoreDeviceFlowClient deviceFlowClient;
    private final ObjectMapper mapper;

    public SigstoreTokenDaemon(SigstoreDeviceFlowClient deviceFlowClient, ObjectMapper mapper) {
        this.deviceFlowClient = deviceFlowClient;
        this.mapper = mapper;
    }

    @Command(name = "sigstore-token-daemon",
        description = "Run a device-flow OIDC login against Sigstore, write the id_token to "
            + "--token-file, then loop refreshing it before expiry. Stop with Ctrl+C.")
    public void run(
        @Option(longName = "issuer",
            description = "Sigstore OIDC issuer (default: discover via Sigstore TUF)") String issuer,
        @Option(longName = "token-file",
            description = "Path to write the id_token to (default ~/.sigstore-id-token)") Path tokenFile,
        @Option(longName = "refresh-lead-seconds",
            description = "Refresh this many seconds before expiry",
            defaultValue = "10") int refreshLeadSeconds
    ) throws IOException {
        Path resolvedTokenFile = resolveTokenFile(tokenFile);
        try {
            OidcEndpoints endpoints = deviceFlowClient.discoverEndpoints(issuer);
            TokenResponse state = deviceFlowClient.runDeviceFlow(endpoints, SCOPE_OFFLINE);
            if (state.refreshToken() == null) {
                log.warn("Issuer did not return a refresh_token — will re-run device flow on each expiry.");
            }
            loopRefreshing(endpoints, resolvedTokenFile, refreshLeadSeconds, state);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("Daemon stopped.");
        }
    }

    private void loopRefreshing(
        OidcEndpoints endpoints, Path tokenFile, int refreshLeadSeconds, TokenResponse initialState
    ) throws IOException, InterruptedException {
        TokenResponse state = initialState;
        boolean hasRefresh = state.refreshToken() != null;
        while (true) {
            long sleepSeconds = writeToken(tokenFile, state.idToken(), refreshLeadSeconds);
            Thread.sleep(sleepSeconds * 1000L);
            if (hasRefresh) {
                TokenResponse refreshed = deviceFlowClient.refresh(endpoints, state.refreshToken());
                if (refreshed == null) {
                    log.warn("Refresh failed — re-running device flow");
                    state = deviceFlowClient.runDeviceFlow(endpoints, SCOPE_OFFLINE);
                    hasRefresh = state.refreshToken() != null;
                } else {
                    String carriedRefresh = refreshed.refreshToken() != null
                        ? refreshed.refreshToken() : state.refreshToken();
                    state = new TokenResponse(refreshed.idToken(), carriedRefresh);
                }
            } else {
                state = deviceFlowClient.runDeviceFlow(endpoints, SCOPE_BARE);
                hasRefresh = state.refreshToken() != null;
            }
        }
    }

    private Path resolveTokenFile(Path tokenFile) {
        if (tokenFile != null) {
            return tokenFile;
        }
        return Path.of(System.getProperty("user.home"), DEFAULT_TOKEN_FILENAME);
    }

    private long writeToken(Path tokenFile, String idToken, int refreshLeadSeconds) throws IOException {
        atomicWrite(tokenFile, idToken + "\n");
        long expEpoch = jwtExp(idToken);
        long remaining = expEpoch - (System.currentTimeMillis() / 1000L);
        long sleepSeconds = Math.max(10, remaining - refreshLeadSeconds);
        log.info("Wrote {} — valid for {}s (next refresh in {}s)", tokenFile, remaining, sleepSeconds);
        return sleepSeconds;
    }

    private static void atomicWrite(Path path, String content) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.deleteIfExists(tmp);
        createWithOwnerOnlyPermissions(tmp);
        Files.writeString(tmp, content);
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static void createWithOwnerOnlyPermissions(Path path) throws IOException {
        FileAttribute<Set<PosixFilePermission>> perms = PosixFilePermissions.asFileAttribute(
            PosixFilePermissions.fromString("rw-------"));
        try {
            Files.createFile(path, perms);
        } catch (UnsupportedOperationException ignored) {
            Files.createFile(path);
        } catch (FileAlreadyExistsException e) {
            throw new IOException("Token tmp file already exists: " + path, e);
        }
    }

    long jwtExp(String idToken) {
        String[] parts = idToken.split("\\.", -1);
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid JWT: expected at least 2 segments");
        }
        byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
        JsonNode claims;
        try {
            claims = mapper.readTree(decoded);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JWT payload", e);
        }
        JsonNode exp = claims.get("exp");
        if (exp == null) {
            throw new IllegalArgumentException("JWT has no exp claim");
        }
        return exp.asLong();
    }
}
