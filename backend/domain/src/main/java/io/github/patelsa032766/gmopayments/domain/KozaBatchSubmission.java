package io.github.patelsa032766.gmopayments.domain;
import java.util.List;
public record KozaBatchSubmission(String batchId,String batchReference,String state,int submittedCount,long totalJpy,List<PaymentSubmissionResult> payments){public KozaBatchSubmission{payments=List.copyOf(payments);}}
