package io.github.patelsa032766.gmopayments.persistence;

import io.github.patelsa032766.gmopayments.application.port.PaymentInstrumentPreferenceRepository;
import io.github.patelsa032766.gmopayments.domain.PaymentInstrumentSnapshot;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.List;

@Repository
public class SQLitePaymentInstrumentPreferenceRepository implements PaymentInstrumentPreferenceRepository {
    private final JdbcClient jdbc;private final SQLiteLockRetryExecutor retry;private final TransactionTemplate tx;private final SQLitePaymentOperationsRepository reader;
    public SQLitePaymentInstrumentPreferenceRepository(JdbcClient jdbc,SQLiteLockRetryExecutor retry,PlatformTransactionManager manager,SQLitePaymentOperationsRepository reader){this.jdbc=jdbc;this.retry=retry;this.tx=new TransactionTemplate(manager);this.reader=reader;}
    @Override public List<PaymentInstrumentSnapshot> setPreferences(String customer,String primary,String backup){return retry.execute("set payment preferences",()->tx.execute(s->{long customerId=jdbc.sql("SELECT id FROM customer WHERE customer_code=:customer").param("customer",customer).query(Long.class).optional().orElseThrow(()->new IllegalArgumentException("Customer not found"));validate(customerId,primary);if(backup!=null)validate(customerId,backup);String now=Instant.now().toString();jdbc.sql("UPDATE payment_instrument SET preference_role=NULL,version=version+1,updated_at=:now WHERE customer_id=:customer").param("now",now).param("customer",customerId).update();jdbc.sql("UPDATE payment_instrument SET preference_role='PRIMARY' WHERE instrument_id=:id").param("id",primary).update();if(backup!=null)jdbc.sql("UPDATE payment_instrument SET preference_role='BACKUP' WHERE instrument_id=:id").param("id",backup).update();return reader.findActiveInstruments().stream().filter(i->i.customerCode().equals(customer)).toList();}));}
    private void validate(long customer,String instrument){int count=jdbc.sql("SELECT COUNT(*) FROM payment_instrument WHERE instrument_id=:instrument AND customer_id=:customer AND state='ACTIVE'").param("instrument",instrument).param("customer",customer).query(Integer.class).single();if(count!=1)throw new IllegalArgumentException("Instrument is not active for this customer: "+instrument);}
}
