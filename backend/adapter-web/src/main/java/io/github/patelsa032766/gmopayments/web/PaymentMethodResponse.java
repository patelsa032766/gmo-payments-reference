package io.github.patelsa032766.gmopayments.web;

import io.github.patelsa032766.gmopayments.application.service.EligiblePaymentMethod;

record PaymentMethodResponse(
        String code,
        String label,
        String description,
        boolean recurring,
        int displayOrder) {

    static PaymentMethodResponse from(EligiblePaymentMethod method) {
        return new PaymentMethodResponse(
                method.code().apiValue(), method.label(), method.description(),
                method.recurring(), method.displayOrder());
    }
}
