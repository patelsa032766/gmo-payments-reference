package io.github.patelsa032766.gmopayments.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Bounded retry boundary for short SQLite write transactions.
 *
 * <p>Provider and SFTP calls must never run inside the supplied operation. The
 * caller first completes the remote work, then invokes this executor for the
 * short idempotent persistence step. Exhaustion is surfaced to the caller and
 * is never treated as a successful payment.</p>
 */
@Component
public final class SQLiteLockRetryExecutor {
    private static final Logger log = LoggerFactory.getLogger(SQLiteLockRetryExecutor.class);
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration BASE_DELAY = Duration.ofMillis(25);

    public <T> T execute(String operationName, Supplier<T> operation) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return operation.get();
            } catch (TransientDataAccessException exception) {
                if (attempt == MAX_ATTEMPTS) throw exception;
                long ceiling = BASE_DELAY.toMillis() * (1L << (attempt - 1));
                long delay = ThreadLocalRandom.current().nextLong(ceiling / 2, ceiling + 1);
                log.warn("SQLite lock while running {} (attempt {}/{}); retrying in {} ms",
                        operationName, attempt, MAX_ATTEMPTS, delay);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting to retry " + operationName, interrupted);
                }
            }
        }
        throw new IllegalStateException("Unreachable SQLite retry state");
    }
}
