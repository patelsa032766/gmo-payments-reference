package io.github.patelsa032766.gmopayments.domain;

import java.util.Map;

/** Stable command response used by Angular and idempotent replays. */
public record PaymentSubmissionResult(
        String transactionId,
        String applicationNumber,
        PaymentMethodCode method,
        String state,
        String providerStatus,
        boolean requiresAttention,
        PaymentNextAction nextAction,
        Map<String, Object> instructions,
        boolean idempotentReplay) {
    public PaymentSubmissionResult {
        nextAction = nextAction == null ? PaymentNextAction.none() : nextAction;
        instructions = Map.copyOf(instructions == null ? Map.of() : instructions);
    }
}
