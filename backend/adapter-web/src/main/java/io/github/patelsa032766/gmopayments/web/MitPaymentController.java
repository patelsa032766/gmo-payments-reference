package io.github.patelsa032766.gmopayments.web;

import io.github.patelsa032766.gmopayments.application.port.OperatorActionAuthorizer;
import io.github.patelsa032766.gmopayments.application.service.MitPaymentService;
import io.github.patelsa032766.gmopayments.domain.PaymentSubmissionResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/** Operator-confirmed individual merchant-initiated payment commands. */
@RestController
@RequestMapping("/api/v1/mit/payments")
public final class MitPaymentController {
    private final MitPaymentService payments; private final OperatorActionAuthorizer authorizer;
    public MitPaymentController(MitPaymentService payments,OperatorActionAuthorizer authorizer){this.payments=payments;this.authorizer=authorizer;}
    @PostMapping PaymentSubmissionResult submit(
            @RequestHeader(name="X-Operator-Token",required=false)String operatorToken,
            @RequestHeader("Idempotency-Key")String idempotencyKey,
            @Valid @RequestBody Request request){
        if(!authorizer.authorized(operatorToken))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"A valid operator credential is required");
        return payments.submit(request.instrumentId(),request.amountJpy(),request.merchantReference(),idempotencyKey,request.details());
    }
    record Request(@NotBlank String instrumentId,@Min(1)long amountJpy,@NotBlank String merchantReference,
                   @NotNull Map<String,Object> details){}
}
