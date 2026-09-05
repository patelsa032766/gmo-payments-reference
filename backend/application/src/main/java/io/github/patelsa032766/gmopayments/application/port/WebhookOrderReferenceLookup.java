package io.github.patelsa032766.gmopayments.application.port;

import java.util.Optional;

/**
 * Resolves the order ID used to derive an OpenAPI webhook CSRF token.
 *
 * <p>GMO's cash and wallet webhook bodies contain the provider access ID but
 * not the merchant order ID. The order ID is deliberately recovered through
 * this provider-independent port so the GMO authentication adapter does not
 * acquire a direct dependency on SQLite.</p>
 */
@FunctionalInterface
public interface WebhookOrderReferenceLookup {

    /** Return the GMO order ID paired with {@code providerAccessId}, if known. */
    Optional<String> findProviderOrderId(String providerAccessId);
}
