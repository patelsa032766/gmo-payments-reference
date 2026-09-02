package io.github.patelsa032766.gmopayments.domain;

import java.time.Instant;
import java.util.Map;

/** Sanitized, normalized evidence received from a GMO callback endpoint. */
public record InboundPaymentMessage(
        String sourceFamily,
        String externalEventKey,
        String payloadHash,
        String providerOrderId,
        String providerAccessId,
        String providerStatus,
        Map<String, Object> sanitizedPayload,
        Instant receivedAt
) {}
