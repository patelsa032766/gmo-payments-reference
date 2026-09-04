package io.github.patelsa032766.gmopayments.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.patelsa032766.gmopayments.application.port.MitCommandRepository;
import io.github.patelsa032766.gmopayments.domain.MitExecutionReservation;
import io.github.patelsa032766.gmopayments.domain.PaymentExecutionContext;
import io.github.patelsa032766.gmopayments.domain.PaymentGatewayResult;
import io.github.patelsa032766.gmopayments.domain.PaymentMethodCode;
import io.github.patelsa032766.gmopayments.domain.PaymentNextAction;
import io.github.patelsa032766.gmopayments.domain.PaymentExecutionMode;
import io.github.patelsa032766.gmopayments.domain.PaymentSubmissionResult;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Persists individual MIT commands without holding SQLite locks across GMO.
 *
 * <p>The application service first calls {@link #reserve}; that short write
 * creates the idempotency record and PROCESSING transaction. It then releases
 * SQLite before making the provider call. A second short transaction records
 * either the definitive result or an UNKNOWN state requiring inquiry.</p>
 */
@Repository
public class SQLiteMitCommandRepository implements MitCommandRepository {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> JSON_MAP = new TypeReference<>() {};

    private final JdbcClient jdbc;
    private final SQLiteLockRetryExecutor lockRetry;
    private final TransactionTemplate transactions;

    public SQLiteMitCommandRepository(JdbcClient jdbc, SQLiteLockRetryExecutor lockRetry,
                                      PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.lockRetry = lockRetry;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public MitExecutionReservation reserve(String instrumentId, long amountJpy, String merchantReference,
                                           String idempotencyKey, String fingerprint,
                                           PaymentExecutionMode executionMode) {
        return lockRetry.execute("reserve MIT payment", () -> transactions.execute(status -> {
            Optional<Existing> existing = jdbc.sql("""
                    SELECT i.request_fingerprint, t.transaction_id
                    FROM idempotency_record i
                    JOIN payment_transaction t ON t.id = i.transaction_id
                    WHERE i.idempotency_key = :key
                    """).param("key", idempotencyKey)
                    .query((rs, rowNum) -> new Existing(rs.getString(1), rs.getString(2))).optional();
            if (existing.isPresent()) {
                if (!existing.get().fingerprint().equals(fingerprint)) {
                    throw new IllegalArgumentException("Idempotency key was used for a different request");
                }
                return new MitExecutionReservation(loadContext(existing.get().transactionId()), Map.of(), true);
            }

            Instrument instrument = loadInstrument(instrumentId);
            if (instrument.method() == PaymentMethodCode.KOZA_FURIKAE_SELECT) {
                throw new IllegalArgumentException("Koza Furikae is submitted through the monthly batch workflow");
            }
            String transactionId = "TXN-MIT-" + compactId();
            String correlationId = "CORR-" + UUID.randomUUID();
            jdbc.sql("""
                    INSERT INTO payment_transaction
                        (transaction_id, customer_id, instrument_id, method_code, product_code,
                         initiation_type, operation, amount_jpy, canonical_state,
                         merchant_reference, configuration_version)
                    VALUES (:transactionId, :customerId, :instrumentId, :method, :product,
                            'MIT', 'CHARGE', :amount, 'PROCESSING', :reference, :configurationVersion)
                    """).param("transactionId", transactionId).param("customerId", instrument.customerId())
                    .param("instrumentId", instrument.id()).param("method", instrument.method().apiValue())
                    .param("product", instrument.productCode()).param("amount", amountJpy)
                    .param("reference", merchantReference).param("configurationVersion", instrument.configurationVersion())
                    .update();
            long transactionPk = transactionPk(transactionId);
            jdbc.sql("""
                    INSERT INTO idempotency_record
                        (idempotency_key, command_type, request_fingerprint, status, transaction_id)
                    VALUES (:key, 'MIT_PAYMENT', :fingerprint, 'PROCESSING', :transactionPk)
                    """).param("key", idempotencyKey).param("fingerprint", fingerprint)
                    .param("transactionPk", transactionPk).update();
            appendEvent(transactionPk, "MIT_PAYMENT_SUBMITTED", "OPERATOR", "Recurring payment submitted",
                    "PROCESSING", correlationId, Map.of("instrumentId", instrumentId));

            var facts = new LinkedHashMap<String, Object>(instrument.metadata());
            facts.put("memberId", instrument.memberReference());
            facts.put("instrumentReference", instrument.providerInstrumentReference());
            facts.put("maskedDisplay", instrument.maskedDisplay());
            facts.put("productCode", instrument.productCode());
            var context = new PaymentExecutionContext(transactionId, merchantReference,
                    instrument.customerCode(), instrument.customerName(), "Payment operator",
                    "Example Insurance", instrument.method(), instrument.productCode(), "MIT", "CHARGE",
                    amountJpy, instrument.configurationVersion(), correlationId, executionMode);
            return new MitExecutionReservation(context, facts, false);
        }));
    }

    @Override
    public PaymentSubmissionResult recordSuccess(PaymentExecutionContext context, PaymentGatewayResult result) {
        return lockRetry.execute("complete MIT payment", () -> transactions.execute(status -> {
            long transactionPk = transactionPk(context.transactionId());
            String now = Instant.now().toString();
            jdbc.sql("""
                    UPDATE payment_transaction
                    SET canonical_state=:state, provider_order_id=:orderId, provider_access_id=:accessId,
                        provider_status=:providerStatus, requires_attention=:attention,
                        updated_at=:updatedAt, version=version+1
                    WHERE id=:id
                    """).param("state", result.canonicalState()).param("orderId", result.providerOrderId())
                    .param("accessId", result.providerAccessId()).param("providerStatus", result.providerStatus())
                    .param("attention", result.requiresAttention()).param("updatedAt", now)
                    .param("id", transactionPk).update();
            long eventPk = appendEvent(transactionPk, result.eventType(), "GMO_API", result.summary(),
                    result.canonicalState(), context.correlationId(), result.instructions());
            appendExchange(transactionPk, eventPk, context, result);
            jdbc.sql("""
                    UPDATE idempotency_record SET status='COMPLETED', response_json=:response, updated_at=:updatedAt
                    WHERE transaction_id=:transactionPk
                    """).param("response", json(Map.of("state", result.canonicalState(),
                            "providerStatus", result.providerStatus(), "instructions", result.instructions())))
                    .param("updatedAt", now).param("transactionPk", transactionPk).update();
            return new PaymentSubmissionResult(context.transactionId(), context.applicationNumber(), context.method(),
                    result.canonicalState(), result.providerStatus(), result.requiresAttention(), result.nextAction(),
                    result.instructions(), false);
        }));
    }

    @Override
    public PaymentSubmissionResult recordFailure(PaymentExecutionContext context, String state,
                                                 String summary, boolean attention) {
        return lockRetry.execute("record MIT failure", () -> transactions.execute(status -> {
            long transactionPk = transactionPk(context.transactionId());
            jdbc.sql("""
                    UPDATE payment_transaction
                    SET canonical_state=:state, provider_status=:state, requires_attention=:attention,
                        updated_at=:updatedAt, version=version+1 WHERE id=:id
                    """).param("state", state).param("attention", attention)
                    .param("updatedAt", Instant.now().toString()).param("id", transactionPk).update();
            appendEvent(transactionPk, "PROVIDER_FAILURE", "GMO_API", summary, state,
                    context.correlationId(), Map.of());
            return new PaymentSubmissionResult(context.transactionId(), context.applicationNumber(), context.method(),
                    state, state, attention, PaymentNextAction.none(), Map.of(), false);
        }));
    }

    @Override
    public Optional<PaymentSubmissionResult> findSubmission(String transactionId) {
        return jdbc.sql("""
                SELECT transaction_id, merchant_reference, method_code, canonical_state,
                       provider_status, requires_attention
                FROM payment_transaction WHERE transaction_id=:transactionId
                """).param("transactionId", transactionId).query((rs, rowNum) -> new PaymentSubmissionResult(
                rs.getString(1), rs.getString(2), PaymentMethodCode.fromApiValue(rs.getString(3)),
                rs.getString(4), rs.getString(5), rs.getBoolean(6), PaymentNextAction.none(), Map.of(), true)).optional();
    }

    private Instrument loadInstrument(String instrumentId) {
        return jdbc.sql("""
                SELECT i.id, i.method_code, i.product_code, i.provider_member_reference,
                       i.provider_instrument_reference, i.masked_display, i.metadata_json,
                       c.id customer_id, c.customer_code, c.full_name, r.version configuration_version
                FROM payment_instrument i
                JOIN customer c ON c.id=i.customer_id
                JOIN configuration_release r ON r.status='PUBLISHED'
                WHERE i.instrument_id=:instrumentId AND i.state='ACTIVE'
                """).param("instrumentId", instrumentId).query((rs, rowNum) -> new Instrument(
                rs.getLong("id"), PaymentMethodCode.fromApiValue(rs.getString("method_code")),
                rs.getString("product_code"), rs.getString("provider_member_reference"),
                rs.getString("provider_instrument_reference"), rs.getString("masked_display"),
                parseJson(rs.getString("metadata_json")), rs.getLong("customer_id"),
                rs.getString("customer_code"), rs.getString("full_name"),
                rs.getInt("configuration_version"))).optional()
                .orElseThrow(() -> new IllegalArgumentException("Active instrument not found: " + instrumentId));
    }

    private PaymentExecutionContext loadContext(String transactionId) {
        return jdbc.sql("""
                SELECT t.transaction_id, t.merchant_reference, t.method_code, t.product_code, t.operation,
                       t.amount_jpy, t.configuration_version, c.customer_code, c.full_name,
                       (SELECT correlation_id FROM payment_event WHERE transaction_id=t.id ORDER BY id LIMIT 1) correlation
                FROM payment_transaction t JOIN customer c ON c.id=t.customer_id
                WHERE t.transaction_id=:transactionId
                """).param("transactionId", transactionId).query((rs, rowNum) -> new PaymentExecutionContext(
                rs.getString(1), rs.getString(2), rs.getString(8), rs.getString(9), "Payment operator",
                "Example Insurance", PaymentMethodCode.fromApiValue(rs.getString(3)), rs.getString(4),
                "MIT", rs.getString(5), rs.getLong(6), rs.getInt(7), rs.getString(10),
                PaymentExecutionMode.CAPTURE)).single();
    }

    private long appendEvent(long transactionPk, String type, String source, String summary, String state,
                             String correlationId, Map<String, Object> evidence) {
        String eventId = "EVT-" + compactId();
        jdbc.sql("""
                INSERT INTO payment_event
                    (event_id,transaction_id,event_type,source,summary,canonical_state_after,
                     actor,correlation_id,evidence_json)
                VALUES (:eventId,:transactionPk,:type,:source,:summary,:state,'operator',:correlationId,:evidence)
                """).param("eventId", eventId).param("transactionPk", transactionPk).param("type", type)
                .param("source", source).param("summary", summary).param("state", state)
                .param("correlationId", correlationId).param("evidence", json(evidence)).update();
        return jdbc.sql("SELECT id FROM payment_event WHERE event_id=:eventId")
                .param("eventId", eventId).query(Long.class).single();
    }

    private void appendExchange(long transactionPk, long eventPk, PaymentExecutionContext context,
                                PaymentGatewayResult result) {
        jdbc.sql("""
                INSERT INTO provider_exchange
                    (exchange_id,transaction_id,event_id,direction,transport,operation,endpoint,http_status,
                     duration_ms,request_body_json,response_body_json,outcome,correlation_id)
                VALUES (:exchangeId,:transactionPk,:eventPk,'PAIRED',:transport,:operation,:endpoint,:httpStatus,
                        :durationMs,:requestBody,:responseBody,:outcome,:correlationId)
                """).param("exchangeId", "EXC-" + compactId()).param("transactionPk", transactionPk)
                .param("eventPk", eventPk).param("transport", result.transport())
                .param("operation", result.providerOperation()).param("endpoint", result.endpoint())
                .param("httpStatus", result.httpStatus()).param("durationMs", result.durationMs())
                .param("requestBody", json(result.sanitizedRequest()))
                .param("responseBody", json(result.sanitizedResponse())).param("outcome", result.canonicalState())
                .param("correlationId", context.correlationId()).update();
    }

    private long transactionPk(String transactionId) {
        return jdbc.sql("SELECT id FROM payment_transaction WHERE transaction_id=:transactionId")
                .param("transactionId", transactionId).query(Long.class).single();
    }

    private static String compactId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }
    private static String json(Object value) {
        try { return JSON.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("MIT evidence could not be encoded", exception); }
    }
    private static Map<String, Object> parseJson(String value) {
        try { return JSON.readValue(value == null ? "{}" : value, JSON_MAP); }
        catch (Exception exception) { throw new IllegalStateException("Instrument metadata could not be decoded", exception); }
    }

    private record Existing(String fingerprint, String transactionId) {}
    private record Instrument(long id, PaymentMethodCode method, String productCode, String memberReference,
                              String providerInstrumentReference, String maskedDisplay,
                              Map<String, Object> metadata, long customerId, String customerCode,
                              String customerName, int configurationVersion) {}
}
