package io.github.patelsa032766.gmopayments.application.port;

import io.github.patelsa032766.gmopayments.domain.*;
import java.util.Optional;

/** Two-phase persistence boundary; no provider call occurs while SQLite is locked. */
public interface MitCommandRepository {
    MitExecutionReservation reserve(String instrumentId,long amountJpy,String merchantReference,String idempotencyKey,
                                    String fingerprint, PaymentExecutionMode executionMode);
    PaymentSubmissionResult recordSuccess(PaymentExecutionContext context, PaymentGatewayResult result);
    PaymentSubmissionResult recordFailure(PaymentExecutionContext context,String state,String summary,boolean attention);
    Optional<PaymentSubmissionResult> findSubmission(String transactionId);
}
