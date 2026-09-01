package io.github.patelsa032766.gmopayments.application.port;

import io.github.patelsa032766.gmopayments.domain.ConfigurationRelease;

/** Inbound-facing application port; persistence technology is deliberately absent. */
public interface CheckoutConfigurationRepository {
    ConfigurationRelease findActiveRelease();
}
