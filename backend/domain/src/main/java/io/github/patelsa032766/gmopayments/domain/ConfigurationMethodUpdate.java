package io.github.patelsa032766.gmopayments.domain;

/** Administrator-editable fields; labels and channel contracts remain versioned copies. */
public record ConfigurationMethodUpdate(
        PaymentMethodCode code,
        boolean enabled,
        boolean recurring,
        boolean monthlyOnly,
        long minimumAmountJpy,
        long maximumAmountJpy,
        int displayOrder,
        PaymentExecutionMode citExecutionMode
) {
    public ConfigurationMethodUpdate {
        if (minimumAmountJpy < 0 || maximumAmountJpy < minimumAmountJpy) {
            throw new IllegalArgumentException("Invalid amount range for " + code);
        }
        if (displayOrder < 1) throw new IllegalArgumentException("Display order must be positive");
        if (citExecutionMode == null) throw new IllegalArgumentException("CIT execution mode is required");
        if (citExecutionMode == PaymentExecutionMode.AUTH
                && code != PaymentMethodCode.CARD && code != PaymentMethodCode.PAYPAY) {
            throw new IllegalArgumentException("AUTH is supported only for Card and PayPay");
        }
    }
}
