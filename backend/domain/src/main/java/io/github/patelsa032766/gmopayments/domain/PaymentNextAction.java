package io.github.patelsa032766.gmopayments.domain;

import java.util.Map;

/**
 * Generic customer continuation instruction.
 *
 * <p>{@code FORM_POST} fields may contain a short-lived provider handoff token.
 * They are returned only to the requesting browser and are never persisted or
 * shown in the operator portal.</p>
 */
public record PaymentNextAction(String type, String url, Map<String, String> fields) {
    public PaymentNextAction { fields = Map.copyOf(fields == null ? Map.of() : fields); }
    public static PaymentNextAction none() { return new PaymentNextAction("NONE", null, Map.of()); }
}
