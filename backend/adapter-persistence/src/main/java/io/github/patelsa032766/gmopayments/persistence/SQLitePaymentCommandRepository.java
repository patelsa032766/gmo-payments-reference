package io.github.patelsa032766.gmopayments.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.patelsa032766.gmopayments.application.port.PaymentCommandRepository;
import io.github.patelsa032766.gmopayments.domain.PaymentExecutionContext;
import io.github.patelsa032766.gmopayments.domain.PaymentGatewayResult;
import io.github.patelsa032766.gmopayments.domain.PaymentContinuationResult;
import io.github.patelsa032766.gmopayments.domain.PaymentMethodCode;
import io.github.patelsa032766.gmopayments.domain.PaymentNextAction;
import io.github.patelsa032766.gmopayments.domain.ProviderCallEvidence;
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
    public PaymentSubmissionResult recordSuccess(PaymentExecutionContext context,
                                                 PaymentContinuationResult execution) {
        PaymentGatewayResult result = execution.outcome();
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
                    for (ProviderCallEvidence exchange : execution.exchanges()) {
                        appendContinuationExchange(transactionPk, eventPk,
                                context.correlationId(), exchange);
                    }
                    appendProviderResource(transactionPk, "PROVIDER_ORDER", result.providerOrderId(),
                            result.canonicalState());
                    appendProviderResource(transactionPk, "PROVIDER_ACCESS", result.providerAccessId(),
                            result.canonicalState());

                    if (shouldProvisionInstrument(context.method(), result.canonicalState())
                            && !result.requiresAttention()) {
                        long instrumentPk = provisionInstrument(context, execution,
                                result.providerAccessId());
                        jdbc.sql("UPDATE payment_transaction SET instrument_id=:instrumentId WHERE id=:transactionId")
                                .param("instrumentId", instrumentPk).param("transactionId", transactionPk).update();
                    }

                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("state", result.canonicalState());
                    response.put("providerStatus", result.providerStatus());
                    response.put("requiresAttention", result.requiresAttention());
                    response.put("instructions", result.instructions());
                    jdbc.sql("""
                            UPDATE idempotency_record
                            SET status = :status, response_json = :response, updated_at = :updatedAt
                            WHERE transaction_id = :transactionId
                            """).param("status", isTerminal(result.canonicalState())
                                    ? "COMPLETED" : result.canonicalState())
                            .param("response", json(response)).param("updatedAt", now)
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
                                                 String summary, boolean requiresAttention,
                                                 ProviderCallEvidence evidence) {
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
            long eventPk = appendEvent(transactionPk, "PROVIDER_FAILURE", "GMO_API", summary, state,
                    "system", context.correlationId(), Map.of());
            if (evidence != null) {
                appendContinuationExchange(transactionPk, eventPk, context.correlationId(), evidence);
            }
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

    @Override
    public ContinuationReservation reserveContinuation(String providerReference, PaymentMethodCode method) {
        return lockRetry.execute("reserve browser-return continuation", () -> transactions.execute(status -> {
            var row = jdbc.sql("""
                    SELECT t.transaction_id, t.canonical_state
                    FROM payment_transaction t
                    WHERE t.method_code=:method
                      AND (t.provider_access_id=:providerReference OR EXISTS (
                          SELECT 1 FROM payment_resource r
                          WHERE r.transaction_id=t.id AND r.provider_reference=:providerReference))
                    ORDER BY t.id DESC LIMIT 1
                    """).param("method", method.apiValue()).param("providerReference", providerReference)
                    .query((rs, rowNum) -> new ContinuationRow(rs.getString("transaction_id"),
                            rs.getString("canonical_state"))).optional()
                    .orElseThrow(() -> new IllegalArgumentException("The GMO return could not be linked to a payment"));
            PaymentExecutionContext context = loadContext(row.transactionId());
            boolean ready = "REGISTRATION_PENDING".equals(row.state());
            if (!ready) return new ContinuationReservation(context, true);

            String now = Instant.now().toString();
            jdbc.sql("""
                    UPDATE payment_transaction
                    SET canonical_state='RETURN_PROCESSING', version=version+1, updated_at=:updatedAt
                    WHERE transaction_id=:transactionId AND canonical_state='REGISTRATION_PENDING'
                    """).param("updatedAt", now).param("transactionId", row.transactionId()).update();
            jdbc.sql("""
                    UPDATE application_record SET state='RETURN_PROCESSING', version=version+1,
                        updated_at=:updatedAt
                    WHERE id=(SELECT application_id FROM payment_transaction WHERE transaction_id=:transactionId)
                    """).param("updatedAt", now).param("transactionId", row.transactionId()).update();
            return new ContinuationReservation(context, false);
        }));
    }

    @Override
    public PaymentSubmissionResult recordContinuation(PaymentExecutionContext context,
                                                      PaymentContinuationResult continuation) {
        return lockRetry.execute("record browser-return continuation", () -> transactions.execute(status -> {
            PaymentGatewayResult result = continuation.outcome();
            long transactionPk = transactionPk(context.transactionId());
            String initialProviderReference = jdbc.sql("""
                    SELECT provider_access_id FROM payment_transaction WHERE id=:id
                    """).param("id", transactionPk).query(String.class).optional().orElse(null);
            String now = Instant.now().toString();
            jdbc.sql("""
                    UPDATE payment_transaction
                    SET canonical_state=:state,
                        provider_order_id=COALESCE(:providerOrderId, provider_order_id),
                        provider_access_id=COALESCE(:providerAccessId, provider_access_id),
                        provider_status=:providerStatus, requires_attention=:attention,
                        version=version+1, updated_at=:updatedAt
                    WHERE id=:id
                    """).param("state", result.canonicalState()).param("providerOrderId", result.providerOrderId())
                    .param("providerAccessId", result.providerAccessId()).param("providerStatus", result.providerStatus())
                    .param("attention", result.requiresAttention()).param("updatedAt", now)
                    .param("id", transactionPk).update();
            jdbc.sql("""
                    UPDATE application_record SET state=:state, version=version+1, updated_at=:updatedAt
                    WHERE application_number=:applicationNumber
                    """).param("state", result.canonicalState()).param("updatedAt", now)
                    .param("applicationNumber", context.applicationNumber()).update();

            long eventPk = appendEvent(transactionPk, result.eventType(), "BROWSER_RETURN", result.summary(),
                    result.canonicalState(), "customer", context.correlationId(), result.instructions());
            for (ProviderCallEvidence exchange : continuation.exchanges()) {
                appendContinuationExchange(transactionPk, eventPk, context.correlationId(), exchange);
            }
            appendProviderResource(transactionPk, "REGISTRATION_REFERENCE", initialProviderReference,
                    result.canonicalState());
            appendProviderResource(transactionPk, "PROVIDER_ORDER", result.providerOrderId(),
                    result.canonicalState());
            appendProviderResource(transactionPk, "PROVIDER_ACCESS", result.providerAccessId(),
                    result.canonicalState());

            if (shouldProvisionInstrument(context.method(), result.canonicalState())) {
                long instrumentPk = provisionInstrument(context, continuation, initialProviderReference);
                jdbc.sql("UPDATE payment_transaction SET instrument_id=:instrumentId WHERE id=:transactionId")
                        .param("instrumentId", instrumentPk).param("transactionId", transactionPk).update();
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("state", result.canonicalState());
            response.put("providerStatus", result.providerStatus());
            response.put("requiresAttention", result.requiresAttention());
            response.put("instructions", result.instructions());
            jdbc.sql("""
                    UPDATE idempotency_record
                    SET status=:status, response_json=:response, updated_at=:updatedAt
                    WHERE transaction_id=:transactionId
                    """).param("status", isTerminal(result.canonicalState()) ? "COMPLETED" : result.canonicalState())
                    .param("response", json(response)).param("updatedAt", now)
                    .param("transactionId", transactionPk).update();
            return new PaymentSubmissionResult(context.transactionId(), context.applicationNumber(),
                    context.method(), result.canonicalState(), result.providerStatus(),
                    result.requiresAttention(), PaymentNextAction.none(), result.instructions(), false);
        }));
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

    private void appendContinuationExchange(long transactionPk, long eventPk, String correlationId,
                                            ProviderCallEvidence result) {
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
                .param("operation", result.operation()).param("endpoint", result.endpoint())
                .param("httpStatus", result.httpStatus()).param("durationMs", result.durationMs())
                .param("requestBody", json(result.sanitizedRequest()))
                .param("responseBody", json(result.sanitizedResponse()))
                .param("outcome", result.outcome()).param("correlationId", correlationId).update();
    }

    private void appendProviderResource(long transactionPk, String type, String providerReference,
                                        String state) {
        if (providerReference == null || providerReference.isBlank()) return;
        boolean exists = jdbc.sql("""
                SELECT COUNT(*) FROM payment_resource
                WHERE transaction_id=:transactionId AND resource_type=:type AND provider_reference=:reference
                """).param("transactionId", transactionPk).param("type", type)
                .param("reference", providerReference).query(Integer.class).single() > 0;
        if (exists) return;
        jdbc.sql("""
                INSERT INTO payment_resource
                    (resource_id, transaction_id, resource_type, provider_reference, state)
                VALUES (:resourceId, :transactionId, :type, :reference, :state)
                """).param("resourceId", "RES-" + compactId()).param("transactionId", transactionPk)
                .param("type", type).param("reference", providerReference).param("state", state).update();
    }

    private long provisionInstrument(PaymentExecutionContext context,
                                     PaymentContinuationResult continuation,
                                     String registrationReference) {
        String method = context.method().apiValue();
        String product = productCode(context.method());
        String providerInstrumentReference = switch (context.method()) {
            case CARD -> findValue(continuation, "cardId");
            case PAYPAY -> firstNonBlank(findValue(continuation, "acceptanceCode"),
                    registrationReference);
            case BANK_DIRECT_REALTIME, KOZA_FURIKAE_SELECT -> registrationReference;
            default -> registrationReference;
        };
        String masked = switch (context.method()) {
            case CARD -> firstNonBlank(findValue(continuation, "cardNumber"), "Saved card");
            case PAYPAY -> "Registered PayPay account";
            case BANK_DIRECT_REALTIME -> "Registered real-time debit account";
            case KOZA_FURIKAE_SELECT -> "Registered monthly Koza Furikae mandate";
            default -> "Registered payment method";
        };
        if (context.method() == PaymentMethodCode.BANK_DIRECT_REALTIME
                || context.method() == PaymentMethodCode.KOZA_FURIKAE_SELECT) {
            String maskedAccount = findMaskedAccount(continuation);
            if (maskedAccount != null) masked += " • " + maskedAccount;
        }

        Optional<Long> existing = providerInstrumentReference == null
                ? Optional.empty()
                : jdbc.sql("""
                        SELECT id FROM payment_instrument
                        WHERE customer_id=(SELECT id FROM customer WHERE customer_code=:customerCode)
                          AND method_code=:method AND provider_instrument_reference=:reference
                        ORDER BY id DESC LIMIT 1
                        """).param("customerCode", context.customerCode()).param("method", method)
                        .param("reference", providerInstrumentReference).query(Long.class).optional();

        long customerPk = jdbc.sql("SELECT id FROM customer WHERE customer_code=:customerCode")
                .param("customerCode", context.customerCode()).query(Long.class).single();
        // The newest successfully registered method is primary. The previous
        // primary becomes the sole backup; any older backup remains active but
        // loses its preference role.
        jdbc.sql("UPDATE payment_instrument SET preference_role=NULL WHERE customer_id=:customer AND preference_role='BACKUP'")
                .param("customer", customerPk).update();
        jdbc.sql("UPDATE payment_instrument SET preference_role='BACKUP' WHERE customer_id=:customer AND preference_role='PRIMARY'")
                .param("customer", customerPk).update();

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("memberId", context.customerCode());
        metadata.put("registrationStatus", "SUCCESS");
        metadata.put("registrationReference", registrationReference == null ? "" : registrationReference);
        if (context.method() == PaymentMethodCode.CARD) {
            metadata.put("cardId", providerInstrumentReference == null ? "" : providerInstrumentReference);
            metadata.put("cardType", firstNonBlank(findValue(continuation, "type"), "CREDIT_CARD"));
            metadata.put("cardholderName", firstNonBlank(findValue(continuation, "cardholderName"), ""));
        } else if (context.method() == PaymentMethodCode.PAYPAY) {
            metadata.put("walletType", "PAYPAY");
            metadata.put("acceptanceCode", firstNonBlank(findValue(continuation, "acceptanceCode"), ""));
        }
        if (existing.isPresent()) {
            jdbc.sql("""
                    UPDATE payment_instrument
                    SET product_code=:product, provider_instrument_reference=:reference,
                        masked_display=:masked, state='ACTIVE', preference_role='PRIMARY',
                        metadata_json=:metadata, version=version+1, updated_at=:updatedAt
                    WHERE id=:id
                    """).param("product", product).param("reference", providerInstrumentReference)
                    .param("masked", masked).param("metadata", json(metadata))
                    .param("updatedAt", Instant.now().toString()).param("id", existing.get()).update();
            return existing.get();
        }
        String instrumentId = "PM-" + context.method().name() + "-" + compactId();
        jdbc.sql("""
                INSERT INTO payment_instrument
                    (instrument_id, customer_id, method_code, product_code, provider_member_reference,
                     provider_instrument_reference, masked_display, state, preference_role, metadata_json)
                VALUES (:instrumentId, :customer, :method, :product, :memberId, :reference,
                        :masked, 'ACTIVE', 'PRIMARY', :metadata)
                """).param("instrumentId", instrumentId).param("customer", customerPk)
                .param("method", method).param("product", product).param("memberId", context.customerCode())
                .param("reference", providerInstrumentReference).param("masked", masked)
                .param("metadata", json(metadata)).update();
        return jdbc.sql("SELECT id FROM payment_instrument WHERE instrument_id=:instrumentId")
                .param("instrumentId", instrumentId).query(Long.class).single();
    }

    private static boolean shouldProvisionInstrument(PaymentMethodCode method, String state) {
        return (method == PaymentMethodCode.CARD
                    && ("AUTHORIZED".equals(state) || "PAID".equals(state)))
                || (method == PaymentMethodCode.PAYPAY
                    && ("AUTHORIZED".equals(state) || "PAID".equals(state)))
                || (method == PaymentMethodCode.BANK_DIRECT_REALTIME && "PAID".equals(state))
                || (method == PaymentMethodCode.KOZA_FURIKAE_SELECT
                    && "MANDATE_REGISTERED_TRANSFER_DUE".equals(state));
    }

    private static boolean isTerminal(String state) {
        return !"REGISTRATION_PENDING".equals(state) && !"PROCESSING".equals(state)
                && !"RETURN_PROCESSING".equals(state);
    }

    private static String findMaskedAccount(PaymentContinuationResult continuation) {
        return firstNonBlank(findValue(continuation, "AccountNumber"),
                findValue(continuation, "accountNumber"));
    }

    /** Searches sanitized nested response maps/lists without retaining raw provider data. */
    private static String findValue(PaymentContinuationResult continuation, String key) {
        for (ProviderCallEvidence exchange : continuation.exchanges()) {
            String found = findValue(exchange.sanitizedResponse(), key);
            if (found != null) return found;
        }
        return null;
    }

    private static String findValue(Object node, String key) {
        if (node instanceof Map<?, ?> map) {
            for (var entry : map.entrySet()) {
                if (key.equals(String.valueOf(entry.getKey())) && entry.getValue() != null) {
                    return String.valueOf(entry.getValue());
                }
                String nested = findValue(entry.getValue(), key);
                if (nested != null) return nested;
            }
        } else if (node instanceof Iterable<?> values) {
            for (Object value : values) {
                String nested = findValue(value, key);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
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
    private record ContinuationRow(String transactionId, String state) {}
    private record ApplicationRow(long id, String applicationNumber, long amountJpy,
                                  int configurationVersion, long customerId,
                                  String customerCode, String customerName) {}
}
