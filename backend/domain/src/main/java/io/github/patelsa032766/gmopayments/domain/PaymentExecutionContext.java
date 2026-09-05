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
        PaymentExecutionMode executionMode,
        String paymentPlan) {

    /** True when checkout is expected to create a reusable payment method. */
    public boolean recurringPlan() {
        return "MONTHLY".equalsIgnoreCase(paymentPlan);
    }

    /**
     * Compatibility constructor for MIT, capture, and batch flows. Those
     * commands either already act on a saved method or do not need checkout
     * plan routing, so MONTHLY is the safe semantic default.
     */
    public PaymentExecutionContext(String transactionId, String applicationNumber,
                                   String customerCode, String customerName,
                                   String agentName, String companyName,
                                   PaymentMethodCode method, String productCode,
                                   String initiationType, String operation,
                                   long amountJpy, int configurationVersion,
                                   String correlationId, PaymentExecutionMode executionMode) {
        this(transactionId, applicationNumber, customerCode, customerName, agentName,
                companyName, method, productCode, initiationType, operation, amountJpy,
                configurationVersion, correlationId, executionMode, "MONTHLY");
    }

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
                configurationVersion, correlationId, PaymentExecutionMode.CAPTURE, "MONTHLY");
    }
}
