package io.github.patelsa032766.gmopayments.application.service;

import io.github.patelsa032766.gmopayments.application.port.BrowserPaymentConfigurationProvider;
import io.github.patelsa032766.gmopayments.domain.BrowserPaymentConfiguration;

/** Read use case for safe browser integration settings. */
public final class BrowserPaymentConfigurationService {
    private final BrowserPaymentConfigurationProvider provider;

    public BrowserPaymentConfigurationService(BrowserPaymentConfigurationProvider provider) {
        this.provider = provider;
    }

    public BrowserPaymentConfiguration get() { return provider.get(); }
}
