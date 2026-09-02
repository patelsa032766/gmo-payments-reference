package io.github.patelsa032766.gmopayments.domain;

import java.time.Instant;
import java.util.Map;

/** Sanitized reusable payment method; provider credentials are never represented. */
public record PaymentInstrumentSnapshot(
        String instrumentId,
        String customerCode,
        String customerName,
        PaymentMethodCode method,
        String productCode,
        String maskedDisplay,
        String state,
        String preferenceRole,
        Map<String, Object> metadata,
        Instant updatedAt) {}
