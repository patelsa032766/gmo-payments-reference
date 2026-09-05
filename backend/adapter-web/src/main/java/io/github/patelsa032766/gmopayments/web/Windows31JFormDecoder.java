package io.github.patelsa032766.gmopayments.web;

import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Decodes GMO idPass browser-return forms without asking the servlet container
 * to interpret their bytes as UTF-8.
 *
 * <p>GMO's legacy protocol percent-encodes Japanese values with Windows-31J
 * (the Java-compatible superset of Shift_JIS). Spring's ordinary
 * {@code MultiValueMap} binding delegates form parsing to Tomcat, whose UTF-8
 * decoder rejects those bytes before a controller can inspect the request.
 * Reading the raw body and decoding each form component here preserves the
 * provider contract while leaving every JSON/API endpoint UTF-8.</p>
 */
final class Windows31JFormDecoder {
    private static final Charset WINDOWS_31J = Charset.forName("Windows-31J");

    private Windows31JFormDecoder() {}

    static Map<String, Object> decode(byte[] body, int maxBodyBytes,
                                      int maxFields, int maxValueLength) {
        byte[] safeBody = body == null ? new byte[0] : body;
        if (safeBody.length > maxBodyBytes) {
            throw new IllegalArgumentException("Browser-return form is too large");
        }

        // application/x-www-form-urlencoded bodies are ASCII on the wire:
        // non-ASCII Windows-31J bytes appear as %HH sequences.
        String encoded = new String(safeBody, StandardCharsets.US_ASCII);
        Map<String, Object> values = new LinkedHashMap<>();
        if (encoded.isEmpty()) return values;

        for (String pair : encoded.split("&", -1)) {
            if (values.size() >= maxFields) {
                throw new IllegalArgumentException("Too many browser-return fields");
            }
            String[] parts = pair.split("=", 2);
            String key = decodeComponent(parts[0]);
            String value = decodeComponent(parts.length == 2 ? parts[1] : "");
            if (key.isBlank() || key.length() > 80) {
                throw new IllegalArgumentException("Invalid browser-return field name");
            }
            if (value.length() > maxValueLength) {
                throw new IllegalArgumentException("Browser-return field is too long: " + key);
            }
            // Form semantics use the last occurrence, matching Flask's
            // request.form.to_dict(flat=True) behavior in the working sample.
            values.put(key, value);
        }
        return values;
    }

    private static String decodeComponent(String value) {
        try {
            return URLDecoder.decode(value, WINDOWS_31J);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Malformed GMO browser-return form", exception);
        }
    }
}
