package io.github.patelsa032766.gmopayments.domain;

import java.util.Objects;

/**
 * Immutable representation of a remote reconciliation file.
 *
 * <p>The SFTP adapter creates this value only after it has observed the
 * companion ready marker. The byte array is defensively copied in both
 * directions so callers cannot mutate evidence after its checksum is made.</p>
 */
public record DownloadedReconciliationFile(String remoteName, byte[] content, String readyMarkerName) {
    public DownloadedReconciliationFile {
        Objects.requireNonNull(remoteName, "remoteName");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(readyMarkerName, "readyMarkerName");
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
