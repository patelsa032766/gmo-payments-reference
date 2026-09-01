package io.github.patelsa032766.gmopayments.domain;

/** Stable application codes. Display labels are versioned configuration. */
public enum PaymentMethodCode {
    CARD("card"),
    PAYPAY("paypay"),
    BANK_DIRECT_REALTIME("bankDirect"),
    KOZA_FURIKAE_SELECT("kozaFurikae"),
    KOMBINI("kombini"),
    PAYEASY("payeasy"),
    FURIKOMI("furikomi");

    private final String apiValue;

    PaymentMethodCode(String apiValue) {
        this.apiValue = apiValue;
    }

    public String apiValue() {
        return apiValue;
    }

    public static PaymentMethodCode fromApiValue(String value) {
        for (PaymentMethodCode code : values()) {
            if (code.apiValue.equals(value)) return code;
        }
        throw new IllegalArgumentException("Unsupported payment method code: " + value);
    }
}
