package io.github.patelsa032766.gmopayments.application.service;

import io.github.patelsa032766.gmopayments.application.port.ReconciliationFileSource;
import io.github.patelsa032766.gmopayments.application.port.ReconciliationImportRepository;
import io.github.patelsa032766.gmopayments.domain.DownloadedReconciliationFile;
import io.github.patelsa032766.gmopayments.domain.ReconciliationImportResult;
import io.github.patelsa032766.gmopayments.domain.ReconciliationPollReport;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** Coordinates download, validation, atomic import, and post-commit archive. */
public final class ReconciliationImportService {
    private final ReconciliationFileSource source;
    private final ReconciliationImportRepository repository;
    private final ReconciliationFileParser parser;

    public ReconciliationImportService(ReconciliationFileSource source,
                                       ReconciliationImportRepository repository) {
        this(source, repository, new ReconciliationFileParser());
    }

    ReconciliationImportService(ReconciliationFileSource source,
                                ReconciliationImportRepository repository,
                                ReconciliationFileParser parser) {
        this.source = source;
        this.repository = repository;
        this.parser = parser;
    }

    public ReconciliationPollReport poll(String actor) {
        if (!source.enabled()) return ReconciliationPollReport.disabled();
        List<DownloadedReconciliationFile> files = source.fetchReadyFiles();
        List<ReconciliationImportResult> results = new ArrayList<>();
        int imported = 0;
        int duplicates = 0;
        int matched = 0;
        int unmatched = 0;

        for (DownloadedReconciliationFile file : files) {
            ReconciliationImportResult result = repository.importFile(
                    file.remoteName(), file.readyMarkerName(), sha256(file.content()),
                    parser.parse(file.content()), Instant.now(), actor);
            // Import is now durable. Archiving after this point means an SFTP
            // failure can safely be retried and will deduplicate by checksum.
            source.archive(file);
            results.add(result);
            if (result.duplicate()) duplicates++; else imported++;
            matched += result.matchedCount();
            unmatched += result.unmatchedCount();
        }
        return new ReconciliationPollReport(true, files.size(), imported, duplicates,
                matched, unmatched, results);
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
