package io.github.patelsa032766.gmopayments.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.patelsa032766.gmopayments.application.port.InboundMessageRepository;
import io.github.patelsa032766.gmopayments.domain.InboundMessageResult;
import io.github.patelsa032766.gmopayments.domain.InboundPaymentMessage;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * SQLite inbox implementation for authenticated provider callbacks.
 *
 * <p>The inbox row, timeline event, exchange evidence, and current-state
 * projection share one short database transaction. Returning from this method
 * therefore means the caller may safely acknowledge GMO. A duplicated retry
 * returns the original outcome without appending a second timeline event.</p>
 */
@Repository
public class SQLiteInboundMessageRepository implements InboundMessageRepository {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final JdbcClient jdbc;
    private final SQLiteLockRetryExecutor lockRetry;
    private final TransactionTemplate transactions;

    public SQLiteInboundMessageRepository(JdbcClient jdbc, SQLiteLockRetryExecutor lockRetry,
                                          PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.lockRetry = lockRetry;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public InboundMessageResult receive(InboundPaymentMessage message, boolean applyStateChanges) {
        return lockRetry.execute("record inbound GMO message", () -> transactions.execute(status -> {
            Optional<InboundMessageResult> duplicate = findByHash(message.payloadHash());
            if (duplicate.isPresent()) return duplicate.get();

            Optional<TransactionLink> link = findTransaction(message.providerOrderId(), message.providerAccessId());
            String messageId = "MSG-" + compactId();
            boolean applied = applyStateChanges && link.isPresent();
            String parseStatus = applied ? "APPLIED" : (applyStateChanges ? "RECEIVED_UNLINKED" : "NOT_APPLIED");
            String linkageStatus = link.isPresent() ? (applied ? "LINKED" : "LINKED_NOT_APPLIED") : "UNMATCHED";

            jdbc.sql("""
                    INSERT INTO inbound_message
                        (message_id, source_family, external_event_key, payload_hash, transaction_id,
                         sanitized_payload, parse_status, acknowledgement_status, linkage_status,
                         received_at, processed_at)
                    VALUES (:messageId, :source, :externalKey, :hash, :transactionId,
                            :payload, :parseStatus, 'ACKNOWLEDGED', :linkageStatus,
                            :receivedAt, :processedAt)
                    """).param("messageId", messageId).param("source", message.sourceFamily())
                    .param("externalKey", message.externalEventKey()).param("hash", message.payloadHash())
                    .param("transactionId", link.map(TransactionLink::id).orElse(null))
                    .param("payload", json(message.sanitizedPayload())).param("parseStatus", parseStatus)
                    .param("linkageStatus", linkageStatus).param("receivedAt", message.receivedAt().toString())
                    .param("processedAt", Instant.now().toString()).update();

            if (applied) applyToTransaction(link.orElseThrow(), message);
            return new InboundMessageResult(messageId, false, link.isPresent(), applied,
                    link.map(TransactionLink::transactionId).orElse(null), parseStatus);
        }));
    }

    private Optional<InboundMessageResult> findByHash(String hash) {
        return jdbc.sql("""
                SELECT m.message_id, m.parse_status, t.transaction_id
                FROM inbound_message m
                LEFT JOIN payment_transaction t ON t.id = m.transaction_id
                WHERE m.payload_hash = :hash
                """).param("hash", hash).query((rs, rowNum) -> new InboundMessageResult(
                rs.getString("message_id"), true, rs.getString("transaction_id") != null,
                "APPLIED".equals(rs.getString("parse_status")), rs.getString("transaction_id"),
                rs.getString("parse_status"))).optional();
    }

    private Optional<TransactionLink> findTransaction(String orderId, String accessId) {
        if (blank(orderId) && blank(accessId)) return Optional.empty();
        return jdbc.sql("""
                SELECT t.id, t.transaction_id, t.application_id,
                       COALESCE((SELECT e.correlation_id FROM payment_event e
                                 WHERE e.transaction_id = t.id ORDER BY e.id LIMIT 1), '') correlation_id
                FROM payment_transaction t
                LEFT JOIN application_record a ON a.id = t.application_id
                WHERE (:accessId IS NOT NULL AND t.provider_access_id = :accessId)
                   OR (:orderId IS NOT NULL AND (t.provider_order_id = :orderId
                                                OR t.merchant_reference = :orderId
                                                OR a.application_number = :orderId))
                ORDER BY CASE WHEN :accessId IS NOT NULL AND t.provider_access_id = :accessId THEN 0 ELSE 1 END,
                         t.id DESC
                LIMIT 1
                """).param("accessId", blank(accessId) ? null : accessId)
                .param("orderId", blank(orderId) ? null : orderId)
                .query((rs, rowNum) -> new TransactionLink(rs.getLong("id"),
                        rs.getString("transaction_id"), (Long) rs.getObject("application_id"),
                        rs.getString("correlation_id"))).optional();
    }

    private void applyToTransaction(TransactionLink link, InboundPaymentMessage message) {
        String providerStatus = blank(message.providerStatus()) ? "NOTIFIED" : message.providerStatus().trim();
        String canonicalState = canonicalState(providerStatus);
        boolean attention = requiresAttention(canonicalState);
        String now = Instant.now().toString();
        jdbc.sql("""
                UPDATE payment_transaction
                SET canonical_state = :state,
                    provider_order_id = COALESCE(:orderId, provider_order_id),
                    provider_access_id = COALESCE(:accessId, provider_access_id),
                    provider_status = :providerStatus, requires_attention = :attention,
                    version = version + 1, updated_at = :updatedAt
                WHERE id = :id
                """).param("state", canonicalState).param("orderId", message.providerOrderId())
                .param("accessId", message.providerAccessId()).param("providerStatus", providerStatus)
                .param("attention", attention).param("updatedAt", now).param("id", link.id()).update();
        // If this transaction belongs to a Koza monthly batch, project the
        // asynchronous bank result onto the local batch row as well. The
        // payment transaction remains the authoritative lifecycle thread.
        jdbc.sql("""
                UPDATE debit_batch_item
                SET state=:state,
                    result_code=:providerStatus,
                    failure_reason=CASE WHEN :attention THEN :providerStatus ELSE NULL END,
                    updated_at=:updatedAt
                WHERE transaction_id=:transactionId
                """).param("state", canonicalState).param("providerStatus", providerStatus)
                .param("attention", attention).param("updatedAt", now).param("transactionId", link.id()).update();
        if (link.applicationId() != null) {
            jdbc.sql("""
                    UPDATE application_record
                    SET state = :state, version = version + 1, updated_at = :updatedAt
                    WHERE id = :id
                    """).param("state", canonicalState).param("updatedAt", now)
                    .param("id", link.applicationId()).update();
        }

        String correlationId = blank(link.correlationId()) ? "CORR-" + UUID.randomUUID() : link.correlationId();
        String eventId = "EVT-" + compactId();
        jdbc.sql("""
                INSERT INTO payment_event
                    (event_id, transaction_id, event_type, source, summary, canonical_state_after,
                     actor, correlation_id, evidence_json, provider_occurred_at)
                VALUES (:eventId, :transactionId, 'PROVIDER_NOTIFICATION', 'GMO_WEBHOOK',
                        :summary, :state, 'gmo', :correlationId, :evidence, :occurredAt)
                """).param("eventId", eventId).param("transactionId", link.id())
                .param("summary", "GMO reported " + providerStatus).param("state", canonicalState)
                .param("correlationId", correlationId).param("evidence", json(message.sanitizedPayload()))
                .param("occurredAt", message.receivedAt().toString()).update();
        long eventPk = jdbc.sql("SELECT id FROM payment_event WHERE event_id = :eventId")
                .param("eventId", eventId).query(Long.class).single();
        jdbc.sql("""
                INSERT INTO provider_exchange
                    (exchange_id, transaction_id, event_id, direction, transport, operation,
                     response_body_json, outcome, correlation_id)
                VALUES (:exchangeId, :transactionId, :eventId, 'INBOUND', 'WEBHOOK',
                        :operation, :payload, :outcome, :correlationId)
                """).param("exchangeId", "EXC-" + compactId()).param("transactionId", link.id())
                .param("eventId", eventPk).param("operation", message.sourceFamily() + " notification")
                .param("payload", json(message.sanitizedPayload())).param("outcome", canonicalState)
                .param("correlationId", correlationId).update();
    }

    private static String canonicalState(String providerStatus) {
        return switch (providerStatus.toUpperCase(Locale.ROOT)) {
            case "AUTH", "AUTHENTICATED", "AUTHORIZED" -> "AUTHORIZED";
            case "CAPTURE", "CAPTURED", "SALES", "PAID", "PAYSUCCESS" -> "PAID";
            case "REGISTER", "REGISTERED" -> "REGISTERED";
            case "REQSUCCESS", "REQUEST_ACCEPTED" -> "SCHEDULED";
            case "SEND", "PROCESSING" -> "PROCESSING";
            case "PARTIAL_REFUND", "PARTIALLY_REFUNDED" -> "PARTIALLY_REFUNDED";
            case "REFUND", "REFUNDED" -> "REFUNDED";
            case "CHARGEBACK", "CHARGED_BACK" -> "CHARGED_BACK";
            case "PAYFAIL", "FAILED", "FAIL", "EXPIRED", "CANCEL", "CANCELLED" -> "FAILED";
            default -> providerStatus.toUpperCase(Locale.ROOT);
        };
    }

    private static boolean requiresAttention(String state) {
        return switch (state) {
            case "FAILED", "CHARGED_BACK", "UNKNOWN" -> true;
            default -> false;
        };
    }

    private static String json(Map<String, Object> value) {
        try { return JSON.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("Inbound evidence could not be encoded", exception); }
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String compactId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
    }

    private record TransactionLink(long id, String transactionId, Long applicationId, String correlationId) {}
}
