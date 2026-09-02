package io.github.patelsa032766.gmopayments.domain;

import java.time.Instant;
import java.util.Map;

/** One validated, non-sensitive record from a provider settlement file. */
public record ReconciliationRecord(
        int rowNumber,
        String providerOrderId,
        String providerStatus,
        Long amountJpy,
        Instant occurredAt,
        Map<String, Object> sanitizedRow
) {
    public ReconciliationRecord {
        if (rowNumber < 2) throw new IllegalArgumentException("A data row must follow the header");
        if (providerOrderId == null || providerOrderId.isBlank()) {
            throw new IllegalArgumentException("providerOrderId is required");
        }
        if (providerStatus == null || providerStatus.isBlank()) {
            throw new IllegalArgumentException("providerStatus is required");
        }
        sanitizedRow = Map.copyOf(sanitizedRow);
    }
}
