package io.github.patelsa032766.gmopayments.gmo;

import io.github.patelsa032766.gmopayments.application.service.PaymentGatewayException;
import io.github.patelsa032766.gmopayments.domain.ProviderCallEvidence;

import java.util.Map;

/**
 * Sanitized provider failure with explicit uncertainty semantics.
 *
 * <p>A timeout after a financial request may mean GMO accepted the request even
 * though this process did not receive the response. Callers must inquire before
 * another write; they must never turn {@code outcomeUnknown} into an automatic
 * duplicate charge.</p>
 */
public class GmoProviderException extends PaymentGatewayException {

    public GmoProviderException(String message, Integer statusCode, boolean outcomeUnknown,
                                boolean safeToRetry, Map<String, Object> sanitizedPayload, Throwable cause) {
        super(message, statusCode, outcomeUnknown, safeToRetry, sanitizedPayload, cause);
    }

    public GmoProviderException(String message, Integer statusCode, boolean outcomeUnknown,
                                boolean safeToRetry, Map<String, Object> sanitizedPayload,
                                ProviderCallEvidence evidence, Throwable cause) {
        super(message, statusCode, outcomeUnknown, safeToRetry, sanitizedPayload, evidence, cause);
    }
}
