package io.github.patelsa032766.gmopayments.application.service;

import java.util.Map;

/** Provider-neutral failure semantics consumed by checkout orchestration. */
public class PaymentGatewayException extends RuntimeException {
    private final Integer statusCode;
    private final boolean outcomeUnknown;
    private final boolean safeToRetry;
    private final Map<String, Object> sanitizedPayload;

    public PaymentGatewayException(String message, Integer statusCode, boolean outcomeUnknown,
                                   boolean safeToRetry, Map<String, Object> sanitizedPayload, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.outcomeUnknown = outcomeUnknown;
        this.safeToRetry = safeToRetry;
        this.sanitizedPayload = Map.copyOf(sanitizedPayload == null ? Map.of() : sanitizedPayload);
    }

    public Integer statusCode() { return statusCode; }
    public boolean outcomeUnknown() { return outcomeUnknown; }
    public boolean safeToRetry() { return safeToRetry; }
    public Map<String, Object> sanitizedPayload() { return sanitizedPayload; }
}
