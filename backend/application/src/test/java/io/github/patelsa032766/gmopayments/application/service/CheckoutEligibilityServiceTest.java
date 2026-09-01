package io.github.patelsa032766.gmopayments.application.service;

import io.github.patelsa032766.gmopayments.domain.ConfigurationRelease;
import io.github.patelsa032766.gmopayments.domain.DistributionChannel;
import io.github.patelsa032766.gmopayments.domain.PaymentMethodCode;
import io.github.patelsa032766.gmopayments.domain.PaymentMethodConfiguration;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CheckoutEligibilityServiceTest {

    @Test
    void monthlyCheckoutReturnsOnlyReusableEligibleMethodsInConfiguredOrder() {
        var card = method(PaymentMethodCode.CARD, true, false, 1, 1_000_000, null, 2);
        var furikomi = method(PaymentMethodCode.FURIKOMI, false, false, 1, 1_000_000, null, 1);
        var koza = method(PaymentMethodCode.KOZA_FURIKAE_SELECT, true, true, 1, 1_000_000, null, 3);
        var service = serviceWith(card, furikomi, koza);

        var result = service.findOptions(new CheckoutEligibilityQuery(
                DistributionChannel.PA, 10_000, true, true, "en"));

        assertThat(result.methods()).extracting(EligiblePaymentMethod::code)
                .containsExactly(PaymentMethodCode.CARD, PaymentMethodCode.KOZA_FURIKAE_SELECT);
    }

    @Test
    void nonEkycBankDirectLimitDoesNotAffectOtherMethods() {
        var bank = method(PaymentMethodCode.BANK_DIRECT_REALTIME, true, false, 1, 300_000, 50_000L, 1);
        var card = method(PaymentMethodCode.CARD, true, false, 1, 1_000_000, null, 2);
        var service = serviceWith(bank, card);

        var result = service.findOptions(new CheckoutEligibilityQuery(
                DistributionChannel.PA, 75_000, true, false, "en"));

        assertThat(result.methods()).extracting(EligiblePaymentMethod::code)
                .containsExactly(PaymentMethodCode.CARD);
    }

    private CheckoutEligibilityService serviceWith(PaymentMethodConfiguration... methods) {
        var release = new ConfigurationRelease(1, 1, Instant.EPOCH, "test", List.of(methods));
        return new CheckoutEligibilityService(() -> release);
    }

    private PaymentMethodConfiguration method(
            PaymentMethodCode code,
            boolean recurring,
            boolean monthlyOnly,
            long minimum,
            long maximum,
            Long nonEkycMaximum,
            int order) {
        return new PaymentMethodConfiguration(
                code,
                code.name(),
                "English description",
                code.name(),
                "Japanese description",
                true,
                recurring,
                monthlyOnly,
                minimum,
                maximum,
                nonEkycMaximum,
                Set.of(DistributionChannel.PA),
                order);
    }
}
