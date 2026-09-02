package io.github.patelsa032766.gmopayments.application.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Redacts secrets and account data before an object crosses a persistence or
 * logging boundary. This class is provider-neutral so outbound API evidence,
 * browser returns, webhooks, and SFTP imports use one policy.
 */
public final class SensitiveDataSanitizer {
    private SensitiveDataSanitizer() {}

    public static Map<String, Object> sanitize(Map<String, ?> input) {
        var output = new LinkedHashMap<String, Object>();
        input.forEach((key, value) -> output.put(key, sanitizeValue(key, value)));
        return output;
    }

    private static Object sanitizeValue(String key, Object value) {
        if (value == null) return null;
        String normalized = key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        if (normalized.contains("pass") || normalized.contains("password") || normalized.contains("token")
                || normalized.equals("authorization") || normalized.contains("securitycode")
                || normalized.equals("cvc") || normalized.equals("cvv") || normalized.contains("accesspass")) {
            return "[REDACTED]";
        }
        if (normalized.contains("cardno") || normalized.contains("accountnumber")) {
            String text = String.valueOf(value);
            return text.length() <= 4 ? "••••" : "••••" + text.substring(text.length() - 4);
        }
        if (value instanceof Map<?, ?> map) {
            var nested = new LinkedHashMap<String, Object>();
            map.forEach((nestedKey, nestedValue) -> nested.put(String.valueOf(nestedKey),
                    sanitizeValue(String.valueOf(nestedKey), nestedValue)));
            return nested;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(item -> sanitizeValue(key, item)).toList();
        }
        return value;
    }
}
