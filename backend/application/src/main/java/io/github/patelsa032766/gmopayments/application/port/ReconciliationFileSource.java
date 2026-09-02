package io.github.patelsa032766.gmopayments.application.port;

import io.github.patelsa032766.gmopayments.domain.DownloadedReconciliationFile;

import java.util.List;

/** Provider-neutral source of ready-to-import reconciliation files. */
public interface ReconciliationFileSource {
    boolean enabled();

    List<DownloadedReconciliationFile> fetchReadyFiles();

    /** Called only after the file has been committed to local durable storage. */
    void archive(DownloadedReconciliationFile file);
}
