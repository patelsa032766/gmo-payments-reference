package io.github.patelsa032766.gmopayments.web;

import io.github.patelsa032766.gmopayments.application.service.CheckoutEligibilityService;
import io.github.patelsa032766.gmopayments.domain.ConfigurationRelease;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/configuration")
public final class ConfigurationController {
    private final CheckoutEligibilityService eligibilityService;

    public ConfigurationController(CheckoutEligibilityService eligibilityService) {
        this.eligibilityService = eligibilityService;
    }

    @GetMapping("/active")
    ActiveConfigurationResponse active() {
        return ActiveConfigurationResponse.from(eligibilityService.activeConfiguration());
    }

    record ActiveConfigurationResponse(
            int version,
            Instant publishedAt,
            String publishedBy,
            List<ConfiguredMethodResponse> methods) {
        static ActiveConfigurationResponse from(ConfigurationRelease release) {
            return new ActiveConfigurationResponse(
                    release.version(), release.publishedAt(), release.publishedBy(),
                    release.paymentMethods().stream().map(ConfiguredMethodResponse::from).toList());
        }
    }

    record ConfiguredMethodResponse(
            String code,
            boolean enabled,
            boolean recurring,
            boolean monthlyOnly,
            long minimumAmountJpy,
            long maximumAmountJpy,
            int displayOrder) {
        static ConfiguredMethodResponse from(io.github.patelsa032766.gmopayments.domain.PaymentMethodConfiguration method) {
            return new ConfiguredMethodResponse(
                    method.code().apiValue(), method.enabled(), method.recurring(), method.monthlyOnly(),
                    method.minimumAmountJpy(), method.maximumAmountJpy(), method.displayOrder());
        }
    }
}
