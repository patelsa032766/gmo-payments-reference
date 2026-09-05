package io.github.patelsa032766.gmopayments.application.service;

import io.github.patelsa032766.gmopayments.application.port.MitCommandRepository;
import io.github.patelsa032766.gmopayments.application.port.PaymentGateway;
import io.github.patelsa032766.gmopayments.domain.PaymentSubmissionResult;
import io.github.patelsa032766.gmopayments.domain.PaymentExecutionMode;
import io.github.patelsa032766.gmopayments.domain.PaymentMethodCode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/** Executes one operator-confirmed recurring payment with idempotency and unknown-outcome safety. */
public final class MitPaymentService {
    private final MitCommandRepository repository; private final PaymentGateway gateway;
    public MitPaymentService(MitCommandRepository repository,PaymentGateway gateway){this.repository=repository;this.gateway=gateway;}
    public PaymentSubmissionResult submit(String instrumentId,long amountJpy,String reference,String key,Map<String,Object> details){
        if(amountJpy<1)throw new IllegalArgumentException("Amount must be positive");
        if(reference==null||reference.isBlank())throw new IllegalArgumentException("Merchant reference is required");
        if(key==null||key.isBlank())throw new IllegalArgumentException("Idempotency key is required");
        PaymentExecutionMode mode=PaymentExecutionMode.from(String.valueOf(details.getOrDefault("authorizationMode","CAPTURE")));
        String fingerprint=sha(instrumentId+"|"+amountJpy+"|"+reference+"|"+mode);
        var reservation=repository.reserve(instrumentId,amountJpy,reference,key,fingerprint,mode);
        if(reservation.replayed())return repository.findSubmission(reservation.context().transactionId()).orElseThrow();
        if(mode==PaymentExecutionMode.AUTH && reservation.context().method()!=PaymentMethodCode.CARD
                && reservation.context().method()!=PaymentMethodCode.PAYPAY){
            return repository.recordFailure(reservation.context(),"FAILED",
                    "Authorization is supported only for Card and PayPay",false);
        }
        Map<String,Object> normalized=Map.of("authorizationMode",mode.name());
        try{return repository.recordSuccess(reservation.context(),gateway.executeMit(reservation.context(),reservation.instrumentFacts(),normalized));}
        catch(PaymentGatewayException exception){return repository.recordFailure(reservation.context(),exception.outcomeUnknown()?"UNKNOWN":"FAILED",exception.getMessage(),true,exception.evidence());}
        catch(RuntimeException exception){return repository.recordFailure(reservation.context(),"UNKNOWN","Provider outcome is unknown; inquiry is required",true);}
    }
    private static String sha(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
