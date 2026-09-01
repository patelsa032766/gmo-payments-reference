package io.github.patelsa032766.gmopayments.application.service;

import io.github.patelsa032766.gmopayments.application.port.CheckoutConfigurationRepository;
import io.github.patelsa032766.gmopayments.domain.ConfigurationRelease;
import io.github.patelsa032766.gmopayments.domain.PaymentMethodCode;
import io.github.patelsa032766.gmopayments.domain.PaymentMethodConfiguration;

import java.util.Comparator;

/**
 * Owns checkout eligibility. Angular renders the returned list but never
 * reimplements these rules; otherwise configuration previews and payment
 * submission could disagree about what is allowed.
 */
public final class CheckoutEligibilityService {
    private final CheckoutConfigurationRepository repository;

    public CheckoutEligibilityService(CheckoutConfigurationRepository repository) {
        this.repository = repository;
    }

    public CheckoutOptions findOptions(CheckoutEligibilityQuery query) {
        ConfigurationRelease release = repository.findActiveRelease();
        var methods = release.paymentMethods().stream()
                .filter(PaymentMethodConfiguration::enabled)
                .filter(method -> method.channels().contains(query.channel()))
                .filter(method -> !method.monthlyOnly() || query.monthly())
                .filter(method -> query.amountJpy() >= method.minimumAmountJpy())
                .filter(method -> query.amountJpy() <= method.maximumAmountJpy())
                // The approved monthly experience does not expose the legacy
                // two-step "pay now, choose another future source" journey.
                .filter(method -> !query.monthly() || method.recurring())
                .filter(method -> passesEkycRule(method, query))
                .sorted(Comparator.comparingInt(PaymentMethodConfiguration::displayOrder))
                .map(method -> new EligiblePaymentMethod(
                        method.code(),
                        method.label(query.language()),
                        method.description(query.language()),
                        method.recurring(),
                        method.displayOrder()))
                .toList();
        return new CheckoutOptions(release.version(), methods);
    }

    public ConfigurationRelease activeConfiguration() {
        return repository.findActiveRelease();
    }

    private boolean passesEkycRule(PaymentMethodConfiguration method, CheckoutEligibilityQuery query) {
        if (method.code() != PaymentMethodCode.BANK_DIRECT_REALTIME || query.ekycVerified()) return true;
        Long maximum = method.nonEkycMaximumAmountJpy();
        return maximum == null || query.amountJpy() <= maximum;
    }
}
