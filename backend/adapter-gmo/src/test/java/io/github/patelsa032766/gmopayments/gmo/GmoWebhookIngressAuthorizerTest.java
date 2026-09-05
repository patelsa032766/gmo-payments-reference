package io.github.patelsa032766.gmopayments.gmo;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GmoWebhookIngressAuthorizerTest {

    @Test
    void acceptsAnOrderBoundOpenApiCsrfTokenWithoutAnEdgeHeader() {
        GmoProperties properties = enabledProperties();
        var authorizer = new GmoWebhookIngressAuthorizer(properties);
        String csrf = GmoWebhookCsrf.create(properties, "ORDER-123");

        assertThat(authorizer.authorizedOpenApi(null, Map.of(
                "csrfToken", csrf,
                "orderReference", Map.of("orderId", "ORDER-123")))).isTrue();
        assertThat(authorizer.authorizedOpenApi(null, Map.of(
                "csrfToken", csrf,
                "orderReference", Map.of("orderId", "ORDER-OTHER")))).isFalse();
    }

    @Test
    void protocolNotificationsStillRequireTheTrustedEdgeToken() {
        GmoProperties properties = enabledProperties();
        properties.setWebhookIngressToken("edge-secret");
        var authorizer = new GmoWebhookIngressAuthorizer(properties);

        assertThat(authorizer.authorizedProtocol(null)).isFalse();
        assertThat(authorizer.authorizedProtocol("wrong")).isFalse();
        assertThat(authorizer.authorizedProtocol("edge-secret")).isTrue();
    }

    private static GmoProperties enabledProperties() {
        GmoProperties properties = new GmoProperties();
        properties.setWebhooksEnabled(true);
        properties.setWebhookCsrfSecret("unit-test-secret");
        return properties;
    }
}
