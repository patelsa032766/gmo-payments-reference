package io.github.patelsa032766.gmopayments.application.service;

import io.github.patelsa032766.gmopayments.application.port.PaymentCommandRepository;
import io.github.patelsa032766.gmopayments.application.port.PaymentGateway;
import io.github.patelsa032766.gmopayments.domain.PaymentMethodCode;
import io.github.patelsa032766.gmopayments.domain.PaymentSubmissionResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;

/**
 * Checkout command orchestration with an explicit network/SQLite boundary.
 *
 * <p>The intent and idempotency key commit before the provider call. The
 * provider call occurs without a database lock. Its result is then appended in
 * a second short transaction. A transport timeout becomes {@code UNKNOWN}; the
 * UI stays on Payment and offers inquiry rather than issuing another charge.</p>
 */
public final class CheckoutPaymentService {
    private final PaymentCommandRepository repository;
    private final PaymentGateway gateway;

    public CheckoutPaymentService(PaymentCommandRepository repository, PaymentGateway gateway) {
        this.repository = repository;
        this.gateway = gateway;
    }

    public PaymentSubmissionResult submit(String applicationNumber, PaymentMethodCode method,
                                           String idempotencyKey, Map<String, Object> details) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("An idempotency key is required");
        }
        String fingerprint = fingerprint(applicationNumber, method, details);
        var reservation = repository.reserve(applicationNumber, method, idempotencyKey, fingerprint);
        if (reservation.replay()) {
            return repository.findSubmission(reservation.context().transactionId())
                    .map(result -> new PaymentSubmissionResult(result.transactionId(), result.applicationNumber(),
                            result.method(), result.state(), result.providerStatus(), result.requiresAttention(),
                            result.nextAction(), result.instructions(), true))
                    .orElseThrow(() -> new IllegalStateException("Reserved payment result is unavailable"));
        }

        try {
            return repository.recordSuccess(reservation.context(),
                    gateway.executeCheckout(reservation.context(), details));
        } catch (PaymentGatewayException exception) {
            String state = exception.outcomeUnknown() ? "UNKNOWN" : "FAILED";
            String summary = exception.outcomeUnknown()
                    ? "Provider outcome is unknown; inquiry is required before retry"
                    : "Provider rejected or could not complete the payment";
            return repository.recordFailure(reservation.context(), state, summary,
                    exception.outcomeUnknown(), exception.evidence());
        }
    }

    public PaymentSubmissionResult find(String transactionId) {
        return repository.findSubmission(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown payment: " + transactionId));
    }

    private static String fingerprint(String applicationNumber, PaymentMethodCode method,
                                      Map<String, Object> details) {
        // Sorted top-level keys make ordinary JSON object ordering irrelevant.
        // Sensitive values are hashed, never logged or persisted in this form.
        String canonical = applicationNumber + "|" + method.apiValue() + "|" + new TreeMap<>(details);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
