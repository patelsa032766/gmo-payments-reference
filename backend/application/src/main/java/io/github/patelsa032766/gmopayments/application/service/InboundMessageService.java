package io.github.patelsa032766.gmopayments.application.service;

import io.github.patelsa032766.gmopayments.application.port.InboundMessageConfigurationProvider;
import io.github.patelsa032766.gmopayments.application.port.InboundMessageRepository;
import io.github.patelsa032766.gmopayments.application.port.InboundPaymentMessageResolver;
import io.github.patelsa032766.gmopayments.domain.InboundMessageResult;
import io.github.patelsa032766.gmopayments.domain.InboundPaymentMessage;

import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Normalizes the two GMO notification families into one durable command. */
public final class InboundMessageService {
    private final InboundMessageRepository repository;
    private final InboundMessageConfigurationProvider configuration;
    private final InboundPaymentMessageResolver resolver;

    public InboundMessageService(InboundMessageRepository repository,
                                 InboundMessageConfigurationProvider configuration,
                                 InboundPaymentMessageResolver resolver) {
        this.repository = repository;
        this.configuration = configuration;
        this.resolver = resolver;
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
        // The initial hash is only provisional. Some terse GMO notifications
        // (notably CASH_PAID) contain no amount or unique delivery identifier.
        // The provider adapter enriches those messages with an authoritative
        // inquiry before we calculate the durable inbox fingerprint below.
        String payloadHash = sha256(sourceFamily + "\n" + fingerprint(canonical));
        InboundPaymentMessage received = new InboundPaymentMessage(sourceFamily, externalKey, payloadHash,
                orderId, accessId, status, canonical, Instant.now());
        InboundPaymentMessage resolved = resolver.resolve(received);
        String resolvedHash = sha256(sourceFamily + "\n" + fingerprint(resolved.sanitizedPayload()));
        InboundPaymentMessage durable = new InboundPaymentMessage(resolved.sourceFamily(),
                resolved.externalEventKey(), resolvedHash, resolved.providerOrderId(),
                resolved.providerAccessId(), resolved.providerStatus(), resolved.sanitizedPayload(),
                resolved.receivedAt());
        return repository.receive(durable, configuration.webhooksEnabled());
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

    /**
     * Produces an order-stable representation of a sanitized JSON-like value.
     *
     * <p>Map iteration order is not part of JSON semantics, so keys are sorted
     * recursively. Length prefixes keep adjacent values unambiguous without
     * adding a JSON library to the provider-independent application module.</p>
     */
    private static String fingerprint(Object value) {
        if (value == null) return "N";
        if (value instanceof Map<?, ?> map) {
            List<Map.Entry<String, Object>> entries = new ArrayList<>();
            map.forEach((key, entryValue) -> entries.add(
                    new AbstractMap.SimpleImmutableEntry<>(String.valueOf(key), entryValue)));
            entries.sort(Comparator.comparing(Map.Entry::getKey));
            StringBuilder result = new StringBuilder("M").append(entries.size()).append(':');
            entries.forEach(entry -> result.append(sized(entry.getKey())).append(fingerprint(entry.getValue())));
            return result.toString();
        }
        if (value instanceof Collection<?> collection) {
            StringBuilder result = new StringBuilder("L").append(collection.size()).append(':');
            collection.forEach(item -> result.append(fingerprint(item)));
            return result.toString();
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            StringBuilder result = new StringBuilder("A").append(length).append(':');
            for (int index = 0; index < length; index++) result.append(fingerprint(Array.get(value, index)));
            return result.toString();
        }
        return "V" + sized(String.valueOf(value));
    }

    private static String sized(String value) {
        return value.length() + ":" + value;
    }
}
