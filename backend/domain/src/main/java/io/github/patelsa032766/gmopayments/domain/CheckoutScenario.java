package io.github.patelsa032766.gmopayments.domain;

/** One synthetic application/customer that can drive the local checkout demo. */
public record CheckoutScenario(String applicationNumber, String customerCode, String customerName,
        String policyName, DistributionChannel channel, String paymentPlan,
        boolean ekycVerified, long amountJpy) {}
