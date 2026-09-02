package io.github.patelsa032766.gmopayments.domain;
import java.util.List;
public record KozaBatchReservation(String batchId,String batchReference,String targetDate,List<MitExecutionReservation> items){public KozaBatchReservation{items=List.copyOf(items);}}
