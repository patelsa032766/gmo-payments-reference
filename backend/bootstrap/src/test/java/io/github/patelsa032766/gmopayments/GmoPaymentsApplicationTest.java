package io.github.patelsa032766.gmopayments;

import io.github.patelsa032766.gmopayments.application.port.CheckoutConfigurationRepository;
import io.github.patelsa032766.gmopayments.application.service.CheckoutPaymentService;
import io.github.patelsa032766.gmopayments.application.service.CheckoutExperienceService;
import io.github.patelsa032766.gmopayments.application.service.CapturePaymentService;
import io.github.patelsa032766.gmopayments.application.service.PaymentOperationsQueryService;
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

    @Autowired
    private CapturePaymentService capturePaymentService;

    @Autowired
    private PaymentOperationsQueryService operations;

    @Autowired
    private CheckoutExperienceService checkoutExperience;

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
    void checkoutScenarioPersistsCustomerAmountLanguageAndConfigurationTokenPolicy() {
        var original = checkoutExperience.get();
        var originalCustomer = original.selected();
        try {
            var changed = checkoutExperience.update("APP-20260904-025", 25_000, false, "ja");

            assertThat(changed.selected().customerName()).isEqualTo("Ken Ito");
            assertThat(changed.selected().amountJpy()).isEqualTo(25_000);
            assertThat(changed.checkoutLanguage()).isEqualTo("ja");
            assertThat(changed.configurationTokenRequired()).isFalse();
        } finally {
            // Tests share one migrated fixture DB, so restore the original
            // singleton and keep every test independent of execution order.
            checkoutExperience.update(original.selectedApplicationNumber(), originalCustomer.amountJpy(),
                    original.configurationTokenRequired(), original.checkoutLanguage());
        }
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

    @Test
    void captureAppendsToTheExistingAuthorizedTransactionThread() {
        var authorization = checkoutPaymentService.submit("APP-20260821-001", PaymentMethodCode.CARD,
                "auth-" + UUID.randomUUID(), Map.of("token", "test-token", "holderName", "AIKO TANAKA"));

        assertThat(authorization.state()).isEqualTo("AUTHORIZED");
        var captured = capturePaymentService.capture(authorization.transactionId(),
                "capture-" + UUID.randomUUID());

        assertThat(captured.state()).isEqualTo("PAID");
        var thread = operations.getTransactionThread(authorization.transactionId());
        assertThat(thread.events()).extracting("eventType")
                .contains("CAPTURE_REQUESTED", "PAYMENT_CAPTURED");
        assertThat(thread.exchanges()).extracting("operation").contains("OrderCapture");
    }
}
