package io.github.patelsa032766.gmopayments.application.port;

import io.github.patelsa032766.gmopayments.domain.InboundPaymentMessage;

/**
 * Resolves terse provider notifications before a database transaction begins.
 *
 * <p>GMO cash webhooks are deliberately small and do not say whether an
 * individual bank transfer completed the requested amount.  Implementations
 * may perform a safe, read-only provider inquiry and return enriched sanitized
 * evidence.  Keeping that network call outside the repository also prevents a
 * slow provider from holding SQLite's single-writer lock.</p>
 */
public interface InboundPaymentMessageResolver {
    InboundPaymentMessage resolve(InboundPaymentMessage message);
}

