package io.github.patelsa032766.gmopayments.web;

import io.github.patelsa032766.gmopayments.application.service.PaymentInstrumentPreferenceService;
import io.github.patelsa032766.gmopayments.domain.PaymentInstrumentSnapshot;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/v1/mit/customers/{customerCode}/preferences")
public final class PaymentInstrumentPreferenceController {
    private final PaymentInstrumentPreferenceService service;private final OperatorActionGuard operatorActions;
    public PaymentInstrumentPreferenceController(PaymentInstrumentPreferenceService service,OperatorActionGuard operatorActions){this.service=service;this.operatorActions=operatorActions;}
    @PutMapping List<PaymentInstrumentSnapshot> set(@PathVariable String customerCode,@RequestHeader(name="X-Operator-Token",required=false)String token,@RequestBody Request request){operatorActions.requireAuthorized(token);return service.set(customerCode,request.primaryInstrumentId(),request.backupInstrumentId());}
    record Request(String primaryInstrumentId,String backupInstrumentId){}
}
