package io.github.patelsa032766.gmopayments.domain;

import java.util.Map;

/** One sanitized outbound/inbound pair displayed in the operator thread. */
public record ProviderCallEvidence(
        String transport,
        String operation,
        String endpoint,
        Integer httpStatus,
        Integer durationMs,
        Map<String, Object> sanitizedRequest,
        Map<String, Object> sanitizedResponse,
        String outcome
) {
    public ProviderCallEvidence {
        sanitizedRequest = Map.copyOf(sanitizedRequest == null ? Map.of() : sanitizedRequest);
        sanitizedResponse = Map.copyOf(sanitizedResponse == null ? Map.of() : sanitizedResponse);
    }
}
