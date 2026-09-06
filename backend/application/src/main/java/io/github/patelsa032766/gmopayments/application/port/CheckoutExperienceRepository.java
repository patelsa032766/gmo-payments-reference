package io.github.patelsa032766.gmopayments.application.port;

import io.github.patelsa032766.gmopayments.domain.CheckoutExperienceSettings;
import io.github.patelsa032766.gmopayments.domain.CheckoutScenario;

/** Persistence boundary for the synthetic customer/application used by the demo checkout. */
public interface CheckoutExperienceRepository {
    CheckoutExperienceSettings get();
    CheckoutExperienceSettings update(String applicationNumber, long amountJpy, String paymentPlan,
                                      boolean operatorTokenRequired, String checkoutLanguage);
    CheckoutScenario createApplication(String templateApplicationNumber);
    CheckoutScenario findApplication(String applicationNumber);
}
