package io.github.patelsa032766.gmopayments.application.service;

import io.github.patelsa032766.gmopayments.domain.DistributionChannel;

/** Inputs supplied by the insurance application, not trusted browser rules. */
public record CheckoutEligibilityQuery(
        DistributionChannel channel,
        long amountJpy,
        boolean monthly,
        boolean ekycVerified,
        String language) {

    public CheckoutEligibilityQuery {
        if (channel == null) throw new IllegalArgumentException("channel is required");
        if (amountJpy < 1) throw new IllegalArgumentException("amountJpy must be positive");
        language = "ja".equalsIgnoreCase(language) ? "ja" : "en";
    }
}
