package io.github.patelsa032766.gmopayments.persistence;

import io.github.patelsa032766.gmopayments.application.port.CheckoutExperienceRepository;
import io.github.patelsa032766.gmopayments.domain.CheckoutExperienceSettings;
import io.github.patelsa032766.gmopayments.domain.CheckoutScenario;
import io.github.patelsa032766.gmopayments.domain.DistributionChannel;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** SQLite adapter for local test-customer selection and operator-action authentication policy. */
@Repository
public class SQLiteCheckoutExperienceRepository implements CheckoutExperienceRepository {
    private final JdbcClient jdbc;
    private final SQLiteLockRetryExecutor lockRetry;
    private final TransactionTemplate transactions;
    public SQLiteCheckoutExperienceRepository(JdbcClient jdbc, SQLiteLockRetryExecutor lockRetry,
                                              PlatformTransactionManager manager) {
        this.jdbc=jdbc; this.lockRetry=lockRetry; this.transactions=new TransactionTemplate(manager);
    }
    @Override public CheckoutExperienceSettings get() {
        var settings=jdbc.sql("""
                SELECT selected_application_number,operator_token_required,checkout_language
                FROM checkout_experience_settings WHERE id=1
                """).query((rs,row)->new SettingsRow(rs.getString(1),rs.getBoolean(2),rs.getString(3))).single();
        var customers=jdbc.sql("""
                SELECT a.application_number,c.customer_code,c.full_name,a.policy_name,
                       a.distribution_channel,a.payment_plan,c.ekyc_verified,a.amount_jpy
                FROM application_record a JOIN customer c ON c.id=a.customer_id
                ORDER BY c.full_name,a.application_number
                """).query((rs,row)->new CheckoutScenario(rs.getString(1),rs.getString(2),
                rs.getString(3),rs.getString(4),DistributionChannel.valueOf(rs.getString(5)),
                rs.getString(6),rs.getBoolean(7),rs.getLong(8))).list();
        return new CheckoutExperienceSettings(settings.applicationNumber(),settings.tokenRequired(),settings.language(),customers);
    }
    @Override public CheckoutExperienceSettings update(String applicationNumber,long amountJpy,
                                                       boolean operatorTokenRequired,String checkoutLanguage) {
        lockRetry.execute("update checkout experience",()->transactions.execute(status->{
            int changed=jdbc.sql("""
                    UPDATE application_record SET amount_jpy=:amount,updated_at=:now,version=version+1
                    WHERE application_number=:application
                    """).param("amount",amountJpy).param("now",java.time.Instant.now().toString())
                    .param("application",applicationNumber).update();
            if(changed!=1)throw new IllegalArgumentException("Unknown predefined checkout application: "+applicationNumber);
            jdbc.sql("""
                    UPDATE checkout_experience_settings SET selected_application_number=:application,
                    operator_token_required=:required,checkout_language=:language,updated_at=:now WHERE id=1
                    """).param("application",applicationNumber).param("required",operatorTokenRequired)
                    .param("language",checkoutLanguage)
                    .param("now",java.time.Instant.now().toString()).update();
            return null;
        }));
        return get();
    }
    private record SettingsRow(String applicationNumber,boolean tokenRequired,String language){}
}
