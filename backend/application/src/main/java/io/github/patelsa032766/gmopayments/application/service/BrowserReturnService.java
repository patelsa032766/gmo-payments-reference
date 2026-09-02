package io.github.patelsa032766.gmopayments.application.service;

import io.github.patelsa032766.gmopayments.application.port.PaymentCommandRepository;
import io.github.patelsa032766.gmopayments.application.port.PaymentGateway;
import io.github.patelsa032766.gmopayments.domain.PaymentMethodCode;
import io.github.patelsa032766.gmopayments.domain.PaymentSubmissionResult;

import java.util.Map;

/**
 * Continues registration-based payment methods without trusting browser state.
 *
 * <p>The provider transaction reference is used only to locate the already
 * reserved local transaction. The gateway performs a server-to-server inquiry
 * before any financial action. Replayed returns never issue a second debit.</p>
 */
public final class BrowserReturnService {
    private final PaymentCommandRepository repository;
    private final PaymentGateway gateway;

    public BrowserReturnService(PaymentCommandRepository repository, PaymentGateway gateway) {
        this.repository = repository;
        this.gateway = gateway;
    }

    public PaymentSubmissionResult complete(PaymentMethodCode method, Map<String, Object> fields) {
        if (method != PaymentMethodCode.PAYPAY
                && method != PaymentMethodCode.BANK_DIRECT_REALTIME
                && method != PaymentMethodCode.KOZA_FURIKAE_SELECT) {
            throw new IllegalArgumentException("This payment method has no registration return flow");
        }
        String providerReference = first(fields, "accessId", "AccessID", "AccessId",
                "TranID", "TranId", "TransactionID", "transactionId");
        if (providerReference == null || providerReference.isBlank() || providerReference.length() > 128) {
            throw new IllegalArgumentException("The GMO return did not contain a valid transaction reference");
        }
        var reservation = repository.reserveContinuation(providerReference, method);
        if (reservation.replay()) {
            return repository.findSubmission(reservation.context().transactionId())
                    .orElseThrow(() -> new IllegalStateException("Completed payment result is unavailable"));
        }
        try {
            return repository.recordContinuation(reservation.context(),
                    gateway.continueCheckout(reservation.context(), fields));
        } catch (PaymentGatewayException exception) {
            String state = exception.outcomeUnknown() ? "UNKNOWN" : "FAILED";
            return repository.recordFailure(reservation.context(), state,
                    exception.outcomeUnknown()
                            ? "Provider outcome is unknown; inquiry is required before retry"
                            : "Provider could not complete the registration-based payment",
                    exception.outcomeUnknown(), exception.evidence());
        }
    }

    private static String first(Map<String, Object> fields, String... keys) {
        for (String key : keys) {
            Object value = fields.get(key);
            if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value).trim();
        }
        return null;
    }
}
