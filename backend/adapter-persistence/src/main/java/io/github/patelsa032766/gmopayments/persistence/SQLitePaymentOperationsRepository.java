package io.github.patelsa032766.gmopayments.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.patelsa032766.gmopayments.application.port.PaymentOperationsRepository;
import io.github.patelsa032766.gmopayments.domain.PaymentInstrumentSnapshot;
import io.github.patelsa032766.gmopayments.domain.PaymentMethodCode;
import io.github.patelsa032766.gmopayments.domain.PaymentTimelineEvent;
import io.github.patelsa032766.gmopayments.domain.PaymentTransactionSummary;
import io.github.patelsa032766.gmopayments.domain.PaymentTransactionThread;
import io.github.patelsa032766.gmopayments.domain.ProviderExchangeSnapshot;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** SQLite read-model adapter for the transaction console and MIT workspace. */
@Repository
public class SQLitePaymentOperationsRepository implements PaymentOperationsRepository {
    private static final TypeReference<Map<String, Object>> JSON_MAP = new TypeReference<>() {};
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final JdbcClient jdbc;

    public SQLitePaymentOperationsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<PaymentTransactionSummary> findTransactions() {
        return jdbc.sql("""
                SELECT t.transaction_id, t.root_transaction_id, a.application_number,
                       t.amount_jpy, t.canonical_state, t.method_code, t.product_code,
                       t.initiation_type, t.operation, c.full_name, c.customer_code,
                       t.merchant_reference, t.updated_at, t.requires_attention
                FROM payment_transaction t
                JOIN customer c ON c.id = t.customer_id
                LEFT JOIN application_record a ON a.id = t.application_id
                ORDER BY t.updated_at DESC, t.id DESC
                """).query((rs, rowNum) -> new PaymentTransactionSummary(
                rs.getString("transaction_id"), rs.getString("root_transaction_id"),
                rs.getString("application_number"), rs.getLong("amount_jpy"),
                rs.getString("canonical_state"), PaymentMethodCode.fromApiValue(rs.getString("method_code")),
                rs.getString("product_code"), rs.getString("initiation_type"), rs.getString("operation"),
                rs.getString("full_name"), rs.getString("customer_code"), rs.getString("merchant_reference"),
                Instant.parse(rs.getString("updated_at")), rs.getBoolean("requires_attention"))).list();
    }

    @Override
    public Optional<PaymentTransactionThread> findTransactionThread(String transactionId) {
        var transaction = findTransactions().stream()
                .filter(candidate -> candidate.transactionId().equals(transactionId))
                .findFirst();
        if (transaction.isEmpty()) return Optional.empty();

        var events = jdbc.sql("""
                SELECT e.event_id, e.event_type, e.source, e.summary, e.canonical_state_after,
                       e.actor, e.correlation_id, e.evidence_json, e.provider_occurred_at, e.occurred_at
                FROM payment_event e
                JOIN payment_transaction t ON t.id = e.transaction_id
                WHERE t.transaction_id = :transactionId
                   OR t.root_transaction_id = :transactionId
                ORDER BY e.occurred_at, e.id
                """).param("transactionId", transactionId).query((rs, rowNum) -> new PaymentTimelineEvent(
                rs.getString("event_id"), rs.getString("event_type"), rs.getString("source"),
                rs.getString("summary"), rs.getString("canonical_state_after"), rs.getString("actor"),
                rs.getString("correlation_id"), json(rs.getString("evidence_json")),
                instantOrNull(rs.getString("provider_occurred_at")), Instant.parse(rs.getString("occurred_at")))).list();

        var exchanges = jdbc.sql("""
                SELECT x.exchange_id, e.event_id, x.direction, x.transport, x.operation, x.endpoint,
                       x.http_status, x.duration_ms, x.request_headers_json, x.request_body_json,
                       x.response_headers_json, x.response_body_json, x.outcome, x.attempt_number,
                       x.correlation_id, x.created_at
                FROM provider_exchange x
                JOIN payment_transaction t ON t.id = x.transaction_id
                LEFT JOIN payment_event e ON e.id = x.event_id
                WHERE t.transaction_id = :transactionId
                   OR t.root_transaction_id = :transactionId
                ORDER BY x.created_at, x.id
                """).param("transactionId", transactionId).query((rs, rowNum) -> new ProviderExchangeSnapshot(
                rs.getString("exchange_id"), rs.getString("event_id"), rs.getString("direction"),
                rs.getString("transport"), rs.getString("operation"), rs.getString("endpoint"),
                nullableInteger(rs, "http_status"), nullableInteger(rs, "duration_ms"),
                json(rs.getString("request_headers_json")), json(rs.getString("request_body_json")),
                json(rs.getString("response_headers_json")), json(rs.getString("response_body_json")),
                rs.getString("outcome"), rs.getInt("attempt_number"), rs.getString("correlation_id"),
                Instant.parse(rs.getString("created_at")))).list();

        return Optional.of(new PaymentTransactionThread(transaction.get(), events, exchanges));
    }

    @Override
    public List<PaymentInstrumentSnapshot> findActiveInstruments() {
        return jdbc.sql("""
                SELECT i.instrument_id, c.customer_code, c.full_name, i.method_code, i.product_code,
                       i.masked_display, i.state, i.preference_role, i.metadata_json, i.updated_at
                FROM payment_instrument i
                JOIN customer c ON c.id = i.customer_id
                WHERE i.state = 'ACTIVE'
                ORDER BY c.full_name, CASE i.preference_role WHEN 'PRIMARY' THEN 1 WHEN 'BACKUP' THEN 2 ELSE 3 END,
                         i.updated_at DESC
                """).query((rs, rowNum) -> new PaymentInstrumentSnapshot(
                rs.getString("instrument_id"), rs.getString("customer_code"), rs.getString("full_name"),
                PaymentMethodCode.fromApiValue(rs.getString("method_code")), rs.getString("product_code"),
                rs.getString("masked_display"), rs.getString("state"), rs.getString("preference_role"),
                json(rs.getString("metadata_json")), Instant.parse(rs.getString("updated_at")))).list();
    }

    private Map<String, Object> json(String value) {
        try {
            return OBJECT_MAPPER.readValue(value == null ? "{}" : value, JSON_MAP);
        } catch (Exception exception) {
            throw new IllegalStateException("Stored sanitized JSON could not be read", exception);
        }
    }

    private static Instant instantOrNull(String value) {
        return value == null ? null : Instant.parse(value);
    }

    private static Integer nullableInteger(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
