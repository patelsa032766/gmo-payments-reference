package io.github.patelsa032766.gmopayments.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.patelsa032766.gmopayments.application.port.PaymentCommandRepository;
import io.github.patelsa032766.gmopayments.domain.PaymentExecutionContext;
import io.github.patelsa032766.gmopayments.domain.PaymentGatewayResult;
import io.github.patelsa032766.gmopayments.domain.PaymentMethodCode;
import io.github.patelsa032766.gmopayments.domain.PaymentNextAction;
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
 * SQLite payment-command persistence with one short transaction per phase.
 *
 * <p>The gateway call deliberately occurs in the application service between
 * {@link #reserve} and {@link #recordSuccess}; this repository therefore never
 * holds SQLite's writer lock while waiting for GMO.</p>
 */
@Repository
public class SQLitePaymentCommandRepository implements PaymentCommandRepository {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> JSON_MAP = new TypeReference<>() {};
    private final JdbcClient jdbc;
    private final SQLiteLockRetryExecutor lockRetry;
    private final TransactionTemplate transactions;

    public SQLitePaymentCommandRepository(JdbcClient jdbc, SQLiteLockRetryExecutor lockRetry,
                                          PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.lockRetry = lockRetry;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public Reservation reserve(String applicationNumber, PaymentMethodCode method,
                               String idempotencyKey, String requestFingerprint) {
        return lockRetry.execute("reserve payment command", () -> transactions.execute(status -> {
            var existing = jdbc.sql("""
                    SELECT i.request_fingerprint, t.transaction_id
                    FROM idempotency_record i
                    JOIN payment_transaction t ON t.id = i.transaction_id
                    WHERE i.idempotency_key = :key
                    """).param("key", idempotencyKey).query((rs, rowNum) ->
                    new ExistingReservation(rs.getString("request_fingerprint"),
                            rs.getString("transaction_id"))).optional();
            if (existing.isPresent()) {
                if (!existing.get().fingerprint().equals(requestFingerprint)) {
                    throw new IllegalArgumentException("Idempotency key was already used for a different request");
                }
                return new Reservation(loadContext(existing.get().transactionId()), true);
            }

            var application = jdbc.sql("""
                    SELECT a.id application_id, a.application_number, a.amount_jpy,
                           a.configuration_version, c.id customer_id, c.customer_code, c.full_name
                    FROM application_record a
                    JOIN customer c ON c.id = a.customer_id
                    WHERE a.application_number = :applicationNumber
                    """).param("applicationNumber", applicationNumber)
                    .query((rs, rowNum) -> new ApplicationRow(rs.getLong("application_id"),
                            rs.getString("application_number"), rs.getLong("amount_jpy"),
                            rs.getInt("configuration_version"), rs.getLong("customer_id"),
                            rs.getString("customer_code"), rs.getString("full_name")))
                    .optional().orElseThrow(() -> new IllegalArgumentException(
                            "Unknown application: " + applicationNumber));

            String transactionId = "TXN-" + method.apiValue().toUpperCase() + "-" + compactId();
            String correlationId = "CORR-" + UUID.randomUUID();
            String product = productCode(method);
            String operation = operation(method);
            jdbc.sql("""
                    INSERT INTO payment_transaction
                        (transaction_id, application_id, customer_id, method_code, product_code,
                         initiation_type, operation, amount_jpy, canonical_state, merchant_reference,
                         configuration_version)
                    VALUES (:transactionId, :applicationId, :customerId, :method, :product,
                            'CIT', :operation, :amount, 'PROCESSING', :reference, :configurationVersion)
                    """).param("transactionId", transactionId).param("applicationId", application.id())
                    .param("customerId", application.customerId()).param("method", method.apiValue())
                    .param("product", product).param("operation", operation)
                    .param("amount", application.amountJpy()).param("reference", application.applicationNumber())
                    .param("configurationVersion", application.configurationVersion()).update();
            long transactionPk = transactionPk(transactionId);
            jdbc.sql("""
                    INSERT INTO idempotency_record
                        (idempotency_key, command_type, request_fingerprint, status, transaction_id)
                    VALUES (:key, 'CHECKOUT_PAYMENT', :fingerprint, 'PROCESSING', :transactionId)
                    """).param("key", idempotencyKey).param("fingerprint", requestFingerprint)
                    .param("transactionId", transactionPk).update();
            appendEvent(transactionPk, "PAYMENT_SUBMITTED", "CUSTOMER", "Payment submitted",
                    "PROCESSING", "customer", correlationId, Map.of());
            jdbc.sql("""
                    UPDATE application_record
                    SET selected_method = :method, state = 'PROCESSING', version = version + 1,
                        updated_at = :updatedAt
                    WHERE id = :id
                    """).param("method", method.apiValue()).param("updatedAt", Instant.now().toString())
                    .param("id", application.id()).update();
            return new Reservation(new PaymentExecutionContext(transactionId, application.applicationNumber(),
                    application.customerCode(), application.customerName(), "A. Suzuki", "Example Insurance",
                    method, product, "CIT", operation, application.amountJpy(),
                    application.configurationVersion(), correlationId), false);
        }));
    }

    @Override
    public PaymentSubmissionResult recordSuccess(PaymentExecutionContext context, PaymentGatewayResult result) {
        PaymentSubmissionResult persistent = lockRetry.execute("record provider success", () ->
                transactions.execute(status -> {
                    long transactionPk = transactionPk(context.transactionId());
                    String now = Instant.now().toString();
                    jdbc.sql("""
                            UPDATE payment_transaction
                            SET canonical_state = :state, provider_order_id = :orderId,
                                provider_access_id = :accessId, provider_status = :providerStatus,
                                requires_attention = :attention, version = version + 1, updated_at = :updatedAt
                            WHERE id = :id
                            """).param("state", result.canonicalState()).param("orderId", result.providerOrderId())
                            .param("accessId", result.providerAccessId()).param("providerStatus", result.providerStatus())
                            .param("attention", result.requiresAttention()).param("updatedAt", now)
                            .param("id", transactionPk).update();
                    jdbc.sql("""
                            UPDATE application_record SET state = :state, version = version + 1, updated_at = :updatedAt
                            WHERE application_number = :applicationNumber
                            """).param("state", result.canonicalState()).param("updatedAt", now)
                            .param("applicationNumber", context.applicationNumber()).update();
                    long eventPk = appendEvent(transactionPk, result.eventType(), "GMO_API", result.summary(),
                            result.canonicalState(), "system", context.correlationId(), result.instructions());
                    appendExchange(transactionPk, eventPk, context, result);

                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("state", result.canonicalState());
                    response.put("providerStatus", result.providerStatus());
                    response.put("requiresAttention", result.requiresAttention());
                    response.put("instructions", result.instructions());
                    jdbc.sql("""
                            UPDATE idempotency_record
                            SET status = 'COMPLETED', response_json = :response, updated_at = :updatedAt
                            WHERE transaction_id = :transactionId
                            """).param("response", json(response)).param("updatedAt", now)
                            .param("transactionId", transactionPk).update();
                    return new PaymentSubmissionResult(context.transactionId(), context.applicationNumber(),
                            context.method(), result.canonicalState(), result.providerStatus(),
                            result.requiresAttention(), PaymentNextAction.none(), result.instructions(), false);
                }));
        // Provider handoff tokens live only in this in-memory response.
        return new PaymentSubmissionResult(persistent.transactionId(), persistent.applicationNumber(),
                persistent.method(), persistent.state(), persistent.providerStatus(),
                persistent.requiresAttention(), result.nextAction(), persistent.instructions(), false);
    }

    @Override
    public PaymentSubmissionResult recordFailure(PaymentExecutionContext context, String state,
                                                 String summary, boolean requiresAttention) {
        return lockRetry.execute("record provider failure", () -> transactions.execute(status -> {
            long transactionPk = transactionPk(context.transactionId());
            String now = Instant.now().toString();
            jdbc.sql("""
                    UPDATE payment_transaction
                    SET canonical_state = :state, provider_status = :state,
                        requires_attention = :attention, version = version + 1, updated_at = :updatedAt
                    WHERE id = :id
                    """).param("state", state).param("attention", requiresAttention)
                    .param("updatedAt", now).param("id", transactionPk).update();
            jdbc.sql("""
                    UPDATE application_record SET state = :state, version = version + 1, updated_at = :updatedAt
                    WHERE application_number = :applicationNumber
                    """).param("state", state).param("updatedAt", now)
                    .param("applicationNumber", context.applicationNumber()).update();
            appendEvent(transactionPk, "PROVIDER_FAILURE", "GMO_API", summary, state,
                    "system", context.correlationId(), Map.of());
            jdbc.sql("""
                    UPDATE idempotency_record SET status = :status, response_json = :response, updated_at = :updatedAt
                    WHERE transaction_id = :transactionId
                    """).param("status", state).param("response", json(Map.of("state", state,
                            "requiresAttention", requiresAttention))).param("updatedAt", now)
                    .param("transactionId", transactionPk).update();
            return new PaymentSubmissionResult(context.transactionId(), context.applicationNumber(),
                    context.method(), state, state, requiresAttention, PaymentNextAction.none(), Map.of(), false);
        }));
    }

    @Override
    public Optional<PaymentSubmissionResult> findSubmission(String transactionId) {
        return jdbc.sql("""
                SELECT t.transaction_id, a.application_number, t.method_code, t.canonical_state,
                       t.provider_status, t.requires_attention, i.response_json
                FROM payment_transaction t
                JOIN application_record a ON a.id = t.application_id
                LEFT JOIN idempotency_record i ON i.transaction_id = t.id
                WHERE t.transaction_id = :transactionId
                """).param("transactionId", transactionId).query((rs, rowNum) -> {
            Map<String, Object> response = parseJson(rs.getString("response_json"));
            return new PaymentSubmissionResult(rs.getString("transaction_id"),
                    rs.getString("application_number"), PaymentMethodCode.fromApiValue(rs.getString("method_code")),
                    rs.getString("canonical_state"), rs.getString("provider_status"),
                    rs.getBoolean("requires_attention"), PaymentNextAction.none(),
                    nestedMap(response, "instructions"), true);
        }).optional();
    }

    private PaymentExecutionContext loadContext(String transactionId) {
        return jdbc.sql("""
                SELECT t.transaction_id, a.application_number, c.customer_code, c.full_name,
                       t.method_code, t.product_code, t.initiation_type, t.operation, t.amount_jpy,
                       t.configuration_version,
                       (SELECT correlation_id FROM payment_event WHERE transaction_id = t.id ORDER BY id LIMIT 1) correlation_id
                FROM payment_transaction t
                JOIN application_record a ON a.id = t.application_id
                JOIN customer c ON c.id = t.customer_id
                WHERE t.transaction_id = :transactionId
                """).param("transactionId", transactionId).query((rs, rowNum) -> new PaymentExecutionContext(
                rs.getString("transaction_id"), rs.getString("application_number"),
                rs.getString("customer_code"), rs.getString("full_name"), "A. Suzuki", "Example Insurance",
                PaymentMethodCode.fromApiValue(rs.getString("method_code")), rs.getString("product_code"),
                rs.getString("initiation_type"), rs.getString("operation"), rs.getLong("amount_jpy"),
                rs.getInt("configuration_version"), rs.getString("correlation_id")))
                .optional().orElseThrow(() -> new IllegalStateException("Reserved transaction was not found"));
    }

    private long appendEvent(long transactionPk, String eventType, String source, String summary,
                             String state, String actor, String correlationId, Map<String, Object> evidence) {
        String eventId = "EVT-" + compactId();
        jdbc.sql("""
                INSERT INTO payment_event
                    (event_id, transaction_id, event_type, source, summary, canonical_state_after,
                     actor, correlation_id, evidence_json)
                VALUES (:eventId, :transactionId, :eventType, :source, :summary, :state,
                        :actor, :correlationId, :evidence)
                """).param("eventId", eventId).param("transactionId", transactionPk)
                .param("eventType", eventType).param("source", source).param("summary", summary)
                .param("state", state).param("actor", actor).param("correlationId", correlationId)
                .param("evidence", json(evidence)).update();
        return jdbc.sql("SELECT id FROM payment_event WHERE event_id = :eventId")
                .param("eventId", eventId).query(Long.class).single();
    }

    private void appendExchange(long transactionPk, long eventPk, PaymentExecutionContext context,
                                PaymentGatewayResult result) {
        jdbc.sql("""
                INSERT INTO provider_exchange
                    (exchange_id, transaction_id, event_id, direction, transport, operation,
                     endpoint, http_status, duration_ms, request_body_json, response_body_json,
                     outcome, correlation_id)
                VALUES (:exchangeId, :transactionId, :eventId, 'PAIRED', :transport, :operation,
                        :endpoint, :httpStatus, :durationMs, :requestBody, :responseBody,
                        :outcome, :correlationId)
                """).param("exchangeId", "EXC-" + compactId()).param("transactionId", transactionPk)
                .param("eventId", eventPk).param("transport", result.transport())
                .param("operation", result.providerOperation()).param("endpoint", result.endpoint())
                .param("httpStatus", result.httpStatus()).param("durationMs", result.durationMs())
                .param("requestBody", json(result.sanitizedRequest()))
                .param("responseBody", json(result.sanitizedResponse()))
                .param("outcome", result.canonicalState()).param("correlationId", context.correlationId()).update();
    }

    private long transactionPk(String transactionId) {
        return jdbc.sql("SELECT id FROM payment_transaction WHERE transaction_id = :transactionId")
                .param("transactionId", transactionId).query(Long.class).single();
    }

    private static String productCode(PaymentMethodCode method) {
        return switch (method) {
            case CARD -> "card_openapi";
            case PAYPAY -> "paypay_recurring";
            case BANK_DIRECT_REALTIME -> "bank_direct_realtime";
            case KOZA_FURIKAE_SELECT -> "koza_furikae_select";
            case KOMBINI -> "kombini_openapi";
            case PAYEASY -> "payeasy_openapi";
            case FURIKOMI -> "furikomi_virtual_account";
        };
    }

    private static String operation(PaymentMethodCode method) {
        return switch (method) {
            case CARD, PAYPAY -> "AUTHORIZE";
            case BANK_DIRECT_REALTIME -> "REGISTER_AND_DEBIT";
            case KOZA_FURIKAE_SELECT -> "REGISTER_MANDATE_AND_ISSUE_FIRST_PAYMENT";
            case KOMBINI, PAYEASY, FURIKOMI -> "ISSUE_INSTRUCTIONS";
        };
    }

    private static String json(Object value) {
        try { return JSON.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("Sanitized evidence could not be encoded", exception); }
    }

    private static Map<String, Object> parseJson(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try { return JSON.readValue(value, JSON_MAP); }
        catch (Exception exception) { throw new IllegalStateException("Stored command result could not be decoded", exception); }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nestedMap(Map<String, Object> source, String key) {
        return source.get(key) instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static String compactId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private record ExistingReservation(String fingerprint, String transactionId) {}
    private record ApplicationRow(long id, String applicationNumber, long amountJpy,
                                  int configurationVersion, long customerId,
                                  String customerCode, String customerName) {}
}
