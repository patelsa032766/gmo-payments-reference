package io.github.patelsa032766.gmopayments.domain;

import java.time.Instant;
import java.util.Map;

/** Immutable event evidence rendered on the left side of a transaction thread. */
public record PaymentTimelineEvent(
        String eventId,
        String eventType,
        String source,
        String summary,
        String canonicalStateAfter,
        String actor,
        String correlationId,
        Map<String, Object> evidence,
        Instant providerOccurredAt,
        Instant occurredAt) {}
