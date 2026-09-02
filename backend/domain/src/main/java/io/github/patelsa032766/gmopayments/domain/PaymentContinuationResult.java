package io.github.patelsa032766.gmopayments.domain;

import java.util.List;

/** Final customer outcome plus every provider call made after browser return. */
public record PaymentContinuationResult(
        PaymentGatewayResult outcome,
        List<ProviderCallEvidence> exchanges
) {
    public PaymentContinuationResult {
        exchanges = List.copyOf(exchanges);
    }
}
