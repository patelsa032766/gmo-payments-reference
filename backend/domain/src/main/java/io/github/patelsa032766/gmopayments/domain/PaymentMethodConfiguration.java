package io.github.patelsa032766.gmopayments.domain;

import java.util.Objects;
import java.util.Set;

/**
 * One payment method inside an immutable, published configuration release.
 * Amounts use whole JPY because the approved experience does not accept a
 * fractional currency representation.
 */
public record PaymentMethodConfiguration(
        PaymentMethodCode code,
        String labelEn,
        String descriptionEn,
        String labelJa,
        String descriptionJa,
        boolean enabled,
        boolean recurring,
        boolean monthlyOnly,
        long minimumAmountJpy,
        long maximumAmountJpy,
        Long nonEkycMaximumAmountJpy,
        Set<DistributionChannel> channels,
        int displayOrder,
        PaymentExecutionMode citExecutionMode) {

    public PaymentMethodConfiguration {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(labelEn, "labelEn");
        Objects.requireNonNull(descriptionEn, "descriptionEn");
        Objects.requireNonNull(labelJa, "labelJa");
        Objects.requireNonNull(descriptionJa, "descriptionJa");
        channels = Set.copyOf(Objects.requireNonNull(channels, "channels"));
        Objects.requireNonNull(citExecutionMode, "citExecutionMode");
        if (citExecutionMode == PaymentExecutionMode.AUTH
                && code != PaymentMethodCode.CARD && code != PaymentMethodCode.PAYPAY) {
            throw new IllegalArgumentException("AUTH is supported only for Card and PayPay");
        }
        if (minimumAmountJpy < 0 || maximumAmountJpy < minimumAmountJpy) {
            throw new IllegalArgumentException("Invalid JPY amount range for " + code);
        }
        if (displayOrder < 1) {
            throw new IllegalArgumentException("displayOrder must be positive");
        }
    }

    /** Convenience for products/tests that retain the immediate-sale default. */
    public PaymentMethodConfiguration(PaymentMethodCode code, String labelEn, String descriptionEn,
                                      String labelJa, String descriptionJa, boolean enabled,
                                      boolean recurring, boolean monthlyOnly, long minimumAmountJpy,
                                      long maximumAmountJpy, Long nonEkycMaximumAmountJpy,
                                      Set<DistributionChannel> channels, int displayOrder) {
        this(code, labelEn, descriptionEn, labelJa, descriptionJa, enabled, recurring,
                monthlyOnly, minimumAmountJpy, maximumAmountJpy, nonEkycMaximumAmountJpy,
                channels, displayOrder, PaymentExecutionMode.CAPTURE);
    }

    public String label(String language) {
        return "ja".equalsIgnoreCase(language) ? labelJa : labelEn;
    }

    public String description(String language) {
        return "ja".equalsIgnoreCase(language) ? descriptionJa : descriptionEn;
    }
}
