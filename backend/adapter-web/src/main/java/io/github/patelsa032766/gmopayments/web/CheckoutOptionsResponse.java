package io.github.patelsa032766.gmopayments.web;

import io.github.patelsa032766.gmopayments.application.service.CheckoutOptions;

import java.util.List;

record CheckoutOptionsResponse(int configurationVersion, List<PaymentMethodResponse> methods) {
    static CheckoutOptionsResponse from(CheckoutOptions options) {
        return new CheckoutOptionsResponse(
                options.configurationVersion(),
                options.methods().stream().map(PaymentMethodResponse::from).toList());
    }
}
