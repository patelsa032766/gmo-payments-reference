package io.github.patelsa032766.gmopayments;

import io.github.patelsa032766.gmopayments.application.port.CheckoutConfigurationRepository;
import io.github.patelsa032766.gmopayments.application.service.CheckoutEligibilityService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Explicitly assembles framework-free application services. This is the only
 * module allowed to decide which adapter implements an application port.
 */
@Configuration
public class ApplicationUseCaseConfiguration {
    @Bean
    CheckoutEligibilityService checkoutEligibilityService(CheckoutConfigurationRepository repository) {
        return new CheckoutEligibilityService(repository);
    }
}
