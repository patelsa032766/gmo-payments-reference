package io.github.patelsa032766.gmopayments.gmo;

import io.github.patelsa032766.gmopayments.application.port.BrowserPaymentConfigurationProvider;
import io.github.patelsa032766.gmopayments.domain.BrowserPaymentConfiguration;
import org.springframework.stereotype.Component;

/** Explicit allowlist preventing provider passwords from leaking to Angular. */
@Component
public class GmoBrowserPaymentConfigurationProvider implements BrowserPaymentConfigurationProvider {
    private final GmoProperties properties;

    public GmoBrowserPaymentConfigurationProvider(GmoProperties properties) {
        this.properties = properties;
    }

    @Override
    public BrowserPaymentConfiguration get() {
        return new BrowserPaymentConfiguration(properties.isLiveCallsEnabled(), properties.isWebhooksEnabled(),
                properties.getMpTokenJsUrl(), properties.getShopId(), properties.getPublicBaseUrl(),
                properties.resolvedOpenapiWebhookUrl(), properties.resolvedProtocolNotificationUrl());
    }
}
