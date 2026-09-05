package io.github.patelsa032766.gmopayments.web;

import io.github.patelsa032766.gmopayments.application.service.MitPaymentService;
import io.github.patelsa032766.gmopayments.domain.PaymentSubmissionResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Operator-confirmed individual merchant-initiated payment commands. */
@RestController
@RequestMapping("/api/v1/mit/payments")
public final class MitPaymentController {
    private final MitPaymentService payments; private final OperatorActionGuard operatorActions;
    public MitPaymentController(MitPaymentService payments,OperatorActionGuard operatorActions){this.payments=payments;this.operatorActions=operatorActions;}
    @PostMapping PaymentSubmissionResult submit(
            @RequestHeader(name="X-Operator-Token",required=false)String operatorToken,
            @RequestHeader("Idempotency-Key")String idempotencyKey,
            @Valid @RequestBody Request request){
        operatorActions.requireAuthorized(operatorToken);
        return payments.submit(request.instrumentId(),request.amountJpy(),request.merchantReference(),idempotencyKey,request.details());
    }
    record Request(@NotBlank String instrumentId,@Min(1)long amountJpy,@NotBlank String merchantReference,
                   @NotNull Map<String,Object> details){}
}
