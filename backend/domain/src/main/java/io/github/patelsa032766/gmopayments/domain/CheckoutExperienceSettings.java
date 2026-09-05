package io.github.patelsa032766.gmopayments.domain;

import java.util.List;

/** Developer-facing checkout scenario; no GMO credential or sensitive payment data is stored. */
public record CheckoutExperienceSettings(String selectedApplicationNumber,
        boolean configurationTokenRequired, String checkoutLanguage, List<CheckoutScenario> customers) {
    public CheckoutExperienceSettings { customers = List.copyOf(customers); }
    public CheckoutScenario selected() {
        return customers.stream().filter(item -> item.applicationNumber().equals(selectedApplicationNumber))
                .findFirst().orElseThrow(() -> new IllegalStateException("Selected checkout scenario does not exist"));
    }
}
