package io.github.patelsa032766.gmopayments.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.patelsa032766.gmopayments.application.port.ReconciliationImportRepository;
import io.github.patelsa032766.gmopayments.domain.ReconciliationImportResult;
import io.github.patelsa032766.gmopayments.domain.ReconciliationRecord;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Atomic SQLite import for SFTP reconciliation evidence.
 *
 * <p>Every file, row, match, lifecycle event, exchange, and current-state
 * projection is committed in one short local transaction. No network call is
 * made while SQLite is locked. The checksum unique constraint and preliminary
 * lookup make repeated downloads idempotent.</p>
 */
@Repository
public class SQLiteReconciliationImportRepository implements ReconciliationImportRepository {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final JdbcClient jdbc;
    private final SQLiteLockRetryExecutor lockRetry;
    private final TransactionTemplate transactions;

    public SQLiteReconciliationImportRepository(JdbcClient jdbc,
                                                SQLiteLockRetryExecutor lockRetry,
                                                PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.lockRetry = lockRetry;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public ReconciliationImportResult importFile(String remoteName,
                                                 String readyMarker,
                                                 String checksum,
                                                 List<ReconciliationRecord> records,
                                                 Instant receivedAt,
                                                 String actor) {
        return lockRetry.execute("import SFTP reconciliation file", () ->
                transactions.execute(status -> doImport(remoteName, readyMarker, checksum,
                        records, receivedAt, safeActor(actor))));
    }

    private ReconciliationImportResult doImport(String remoteName,
                                                String readyMarker,
                                                String checksum,
                                                List<ReconciliationRecord> records,
                                                Instant receivedAt,
                                                String actor) {
        Optional<ReconciliationImportResult> duplicate = findDuplicate(checksum);
        if (duplicate.isPresent()) return duplicate.get();

        String fileId = "REC-" + compactId();
        jdbc.sql("""
                INSERT INTO reconciliation_file
                    (file_id, remote_name, checksum_sha256, ready_marker_name, source, state,
                     row_count, imported_by, received_at)
                VALUES (:fileId, :remoteName, :checksum, :readyMarker, 'SFTP', 'IMPORTING',
                        :rowCount, :actor, :receivedAt)
                """).param("fileId", fileId).param("remoteName", remoteName)
                .param("checksum", checksum).param("readyMarker", readyMarker)
                .param("rowCount", records.size()).param("actor", actor)
                .param("receivedAt", receivedAt.toString()).update();
        long filePk = jdbc.sql("SELECT id FROM reconciliation_file WHERE file_id=:fileId")
                .param("fileId", fileId).query(Long.class).single();

        int matched = 0;
        int unmatched = 0;
        for (ReconciliationRecord record : records) {
            long rowPk = insertRow(filePk, record);
            Optional<TransactionLink> link = findTransaction(record.providerOrderId());
            if (link.isEmpty()) {
                insertMatch(rowPk, null, "UNMATCHED", "No transaction matched the provider order ID", actor);
                unmatched++;
            } else {
                applyMatchedRecord(rowPk, link.orElseThrow(), record, fileId, actor);
                matched++;
            }
        }
        jdbc.sql("""
                UPDATE reconciliation_file
                SET state='COMPLETED', completed_at=:completedAt
                WHERE id=:fileId
                """).param("completedAt", Instant.now().toString()).param("fileId", filePk).update();
        return new ReconciliationImportResult(fileId, false, records.size(), matched, unmatched);
    }

    private Optional<ReconciliationImportResult> findDuplicate(String checksum) {
        return jdbc.sql("""
                SELECT f.id, f.file_id, f.row_count,
                       SUM(CASE WHEN m.match_status='MATCHED' THEN 1 ELSE 0 END) matched_count,
                       SUM(CASE WHEN m.match_status='UNMATCHED' THEN 1 ELSE 0 END) unmatched_count
                FROM reconciliation_file f
                LEFT JOIN reconciliation_row r ON r.file_id=f.id
                LEFT JOIN reconciliation_match m ON m.row_id=r.id
                WHERE f.checksum_sha256=:checksum
                GROUP BY f.id, f.file_id, f.row_count
                """).param("checksum", checksum).query((rs, rowNum) -> new ReconciliationImportResult(
                rs.getString("file_id"), true, rs.getInt("row_count"),
                rs.getInt("matched_count"), rs.getInt("unmatched_count"))).optional();
    }

    private long insertRow(long filePk, ReconciliationRecord record) {
        String safeJson = json(record.sanitizedRow());
        String rowHash = sha256(record.rowNumber() + "\n" + safeJson);
        jdbc.sql("""
                INSERT INTO reconciliation_row
                    (file_id, row_number, row_hash, provider_order_id, provider_status,
                     amount_jpy, event_occurred_at, sanitized_row_json, parse_status)
                VALUES (:fileId, :rowNumber, :rowHash, :orderId, :providerStatus,
                        :amount, :occurredAt, :safeJson, 'PARSED')
                """).param("fileId", filePk).param("rowNumber", record.rowNumber())
                .param("rowHash", rowHash).param("orderId", record.providerOrderId())
                .param("providerStatus", record.providerStatus()).param("amount", record.amountJpy())
                .param("occurredAt", record.occurredAt() == null ? null : record.occurredAt().toString())
                .param("safeJson", safeJson).update();
        return jdbc.sql("SELECT id FROM reconciliation_row WHERE file_id=:fileId AND row_number=:rowNumber")
                .param("fileId", filePk).param("rowNumber", record.rowNumber()).query(Long.class).single();
    }

    private Optional<TransactionLink> findTransaction(String providerOrderId) {
        return jdbc.sql("""
                SELECT t.id, t.transaction_id, t.application_id, t.amount_jpy, t.canonical_state,
                       COALESCE((SELECT e.correlation_id FROM payment_event e
                                 WHERE e.transaction_id=t.id ORDER BY e.id LIMIT 1), '') correlation_id
                FROM payment_transaction t
                LEFT JOIN application_record a ON a.id=t.application_id
                WHERE t.provider_order_id=:orderId OR t.merchant_reference=:orderId
                   OR a.application_number=:orderId
                ORDER BY CASE WHEN t.provider_order_id=:orderId THEN 0
                              WHEN t.merchant_reference=:orderId THEN 1 ELSE 2 END,
                         t.id DESC
                LIMIT 1
                """).param("orderId", providerOrderId).query((rs, rowNum) -> new TransactionLink(
                rs.getLong("id"), rs.getString("transaction_id"),
                (Long) rs.getObject("application_id"), rs.getLong("amount_jpy"),
                rs.getString("canonical_state"), rs.getString("correlation_id"))).optional();
    }

    private void applyMatchedRecord(long rowPk,
                                    TransactionLink link,
                                    ReconciliationRecord record,
                                    String fileId,
                                    String actor) {
        boolean amountMismatch = record.amountJpy() != null && record.amountJpy() != link.amountJpy();
        String canonicalState = amountMismatch ? "NEEDS_REVIEW" : canonicalState(record.providerStatus());
        boolean attention = amountMismatch || requiresAttention(canonicalState);
        String resolution = amountMismatch ? "ORDER_ID_MATCH_AMOUNT_MISMATCH" : "AUTO_ORDER_ID";
        insertMatch(rowPk, link.id(), "MATCHED", resolution, actor);

        String now = Instant.now().toString();
        jdbc.sql("""
                UPDATE payment_transaction
                SET canonical_state=:state, provider_status=:providerStatus,
                    requires_attention=:attention, version=version+1, updated_at=:updatedAt
                WHERE id=:transactionId
                """).param("state", canonicalState).param("providerStatus", record.providerStatus())
                .param("attention", attention).param("updatedAt", now).param("transactionId", link.id()).update();
        jdbc.sql("""
                UPDATE debit_batch_item
                SET state=:state, result_code=:providerStatus,
                    failure_reason=CASE WHEN :attention THEN :reason ELSE NULL END,
                    updated_at=:updatedAt
                WHERE transaction_id=:transactionId
                """).param("state", canonicalState).param("providerStatus", record.providerStatus())
                .param("attention", attention).param("reason", resolution)
                .param("updatedAt", now).param("transactionId", link.id()).update();
        if (link.applicationId() != null) {
            jdbc.sql("""
                    UPDATE application_record
                    SET state=:state, version=version+1, updated_at=:updatedAt
                    WHERE id=:applicationId
                    """).param("state", canonicalState).param("updatedAt", now)
                    .param("applicationId", link.applicationId()).update();
        }

        String correlation = link.correlationId().isBlank() ? "CORR-" + compactId() : link.correlationId();
        String eventId = "EVT-" + compactId();
        String summary = amountMismatch
                ? "SFTP result matched, but the amount differs and requires review"
                : "SFTP reconciliation reported " + record.providerStatus();
        jdbc.sql("""
                INSERT INTO payment_event
                    (event_id, transaction_id, event_type, source, summary, canonical_state_after,
                     actor, correlation_id, evidence_json, provider_occurred_at)
                VALUES (:eventId, :transactionId, 'RECONCILIATION_RESULT', 'SFTP', :summary, :state,
                        :actor, :correlation, :evidence, :occurredAt)
                """).param("eventId", eventId).param("transactionId", link.id())
                .param("summary", summary).param("state", canonicalState).param("actor", actor)
                .param("correlation", correlation).param("evidence", json(record.sanitizedRow()))
                .param("occurredAt", record.occurredAt() == null ? null : record.occurredAt().toString()).update();
        long eventPk = jdbc.sql("SELECT id FROM payment_event WHERE event_id=:eventId")
                .param("eventId", eventId).query(Long.class).single();
        jdbc.sql("""
                INSERT INTO provider_exchange
                    (exchange_id, transaction_id, event_id, direction, transport, operation,
                     response_body_json, outcome, correlation_id)
                VALUES (:exchangeId, :transactionId, :eventId, 'INBOUND', 'SFTP',
                        'reconciliation-file-import', :response, :outcome, :correlation)
                """).param("exchangeId", "EXC-" + compactId()).param("transactionId", link.id())
                .param("eventId", eventPk).param("response", json(record.sanitizedRow()))
                .param("outcome", canonicalState).param("correlation", correlation).update();
    }

    private void insertMatch(long rowPk, Long transactionPk, String status, String resolution, String actor) {
        jdbc.sql("""
                INSERT INTO reconciliation_match
                    (row_id, transaction_id, match_status, resolution, resolved_by, resolved_at)
                VALUES (:rowId, :transactionId, :status, :resolution, :actor, :resolvedAt)
                """).param("rowId", rowPk).param("transactionId", transactionPk)
                .param("status", status).param("resolution", resolution).param("actor", actor)
                .param("resolvedAt", Instant.now().toString()).update();
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
            default -> "NEEDS_REVIEW";
        };
    }

    private static boolean requiresAttention(String state) {
        return state.equals("FAILED") || state.equals("CHARGED_BACK")
                || state.equals("UNKNOWN") || state.equals("NEEDS_REVIEW");
    }

    private static String safeActor(String actor) {
        if (actor == null || actor.isBlank()) return "sftp-import";
        return actor.length() > 80 ? actor.substring(0, 80) : actor;
    }

    private static String json(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Reconciliation evidence could not be encoded", exception);
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String compactId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
    }

    private record TransactionLink(long id, String transactionId, Long applicationId, long amountJpy,
                                   String currentState, String correlationId) {}
}
