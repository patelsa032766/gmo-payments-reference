package io.github.patelsa032766.gmopayments.application.service;

import io.github.patelsa032766.gmopayments.application.port.CaptureCommandRepository;
import io.github.patelsa032766.gmopayments.application.port.PaymentGateway;
import io.github.patelsa032766.gmopayments.domain.PaymentSubmissionResult;

/** Executes a deliberate operator capture against an existing authorization. */
public final class CapturePaymentService {
    private final CaptureCommandRepository repository;
    private final PaymentGateway gateway;

    public CapturePaymentService(CaptureCommandRepository repository, PaymentGateway gateway) {
        this.repository = repository;
        this.gateway = gateway;
    }

    public PaymentSubmissionResult capture(String transactionId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("An idempotency key is required");
        }
        var reservation = repository.reserve(transactionId, idempotencyKey);
        if (reservation.replayed()) {
            return repository.findSubmission(transactionId).orElseThrow();
        }
        try {
            return repository.recordSuccess(reservation, gateway.capture(reservation.context(),
                    reservation.providerAccessId(), reservation.providerOrderId()));
        } catch (PaymentGatewayException exception) {
            return repository.recordFailure(reservation,
                    exception.outcomeUnknown() ? "UNKNOWN" : "AUTHORIZED",
                    exception.outcomeUnknown()
                            ? "Capture outcome is unknown; inquire before another attempt"
                            : "GMO did not complete the capture; the authorization remains open",
                    true, exception.evidence());
        } catch (RuntimeException exception) {
            return repository.recordFailure(reservation, "UNKNOWN",
                    "Capture outcome is unknown; inquire before another attempt", true, null);
        }
    }
}
