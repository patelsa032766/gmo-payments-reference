package io.github.patelsa032766.gmopayments.domain;

/** Immutable facts resolved before the provider network call begins. */
public record PaymentExecutionContext(
        String transactionId,
        String applicationNumber,
        String customerCode,
        String customerName,
        String agentName,
        String companyName,
        PaymentMethodCode method,
        String productCode,
        String initiationType,
        String operation,
        long amountJpy,
        int configurationVersion,
        String correlationId) {}
