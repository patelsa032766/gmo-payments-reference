package io.github.patelsa032766.gmopayments.application.service;

import java.util.List;

/** Complete, reproducible result from one published configuration release. */
public record CheckoutOptions(int configurationVersion, List<EligiblePaymentMethod> methods) {
    public CheckoutOptions {
        methods = List.copyOf(methods);
    }
}
