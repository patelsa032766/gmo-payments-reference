package io.github.patelsa032766.gmopayments.sftp;

import io.github.patelsa032766.gmopayments.application.service.ReconciliationImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/** Runs the optional poll without allowing overlapping scheduled executions. */
@Component
public final class ScheduledReconciliationImporter {
    private static final Logger log = LoggerFactory.getLogger(ScheduledReconciliationImporter.class);
    private final ReconciliationImportService service;
    private final SftpReconciliationProperties properties;
    private final AtomicBoolean running = new AtomicBoolean();

    public ScheduledReconciliationImporter(ReconciliationImportService service,
                                           SftpReconciliationProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${reconciliation.sftp.poll-interval-ms:300000}")
    public void poll() {
        if (!properties.enabled() || !running.compareAndSet(false, true)) return;
        try {
            var report = service.poll("sftp-scheduler");
            if (report.filesDiscovered() > 0) {
                log.info("SFTP reconciliation completed: files={}, imported={}, duplicates={}, matched={}, unmatched={}",
                        report.filesDiscovered(), report.filesImported(), report.duplicates(),
                        report.rowsMatched(), report.rowsUnmatched());
            }
        } catch (RuntimeException exception) {
            // The next scheduled run retries. Files are archived only after a
            // committed import, and checksum deduplication makes that retry safe.
            log.error("SFTP reconciliation poll failed; source files remain retryable", exception);
        } finally {
            running.set(false);
        }
    }
}
