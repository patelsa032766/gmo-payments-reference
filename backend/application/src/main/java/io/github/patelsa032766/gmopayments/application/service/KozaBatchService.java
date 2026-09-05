package io.github.patelsa032766.gmopayments.application.service;
import io.github.patelsa032766.gmopayments.application.port.*;
import io.github.patelsa032766.gmopayments.domain.*;
import java.util.*;

/** Submits one independent GMO debit transaction for every local batch item. */
public final class KozaBatchService {
    private final KozaBatchRepository batches;private final MitCommandRepository commands;private final PaymentGateway gateway;
    public KozaBatchService(KozaBatchRepository batches,MitCommandRepository commands,PaymentGateway gateway){this.batches=batches;this.commands=commands;this.gateway=gateway;}
    public KozaBatchSubmission submit(String reference,int year,int month,String targetDate,String cutoff,String resultDate,String actor,List<KozaBatchItemRequest> items){
        if(items==null||items.isEmpty())throw new IllegalArgumentException("Select at least one Koza mandate");
        var reservation=batches.reserve(reference,year,month,targetDate,cutoff,resultDate,actor,items);var results=new ArrayList<PaymentSubmissionResult>();
        for(var item:reservation.items())try{results.add(commands.recordSuccess(item.context(),gateway.executeKozaDebit(item.context(),item.instrumentFacts(),targetDate,reference)));}catch(PaymentGatewayException e){results.add(commands.recordFailure(item.context(),e.outcomeUnknown()?"UNKNOWN":"FAILED",e.getMessage(),true,e.evidence()));}catch(RuntimeException e){results.add(commands.recordFailure(item.context(),"UNKNOWN","Koza debit outcome requires inquiry",true));}
        batches.markSubmitted(reservation.batchId());long total=items.stream().mapToLong(KozaBatchItemRequest::amountJpy).sum();return new KozaBatchSubmission(reservation.batchId(),reference,"RESULTS_PENDING",items.size(),total,results);
    }
}
