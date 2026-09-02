package io.github.patelsa032766.gmopayments.gmo;

import io.github.patelsa032766.gmopayments.application.port.InboundMessageConfigurationProvider;
import org.springframework.stereotype.Component;

/** Exposes only the webhook switch, never GMO credentials, to the use case. */
@Component
public final class GmoInboundMessageConfigurationProvider implements InboundMessageConfigurationProvider {
    private final GmoProperties properties;

    public GmoInboundMessageConfigurationProvider(GmoProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean webhooksEnabled() {
        return properties.isWebhooksEnabled();
    }
}
