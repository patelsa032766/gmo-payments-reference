package io.github.patelsa032766.gmopayments.gmo;

import java.util.Map;

/**
 * Adapter-internal transport result.
 *
 * <p>The raw map is required briefly to read provider references and browser
 * handoff tokens. Only {@code sanitizedPayload} may cross into persistence or
 * an operator response.</p>
 */
record GmoHttpResult(int statusCode, long durationMs, Map<String, Object> rawPayload,
                     Map<String, Object> sanitizedPayload) {
    GmoHttpResult {
        rawPayload = Map.copyOf(rawPayload);
        sanitizedPayload = Map.copyOf(sanitizedPayload);
    }
}
