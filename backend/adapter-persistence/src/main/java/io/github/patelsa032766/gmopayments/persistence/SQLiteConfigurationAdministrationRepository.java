package io.github.patelsa032766.gmopayments.persistence;

import io.github.patelsa032766.gmopayments.application.port.ConfigurationAdministrationRepository;
import io.github.patelsa032766.gmopayments.domain.ConfigurationMethodUpdate;
import io.github.patelsa032766.gmopayments.domain.ConfigurationRelease;
import io.github.patelsa032766.gmopayments.domain.ConfigurationWorkspace;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

/** Copy-on-write SQLite release administration; published rows are immutable. */
@Repository
public class SQLiteConfigurationAdministrationRepository implements ConfigurationAdministrationRepository {
    private final JdbcClient jdbc; private final SQLiteCheckoutConfigurationRepository reader;
    private final SQLiteLockRetryExecutor lockRetry; private final TransactionTemplate transactions;
    public SQLiteConfigurationAdministrationRepository(JdbcClient jdbc, SQLiteCheckoutConfigurationRepository reader,
            SQLiteLockRetryExecutor lockRetry, PlatformTransactionManager manager) {
        this.jdbc=jdbc; this.reader=reader; this.lockRetry=lockRetry; this.transactions=new TransactionTemplate(manager);
    }

    @Override public ConfigurationWorkspace workspace() { return new ConfigurationWorkspace(reader.findActiveRelease(), reader.findDraftRelease().orElse(null)); }

    @Override public ConfigurationRelease saveDraft(List<ConfigurationMethodUpdate> methods) {
        return lockRetry.execute("save configuration draft", () -> transactions.execute(status -> {
            long draftId = ensureDraft();
            jdbc.sql("UPDATE payment_method_configuration SET display_order = display_order + 1000 WHERE release_id=:id")
                    .param("id",draftId).update();
            for (var method:methods) jdbc.sql("""
                    UPDATE payment_method_configuration SET enabled=:enabled, recurring=:recurring,
                      monthly_only=:monthlyOnly, min_amount_jpy=:minimum, max_amount_jpy=:maximum,
                      display_order=:displayOrder WHERE release_id=:releaseId AND code=:code
                    """).param("enabled",method.enabled()).param("recurring",method.recurring())
                    .param("monthlyOnly",method.monthlyOnly()).param("minimum",method.minimumAmountJpy())
                    .param("maximum",method.maximumAmountJpy()).param("displayOrder",method.displayOrder())
                    .param("releaseId",draftId).param("code",method.code().apiValue()).update();
            return reader.findDraftRelease().orElseThrow();
        }));
    }

    @Override public ConfigurationRelease publish(String actor) {
        return lockRetry.execute("publish configuration", () -> transactions.execute(status -> {
            Long draftId=jdbc.sql("SELECT id FROM configuration_release WHERE status='DRAFT' ORDER BY version DESC LIMIT 1").query(Long.class).optional().orElseThrow(() -> new IllegalStateException("No draft exists"));
            jdbc.sql("UPDATE configuration_release SET status='RETIRED' WHERE status='PUBLISHED'").update();
            jdbc.sql("UPDATE configuration_release SET status='PUBLISHED', published_at=:at, published_by=:actor WHERE id=:id")
                    .param("at",Instant.now().toString()).param("actor",actor).param("id",draftId).update();
            return reader.findActiveRelease();
        }));
    }

    @Override public void discardDraft() {
        lockRetry.execute("discard configuration draft", () -> transactions.execute(status -> {
            var id=jdbc.sql("SELECT id FROM configuration_release WHERE status='DRAFT'").query(Long.class).optional();
            id.ifPresent(value -> { jdbc.sql("DELETE FROM retry_policy_configuration WHERE release_id=:id").param("id",value).update(); jdbc.sql("DELETE FROM system_feature_configuration WHERE release_id=:id").param("id",value).update(); jdbc.sql("DELETE FROM payment_method_configuration WHERE release_id=:id").param("id",value).update(); jdbc.sql("DELETE FROM configuration_release WHERE id=:id").param("id",value).update(); });
            return null;
        }));
    }

    private long ensureDraft() {
        var existing=jdbc.sql("SELECT id FROM configuration_release WHERE status='DRAFT' ORDER BY version DESC LIMIT 1").query(Long.class).optional();
        if(existing.isPresent()) return existing.get();
        var active=jdbc.sql("SELECT id,version FROM configuration_release WHERE status='PUBLISHED'").query((rs,n)->new Active(rs.getLong(1),rs.getInt(2))).single();
        int version=jdbc.sql("SELECT COALESCE(MAX(version),0)+1 FROM configuration_release").query(Integer.class).single();
        jdbc.sql("INSERT INTO configuration_release(version,status) VALUES(:version,'DRAFT')").param("version",version).update();
        long draft=jdbc.sql("SELECT id FROM configuration_release WHERE version=:version").param("version",version).query(Long.class).single();
        jdbc.sql("""
            INSERT INTO payment_method_configuration
                (release_id,code,label_en,description_en,label_ja,description_ja,enabled,recurring,
                 monthly_only,min_amount_jpy,max_amount_jpy,non_ekyc_max_amount_jpy,channels,display_order)
            SELECT :draft,code,label_en,description_en,label_ja,description_ja,enabled,recurring,
                   monthly_only,min_amount_jpy,max_amount_jpy,non_ekyc_max_amount_jpy,channels,display_order
            FROM payment_method_configuration WHERE release_id=:active
            """).param("draft",draft).param("active",active.id()).update();
        jdbc.sql("INSERT INTO system_feature_configuration(release_id,feature_code,enabled,value_json) SELECT :draft,feature_code,enabled,value_json FROM system_feature_configuration WHERE release_id=:active").param("draft",draft).param("active",active.id()).update();
        jdbc.sql("INSERT INTO retry_policy_configuration(release_id,operation_code,maximum_attempts,base_delay_ms,maximum_delay_ms,jitter_ratio,retryable_codes) SELECT :draft,operation_code,maximum_attempts,base_delay_ms,maximum_delay_ms,jitter_ratio,retryable_codes FROM retry_policy_configuration WHERE release_id=:active").param("draft",draft).param("active",active.id()).update();
        return draft;
    }
    private record Active(long id,int version){}
}
