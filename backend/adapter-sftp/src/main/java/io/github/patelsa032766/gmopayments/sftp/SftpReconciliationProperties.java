package io.github.patelsa032766.gmopayments.sftp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Environment-backed SFTP settings.
 *
 * <p>No useful credential or host default exists. Enabling the adapter with
 * incomplete settings fails at connection time instead of silently weakening
 * host verification.</p>
 */
@Component
public final class SftpReconciliationProperties {
    private final boolean enabled;
    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String privateKeyPath;
    private final String privateKeyPassphrase;
    private final String knownHostsPath;
    private final String incomingPath;
    private final String archivePath;
    private final String readyMarkerSuffix;
    private final String filenameRegex;
    private final long maxFileBytes;
    private final Duration connectTimeout;

    public SftpReconciliationProperties(
            @Value("${reconciliation.sftp.enabled:false}") boolean enabled,
            @Value("${reconciliation.sftp.host:}") String host,
            @Value("${reconciliation.sftp.port:22}") int port,
            @Value("${reconciliation.sftp.username:}") String username,
            @Value("${reconciliation.sftp.password:}") String password,
            @Value("${reconciliation.sftp.private-key-path:}") String privateKeyPath,
            @Value("${reconciliation.sftp.private-key-passphrase:}") String privateKeyPassphrase,
            @Value("${reconciliation.sftp.known-hosts-path:}") String knownHostsPath,
            @Value("${reconciliation.sftp.incoming-path:/incoming}") String incomingPath,
            @Value("${reconciliation.sftp.archive-path:/archive}") String archivePath,
            @Value("${reconciliation.sftp.ready-marker-suffix:.ok}") String readyMarkerSuffix,
            @Value("${reconciliation.sftp.filename-regex:^gmo-reconciliation-[A-Za-z0-9._-]+\\.(csv|tsv)$}") String filenameRegex,
            @Value("${reconciliation.sftp.max-file-bytes:10485760}") long maxFileBytes,
            @Value("${reconciliation.sftp.connect-timeout-ms:10000}") long connectTimeoutMs) {
        this.enabled = enabled;
        this.host = trim(host);
        this.port = port;
        this.username = trim(username);
        this.password = password == null ? "" : password;
        this.privateKeyPath = trim(privateKeyPath);
        this.privateKeyPassphrase = privateKeyPassphrase == null ? "" : privateKeyPassphrase;
        this.knownHostsPath = trim(knownHostsPath);
        this.incomingPath = safeRemoteDirectory(incomingPath, "incoming-path");
        this.archivePath = safeRemoteDirectory(archivePath, "archive-path");
        this.readyMarkerSuffix = safeSuffix(readyMarkerSuffix);
        this.filenameRegex = filenameRegex;
        this.maxFileBytes = maxFileBytes;
        this.connectTimeout = Duration.ofMillis(connectTimeoutMs);
    }

    public boolean enabled() { return enabled; }
    public String host() { return host; }
    public int port() { return port; }
    public String username() { return username; }
    public String password() { return password; }
    public String privateKeyPath() { return privateKeyPath; }
    public String privateKeyPassphrase() { return privateKeyPassphrase; }
    public String knownHostsPath() { return knownHostsPath; }
    public String incomingPath() { return incomingPath; }
    public String archivePath() { return archivePath; }
    public String readyMarkerSuffix() { return readyMarkerSuffix; }
    public String filenameRegex() { return filenameRegex; }
    public long maxFileBytes() { return maxFileBytes; }
    public Duration connectTimeout() { return connectTimeout; }

    public void validateForConnection() {
        if (!enabled) return;
        if (host.isBlank()) throw new IllegalStateException("SFTP host is required when reconciliation is enabled");
        if (port < 1 || port > 65_535) throw new IllegalStateException("SFTP port is invalid");
        if (username.isBlank()) throw new IllegalStateException("SFTP username is required");
        if (knownHostsPath.isBlank()) {
            throw new IllegalStateException("SFTP known-hosts-path is required; host-key checks cannot be disabled");
        }
        if (!Path.of(knownHostsPath).toFile().isFile()) {
            throw new IllegalStateException("SFTP known-hosts file does not exist: " + knownHostsPath);
        }
        boolean passwordConfigured = !password.isBlank();
        boolean keyConfigured = !privateKeyPath.isBlank();
        if (passwordConfigured == keyConfigured) {
            throw new IllegalStateException("Configure exactly one SFTP authentication mode: password or private key");
        }
        if (keyConfigured && !Path.of(privateKeyPath).toFile().isFile()) {
            throw new IllegalStateException("SFTP private key does not exist: " + privateKeyPath);
        }
        if (maxFileBytes < 1) throw new IllegalStateException("SFTP max-file-bytes must be positive");
        if (connectTimeout.isZero() || connectTimeout.isNegative()) {
            throw new IllegalStateException("SFTP connect timeout must be positive");
        }
    }

    private static String safeRemoteDirectory(String value, String setting) {
        String path = trim(value);
        if (path.isBlank() || !path.startsWith("/") || path.contains("..") || path.contains("\0")) {
            throw new IllegalArgumentException("SFTP " + setting + " must be an absolute path without '..'");
        }
        return path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    private static String safeSuffix(String value) {
        String suffix = trim(value);
        if (suffix.isBlank() || suffix.contains("/") || suffix.contains("\\") || suffix.contains("..")) {
            throw new IllegalArgumentException("SFTP ready-marker-suffix is unsafe");
        }
        return suffix;
    }

    private static String trim(String value) { return value == null ? "" : value.trim(); }
}
