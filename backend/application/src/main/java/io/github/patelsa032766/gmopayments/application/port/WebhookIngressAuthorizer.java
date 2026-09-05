package io.github.patelsa032766.gmopayments.application.port;

import java.util.Map;

/**
 * Deployment-owned authentication boundary for otherwise unsigned provider
 * callbacks. The reverse proxy injects a secret header after it accepts a
 * request on the private webhook route.
 */
public interface WebhookIngressAuthorizer {
    boolean enabled();

    /**
     * OpenAPI notifications can be authenticated either by a trusted edge or
     * by GMO echoing the per-transaction CSRF token supplied when the order was
     * created. The payload is required for the latter check.
     */
    boolean authorizedOpenApi(String presentedToken, Map<String, ?> payload);

    /**
     * Legacy protocol notifications do not carry the OpenAPI CSRF token and
     * therefore still require the trusted edge credential.
     */
    boolean authorizedProtocol(String presentedToken);
}
