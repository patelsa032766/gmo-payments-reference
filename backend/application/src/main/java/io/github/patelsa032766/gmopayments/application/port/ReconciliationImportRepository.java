package io.github.patelsa032766.gmopayments.application.port;

import io.github.patelsa032766.gmopayments.domain.ReconciliationImportResult;
import io.github.patelsa032766.gmopayments.domain.ReconciliationRecord;

import java.time.Instant;
import java.util.List;

/** Atomic local import boundary. Implementations must deduplicate by checksum. */
public interface ReconciliationImportRepository {
    ReconciliationImportResult importFile(
            String remoteName,
            String readyMarker,
            String checksum,
            List<ReconciliationRecord> records,
            Instant receivedAt,
            String actor
    );
}
