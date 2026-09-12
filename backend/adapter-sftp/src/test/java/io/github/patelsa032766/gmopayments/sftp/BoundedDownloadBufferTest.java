package io.github.patelsa032766.gmopayments.sftp;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class BoundedDownloadBufferTest {
    @Test
    void acceptsExactlyTheLimitAndRejectsNextByte() throws IOException {
        var buffer = new BoundedDownloadBuffer(3);
        buffer.write(new byte[]{1, 2});
        buffer.write(3);
        assertThatThrownBy(() -> buffer.write(4)).isInstanceOf(IOException.class);
        assertThat(buffer.toByteArray()).containsExactly(1, 2, 3);
    }

    @Test
    void rejectsWholeOversizedChunkBeforeRetainingIt() throws IOException {
        var buffer = new BoundedDownloadBuffer(3);
        buffer.write(1);
        assertThatThrownBy(() -> buffer.write(new byte[]{2, 3, 4}))
                .isInstanceOf(IOException.class);
        assertThat(buffer.toByteArray()).containsExactly(1);
    }

    @Test
    void supportsSlicesAndEmptyWrites() throws IOException {
        var buffer = new BoundedDownloadBuffer(2);
        buffer.write(new byte[]{0, 1, 2, 3}, 1, 2);
        buffer.write(new byte[0]);
        assertThat(buffer.toByteArray()).containsExactly(1, 2);
    }

    @Test
    void rejectsInvalidLimitsAndOffsets() {
        assertThatThrownBy(() -> new BoundedDownloadBuffer(-1)).isInstanceOf(IllegalArgumentException.class);
        var buffer = new BoundedDownloadBuffer(2);
        assertThatThrownBy(() -> buffer.write(new byte[1], 0, 2)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThat(buffer.toByteArray()).isEmpty();
    }
}
