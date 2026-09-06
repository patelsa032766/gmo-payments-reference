package io.github.patelsa032766.gmopayments.web;

import io.github.patelsa032766.gmopayments.application.service.KozaBatchService;
import io.github.patelsa032766.gmopayments.domain.KozaBatchItemRequest;
import io.github.patelsa032766.gmopayments.domain.PaymentSubmissionResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Operator API for one future Koza Furikae debit.
 *
 * <p>GMO uses the same EntryTranBankaccount/ExecTranBankaccount pair whether
 * this command originated from an individual API call or a monthly selection.
 * Reusing the batch application service guarantees identical persistence and
 * asynchronous webhook handling; a one-item API grouping remains visible for
 * audit without pretending the bank has already collected the money.</p>
 */
@RestController
@RequestMapping("/api/v1/mit/koza-debits")
public final class KozaDebitController {
    private static final DateTimeFormatter GMO_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private final KozaBatchService service;
    private final OperatorActionGuard operatorActions;

    public KozaDebitController(KozaBatchService service, OperatorActionGuard operatorActions) {
        this.service = service;
        this.operatorActions = operatorActions;
    }

    @PostMapping
    PaymentSubmissionResult submit(
            @RequestHeader(name = "X-Operator-Token", required = false) String token,
            @RequestHeader(name = "X-Operator-Id", defaultValue = "payment-operator") String actor,
            @RequestBody Request request) {
        operatorActions.requireAuthorized(token);
        if (request.amountJpy() <= 0) throw new IllegalArgumentException("amountJpy must be positive");
        if (request.merchantReference() == null || request.merchantReference().isBlank()) {
            throw new IllegalArgumentException("merchantReference is required");
        }
        LocalDate debitDate = LocalDate.parse(request.targetDate(), GMO_DATE);
        var result = service.submit("API-" + request.merchantReference(), debitDate.getYear(),
                debitDate.getMonthValue(), request.targetDate(), "API submission",
                request.targetDate(), actor,
                List.of(new KozaBatchItemRequest(request.instrumentId(), request.amountJpy())));
        return result.payments().getFirst();
    }

    record Request(String instrumentId, long amountJpy, String merchantReference, String targetDate) {}
}
