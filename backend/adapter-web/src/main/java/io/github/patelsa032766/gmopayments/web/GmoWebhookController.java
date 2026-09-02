package io.github.patelsa032766.gmopayments.web;

import io.github.patelsa032766.gmopayments.application.port.WebhookIngressAuthorizer;
import io.github.patelsa032766.gmopayments.application.service.InboundMessageService;
import io.github.patelsa032766.gmopayments.domain.InboundMessageResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public callback endpoints protected by a secret injected at the trusted edge.
 * GMO's legacy idPass notification does not provide a signed envelope, so the
 * application never accepts it directly from an unauthenticated public route.
 */
@RestController
@RequestMapping("/webhooks/gmo")
public final class GmoWebhookController {
    public static final String INGRESS_HEADER = "X-Webhook-Ingress-Token";
    private final InboundMessageService inboundMessages;
    private final WebhookIngressAuthorizer authorizer;

    public GmoWebhookController(InboundMessageService inboundMessages, WebhookIngressAuthorizer authorizer) {
        this.inboundMessages = inboundMessages;
        this.authorizer = authorizer;
    }

    @PostMapping(path = "/openapi", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> receiveOpenApi(
            @RequestHeader(name = INGRESS_HEADER, required = false) String token,
            @RequestBody(required = false) Map<String, Object> payload) {
        authorize(token);
        InboundMessageResult result = inboundMessages.receive("OPENAPI", payload == null ? Map.of() : payload);
        return Map.of("ok", true, "messageId", result.messageId(), "duplicate", result.duplicate(),
                "linked", result.linked(), "applied", result.applied());
    }

    @PostMapping(path = "/protocol", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> receiveProtocol(
            @RequestHeader(name = INGRESS_HEADER, required = false) String token,
            @RequestBody MultiValueMap<String, String> form) {
        authorize(token);
        Map<String, Object> payload = new LinkedHashMap<>();
        form.forEach((key, values) -> payload.put(key,
                values == null || values.isEmpty() ? "" : values.get(values.size() - 1)));
        inboundMessages.receive("IDPASS", payload);
        // GMO requires the literal body "0", returned only after durable receipt.
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body("0");
    }

    private void authorize(String token) {
        if (!authorizer.enabled()) {
            // Do not advertise a disabled callback route to opportunistic scans.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (!authorizer.authorized(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid webhook ingress credential");
        }
    }
}
