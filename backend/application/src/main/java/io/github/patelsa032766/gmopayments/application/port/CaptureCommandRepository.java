package io.github.patelsa032766.gmopayments.application.port;

import io.github.patelsa032766.gmopayments.domain.CaptureReservation;
import io.github.patelsa032766.gmopayments.domain.PaymentGatewayResult;
import io.github.patelsa032766.gmopayments.domain.PaymentSubmissionResult;
import io.github.patelsa032766.gmopayments.domain.ProviderCallEvidence;

import java.util.Optional;

/**
 * Two-phase persistence boundary for operator capture.
 * SQLite is never locked while the financial provider call is in flight.
 */
public interface CaptureCommandRepository {
    CaptureReservation reserve(String transactionId, String idempotencyKey);
    PaymentSubmissionResult recordSuccess(CaptureReservation reservation, PaymentGatewayResult result);
    PaymentSubmissionResult recordFailure(CaptureReservation reservation, String state, String summary,
                                          boolean requiresAttention, ProviderCallEvidence evidence);
    Optional<PaymentSubmissionResult> findSubmission(String transactionId);
}
