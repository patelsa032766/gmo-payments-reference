package io.github.patelsa032766.gmopayments.application.service;

import io.github.patelsa032766.gmopayments.application.port.PaymentOperationsRepository;
import io.github.patelsa032766.gmopayments.domain.PaymentInstrumentSnapshot;
import io.github.patelsa032766.gmopayments.domain.PaymentTransactionSummary;
import io.github.patelsa032766.gmopayments.domain.PaymentTransactionThread;

import java.util.List;

/** Framework-free query use cases for payment operations. */
public final class PaymentOperationsQueryService {
    private final PaymentOperationsRepository repository;

    public PaymentOperationsQueryService(PaymentOperationsRepository repository) {
        this.repository = repository;
    }

    public List<PaymentTransactionSummary> listTransactions() {
        return repository.findTransactions();
    }

    public PaymentTransactionThread getTransactionThread(String transactionId) {
        return repository.findTransactionThread(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown transaction: " + transactionId));
    }

    public List<PaymentInstrumentSnapshot> listActiveInstruments() {
        return repository.findActiveInstruments();
    }
}
