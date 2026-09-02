package io.github.patelsa032766.gmopayments.domain;

import java.time.Instant;
import java.util.Map;

/**
 * Sanitized outbound/inbound pair shown beside a selected timeline event.
 * Credential headers, tokens, PAN, CVC, and raw bank credentials are forbidden.
 */
public record ProviderExchangeSnapshot(
        String exchangeId,
        String eventId,
        String direction,
        String transport,
        String operation,
        String endpoint,
        Integer httpStatus,
        Integer durationMs,
        Map<String, Object> requestHeaders,
        Map<String, Object> requestBody,
        Map<String, Object> responseHeaders,
        Map<String, Object> responseBody,
        String outcome,
        int attemptNumber,
        String correlationId,
        Instant createdAt) {}
