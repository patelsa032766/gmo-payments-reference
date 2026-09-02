package io.github.patelsa032766.gmopayments.web;

import io.github.patelsa032766.gmopayments.application.service.PaymentOperationsQueryService;
import io.github.patelsa032766.gmopayments.domain.PaymentInstrumentSnapshot;
import io.github.patelsa032766.gmopayments.domain.PaymentTransactionSummary;
import io.github.patelsa032766.gmopayments.domain.PaymentTransactionThread;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Sanitized operator read endpoints shared by API/Webhooks and MIT. */
@RestController
@RequestMapping("/api/v1")
public class PaymentOperationsController {
    private final PaymentOperationsQueryService service;

    public PaymentOperationsController(PaymentOperationsQueryService service) {
        this.service = service;
    }

    @GetMapping("/operations/transactions")
    List<PaymentTransactionSummary> transactions() {
        return service.listTransactions();
    }

    @GetMapping("/operations/transactions/{transactionId}")
    PaymentTransactionThread transaction(@PathVariable String transactionId) {
        return service.getTransactionThread(transactionId);
    }

    @GetMapping("/mit/instruments")
    List<PaymentInstrumentSnapshot> instruments() {
        return service.listActiveInstruments();
    }
}
