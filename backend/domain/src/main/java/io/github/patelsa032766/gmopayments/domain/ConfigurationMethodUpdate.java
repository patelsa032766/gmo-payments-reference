package io.github.patelsa032766.gmopayments.domain;

/** Administrator-editable fields; labels and channel contracts remain versioned copies. */
public record ConfigurationMethodUpdate(
        PaymentMethodCode code,
        boolean enabled,
        boolean recurring,
        boolean monthlyOnly,
        long minimumAmountJpy,
        long maximumAmountJpy,
        int displayOrder
) {
    public ConfigurationMethodUpdate {
        if (minimumAmountJpy < 0 || maximumAmountJpy < minimumAmountJpy) {
            throw new IllegalArgumentException("Invalid amount range for " + code);
        }
        if (displayOrder < 1) throw new IllegalArgumentException("Display order must be positive");
    }
}
