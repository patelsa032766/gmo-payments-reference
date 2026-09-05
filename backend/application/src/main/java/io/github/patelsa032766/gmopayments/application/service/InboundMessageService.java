package io.github.patelsa032766.gmopayments.application.service;

import io.github.patelsa032766.gmopayments.application.port.InboundMessageConfigurationProvider;
import io.github.patelsa032766.gmopayments.application.port.InboundMessageRepository;
import io.github.patelsa032766.gmopayments.domain.InboundMessageResult;
import io.github.patelsa032766.gmopayments.domain.InboundPaymentMessage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/** Normalizes the two GMO notification families into one durable command. */
public final class InboundMessageService {
    private final InboundMessageRepository repository;
    private final InboundMessageConfigurationProvider configuration;

    public InboundMessageService(InboundMessageRepository repository,
                                 InboundMessageConfigurationProvider configuration) {
        this.repository = repository;
        this.configuration = configuration;
    }

    public InboundMessageResult receive(String sourceFamily, Map<String, ?> rawPayload) {
        Map<String, Object> sanitized = SensitiveDataSanitizer.sanitize(rawPayload);
        String orderId = firstText(rawPayload, "OrderID", "orderId", "OrderId");
        String accessId = firstText(rawPayload, "TranID", "AccessID", "accessId", "transactionId");
        String status = firstText(rawPayload, "Status", "status", "resultStatus");
        // GMO's OpenAPI cash and wallet webhooks identify their outcome in
        // `event` rather than `status` (for example CASH_PAID). Preserve that
        // provider vocabulary and let the persistence projection normalize it
        // into the application's canonical lifecycle state.
        if (status == null) status = firstText(rawPayload, "event", "Event");

        Object nestedReference = rawPayload.get("orderReference");
        if (nestedReference instanceof Map<?, ?> nested) {
            if (orderId == null) orderId = text(nested.get("orderId"));
            if (accessId == null) accessId = text(nested.get("accessId"));
            if (status == null) status = text(nested.get("status"));
        }
        String externalKey = firstText(rawPayload, "notificationId", "id");
        if (externalKey == null) externalKey = accessId != null ? accessId : orderId;

        // LinkedHashMap gives a stable top-level order before hashing. Duplicate
        // callbacks with the same semantic body therefore resolve to one row.
        Map<String, Object> canonical = new LinkedHashMap<>();
        sanitized.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> canonical.put(entry.getKey(), entry.getValue()));
        String payloadHash = sha256(sourceFamily + "\n" + canonical);
        return repository.receive(new InboundPaymentMessage(sourceFamily, externalKey, payloadHash,
                orderId, accessId, status, canonical, Instant.now()), configuration.webhooksEnabled());
    }

    private static String firstText(Map<String, ?> payload, String... keys) {
        for (String key : keys) {
            String value = text(payload.get(key));
            if (value != null) return value;
        }
        return null;
    }

    private static String text(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
