package io.github.patelsa032766.gmopayments.application.service;

import io.github.patelsa032766.gmopayments.application.port.ConfigurationAdministrationRepository;
import io.github.patelsa032766.gmopayments.domain.ConfigurationMethodUpdate;
import io.github.patelsa032766.gmopayments.domain.ConfigurationRelease;
import io.github.patelsa032766.gmopayments.domain.ConfigurationWorkspace;

import java.util.HashSet;
import java.util.List;

/** Validates whole-release invariants before a draft is persisted or published. */
public final class ConfigurationAdministrationService {
    private final ConfigurationAdministrationRepository repository;
    public ConfigurationAdministrationService(ConfigurationAdministrationRepository repository) { this.repository = repository; }
    public ConfigurationWorkspace workspace() { return repository.workspace(); }
    public ConfigurationRelease saveDraft(List<ConfigurationMethodUpdate> methods) {
        if (methods == null || methods.isEmpty()) throw new IllegalArgumentException("A configuration must contain payment methods");
        var codes = new HashSet<>(); var orders = new HashSet<>();
        for (var method : methods) {
            if (!codes.add(method.code())) throw new IllegalArgumentException("Duplicate method " + method.code());
            if (!orders.add(method.displayOrder())) throw new IllegalArgumentException("Duplicate display order " + method.displayOrder());
        }
        return repository.saveDraft(List.copyOf(methods));
    }
    public ConfigurationRelease publish(String actor) {
        if (actor == null || actor.isBlank()) throw new IllegalArgumentException("Publishing actor is required");
        return repository.publish(actor.trim());
    }
    public void discardDraft() { repository.discardDraft(); }
}
