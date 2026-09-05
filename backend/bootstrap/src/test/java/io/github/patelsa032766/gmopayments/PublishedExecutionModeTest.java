package io.github.patelsa032766.gmopayments;

import io.github.patelsa032766.gmopayments.application.port.CheckoutConfigurationRepository;
import io.github.patelsa032766.gmopayments.application.service.CheckoutPaymentService;
import io.github.patelsa032766.gmopayments.application.service.ConfigurationAdministrationService;
import io.github.patelsa032766.gmopayments.application.service.PaymentOperationsQueryService;
import io.github.patelsa032766.gmopayments.domain.ConfigurationMethodUpdate;
import io.github.patelsa032766.gmopayments.domain.PaymentExecutionMode;
import io.github.patelsa032766.gmopayments.domain.PaymentMethodCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Regression coverage for keeping the checkout display and GMO command on one release. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:target/published-execution-mode-test.db?journal_mode=WAL&busy_timeout=5000&foreign_keys=on"
})
class PublishedExecutionModeTest {
    @Autowired private CheckoutConfigurationRepository configuration;
    @Autowired private ConfigurationAdministrationService administration;
    @Autowired private CheckoutPaymentService checkout;
    @Autowired private PaymentOperationsQueryService operations;

    @Test
    void newlyPublishedImmediateSaleOverridesApplicationsPreviouslyUsingAuthorization() {
        var updates = configuration.findActiveRelease().paymentMethods().stream()
                .map(method -> new ConfigurationMethodUpdate(
                        method.code(), method.enabled(), method.recurring(), method.monthlyOnly(),
                        method.minimumAmountJpy(), method.maximumAmountJpy(), method.displayOrder(),
                        method.code() == PaymentMethodCode.CARD
                                ? PaymentExecutionMode.CAPTURE : method.citExecutionMode()))
                .toList();
        administration.saveDraft(updates);
        administration.publish("execution-mode-test");

        var result = checkout.submit("APP-20260821-001", PaymentMethodCode.CARD,
                "sale-" + UUID.randomUUID(),
                Map.of("token", "test-token", "holderName", "AIKO TANAKA"));

        // The simulated gateway deliberately returns AUTHORIZED for AUTH and
        // PAID for CAPTURE, so this assertion proves the published execution
        // mode reached the provider adapter rather than merely changing UI.
        assertThat(result.state()).isEqualTo("PAID");
        assertThat(operations.getTransactionThread(result.transactionId()).events())
                .extracting("eventType")
                .contains("PAYMENT_CAPTURED");
    }
}
