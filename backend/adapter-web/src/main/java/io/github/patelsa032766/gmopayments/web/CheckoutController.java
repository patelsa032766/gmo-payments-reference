package io.github.patelsa032766.gmopayments.web;

import io.github.patelsa032766.gmopayments.application.service.CheckoutEligibilityQuery;
import io.github.patelsa032766.gmopayments.application.service.CheckoutEligibilityService;
import io.github.patelsa032766.gmopayments.domain.DistributionChannel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/checkout")
public final class CheckoutController {
    private final CheckoutEligibilityService eligibilityService;

    public CheckoutController(CheckoutEligibilityService eligibilityService) {
        this.eligibilityService = eligibilityService;
    }

    @GetMapping("/options")
    CheckoutOptionsResponse options(
            @RequestParam(defaultValue = "PA") DistributionChannel channel,
            @RequestParam(defaultValue = "10000") long amountJpy,
            @RequestParam(defaultValue = "true") boolean monthly,
            @RequestParam(defaultValue = "true") boolean ekycVerified,
            @RequestParam(defaultValue = "en") String language) {
        return CheckoutOptionsResponse.from(eligibilityService.findOptions(
                new CheckoutEligibilityQuery(channel, amountJpy, monthly, ekycVerified, language)));
    }
}
