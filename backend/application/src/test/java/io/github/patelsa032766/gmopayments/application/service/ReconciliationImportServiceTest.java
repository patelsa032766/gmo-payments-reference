package io.github.patelsa032766.gmopayments.application.service;

import io.github.patelsa032766.gmopayments.application.port.ReconciliationFileSource;
import io.github.patelsa032766.gmopayments.domain.DownloadedReconciliationFile;
import io.github.patelsa032766.gmopayments.domain.ReconciliationImportResult;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** Tests the durable-import boundary without opening a network or database connection. */
class ReconciliationImportServiceTest {
    private static final String CSV = "orderId,status,amountJpy,eventOccurredAt\n"
            + "ORDER-1,PAYSUCCESS,10000,2026-09-01T12:00:00Z\n";

    @Test
    void disabledSourceDoesNoWork() {
        var source = new Source();
        source.enabled = false;
        var service = new ReconciliationImportService(source, (a,b,c,d,e,f) -> {
            throw new AssertionError("Disabled source must not import");
        });
        assertThat(service.poll("test").enabled()).isFalse();
        assertThat(source.actions).isEmpty();
    }

    @Test
    void commitsBeforeArchivingAndReportsCounts() {
        var source = new Source();
        var service = new ReconciliationImportService(source, (name,marker,checksum,rows,time,actor) -> {
            source.actions.add("commit");
            assertThat(checksum).matches("[0-9a-f]{64}");
            assertThat(rows).hasSize(1);
            assertThat(actor).isEqualTo("operator");
            return new ReconciliationImportResult("file-1", false, 1, 1, 0);
        });
        var report = service.poll("operator");
        assertThat(source.actions).containsExactly("fetch", "commit", "archive");
        assertThat(report.filesImported()).isEqualTo(1);
        assertThat(report.rowsMatched()).isEqualTo(1);
    }

    @Test
    void failedImportNeverArchives() {
        var source = new Source();
        var service = new ReconciliationImportService(source, (a,b,c,d,e,f) -> {
            throw new IllegalStateException("database unavailable");
        });
        assertThatThrownBy(() -> service.poll("test")).hasMessage("database unavailable");
        assertThat(source.actions).containsExactly("fetch");
    }

    @Test
    void malformedFileNeverImportsOrArchives() {
        var source = new Source();
        source.content = "not a reconciliation file";
        var service = new ReconciliationImportService(source, (a,b,c,d,e,f) -> {
            throw new AssertionError("Malformed file must not import");
        });
        assertThatThrownBy(() -> service.poll("test")).isInstanceOf(IllegalArgumentException.class);
        assertThat(source.actions).containsExactly("fetch");
    }

    @Test
    void archiveFailureCanBeRetriedWithTheSameChecksum() {
        var source = new Source();
        source.failArchive = true;
        var checksums = new ArrayList<String>();
        var imports = new AtomicInteger();
        var service = new ReconciliationImportService(source, (a,b,checksum,d,e,f) -> {
            checksums.add(checksum);
            // Model the repository contract: the second import is already committed.
            return new ReconciliationImportResult("file-1", imports.getAndIncrement() > 0, 1, 1, 0);
        });
        assertThatThrownBy(() -> service.poll("test")).hasMessage("archive unavailable");
        source.failArchive = false;
        var report = service.poll("test");
        assertThat(checksums).hasSize(2);
        assertThat(checksums.get(0)).isEqualTo(checksums.get(1));
        assertThat(report.duplicates()).isEqualTo(1);
        assertThat(report.filesImported()).isZero();
    }

    private static final class Source implements ReconciliationFileSource {
        boolean enabled = true;
        boolean failArchive;
        String content = CSV;
        final List<String> actions = new ArrayList<>();
        public boolean enabled() { return enabled; }
        public List<DownloadedReconciliationFile> fetchReadyFiles() {
            actions.add("fetch");
            return List.of(new DownloadedReconciliationFile("result.csv",
                    content.getBytes(StandardCharsets.UTF_8), "result.csv.ready"));
        }
        public void archive(DownloadedReconciliationFile file) {
            actions.add("archive");
            if (failArchive) throw new IllegalStateException("archive unavailable");
        }
    }
}
