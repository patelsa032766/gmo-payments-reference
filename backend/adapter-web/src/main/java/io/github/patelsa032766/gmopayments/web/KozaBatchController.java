package io.github.patelsa032766.gmopayments.web;
import io.github.patelsa032766.gmopayments.application.port.OperatorActionAuthorizer;
import io.github.patelsa032766.gmopayments.application.service.KozaBatchService;
import io.github.patelsa032766.gmopayments.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController @RequestMapping("/api/v1/mit/koza-batches")
public final class KozaBatchController {
    private final KozaBatchService service;private final OperatorActionAuthorizer authorizer;
    public KozaBatchController(KozaBatchService service,OperatorActionAuthorizer authorizer){this.service=service;this.authorizer=authorizer;}
    @PostMapping KozaBatchSubmission submit(@RequestHeader(name="X-Operator-Token",required=false)String token,@RequestHeader(name="X-Operator-Id",defaultValue="payment-operator")String actor,@RequestBody Request request){if(!authorizer.authorized(token))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);return service.submit(request.batchReference(),request.cycleYear(),request.cycleMonth(),request.targetDate(),request.submissionCutoffAt(),request.expectedResultDate(),actor,request.items());}
    record Request(String batchReference,int cycleYear,int cycleMonth,String targetDate,String submissionCutoffAt,String expectedResultDate,List<KozaBatchItemRequest> items){}
}
