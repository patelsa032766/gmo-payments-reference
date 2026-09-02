package io.github.patelsa032766.gmopayments.domain;

import java.util.List;

/** Summary returned to the scheduler and protected operator endpoint. */
public record ReconciliationPollReport(
        boolean enabled,
        int filesDiscovered,
        int filesImported,
        int duplicates,
        int rowsMatched,
        int rowsUnmatched,
        List<ReconciliationImportResult> results
) {
    public ReconciliationPollReport {
        results = List.copyOf(results);
    }

    public static ReconciliationPollReport disabled() {
        return new ReconciliationPollReport(false, 0, 0, 0, 0, 0, List.of());
    }
}
