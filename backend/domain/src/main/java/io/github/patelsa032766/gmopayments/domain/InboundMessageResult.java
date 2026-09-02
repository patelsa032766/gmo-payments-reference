package io.github.patelsa032766.gmopayments.domain;

/** Result returned only after the callback has been durably recorded. */
public record InboundMessageResult(
        String messageId,
        boolean duplicate,
        boolean linked,
        boolean applied,
        String transactionId,
        String parseStatus
) {}
