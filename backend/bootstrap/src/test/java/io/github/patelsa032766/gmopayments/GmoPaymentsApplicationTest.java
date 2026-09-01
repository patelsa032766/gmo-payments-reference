package io.github.patelsa032766.gmopayments;

import io.github.patelsa032766.gmopayments.application.port.CheckoutConfigurationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:target/application-test.db?journal_mode=WAL&busy_timeout=5000&foreign_keys=on"
})
class GmoPaymentsApplicationTest {
    @Autowired
    private CheckoutConfigurationRepository configurationRepository;

    @Test
    void contextLoadsWithMigratedSQLiteConfiguration() {
        // Querying through the application port is intentional: a context-only
        // assertion would not catch missing Flyway auto-configuration because
        // the datasource can be created lazily.
        var release = configurationRepository.findActiveRelease();

        assertThat(release.version()).isEqualTo(1);
        assertThat(release.paymentMethods()).hasSize(7);
    }
}
