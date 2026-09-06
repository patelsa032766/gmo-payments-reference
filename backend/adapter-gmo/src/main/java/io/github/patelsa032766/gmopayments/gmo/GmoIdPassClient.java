package io.github.patelsa032766.gmopayments.gmo;

import io.github.patelsa032766.gmopayments.domain.ProviderCallEvidence;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GMO idPass form transport.
 *
 * <p>The protocol requires Windows-31J/Shift_JIS for Japanese fields. Values
 * are encoded into an ASCII form body before Spring sees it, avoiding an HTTP
 * converter silently re-encoding the body as UTF-8.</p>
 */
@Component
public class GmoIdPassClient {
    private static final Charset WINDOWS_31J = Charset.forName("Windows-31J");
    private static final MediaType WINDOWS_31J_FORM =
            MediaType.parseMediaType("application/x-www-form-urlencoded; charset=windows-31j");
    private static final MediaType SHIFT_JIS_TEXT =
            MediaType.parseMediaType("text/plain; charset=Shift_JIS");
    private final GmoProperties properties;
    private final GmoSafeReadRetryExecutor retries;
    private final RestClient client;

    public GmoIdPassClient(GmoProperties properties, GmoSafeReadRetryExecutor retries) {
        this.properties = properties;
        this.retries = retries;
        this.client = RestClient.builder().baseUrl(properties.getProtocolBaseUrl()).build();
    }

    public GmoHttpResult post(String operation, Map<String, String> fields, boolean financialWrite) {
        properties.requireProtocolCredentials();
        if (financialWrite) return invoke(operation, fields, true);
        return retries.execute(() -> invoke(operation, fields, false));
    }

    private GmoHttpResult invoke(String operation, Map<String, String> fields, boolean financialWrite) {
        Instant started = Instant.now();
        try {
            byte[] body = encode(fields).getBytes(StandardCharsets.US_ASCII);
            return client.post().uri("/" + operation.replaceFirst("^/", ""))
                    .contentType(WINDOWS_31J_FORM)
                    .accept(SHIFT_JIS_TEXT)
                    .body(body)
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        Map<String, Object> parsed = parseResponse(response.getBody().readAllBytes());
                        if (status < 200 || status >= 300) {
                            boolean transientResponse = status == 429 || status == 502
                                    || status == 503 || status == 504;
                            var evidence = evidence(operation, status,
                                    Duration.between(started, Instant.now()).toMillis(), fields, parsed,
                                    financialWrite && status >= 500 ? "OUTCOME_UNKNOWN" : "REJECTED");
                            throw new GmoProviderException("GMO idPass returned HTTP " + status,
                                    status, financialWrite && status >= 500,
                                    !financialWrite && transientResponse,
                                    GmoSanitizer.sanitize(parsed), evidence, null);
                        }
                        if (parsed.containsKey("ErrCode") || parsed.containsKey("ErrInfo")) {
                            var evidence = evidence(operation, status,
                                    Duration.between(started, Instant.now()).toMillis(), fields, parsed,
                                    "REJECTED");
                            throw new GmoProviderException("GMO idPass returned a provider error",
                                    status, false, false, GmoSanitizer.sanitize(parsed), evidence, null);
                        }
                        return new GmoHttpResult(status,
                                Duration.between(started, Instant.now()).toMillis(), parsed,
                                GmoSanitizer.sanitize(parsed));
                    });
        } catch (GmoProviderException exception) {
            throw exception;
        } catch (RestClientException exception) {
            var evidence = evidence(operation, null,
                    Duration.between(started, Instant.now()).toMillis(), fields, Map.of(),
                    financialWrite ? "OUTCOME_UNKNOWN" : "TRANSPORT_FAILURE");
            throw new GmoProviderException("GMO idPass request did not complete conclusively", null,
                    financialWrite, !financialWrite, Map.of(), evidence, exception);
        }
    }

    private static ProviderCallEvidence evidence(String operation, Integer status, long duration,
                                                  Map<String, String> request, Map<String, Object> response,
                                                  String outcome) {
        return new ProviderCallEvidence("IDPASS", operation.replace(".idPass", ""), operation,
                status, duration > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) duration,
                GmoSanitizer.sanitize(request), GmoSanitizer.sanitize(response), outcome);
    }

    private static String encode(Map<String, String> fields) {
        return fields.entrySet().stream()
                .map(entry -> urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right).orElse("");
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, WINDOWS_31J);
    }

    /**
     * Parse GMO's idPass response without corrupting a Koza handoff token.
     *
     * <p>Most idPass responses resemble an application/x-www-form-urlencoded
     * body, where a literal plus normally means a space. BankAccountEntry is a
     * provider-specific exception: GMO's own successful example returns an
     * unescaped Base64-like {@code Token} containing literal plus characters.
     * URLDecoder would silently turn those characters into spaces and
     * BankAccountStart would then reject the browser handoff with M01074090.
     * Preserve plus only for the Token field; all other response fields retain
     * ordinary form-decoding semantics.</p>
     */
    static Map<String, Object> parseResponse(byte[] response) {
        if (response == null || response.length == 0) return Map.of();
        String text = new String(response, WINDOWS_31J).trim();
        var parsed = new LinkedHashMap<String, Object>();
        for (String pair : text.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], WINDOWS_31J);
            String rawValue = parts.length == 2 ? parts[1] : "";
            if ("Token".equalsIgnoreCase(key)) rawValue = rawValue.replace("+", "%2B");
            parsed.put(key, URLDecoder.decode(rawValue, WINDOWS_31J));
        }
        return parsed;
    }
}
