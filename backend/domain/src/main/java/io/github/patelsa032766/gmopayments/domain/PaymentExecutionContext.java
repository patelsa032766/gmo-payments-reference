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
        String correlationId,
        PaymentExecutionMode executionMode) {

    /** Backward-compatible constructor for flows with no authorization choice. */
    public PaymentExecutionContext(String transactionId, String applicationNumber,
                                   String customerCode, String customerName,
                                   String agentName, String companyName,
                                   PaymentMethodCode method, String productCode,
                                   String initiationType, String operation,
                                   long amountJpy, int configurationVersion,
                                   String correlationId) {
        this(transactionId, applicationNumber, customerCode, customerName, agentName,
                companyName, method, productCode, initiationType, operation, amountJpy,
                configurationVersion, correlationId, PaymentExecutionMode.CAPTURE);
    }
}
