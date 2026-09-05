package io.github.patelsa032766.gmopayments.application.service;

import io.github.patelsa032766.gmopayments.application.port.CheckoutExperienceRepository;
import io.github.patelsa032766.gmopayments.domain.CheckoutExperienceSettings;

import java.util.List;

/** Validates and coordinates the developer-facing checkout scenario controls. */
public final class CheckoutExperienceService {
    private final CheckoutExperienceRepository repository;
    public CheckoutExperienceService(CheckoutExperienceRepository repository) { this.repository = repository; }
    public CheckoutExperienceSettings get() { return repository.get(); }
    public CheckoutExperienceSettings update(String applicationNumber, long amountJpy,
                                             boolean operatorTokenRequired, String checkoutLanguage) {
        if (applicationNumber == null || applicationNumber.isBlank())
            throw new IllegalArgumentException("A predefined checkout customer is required");
        if (amountJpy < 1) throw new IllegalArgumentException("Due today must be at least JPY 1");
        if (!List.of("en", "ja").contains(checkoutLanguage))
            throw new IllegalArgumentException("Checkout language must be en or ja");
        return repository.update(applicationNumber, amountJpy, operatorTokenRequired, checkoutLanguage);
    }
}
