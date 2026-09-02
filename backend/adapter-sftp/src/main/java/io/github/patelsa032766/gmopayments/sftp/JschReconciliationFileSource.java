package io.github.patelsa032766.gmopayments.sftp;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import io.github.patelsa032766.gmopayments.application.port.ReconciliationFileSource;
import io.github.patelsa032766.gmopayments.domain.DownloadedReconciliationFile;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * SFTP implementation with mandatory host-key verification.
 *
 * <p>The adapter downloads only regular files whose names match the configured
 * allow-list regex and whose companion ready marker is present. It never logs
 * credentials, file contents, or private-key material.</p>
 */
@Component
public final class JschReconciliationFileSource implements ReconciliationFileSource {
    private static final DateTimeFormatter ARCHIVE_SUFFIX = DateTimeFormatter
            .ofPattern("yyyyMMddHHmmssSSS").withZone(ZoneOffset.UTC);

    private final SftpReconciliationProperties properties;
    private final Pattern filenamePattern;

    public JschReconciliationFileSource(SftpReconciliationProperties properties) {
        this.properties = properties;
        this.filenamePattern = Pattern.compile(properties.filenameRegex());
    }

    @Override
    public boolean enabled() {
        return properties.enabled();
    }

    @Override
    public List<DownloadedReconciliationFile> fetchReadyFiles() {
        if (!enabled()) return List.of();
        return withChannel(channel -> {
            @SuppressWarnings("unchecked")
            List<ChannelSftp.LsEntry> entries = channel.ls(properties.incomingPath());
            Set<String> regularNames = new HashSet<>();
            for (ChannelSftp.LsEntry entry : entries) {
                if (!entry.getAttrs().isDir() && !entry.getAttrs().isLink()) {
                    regularNames.add(entry.getFilename());
                }
            }

            List<String> readyDataNames = regularNames.stream()
                    .filter(this::safeRemoteName)
                    .filter(name -> filenamePattern.matcher(name).matches())
                    .filter(name -> regularNames.contains(name + properties.readyMarkerSuffix()))
                    .sorted(Comparator.naturalOrder()).toList();
            List<DownloadedReconciliationFile> files = new ArrayList<>();
            for (String name : readyDataNames) {
                ChannelSftp.LsEntry entry = entries.stream()
                        .filter(candidate -> candidate.getFilename().equals(name)).findFirst().orElseThrow();
                long size = entry.getAttrs().getSize();
                if (size < 0 || size > properties.maxFileBytes()) {
                    throw new IllegalStateException("SFTP file exceeds configured size limit: " + name);
                }
                ByteArrayOutputStream output = new ByteArrayOutputStream((int) Math.min(size, 65_536));
                channel.get(remote(properties.incomingPath(), name), output);
                if (output.size() > properties.maxFileBytes()) {
                    throw new IllegalStateException("SFTP file exceeded size limit while downloading: " + name);
                }
                files.add(new DownloadedReconciliationFile(name, output.toByteArray(),
                        name + properties.readyMarkerSuffix()));
            }
            return List.copyOf(files);
        });
    }

    @Override
    public void archive(DownloadedReconciliationFile file) {
        if (!enabled()) throw new IllegalStateException("SFTP reconciliation is disabled");
        if (!safeRemoteName(file.remoteName()) || !safeRemoteName(file.readyMarkerName())) {
            throw new IllegalArgumentException("Unsafe SFTP archive filename");
        }
        withChannel(channel -> {
            ensureArchiveDirectory(channel);
            // Timestamped targets avoid overwriting prior evidence. Moving the
            // imported data first is safe because the local checksum is already
            // committed; an orphaned marker is harmless and observable.
            String suffix = "." + ARCHIVE_SUFFIX.format(Instant.now());
            channel.rename(remote(properties.incomingPath(), file.remoteName()),
                    remote(properties.archivePath(), file.remoteName() + suffix));
            channel.rename(remote(properties.incomingPath(), file.readyMarkerName()),
                    remote(properties.archivePath(), file.readyMarkerName() + suffix));
            return null;
        });
    }

    private void ensureArchiveDirectory(ChannelSftp channel) throws SftpException {
        try {
            channel.stat(properties.archivePath());
        } catch (SftpException missing) {
            channel.mkdir(properties.archivePath());
        }
    }

    private <T> T withChannel(SftpWork<T> work) {
        properties.validateForConnection();
        Session session = null;
        ChannelSftp channel = null;
        try {
            JSch jsch = new JSch();
            jsch.setKnownHosts(properties.knownHostsPath());
            if (!properties.privateKeyPath().isBlank()) {
                if (properties.privateKeyPassphrase().isBlank()) jsch.addIdentity(properties.privateKeyPath());
                else jsch.addIdentity(properties.privateKeyPath(), properties.privateKeyPassphrase());
            }
            session = jsch.getSession(properties.username(), properties.host(), properties.port());
            session.setConfig("StrictHostKeyChecking", "yes");
            session.setConfig("PreferredAuthentications",
                    properties.privateKeyPath().isBlank() ? "password" : "publickey");
            if (!properties.password().isBlank()) session.setPassword(properties.password());
            session.connect(Math.toIntExact(properties.connectTimeout().toMillis()));
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(Math.toIntExact(properties.connectTimeout().toMillis()));
            return work.execute(channel);
        } catch (JSchException | SftpException exception) {
            throw new IllegalStateException("SFTP reconciliation operation failed", exception);
        } finally {
            if (channel != null && channel.isConnected()) channel.disconnect();
            if (session != null && session.isConnected()) session.disconnect();
        }
    }

    private boolean safeRemoteName(String name) {
        return name != null && !name.isBlank() && !name.equals(".") && !name.equals("..")
                && !name.contains("/") && !name.contains("\\") && !name.contains("\0")
                && name.length() <= 255;
    }

    private static String remote(String directory, String name) {
        return directory + "/" + name;
    }

    @FunctionalInterface
    private interface SftpWork<T> {
        T execute(ChannelSftp channel) throws SftpException;
    }
}
