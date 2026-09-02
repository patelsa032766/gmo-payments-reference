package io.github.patelsa032766.gmopayments.application.port;

/** Provides deployment-owned webhook behavior without exposing credentials. */
public interface InboundMessageConfigurationProvider {
    boolean webhooksEnabled();
}
