package io.github.patelsa032766.gmopayments.application.port;

import io.github.patelsa032766.gmopayments.domain.ConfigurationMethodUpdate;
import io.github.patelsa032766.gmopayments.domain.ConfigurationRelease;
import io.github.patelsa032766.gmopayments.domain.ConfigurationWorkspace;

import java.util.List;

/** Audited draft/publish boundary for checkout policy configuration. */
public interface ConfigurationAdministrationRepository {
    ConfigurationWorkspace workspace();
    ConfigurationRelease saveDraft(List<ConfigurationMethodUpdate> methods);
    ConfigurationRelease publish(String actor);
    void discardDraft();
}
