package io.github.patelsa032766.gmopayments.domain;

import java.util.Map;

/** Provider result normalized before it reaches application orchestration. */
public record PaymentGatewayResult(
        String canonicalState,
        String providerStatus,
        String providerOrderId,
        String providerAccessId,
        String eventType,
        String summary,
        boolean requiresAttention,
        PaymentNextAction nextAction,
        Map<String, Object> instructions,
        String transport,
        String providerOperation,
        String endpoint,
        Integer httpStatus,
        Integer durationMs,
        Map<String, Object> sanitizedRequest,
        Map<String, Object> sanitizedResponse) {
    public PaymentGatewayResult {
        nextAction = nextAction == null ? PaymentNextAction.none() : nextAction;
        instructions = Map.copyOf(instructions == null ? Map.of() : instructions);
        sanitizedRequest = Map.copyOf(sanitizedRequest == null ? Map.of() : sanitizedRequest);
        sanitizedResponse = Map.copyOf(sanitizedResponse == null ? Map.of() : sanitizedResponse);
    }
}
