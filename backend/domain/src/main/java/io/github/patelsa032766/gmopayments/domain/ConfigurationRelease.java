package io.github.patelsa032766.gmopayments.domain;

import java.time.Instant;
import java.util.List;

/** An immutable snapshot used to make a checkout eligibility decision. */
public record ConfigurationRelease(
        long id,
        int version,
        Instant publishedAt,
        String publishedBy,
        List<PaymentMethodConfiguration> paymentMethods) {

    public ConfigurationRelease {
        paymentMethods = List.copyOf(paymentMethods);
    }
}
