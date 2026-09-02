package io.github.patelsa032766.gmopayments.domain;

/** Durable outcome returned before the remote file is archived. */
public record ReconciliationImportResult(
        String fileId,
        boolean duplicate,
        int rowCount,
        int matchedCount,
        int unmatchedCount
) {}
