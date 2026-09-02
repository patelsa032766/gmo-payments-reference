package io.github.patelsa032766.gmopayments.domain;

import java.util.List;

/** Complete durable thread: canonical summary plus all linked lifecycle evidence. */
public record PaymentTransactionThread(
        PaymentTransactionSummary transaction,
        List<PaymentTimelineEvent> events,
        List<ProviderExchangeSnapshot> exchanges) {
    public PaymentTransactionThread {
        events = List.copyOf(events);
        exchanges = List.copyOf(exchanges);
    }
}
