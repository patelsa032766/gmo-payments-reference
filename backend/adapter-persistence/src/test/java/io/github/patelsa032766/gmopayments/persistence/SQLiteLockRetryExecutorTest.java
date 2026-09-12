package io.github.patelsa032766.gmopayments.persistence;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import static org.assertj.core.api.Assertions.*;

/** Retry policy tests; these deliberately do not claim to test real SQLite contention. */
class SQLiteLockRetryExecutorTest {
    private final SQLiteLockRetryExecutor executor = new SQLiteLockRetryExecutor();

    @Test
    void retriesTransientFailureThenReturnsSuccess() {
        var calls = new AtomicInteger();
        assertThat(executor.execute("test", () -> {
            if (calls.incrementAndGet() == 1) throw new CannotAcquireLockException("busy");
            return "committed";
        })).isEqualTo("committed");
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void exhaustionPreservesFailureAndStopsAfterFiveAttempts() {
        var calls = new AtomicInteger();
        var failure = new CannotAcquireLockException("busy");
        assertThatThrownBy(() -> executor.execute("test", () -> {
            calls.incrementAndGet();
            throw failure;
        })).isSameAs(failure);
        assertThat(calls.get()).isEqualTo(5);
    }

    @Test
    void nonTransientFailureIsNotRetried() {
        var calls = new AtomicInteger();
        assertThatThrownBy(() -> executor.execute("test", () -> {
            calls.incrementAndGet();
            throw new IllegalArgumentException("invalid");
        })).isInstanceOf(IllegalArgumentException.class);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void interruptionStopsRetryAndPreservesInterruptFlag() {
        try {
            Thread.currentThread().interrupt();
            assertThatThrownBy(() -> executor.execute("test", () -> {
                throw new CannotAcquireLockException("busy");
            })).isInstanceOf(IllegalStateException.class).hasCauseInstanceOf(InterruptedException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted(); // Do not leak the interrupt into other tests.
        }
    }
}
