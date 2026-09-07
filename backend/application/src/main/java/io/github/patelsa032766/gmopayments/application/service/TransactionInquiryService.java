package io.github.patelsa032766.gmopayments.application.service;

import io.github.patelsa032766.gmopayments.application.port.PaymentGateway;
import io.github.patelsa032766.gmopayments.application.port.TransactionInquiryRepository;
import io.github.patelsa032766.gmopayments.domain.PaymentSubmissionResult;

/** Reconciles a stale local transaction from GMO's authoritative read API. */
public final class TransactionInquiryService {
    private final TransactionInquiryRepository repository;
    private final PaymentGateway gateway;

    public TransactionInquiryService(TransactionInquiryRepository repository, PaymentGateway gateway) {
        this.repository = repository;
        this.gateway = gateway;
    }

    public PaymentSubmissionResult refresh(String transactionId) {
        var context = repository.load(transactionId);
        return repository.record(context, gateway.inquire(context));
    }
}
