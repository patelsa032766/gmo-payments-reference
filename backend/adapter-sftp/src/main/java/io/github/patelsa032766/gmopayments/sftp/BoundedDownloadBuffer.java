package io.github.patelsa032766.gmopayments.sftp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

/**
 * Enforces the download limit before allocating space for each incoming chunk.
 * Remote file metadata is only a preliminary check: a file can grow between
 * listing and download. Never rely on a post-download check to bound memory.
 */
final class BoundedDownloadBuffer extends OutputStream {
    private final long limit;
    private final ByteArrayOutputStream buffer;

    BoundedDownloadBuffer(long limit) {
        if (limit < 0) throw new IllegalArgumentException("Negative download limit");
        this.limit = limit;
        this.buffer = new ByteArrayOutputStream((int) Math.min(limit, 65_536));
    }

    @Override
    public void write(int value) throws IOException {
        requireCapacity(1);
        buffer.write(value);
    }

    @Override
    public void write(byte[] bytes, int offset, int length) throws IOException {
        Objects.checkFromIndexSize(offset, length, bytes.length);
        requireCapacity(length);
        buffer.write(bytes, offset, length);
    }

    private void requireCapacity(int additional) throws IOException {
        if (additional > limit - buffer.size()) {
            throw new IOException("SFTP file exceeded configured size limit while downloading");
        }
    }

    byte[] toByteArray() {
        return buffer.toByteArray();
    }
}
