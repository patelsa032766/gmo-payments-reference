package io.github.patelsa032766.gmopayments.persistence;

import io.github.patelsa032766.gmopayments.application.port.CheckoutExperienceRepository;
import io.github.patelsa032766.gmopayments.domain.CheckoutExperienceSettings;
import io.github.patelsa032766.gmopayments.domain.CheckoutScenario;
import io.github.patelsa032766.gmopayments.domain.DistributionChannel;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

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
                WHERE a.checkout_template=1
                ORDER BY c.full_name,a.application_number
                """).query((rs,row)->new CheckoutScenario(rs.getString(1),rs.getString(2),
                rs.getString(3),rs.getString(4),DistributionChannel.valueOf(rs.getString(5)),
                rs.getString(6),rs.getBoolean(7),rs.getLong(8))).list();
        return new CheckoutExperienceSettings(settings.applicationNumber(),settings.tokenRequired(),settings.language(),customers);
    }
    @Override public CheckoutExperienceSettings update(String applicationNumber,long amountJpy,String paymentPlan,
                                                       boolean operatorTokenRequired,String checkoutLanguage) {
        lockRetry.execute("update checkout experience",()->transactions.execute(status->{
            int changed=jdbc.sql("""
                    UPDATE application_record SET amount_jpy=:amount,payment_plan=:paymentPlan,
                    updated_at=:now,version=version+1
                    WHERE application_number=:application AND checkout_template=1
                    """).param("amount",amountJpy).param("paymentPlan",paymentPlan)
                    .param("now",java.time.Instant.now().toString())
                    .param("application",applicationNumber).update();
            if(changed!=1)throw new IllegalArgumentException("Unknown checkout scenario template: "+applicationNumber);
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

    /**
     * Materializes one business application from a reusable demo template.
     *
     * <p>The sequence is allocated while SQLite owns the write transaction, so
     * two browser tabs cannot receive the same application number. Tokyo is the
     * business date used throughout the payment-operations UI. Templates remain
     * untouched and continue to hold the operator-selected amount and plan.</p>
     */
    @Override public CheckoutScenario createApplication(String templateApplicationNumber) {
        return lockRetry.execute("create checkout application", () -> transactions.execute(status -> {
            int templateCount = jdbc.sql("""
                    SELECT COUNT(*) FROM application_record
                    WHERE application_number=:application AND checkout_template=1
                    """).param("application", templateApplicationNumber).query(Integer.class).single();
            if (templateCount != 1) {
                throw new IllegalArgumentException("Unknown checkout scenario template: "
                        + templateApplicationNumber);
            }

            String date = LocalDate.now(ZoneId.of("Asia/Tokyo"))
                    .format(DateTimeFormatter.BASIC_ISO_DATE);
            String prefix = "APP-" + date + "-";
            int sequence = jdbc.sql("""
                    SELECT COALESCE(MAX(CAST(SUBSTR(application_number, 14) AS INTEGER)), 0) + 1
                    FROM application_record
                    WHERE application_number LIKE :pattern
                    """).param("pattern", prefix + "%").query(Integer.class).single();
            String applicationNumber = prefix + String.format(Locale.ROOT, "%03d", sequence);

            int inserted = jdbc.sql("""
                    INSERT INTO application_record
                        (application_number,customer_id,policy_name,distribution_channel,payment_plan,
                         amount_jpy,selected_method,state,configuration_version,checkout_template)
                    SELECT :newApplication,customer_id,policy_name,distribution_channel,payment_plan,
                           amount_jpy,NULL,'READY',
                           (SELECT version FROM configuration_release WHERE status='PUBLISHED'),0
                    FROM application_record
                    WHERE application_number=:templateApplication AND checkout_template=1
                    """).param("newApplication", applicationNumber)
                    .param("templateApplication", templateApplicationNumber).update();
            if (inserted != 1) throw new IllegalStateException("Checkout application could not be created");
            return findApplication(applicationNumber);
        }));
    }

    @Override public CheckoutScenario findApplication(String applicationNumber) {
        return jdbc.sql("""
                SELECT a.application_number,c.customer_code,c.full_name,a.policy_name,
                       a.distribution_channel,a.payment_plan,c.ekyc_verified,a.amount_jpy
                FROM application_record a JOIN customer c ON c.id=a.customer_id
                WHERE a.application_number=:application
                """).param("application", applicationNumber)
                .query((rs,row)->new CheckoutScenario(rs.getString(1),rs.getString(2),
                        rs.getString(3),rs.getString(4),DistributionChannel.valueOf(rs.getString(5)),
                        rs.getString(6),rs.getBoolean(7),rs.getLong(8)))
                .optional().orElseThrow(() -> new IllegalArgumentException(
                        "Unknown checkout application: " + applicationNumber));
    }
    private record SettingsRow(String applicationNumber,boolean tokenRequired,String language){}
}
