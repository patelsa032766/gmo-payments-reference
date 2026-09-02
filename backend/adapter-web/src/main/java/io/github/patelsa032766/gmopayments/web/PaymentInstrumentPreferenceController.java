package io.github.patelsa032766.gmopayments.web;

import io.github.patelsa032766.gmopayments.application.port.OperatorActionAuthorizer;
import io.github.patelsa032766.gmopayments.application.service.PaymentInstrumentPreferenceService;
import io.github.patelsa032766.gmopayments.domain.PaymentInstrumentSnapshot;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController @RequestMapping("/api/v1/mit/customers/{customerCode}/preferences")
public final class PaymentInstrumentPreferenceController {
    private final PaymentInstrumentPreferenceService service;private final OperatorActionAuthorizer authorizer;
    public PaymentInstrumentPreferenceController(PaymentInstrumentPreferenceService service,OperatorActionAuthorizer authorizer){this.service=service;this.authorizer=authorizer;}
    @PutMapping List<PaymentInstrumentSnapshot> set(@PathVariable String customerCode,@RequestHeader(name="X-Operator-Token",required=false)String token,@RequestBody Request request){if(!authorizer.authorized(token))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);return service.set(customerCode,request.primaryInstrumentId(),request.backupInstrumentId());}
    record Request(String primaryInstrumentId,String backupInstrumentId){}
}
