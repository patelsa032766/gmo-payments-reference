package io.github.patelsa032766.gmopayments.domain;

/** Provider identifiers and immutable local facts reserved before capture. */
public record CaptureReservation(
        PaymentExecutionContext context,
        String providerAccessId,
        String providerOrderId,
        boolean replayed) {
}
