package io.github.patelsa032766.gmopayments.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.patelsa032766.gmopayments.application.port.CaptureCommandRepository;
import io.github.patelsa032766.gmopayments.domain.CaptureReservation;
import io.github.patelsa032766.gmopayments.domain.PaymentExecutionContext;
import io.github.patelsa032766.gmopayments.domain.PaymentExecutionMode;
import io.github.patelsa032766.gmopayments.domain.PaymentGatewayResult;
import io.github.patelsa032766.gmopayments.domain.PaymentMethodCode;
import io.github.patelsa032766.gmopayments.domain.PaymentNextAction;
import io.github.patelsa032766.gmopayments.domain.PaymentSubmissionResult;
import io.github.patelsa032766.gmopayments.domain.ProviderCallEvidence;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** SQLite implementation that appends capture to the original payment thread. */
@Repository
public class SQLiteCaptureCommandRepository implements CaptureCommandRepository {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final JdbcClient jdbc;
    private final SQLiteLockRetryExecutor lockRetry;
    private final TransactionTemplate transactions;

    public SQLiteCaptureCommandRepository(JdbcClient jdbc, SQLiteLockRetryExecutor lockRetry,
                                          PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.lockRetry = lockRetry;
        this.transactions = new TransactionTemplate(manager);
    }

    @Override
    public CaptureReservation reserve(String transactionId, String idempotencyKey) {
        return lockRetry.execute("reserve capture", () -> transactions.execute(status -> {
            var existing = jdbc.sql("""
                    SELECT i.request_fingerprint FROM idempotency_record i
                    JOIN payment_transaction t ON t.id=i.transaction_id
                    WHERE i.idempotency_key=:key AND i.command_type='CAPTURE'
                    """).param("key", idempotencyKey).query(String.class).optional();
            String fingerprint = transactionId + "|CAPTURE";
            if (existing.isPresent()) {
                if (!existing.get().equals(fingerprint)) {
                    throw new IllegalArgumentException("Idempotency key was used for another capture");
                }
                return load(transactionId, true);
            }

            CaptureReservation reservation = load(transactionId, false);
            if (reservation.context().method() != PaymentMethodCode.CARD
                    && reservation.context().method() != PaymentMethodCode.PAYPAY) {
                throw new IllegalArgumentException("Only Card and PayPay authorizations can be captured");
            }
            if (reservation.providerAccessId() == null || reservation.providerAccessId().isBlank()) {
                throw new IllegalArgumentException("The authorization has no GMO access ID");
            }
            int changed = jdbc.sql("""
                    UPDATE payment_transaction SET canonical_state='CAPTURE_PROCESSING',
                        operation='CAPTURE', updated_at=:now, version=version+1
                    WHERE transaction_id=:transactionId AND canonical_state='AUTHORIZED'
                    """).param("now", Instant.now().toString()).param("transactionId", transactionId).update();
            if (changed != 1) throw new IllegalArgumentException("This transaction is not waiting for capture");

            long transactionPk = transactionPk(transactionId);
            jdbc.sql("""
                    INSERT INTO idempotency_record
                        (idempotency_key,command_type,request_fingerprint,status,transaction_id)
                    VALUES (:key,'CAPTURE',:fingerprint,'PROCESSING',:transactionPk)
                    """).param("key", idempotencyKey).param("fingerprint", fingerprint)
                    .param("transactionPk", transactionPk).update();
            appendEvent(transactionPk, "CAPTURE_REQUESTED", "OPERATOR", "Capture requested",
                    "CAPTURE_PROCESSING", reservation.context().correlationId(), Map.of());
            return reservation;
        }));
    }

    @Override
    public PaymentSubmissionResult recordSuccess(CaptureReservation reservation, PaymentGatewayResult result) {
        return lockRetry.execute("record capture", () -> transactions.execute(status -> {
            long transactionPk = transactionPk(reservation.context().transactionId());
            String now = Instant.now().toString();
            jdbc.sql("""
                    UPDATE payment_transaction SET canonical_state=:state, provider_status=:providerStatus,
                        provider_order_id=COALESCE(:orderId,provider_order_id),
                        provider_access_id=COALESCE(:accessId,provider_access_id), requires_attention=0,
                        operation='CAPTURE', updated_at=:now, version=version+1 WHERE id=:id
                    """).param("state", result.canonicalState()).param("providerStatus", result.providerStatus())
                    .param("orderId", result.providerOrderId()).param("accessId", result.providerAccessId())
                    .param("now", now).param("id", transactionPk).update();
            updateApplication(reservation.context().applicationNumber(), result.canonicalState(), now);
            long eventPk = appendEvent(transactionPk, "PAYMENT_CAPTURED", "GMO_API",
                    "Authorization captured", result.canonicalState(), reservation.context().correlationId(),
                    Map.of("providerStatus", result.providerStatus()));
            appendExchange(transactionPk, eventPk, reservation.context().correlationId(), result);
            completeIdempotency(transactionPk, result.canonicalState(), now);
            return submission(reservation.context(), result.canonicalState(), result.providerStatus(), false);
        }));
    }

    @Override
    public PaymentSubmissionResult recordFailure(CaptureReservation reservation, String state, String summary,
                                                 boolean requiresAttention, ProviderCallEvidence evidence) {
        return lockRetry.execute("record capture failure", () -> transactions.execute(status -> {
            long transactionPk = transactionPk(reservation.context().transactionId());
            String now = Instant.now().toString();
            jdbc.sql("""
                    UPDATE payment_transaction SET canonical_state=:state, provider_status=:state,
                        requires_attention=:attention, updated_at=:now, version=version+1 WHERE id=:id
                    """).param("state", state).param("attention", requiresAttention)
                    .param("now", now).param("id", transactionPk).update();
            updateApplication(reservation.context().applicationNumber(), state, now);
            long eventPk = appendEvent(transactionPk, "CAPTURE_FAILED", "GMO_API", summary, state,
                    reservation.context().correlationId(), Map.of());
            if (evidence != null) appendExchange(transactionPk, eventPk,
                    reservation.context().correlationId(), evidence);
            completeIdempotency(transactionPk, state, now);
            return submission(reservation.context(), state, state, requiresAttention);
        }));
    }

    @Override
    public Optional<PaymentSubmissionResult> findSubmission(String transactionId) {
        return jdbc.sql("""
                SELECT t.transaction_id,a.application_number,t.method_code,t.canonical_state,
                       t.provider_status,t.requires_attention
                FROM payment_transaction t LEFT JOIN application_record a ON a.id=t.application_id
                WHERE t.transaction_id=:transactionId
                """).param("transactionId", transactionId).query((rs, n) -> new PaymentSubmissionResult(
                rs.getString(1), rs.getString(2), PaymentMethodCode.fromApiValue(rs.getString(3)),
                rs.getString(4), rs.getString(5), rs.getBoolean(6), PaymentNextAction.none(),
                Map.of(), true)).optional();
    }

    private CaptureReservation load(String transactionId, boolean replayed) {
        return jdbc.sql("""
                SELECT t.transaction_id,a.application_number,t.merchant_reference,t.method_code,
                       t.product_code,t.initiation_type,t.amount_jpy,t.configuration_version,
                       t.provider_access_id,t.provider_order_id,c.customer_code,c.full_name,
                       (SELECT correlation_id FROM payment_event WHERE transaction_id=t.id ORDER BY id LIMIT 1) correlation
                FROM payment_transaction t JOIN customer c ON c.id=t.customer_id
                LEFT JOIN application_record a ON a.id=t.application_id
                WHERE t.transaction_id=:transactionId
                """).param("transactionId", transactionId).query((rs, n) -> {
            var context = new PaymentExecutionContext(rs.getString("transaction_id"),
                    rs.getString("application_number"), rs.getString("customer_code"),
                    rs.getString("full_name"), "Payment operator", "Example Insurance",
                    PaymentMethodCode.fromApiValue(rs.getString("method_code")), rs.getString("product_code"),
                    rs.getString("initiation_type"), "CAPTURE", rs.getLong("amount_jpy"),
                    rs.getInt("configuration_version"), rs.getString("correlation"), PaymentExecutionMode.CAPTURE);
            return new CaptureReservation(context, rs.getString("provider_access_id"),
                    rs.getString("provider_order_id"), replayed);
        }).optional().orElseThrow(() -> new IllegalArgumentException("Unknown transaction: " + transactionId));
    }

    private PaymentSubmissionResult submission(PaymentExecutionContext context, String state,
                                               String providerStatus, boolean attention) {
        return new PaymentSubmissionResult(context.transactionId(), context.applicationNumber(), context.method(),
                state, providerStatus, attention, PaymentNextAction.none(), Map.of(), false);
    }

    private void updateApplication(String applicationNumber, String state, String now) {
        if (applicationNumber == null) return;
        jdbc.sql("UPDATE application_record SET state=:state,updated_at=:now,version=version+1 WHERE application_number=:number")
                .param("state", state).param("now", now).param("number", applicationNumber).update();
    }

    private long appendEvent(long transactionPk, String type, String source, String summary, String state,
                             String correlationId, Map<String,Object> evidence) {
        String eventId = "EVT-" + compact();
        jdbc.sql("""
                INSERT INTO payment_event(event_id,transaction_id,event_type,source,summary,
                    canonical_state_after,actor,correlation_id,evidence_json)
                VALUES(:eventId,:transactionPk,:type,:source,:summary,:state,'payment-operator',:correlation,:evidence)
                """).param("eventId", eventId).param("transactionPk", transactionPk).param("type", type)
                .param("source", source).param("summary", summary).param("state", state)
                .param("correlation", correlationId).param("evidence", json(evidence)).update();
        return jdbc.sql("SELECT id FROM payment_event WHERE event_id=:eventId")
                .param("eventId", eventId).query(Long.class).single();
    }

    private void appendExchange(long transactionPk, long eventPk, String correlationId,
                                PaymentGatewayResult result) {
        jdbc.sql("""
                INSERT INTO provider_exchange(exchange_id,transaction_id,event_id,direction,transport,
                    operation,endpoint,http_status,duration_ms,request_body_json,response_body_json,
                    outcome,correlation_id)
                VALUES(:exchange,:transaction,:event,'PAIRED',:transport,:operation,:endpoint,:status,
                    :duration,:request,:response,:outcome,:correlation)
                """).param("exchange", "EXC-" + compact()).param("transaction", transactionPk)
                .param("event", eventPk).param("transport", result.transport())
                .param("operation", result.providerOperation()).param("endpoint", result.endpoint())
                .param("status", result.httpStatus()).param("duration", result.durationMs())
                .param("request", json(result.sanitizedRequest())).param("response", json(result.sanitizedResponse()))
                .param("outcome", result.canonicalState()).param("correlation", correlationId).update();
    }

    private void appendExchange(long transactionPk, long eventPk, String correlationId,
                                ProviderCallEvidence result) {
        jdbc.sql("""
                INSERT INTO provider_exchange(exchange_id,transaction_id,event_id,direction,transport,
                    operation,endpoint,http_status,duration_ms,request_body_json,response_body_json,
                    outcome,correlation_id)
                VALUES(:exchange,:transaction,:event,'PAIRED',:transport,:operation,:endpoint,:status,
                    :duration,:request,:response,:outcome,:correlation)
                """).param("exchange", "EXC-" + compact()).param("transaction", transactionPk)
                .param("event", eventPk).param("transport", result.transport()).param("operation", result.operation())
                .param("endpoint", result.endpoint()).param("status", result.httpStatus())
                .param("duration", result.durationMs()).param("request", json(result.sanitizedRequest()))
                .param("response", json(result.sanitizedResponse())).param("outcome", result.outcome())
                .param("correlation", correlationId).update();
    }

    private void completeIdempotency(long transactionPk, String state, String now) {
        jdbc.sql("UPDATE idempotency_record SET status=:state,updated_at=:now WHERE transaction_id=:id AND command_type='CAPTURE' AND status='PROCESSING'")
                .param("state", state).param("now", now).param("id", transactionPk).update();
    }

    private long transactionPk(String id) {
        return jdbc.sql("SELECT id FROM payment_transaction WHERE transaction_id=:id")
                .param("id", id).query(Long.class).single();
    }

    private static String json(Object value) {
        try { return JSON.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("Capture evidence could not be encoded", exception); }
    }

    private static String compact() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }
}
