package io.github.patelsa032766.gmopayments.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.patelsa032766.gmopayments.application.port.TransactionInquiryRepository;
import io.github.patelsa032766.gmopayments.domain.PaymentExecutionContext;
import io.github.patelsa032766.gmopayments.domain.PaymentExecutionMode;
import io.github.patelsa032766.gmopayments.domain.PaymentGatewayResult;
import io.github.patelsa032766.gmopayments.domain.PaymentInquiryContext;
import io.github.patelsa032766.gmopayments.domain.PaymentMethodCode;
import io.github.patelsa032766.gmopayments.domain.PaymentNextAction;
import io.github.patelsa032766.gmopayments.domain.PaymentSubmissionResult;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Persists provider inquiries in the original transaction thread.
 *
 * <p>The network call occurs before {@link #record}; consequently SQLite is
 * never held open while GMO is contacted. The result is explicitly labelled
 * as an inquiry, not a webhook, preserving an accurate audit trail.</p>
 */
@Repository
public class SQLiteTransactionInquiryRepository implements TransactionInquiryRepository {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final JdbcClient jdbc;
    private final SQLiteLockRetryExecutor lockRetry;
    private final TransactionTemplate transactions;

    public SQLiteTransactionInquiryRepository(JdbcClient jdbc, SQLiteLockRetryExecutor lockRetry,
                                              PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.lockRetry = lockRetry;
        this.transactions = new TransactionTemplate(manager);
    }

    @Override
    public PaymentInquiryContext load(String transactionId) {
        return jdbc.sql("""
                SELECT t.transaction_id,a.application_number,t.method_code,t.product_code,
                       t.initiation_type,t.operation,t.amount_jpy,t.configuration_version,
                       t.provider_order_id,t.provider_access_id,c.customer_code,c.full_name,
                       (SELECT correlation_id FROM payment_event WHERE transaction_id=t.id ORDER BY id LIMIT 1) correlation
                FROM payment_transaction t JOIN customer c ON c.id=t.customer_id
                LEFT JOIN application_record a ON a.id=t.application_id
                WHERE t.transaction_id=:transactionId
                """).param("transactionId", transactionId).query((rs, n) -> {
            String providerOrderId = rs.getString("provider_order_id");
            if (providerOrderId == null || providerOrderId.isBlank()) {
                throw new IllegalArgumentException("The transaction has no GMO order ID to inquire about");
            }
            var execution = new PaymentExecutionContext(rs.getString("transaction_id"),
                    rs.getString("application_number"), rs.getString("customer_code"),
                    rs.getString("full_name"), "Payment operator", "Example Insurance",
                    PaymentMethodCode.fromApiValue(rs.getString("method_code")), rs.getString("product_code"),
                    rs.getString("initiation_type"), rs.getString("operation"), rs.getLong("amount_jpy"),
                    rs.getInt("configuration_version"), rs.getString("correlation"), PaymentExecutionMode.CAPTURE);
            return new PaymentInquiryContext(execution, providerOrderId, rs.getString("provider_access_id"));
        }).optional().orElseThrow(() -> new IllegalArgumentException("Unknown transaction: " + transactionId));
    }

    @Override
    public PaymentSubmissionResult record(PaymentInquiryContext inquiry, PaymentGatewayResult result) {
        return lockRetry.execute("record provider inquiry", () -> transactions.execute(status -> {
            String transactionId = inquiry.execution().transactionId();
            long transactionPk = jdbc.sql("SELECT id FROM payment_transaction WHERE transaction_id=:id")
                    .param("id", transactionId).query(Long.class).single();
            String now = Instant.now().toString();
            jdbc.sql("""
                    UPDATE payment_transaction SET canonical_state=:state,provider_status=:providerStatus,
                        provider_access_id=COALESCE(:accessId,provider_access_id),
                        settled_amount_jpy=CASE WHEN :state='PAID' THEN amount_jpy ELSE settled_amount_jpy END,
                        requires_attention=:attention,updated_at=:now,version=version+1 WHERE id=:id
                    """).param("state", result.canonicalState()).param("providerStatus", result.providerStatus())
                    .param("accessId", result.providerAccessId()).param("attention", result.requiresAttention())
                    .param("now", now).param("id", transactionPk).update();
            jdbc.sql("UPDATE debit_batch_item SET state=:state,updated_at=:now WHERE transaction_id=:id")
                    .param("state", result.canonicalState()).param("now", now).param("id", transactionPk).update();

            String eventId = "EVT-" + compact();
            jdbc.sql("""
                    INSERT INTO payment_event(event_id,transaction_id,event_type,source,summary,
                        canonical_state_after,actor,correlation_id,evidence_json)
                    VALUES(:event,:transaction,'PROVIDER_STATUS_INQUIRY','INQUIRY',:summary,
                        :state,'payment-operator',:correlation,:evidence)
                    """).param("event", eventId).param("transaction", transactionPk)
                    .param("summary", result.summary()).param("state", result.canonicalState())
                    .param("correlation", inquiry.execution().correlationId())
                    .param("evidence", json(Map.of("providerStatus", result.providerStatus()))).update();
            long eventPk = jdbc.sql("SELECT id FROM payment_event WHERE event_id=:event")
                    .param("event", eventId).query(Long.class).single();
            jdbc.sql("""
                    INSERT INTO provider_exchange(exchange_id,transaction_id,event_id,direction,transport,
                        operation,endpoint,http_status,duration_ms,request_body_json,response_body_json,
                        outcome,correlation_id)
                    VALUES(:exchange,:transaction,:event,'PAIRED','INQUIRY',:operation,:endpoint,:httpStatus,
                        :duration,:request,:response,:outcome,:correlation)
                    """).param("exchange", "EXC-" + compact()).param("transaction", transactionPk)
                    .param("event", eventPk).param("operation", result.providerOperation())
                    .param("endpoint", result.endpoint()).param("httpStatus", result.httpStatus())
                    .param("duration", result.durationMs()).param("request", json(result.sanitizedRequest()))
                    .param("response", json(result.sanitizedResponse())).param("outcome", result.canonicalState())
                    .param("correlation", inquiry.execution().correlationId()).update();

            return new PaymentSubmissionResult(transactionId, inquiry.execution().applicationNumber(),
                    inquiry.execution().method(), result.canonicalState(), result.providerStatus(),
                    result.requiresAttention(), PaymentNextAction.none(), Map.of(), false);
        }));
    }

    private static String json(Object value) {
        try { return JSON.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("Inquiry evidence could not be encoded", exception); }
    }

    private static String compact() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }
}
