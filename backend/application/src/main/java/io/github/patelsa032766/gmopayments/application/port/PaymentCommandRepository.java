package io.github.patelsa032766.gmopayments.application.port;

import io.github.patelsa032766.gmopayments.domain.PaymentExecutionContext;
import io.github.patelsa032766.gmopayments.domain.PaymentGatewayResult;
import io.github.patelsa032766.gmopayments.domain.PaymentContinuationResult;
import io.github.patelsa032766.gmopayments.domain.PaymentMethodCode;
import io.github.patelsa032766.gmopayments.domain.PaymentSubmissionResult;
import io.github.patelsa032766.gmopayments.domain.ProviderCallEvidence;

import java.util.Optional;

/** Short SQLite transactions used before and after a provider network call. */
public interface PaymentCommandRepository {
    Reservation reserve(String applicationNumber, PaymentMethodCode method, String idempotencyKey,
                        String requestFingerprint);
    PaymentSubmissionResult recordSuccess(PaymentExecutionContext context, PaymentContinuationResult result);
    PaymentSubmissionResult recordFailure(PaymentExecutionContext context, String state, String summary,
                                          boolean requiresAttention, ProviderCallEvidence evidence);
    Optional<PaymentSubmissionResult> findSubmission(String transactionId);
    ContinuationReservation reserveContinuation(String providerReference, PaymentMethodCode method);
    PaymentSubmissionResult recordContinuation(PaymentExecutionContext context,
                                               PaymentContinuationResult result);

    record Reservation(PaymentExecutionContext context, boolean replay) {}
    record ContinuationReservation(PaymentExecutionContext context, boolean replay) {}
}
