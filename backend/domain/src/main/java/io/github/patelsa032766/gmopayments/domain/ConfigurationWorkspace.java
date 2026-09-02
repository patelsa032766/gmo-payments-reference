package io.github.patelsa032766.gmopayments.domain;

/** Active release plus the administrator's optional unpublished draft. */
public record ConfigurationWorkspace(ConfigurationRelease active, ConfigurationRelease draft) {}
