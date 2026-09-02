package io.github.patelsa032766.gmopayments.gmo;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GmoSanitizerTest {
    @Test
    void removesCredentialsTokensAndRawAccountValuesFromNestedEvidence() {
        var sanitized = GmoSanitizer.sanitize(Map.of(
                "ShopPass", "secret",
                "creditInformation", Map.of("token", "tok_123", "cardno", "4111111111111111"),
                "AccountNumber", "1234567",
                "status", "AUTH"));

        assertThat(sanitized.toString()).doesNotContain("secret", "tok_123", "4111111111111111", "1234567");
        assertThat(sanitized).containsEntry("AccountNumber", "••••4567");
        assertThat(sanitized).containsEntry("status", "AUTH");
    }
}
