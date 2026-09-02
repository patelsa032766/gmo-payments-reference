package io.github.patelsa032766.gmopayments.application.port;

import io.github.patelsa032766.gmopayments.domain.PaymentExecutionContext;
import io.github.patelsa032766.gmopayments.domain.PaymentGatewayResult;
import io.github.patelsa032766.gmopayments.domain.PaymentMethodCode;
import io.github.patelsa032766.gmopayments.domain.PaymentSubmissionResult;

import java.util.Optional;

/** Short SQLite transactions used before and after a provider network call. */
public interface PaymentCommandRepository {
    Reservation reserve(String applicationNumber, PaymentMethodCode method, String idempotencyKey,
                        String requestFingerprint);
    PaymentSubmissionResult recordSuccess(PaymentExecutionContext context, PaymentGatewayResult result);
    PaymentSubmissionResult recordFailure(PaymentExecutionContext context, String state, String summary,
                                          boolean requiresAttention);
    Optional<PaymentSubmissionResult> findSubmission(String transactionId);

    record Reservation(PaymentExecutionContext context, boolean replay) {}
}
