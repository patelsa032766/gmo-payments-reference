package io.github.patelsa032766.gmopayments.application.port;

import io.github.patelsa032766.gmopayments.domain.CheckoutExperienceSettings;

/** Persistence boundary for the synthetic customer/application used by the demo checkout. */
public interface CheckoutExperienceRepository {
    CheckoutExperienceSettings get();
    CheckoutExperienceSettings update(String applicationNumber, long amountJpy,
                                      boolean operatorTokenRequired, String checkoutLanguage);
}
