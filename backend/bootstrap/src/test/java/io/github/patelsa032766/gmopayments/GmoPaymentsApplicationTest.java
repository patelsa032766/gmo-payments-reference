package io.github.patelsa032766.gmopayments;

import io.github.patelsa032766.gmopayments.application.port.CheckoutConfigurationRepository;
import io.github.patelsa032766.gmopayments.application.service.CheckoutPaymentService;
import io.github.patelsa032766.gmopayments.domain.PaymentMethodCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:target/application-test.db?journal_mode=WAL&busy_timeout=5000&foreign_keys=on"
})
class GmoPaymentsApplicationTest {
    @Autowired
    private CheckoutConfigurationRepository configurationRepository;

    @Autowired
    private CheckoutPaymentService checkoutPaymentService;

    @Test
    void contextLoadsWithMigratedSQLiteConfiguration() {
        // Querying through the application port is intentional: a context-only
        // assertion would not catch missing Flyway auto-configuration because
        // the datasource can be created lazily.
        var release = configurationRepository.findActiveRelease();

        assertThat(release.version()).isEqualTo(1);
        assertThat(release.paymentMethods()).hasSize(7);
    }

    @Test
    void simulatedCheckoutPersistsOneThreadAndReplaysTheIdempotentResult() {
        String idempotencyKey = "test-" + UUID.randomUUID();

        var first = checkoutPaymentService.submit("APP-20260821-001", PaymentMethodCode.FURIKOMI,
                idempotencyKey, Map.of("email", "customer@example.com"));
        var replay = checkoutPaymentService.submit("APP-20260821-001", PaymentMethodCode.FURIKOMI,
                idempotencyKey, Map.of("email", "customer@example.com"));

        assertThat(first.state()).isEqualTo("INSTRUCTIONS_ISSUED");
        assertThat(first.instructions()).containsKeys("bank", "accountNumber", "transferReference");
        assertThat(replay.transactionId()).isEqualTo(first.transactionId());
        assertThat(replay.idempotentReplay()).isTrue();
    }
}
