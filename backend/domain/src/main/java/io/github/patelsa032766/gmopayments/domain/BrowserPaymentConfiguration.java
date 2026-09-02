package io.github.patelsa032766.gmopayments.domain;

/**
 * Public browser integration values. Shop ID and script URL are publishable;
 * Shop/Site passwords are intentionally impossible to represent here.
 */
public record BrowserPaymentConfiguration(
        boolean liveCallsEnabled,
        boolean webhooksEnabled,
        String mpTokenJsUrl,
        String shopId,
        String publicBaseUrl,
        String openapiWebhookUrl,
        String protocolNotificationUrl) {}
