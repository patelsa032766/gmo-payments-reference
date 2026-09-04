package io.github.patelsa032766.gmopayments.web;

import io.github.patelsa032766.gmopayments.application.service.PaymentOperationsQueryService;
import io.github.patelsa032766.gmopayments.application.service.CapturePaymentService;
import io.github.patelsa032766.gmopayments.application.port.OperatorActionAuthorizer;
import io.github.patelsa032766.gmopayments.domain.PaymentInstrumentSnapshot;
import io.github.patelsa032766.gmopayments.domain.PaymentTransactionSummary;
import io.github.patelsa032766.gmopayments.domain.PaymentTransactionThread;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import io.github.patelsa032766.gmopayments.domain.PaymentSubmissionResult;

import java.util.List;

/** Sanitized operator read endpoints shared by API/Webhooks and MIT. */
@RestController
@RequestMapping("/api/v1")
public class PaymentOperationsController {
    private final PaymentOperationsQueryService service;
    private final CapturePaymentService captures;
    private final OperatorActionAuthorizer authorizer;

    public PaymentOperationsController(PaymentOperationsQueryService service, CapturePaymentService captures,
                                       OperatorActionAuthorizer authorizer) {
        this.service = service;
        this.captures = captures;
        this.authorizer = authorizer;
    }

    @GetMapping("/operations/transactions")
    List<PaymentTransactionSummary> transactions() {
        return service.listTransactions();
    }

    @GetMapping("/operations/transactions/{transactionId}")
    PaymentTransactionThread transaction(@PathVariable String transactionId) {
        return service.getTransactionThread(transactionId);
    }

    /**
     * Contextual lifecycle command. It deliberately lives beside the payment
     * thread API because capture acts on an existing authorization, not on the
     * checkout form or the saved instrument itself.
     */
    @PostMapping("/operations/transactions/{transactionId}/capture")
    PaymentSubmissionResult capture(
            @PathVariable String transactionId,
            @RequestHeader(name="X-Operator-Token",required=false) String operatorToken,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        if (!authorizer.authorized(operatorToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "A valid operator credential is required");
        }
        return captures.capture(transactionId, idempotencyKey);
    }

    @GetMapping("/mit/instruments")
    List<PaymentInstrumentSnapshot> instruments() {
        return service.listActiveInstruments();
    }
}
