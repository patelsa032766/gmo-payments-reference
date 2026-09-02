package io.github.patelsa032766.gmopayments.web;

import io.github.patelsa032766.gmopayments.application.service.BrowserPaymentConfigurationService;
import io.github.patelsa032766.gmopayments.application.service.CheckoutPaymentService;
import io.github.patelsa032766.gmopayments.domain.BrowserPaymentConfiguration;
import io.github.patelsa032766.gmopayments.domain.PaymentMethodCode;
import io.github.patelsa032766.gmopayments.domain.PaymentSubmissionResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Customer payment commands. Raw card PAN/CVC must never enter this controller. */
@RestController
@RequestMapping("/api/v1/checkout")
public class CheckoutPaymentController {
    private final CheckoutPaymentService payments;
    private final BrowserPaymentConfigurationService browserConfiguration;

    public CheckoutPaymentController(CheckoutPaymentService payments,
                                     BrowserPaymentConfigurationService browserConfiguration) {
        this.payments = payments;
        this.browserConfiguration = browserConfiguration;
    }

    @GetMapping("/browser-configuration")
    BrowserPaymentConfiguration browserConfiguration() {
        return browserConfiguration.get();
    }

    @PostMapping("/applications/{applicationNumber}/payments")
    PaymentSubmissionResult submit(@PathVariable String applicationNumber,
                                   @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
                                   @Valid @RequestBody PaymentRequest request) {
        rejectRawCardData(request.details());
        return payments.submit(applicationNumber, PaymentMethodCode.fromApiValue(request.method()),
                idempotencyKey, request.details());
    }

    /** Defense in depth if a future Angular regression tries to send PAN/CVC to Spring. */
    private static void rejectRawCardData(Map<String, Object> details) {
        for (String prohibited : new String[]{"cardNumber", "cardno", "pan", "cvc", "cvv", "securityCode"}) {
            if (details.containsKey(prohibited)) {
                throw new IllegalArgumentException("Raw card details are prohibited; submit an MP token instead");
            }
        }
    }

    public record PaymentRequest(@NotBlank String method, @NotNull Map<String, Object> details) {}
}
