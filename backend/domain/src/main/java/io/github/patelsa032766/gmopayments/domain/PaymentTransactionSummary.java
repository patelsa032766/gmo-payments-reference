package io.github.patelsa032766.gmopayments.domain;

import java.time.Instant;

/**
 * Provider-neutral row used by the operator transaction list.
 *
 * <p>The row deliberately contains only sanitized, searchable facts. Raw
 * provider payloads live in {@link ProviderExchangeSnapshot} and are never
 * copied into this high-traffic projection.</p>
 */
public record PaymentTransactionSummary(
        String transactionId,
        String rootTransactionId,
        String applicationNumber,
        long amountJpy,
        String canonicalState,
        PaymentMethodCode method,
        String productCode,
        String initiationType,
        String operation,
        String customerName,
        String customerCode,
        String merchantReference,
        Instant updatedAt,
        boolean requiresAttention) {}
