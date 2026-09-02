package io.github.patelsa032766.gmopayments.application.port;

import io.github.patelsa032766.gmopayments.domain.PaymentInstrumentSnapshot;
import io.github.patelsa032766.gmopayments.domain.PaymentTransactionSummary;
import io.github.patelsa032766.gmopayments.domain.PaymentTransactionThread;

import java.util.List;
import java.util.Optional;

/** Read port shared by the operator timeline and MIT workspaces. */
public interface PaymentOperationsRepository {
    List<PaymentTransactionSummary> findTransactions();
    Optional<PaymentTransactionThread> findTransactionThread(String transactionId);
    List<PaymentInstrumentSnapshot> findActiveInstruments();
}
