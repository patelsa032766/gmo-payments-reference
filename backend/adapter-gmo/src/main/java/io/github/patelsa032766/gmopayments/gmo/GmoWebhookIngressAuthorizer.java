package io.github.patelsa032766.gmopayments.gmo;

import io.github.patelsa032766.gmopayments.application.port.WebhookIngressAuthorizer;
import io.github.patelsa032766.gmopayments.application.port.WebhookOrderReferenceLookup;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/** Constant-time validation of the secret injected by the trusted edge. */
@Component
public final class GmoWebhookIngressAuthorizer implements WebhookIngressAuthorizer {
    private final GmoProperties properties;
    private final WebhookOrderReferenceLookup orderReferences;

    public GmoWebhookIngressAuthorizer(GmoProperties properties,
                                       WebhookOrderReferenceLookup orderReferences) {
        this.properties = properties;
        this.orderReferences = orderReferences;
    }

    @Override
    public boolean enabled() {
        return properties.isWebhooksEnabled();
    }

    @Override
    public boolean authorizedOpenApi(String presentedToken, Map<String, ?> payload) {
        if (!enabled()) return false;
        if (authorizedEdgeToken(presentedToken)) return true;
        String accessId = text(payload.get("accessId"));
        String resolvedOrderId = accessId == null ? null
                : orderReferences.findProviderOrderId(accessId).orElse(null);
        return GmoWebhookCsrf.matches(properties, payload, resolvedOrderId);
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

    private static String text(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
