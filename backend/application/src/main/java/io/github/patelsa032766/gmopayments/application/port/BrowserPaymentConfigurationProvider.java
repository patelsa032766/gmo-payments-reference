package io.github.patelsa032766.gmopayments.application.port;

import io.github.patelsa032766.gmopayments.domain.BrowserPaymentConfiguration;

/** Supplies the small allowlisted subset of integration configuration safe for Angular. */
public interface BrowserPaymentConfigurationProvider {
    BrowserPaymentConfiguration get();
}
