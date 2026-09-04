package io.github.patelsa032766.gmopayments.domain;

/**
 * Merchant choice for payment products that support both authorization and
 * immediate sale. GMO names immediate sale {@code CAPTURE}; a later capture
 * changes an {@code AUTH} transaction into a completed sale.
 */
public enum PaymentExecutionMode {
    AUTH,
    CAPTURE;

    public static PaymentExecutionMode from(String value) {
        if (value == null || value.isBlank()) return CAPTURE;
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Execution mode must be AUTH or CAPTURE");
        }
    }
}
