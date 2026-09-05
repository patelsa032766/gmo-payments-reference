package io.github.patelsa032766.gmopayments.gmo;

import io.github.patelsa032766.gmopayments.application.port.WebhookIngressAuthorizer;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/** Constant-time validation of the secret injected by the trusted edge. */
@Component
public final class GmoWebhookIngressAuthorizer implements WebhookIngressAuthorizer {
    private final GmoProperties properties;

    public GmoWebhookIngressAuthorizer(GmoProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean enabled() {
        return properties.isWebhooksEnabled();
    }

    @Override
    public boolean authorizedOpenApi(String presentedToken, Map<String, ?> payload) {
        return enabled() && (authorizedEdgeToken(presentedToken)
                || GmoWebhookCsrf.matches(properties, payload));
    }

    @Override
    public boolean authorizedProtocol(String presentedToken) {
        return enabled() && authorizedEdgeToken(presentedToken);
    }

    private boolean authorizedEdgeToken(String presentedToken) {
        String expected = properties.getWebhookIngressToken();
        if (expected == null || expected.isBlank()
                || presentedToken == null || presentedToken.isBlank()) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                presentedToken.getBytes(StandardCharsets.UTF_8));
    }
}
