package io.github.patelsa032766.gmopayments.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.patelsa032766.gmopayments.application.port.KozaBatchRepository;
import io.github.patelsa032766.gmopayments.domain.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.*;

/** Creates the local batch grouping and every independent payment thread before GMO is called. */
@Repository
public class SQLiteKozaBatchRepository implements KozaBatchRepository {
    private static final ObjectMapper JSON=new ObjectMapper();private static final TypeReference<Map<String,Object>> MAP=new TypeReference<>(){};
    private final JdbcClient jdbc;private final SQLiteLockRetryExecutor retry;private final TransactionTemplate tx;
    public SQLiteKozaBatchRepository(JdbcClient jdbc,SQLiteLockRetryExecutor retry,PlatformTransactionManager manager){this.jdbc=jdbc;this.retry=retry;this.tx=new TransactionTemplate(manager);}
    @Override public KozaBatchReservation reserve(String reference,int year,int month,String targetDate,String cutoff,String resultDate,String actor,List<KozaBatchItemRequest> items){return retry.execute("reserve Koza batch",()->tx.execute(s->{
        if(jdbc.sql("SELECT COUNT(*) FROM debit_batch WHERE batch_reference=:reference").param("reference",reference).query(Integer.class).single()>0)throw new IllegalArgumentException("Batch reference already exists");
        String batchId="BATCH-"+compact();long total=items.stream().mapToLong(KozaBatchItemRequest::amountJpy).sum();jdbc.sql("""
            INSERT INTO debit_batch(batch_id,batch_reference,cycle_year,cycle_month,target_date,submission_cutoff_at,expected_result_date,state,selected_count,selected_total_jpy,created_by)
            VALUES(:id,:reference,:year,:month,:target,:cutoff,:result,'PREPARING',:count,:total,:actor)
            """).param("id",batchId).param("reference",reference).param("year",year).param("month",month).param("target",targetDate).param("cutoff",cutoff).param("result",resultDate).param("count",items.size()).param("total",total).param("actor",actor).update();long batchPk=jdbc.sql("SELECT id FROM debit_batch WHERE batch_id=:id").param("id",batchId).query(Long.class).single();
        var reservations=new ArrayList<MitExecutionReservation>();int sequence=1;for(var requested:items){var i=instrument(requested.instrumentId());String transactionId="TXN-KOZA-"+compact(),merchantReference=reference+"-"+String.format("%03d",sequence++),correlation="CORR-"+UUID.randomUUID();jdbc.sql("""
            INSERT INTO payment_transaction(transaction_id,customer_id,instrument_id,method_code,product_code,initiation_type,operation,amount_jpy,canonical_state,merchant_reference,configuration_version)
            VALUES(:transaction,:customer,:instrument,'kozaFurikae','koza_furikae_select','MIT','SCHEDULE_DEBIT',:amount,'PROCESSING',:reference,:version)
            """).param("transaction",transactionId).param("customer",i.customerId()).param("instrument",i.id()).param("amount",requested.amountJpy()).param("reference",merchantReference).param("version",i.version()).update();long transactionPk=jdbc.sql("SELECT id FROM payment_transaction WHERE transaction_id=:id").param("id",transactionId).query(Long.class).single();jdbc.sql("INSERT INTO debit_batch_item(batch_id,transaction_id,instrument_id,amount_jpy,state) VALUES(:batch,:transaction,:instrument,:amount,'RESERVED')").param("batch",batchPk).param("transaction",transactionPk).param("instrument",i.id()).param("amount",requested.amountJpy()).update();jdbc.sql("INSERT INTO payment_event(event_id,transaction_id,event_type,source,summary,canonical_state_after,actor,correlation_id,evidence_json) VALUES(:event,:transaction,'KOZA_DEBIT_RESERVED','MIT_BATCH','Monthly debit reserved','PROCESSING',:actor,:correlation,:evidence)").param("event","EVT-"+compact()).param("transaction",transactionPk).param("actor",actor).param("correlation",correlation).param("evidence",json(Map.of("batchReference",reference,"targetDate",targetDate))).update();var facts=new LinkedHashMap<String,Object>(i.metadata());facts.put("memberId",i.member());facts.put("instrumentReference",i.providerReference());facts.put("maskedDisplay",i.masked());reservations.add(new MitExecutionReservation(new PaymentExecutionContext(transactionId,merchantReference,i.customerCode(),i.customerName(),"Payment operator","Example Insurance",PaymentMethodCode.KOZA_FURIKAE_SELECT,"koza_furikae_select","MIT","SCHEDULE_DEBIT",requested.amountJpy(),i.version(),correlation),facts,false));}
        return new KozaBatchReservation(batchId,reference,targetDate,reservations);
    }));}
    @Override public void markSubmitted(String batchId){retry.execute("mark Koza batch submitted",()->tx.execute(s->{String now=Instant.now().toString();jdbc.sql("UPDATE debit_batch SET state='RESULTS_PENDING',submitted_at=:now,updated_at=:now WHERE batch_id=:id").param("now",now).param("id",batchId).update();jdbc.sql("UPDATE debit_batch_item SET state='SUBMITTED',updated_at=:now WHERE batch_id=(SELECT id FROM debit_batch WHERE batch_id=:id)").param("now",now).param("id",batchId).update();return null;}));}
    private Instrument instrument(String id){return jdbc.sql("""
        SELECT i.id,i.provider_member_reference,i.provider_instrument_reference,i.masked_display,i.metadata_json,c.id customer_id,c.customer_code,c.full_name,r.version
        FROM payment_instrument i JOIN customer c ON c.id=i.customer_id JOIN configuration_release r ON r.status='PUBLISHED'
        WHERE i.instrument_id=:id AND i.method_code='kozaFurikae' AND i.state='ACTIVE'
        """).param("id",id).query((rs,n)->new Instrument(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4),parse(rs.getString(5)),rs.getLong(6),rs.getString(7),rs.getString(8),rs.getInt(9))).optional().orElseThrow(()->new IllegalArgumentException("Active Koza mandate not found: "+id));}
    private static String compact(){return UUID.randomUUID().toString().replace("-","").substring(0,12).toUpperCase();}private static String json(Object value){try{return JSON.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}private static Map<String,Object> parse(String value){try{return JSON.readValue(value==null?"{}":value,MAP);}catch(Exception e){throw new IllegalStateException(e);}}
    private record Instrument(long id,String member,String providerReference,String masked,Map<String,Object> metadata,long customerId,String customerCode,String customerName,int version){}
}
