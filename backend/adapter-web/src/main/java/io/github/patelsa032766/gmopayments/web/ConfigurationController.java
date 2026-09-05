package io.github.patelsa032766.gmopayments.web;

import io.github.patelsa032766.gmopayments.application.service.CheckoutEligibilityService;
import io.github.patelsa032766.gmopayments.domain.ConfigurationRelease;
import io.github.patelsa032766.gmopayments.domain.ConfigurationMethodUpdate;
import io.github.patelsa032766.gmopayments.domain.PaymentMethodCode;
import io.github.patelsa032766.gmopayments.application.service.ConfigurationAdministrationService;
import io.github.patelsa032766.gmopayments.application.port.OperatorActionAuthorizer;
import io.github.patelsa032766.gmopayments.application.service.CheckoutExperienceService;
import io.github.patelsa032766.gmopayments.domain.CheckoutExperienceSettings;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/configuration")
public final class ConfigurationController {
    private final CheckoutEligibilityService eligibilityService;
    private final ConfigurationAdministrationService administration;
    private final OperatorActionAuthorizer authorizer;
    private final CheckoutExperienceService experience;

    public ConfigurationController(CheckoutEligibilityService eligibilityService,
                                   ConfigurationAdministrationService administration,
                                   OperatorActionAuthorizer authorizer,
                                   CheckoutExperienceService experience) {
        this.eligibilityService = eligibilityService;
        this.administration = administration;
        this.authorizer = authorizer;
        this.experience = experience;
    }

    @GetMapping("/active")
    ActiveConfigurationResponse active() {
        return ActiveConfigurationResponse.from(eligibilityService.activeConfiguration());
    }

    @GetMapping("/workspace") WorkspaceResponse workspace() {
        var workspace = administration.workspace();
        return new WorkspaceResponse(ActiveConfigurationResponse.from(workspace.active()),
                workspace.draft() == null ? null : ActiveConfigurationResponse.from(workspace.draft()));
    }

    @GetMapping("/experience") CheckoutExperienceSettings experience() { return experience.get(); }

    @PutMapping("/experience") CheckoutExperienceSettings updateExperience(
            @RequestHeader(name="X-Operator-Token",required=false) String token,
            @RequestBody ExperienceRequest request) {
        authorizeConfiguration(token);
        return experience.update(request.applicationNumber(),request.amountJpy(),
                request.configurationTokenRequired(),request.checkoutLanguage());
    }

    @PutMapping("/draft") ActiveConfigurationResponse saveDraft(
            @RequestHeader(name="X-Operator-Token", required=false) String token,
            @RequestBody DraftRequest request) {
        authorizeConfiguration(token);
        var methods=request.methods().stream().map(item -> new ConfigurationMethodUpdate(
                PaymentMethodCode.fromApiValue(item.code()), item.enabled(), item.recurring(), item.monthlyOnly(),
                item.minimumAmountJpy(), item.maximumAmountJpy(), item.displayOrder(),
                io.github.patelsa032766.gmopayments.domain.PaymentExecutionMode.from(item.citExecutionMode()))).toList();
        return ActiveConfigurationResponse.from(administration.saveDraft(methods));
    }

    @PostMapping("/draft/publish") ActiveConfigurationResponse publish(
            @RequestHeader(name="X-Operator-Token", required=false) String token,
            @RequestHeader(name="X-Operator-Id", defaultValue="configuration-administrator") String actor) {
        authorizeConfiguration(token); return ActiveConfigurationResponse.from(administration.publish(actor));
    }

    @DeleteMapping("/draft") void discard(
            @RequestHeader(name="X-Operator-Token", required=false) String token) {
        authorizeConfiguration(token); administration.discardDraft();
    }

    private void authorizeConfiguration(String token) {
        if (!experience.get().configurationTokenRequired()) return;
        if(!authorizer.authorized(token)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "A valid operator credential is required");
    }

    record WorkspaceResponse(ActiveConfigurationResponse active, ActiveConfigurationResponse draft) {}
    record DraftRequest(List<ConfiguredMethodResponse> methods) {}
    record ExperienceRequest(String applicationNumber,long amountJpy,
                             boolean configurationTokenRequired,String checkoutLanguage) {}

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
            int displayOrder,
            String citExecutionMode) {
        static ConfiguredMethodResponse from(io.github.patelsa032766.gmopayments.domain.PaymentMethodConfiguration method) {
            return new ConfiguredMethodResponse(
                    method.code().apiValue(), method.enabled(), method.recurring(), method.monthlyOnly(),
                    method.minimumAmountJpy(), method.maximumAmountJpy(), method.displayOrder(),
                    method.citExecutionMode().name());
        }
    }
}
