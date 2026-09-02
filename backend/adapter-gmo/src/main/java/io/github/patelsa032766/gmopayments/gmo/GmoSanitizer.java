package io.github.patelsa032766.gmopayments.gmo;

import io.github.patelsa032766.gmopayments.application.service.SensitiveDataSanitizer;

import java.util.Map;

/** Central allow-safe persistence sanitizer for provider evidence. */
public final class GmoSanitizer {
    private GmoSanitizer() {}

    public static Map<String, Object> sanitize(Map<String, ?> input) {
        return SensitiveDataSanitizer.sanitize(input);
    }
}
