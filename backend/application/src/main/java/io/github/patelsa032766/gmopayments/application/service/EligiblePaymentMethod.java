package io.github.patelsa032766.gmopayments.application.service;

import io.github.patelsa032766.gmopayments.domain.PaymentMethodCode;
import io.github.patelsa032766.gmopayments.domain.PaymentExecutionMode;

/** Customer-safe projection returned by the eligibility use case. */
public record EligiblePaymentMethod(
        PaymentMethodCode code,
        String label,
        String description,
        boolean recurring,
        int displayOrder,
        PaymentExecutionMode citExecutionMode) {
}
