package io.github.patelsa032766.gmopayments.application.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReconciliationFileParserTest {
    private final ReconciliationFileParser parser = new ReconciliationFileParser();

    @Test
    void parsesQuotedCsvAndKeepsOnlyTheAllowListedEvidence() {
        String file = "orderId,status,amountJpy,eventOccurredAt,bankAccount\n"
                + "\"ORDER,001\",PAYSUCCESS,10000,2026-09-01T12:00:00Z,do-not-store\n";

        var records = parser.parse(file.getBytes(StandardCharsets.UTF_8));

        assertThat(records).hasSize(1);
        assertThat(records.getFirst().providerOrderId()).isEqualTo("ORDER,001");
        assertThat(records.getFirst().amountJpy()).isEqualTo(10_000L);
        assertThat(records.getFirst().sanitizedRow())
                .containsOnlyKeys("orderId", "status", "amountJpy", "eventOccurredAt")
                .doesNotContainKey("bankAccount");
    }

    @Test
    void rejectsMalformedRowsBeforeAnythingCanBePersisted() {
        String file = "orderId,status,amountJpy\nORDER-1,PAYSUCCESS,not-a-number\n";

        assertThatThrownBy(() -> parser.parse(file.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amountJpy");
    }
}
