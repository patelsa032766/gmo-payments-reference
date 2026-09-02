package io.github.patelsa032766.gmopayments.application.port;
import io.github.patelsa032766.gmopayments.domain.*;
import java.util.List;
public interface KozaBatchRepository {KozaBatchReservation reserve(String reference,int year,int month,String targetDate,String cutoff,String expectedResultDate,String actor,List<KozaBatchItemRequest> items);void markSubmitted(String batchId);}
