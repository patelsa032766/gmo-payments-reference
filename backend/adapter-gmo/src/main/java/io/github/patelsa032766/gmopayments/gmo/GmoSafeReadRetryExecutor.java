package io.github.patelsa032766.gmopayments.gmo;

import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Bounded exponential backoff with full jitter for GMO read-only inquiries.
 *
 * <p>This executor must never wrap a charge, authorization, capture, refund,
 * registration, or debit. An interrupted or timed-out write has an unknown
 * outcome and is resolved by inquiry rather than replay. Only exceptions
 * explicitly classified as {@link GmoProviderException#safeToRetry()} enter
 * this loop.</p>
 */
@Component
public final class GmoSafeReadRetryExecutor {
    private final GmoProperties properties;

    public GmoSafeReadRetryExecutor(GmoProperties properties) {
        this.properties = properties;
    }

    public <T> T execute(Supplier<T> operation) {
        var policy = properties.getRetry();
        int attempts = Math.max(1, Math.min(policy.getSafeReadMaxAttempts(), 6));
        long initial = Math.max(1, policy.getInitialDelayMs());
        long maximum = Math.max(initial, policy.getMaxDelayMs());
        for (int attempt = 1; ; attempt++) {
            try {
                return operation.get();
            } catch (GmoProviderException exception) {
                if (!exception.safeToRetry() || attempt >= attempts) throw exception;
                long cap = Math.min(maximum, initial * (1L << Math.min(attempt - 1, 20)));
                long delay = ThreadLocalRandom.current().nextLong(cap + 1);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw exception;
                }
            }
        }
    }
}
