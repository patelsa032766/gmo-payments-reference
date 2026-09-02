package io.github.patelsa032766.gmopayments.application.port;

/**
 * Deployment-owned authentication boundary for otherwise unsigned provider
 * callbacks. The reverse proxy injects a secret header after it accepts a
 * request on the private webhook route.
 */
public interface WebhookIngressAuthorizer {
    boolean enabled();
    boolean authorized(String presentedToken);
}
