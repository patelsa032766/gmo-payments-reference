package io.github.patelsa032766.gmopayments.gmo;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;

/**
 * Generates and verifies the merchant CSRF value echoed by GMO OpenAPI.
 *
 * <p>The value is deterministic for an order but does not disclose either the
 * order secret or the shop password. This means it does not need another
 * database column and can be validated after a process restart. GMO limits the
 * field to 36 URL-safe characters, so the HMAC output is truncated to 32.</p>
 */
final class GmoWebhookCsrf {
    private static final String HMAC = "HmacSHA256";
    private static final int TOKEN_LENGTH = 32;

    private GmoWebhookCsrf() {}

    static String create(GmoProperties properties, String orderId) {
        String secret = properties.resolvedWebhookCsrfSecret();
        if (blank(secret) || blank(orderId)) return null;
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC));
            String encoded = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(orderId.getBytes(StandardCharsets.UTF_8)));
            return encoded.substring(0, TOKEN_LENGTH);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to generate GMO webhook CSRF token", exception);
        }
    }

    static boolean matches(GmoProperties properties, Map<String, ?> payload) {
        String orderId = text(payload.get("orderId"));
        Object orderReference = payload.get("orderReference");
        if (orderId == null && orderReference instanceof Map<?, ?> nested) {
            orderId = text(nested.get("orderId"));
        }
        String presented = text(payload.get("csrfToken"));
        String expected = create(properties, orderId);
        if (expected == null || presented == null) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                presented.getBytes(StandardCharsets.UTF_8));
    }

    private static String text(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
