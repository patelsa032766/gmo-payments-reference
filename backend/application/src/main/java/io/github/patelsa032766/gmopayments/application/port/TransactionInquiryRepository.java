package io.github.patelsa032766.gmopayments.application.port;

import io.github.patelsa032766.gmopayments.domain.PaymentGatewayResult;
import io.github.patelsa032766.gmopayments.domain.PaymentInquiryContext;
import io.github.patelsa032766.gmopayments.domain.PaymentSubmissionResult;

/** Persistence boundary for provider-inquiry reconciliation. */
public interface TransactionInquiryRepository {
    PaymentInquiryContext load(String transactionId);
    PaymentSubmissionResult record(PaymentInquiryContext context, PaymentGatewayResult result);
}
