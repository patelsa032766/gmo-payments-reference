package io.github.patelsa032766.gmopayments.gmo;

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
    private final GmoProperties properties;
    private final RestClient client;

    public GmoIdPassClient(GmoProperties properties) {
        this.properties = properties;
        this.client = RestClient.builder().baseUrl(properties.getProtocolBaseUrl()).build();
    }

    public GmoHttpResult post(String operation, Map<String, String> fields, boolean financialWrite) {
        properties.requireProtocolCredentials();
        Instant started = Instant.now();
        try {
            byte[] body = encode(fields).getBytes(StandardCharsets.US_ASCII);
            var response = client.post().uri("/" + operation.replaceFirst("^/", ""))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve().toEntity(byte[].class);
            Map<String, Object> parsed = parse(response.getBody());
            if (parsed.containsKey("ErrCode") || parsed.containsKey("ErrInfo")) {
                throw new GmoProviderException("GMO idPass returned a provider error",
                        response.getStatusCode().value(), false, false, GmoSanitizer.sanitize(parsed), null);
            }
            return new GmoHttpResult(response.getStatusCode().value(),
                    Duration.between(started, Instant.now()).toMillis(), parsed, GmoSanitizer.sanitize(parsed));
        } catch (GmoProviderException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new GmoProviderException("GMO idPass request did not complete conclusively", null,
                    financialWrite, !financialWrite, Map.of(), exception);
        }
    }

    private static String encode(Map<String, String> fields) {
        return fields.entrySet().stream()
                .map(entry -> urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right).orElse("");
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, WINDOWS_31J);
    }

    private static Map<String, Object> parse(byte[] response) {
        if (response == null || response.length == 0) return Map.of();
        String text = new String(response, WINDOWS_31J).trim();
        var parsed = new LinkedHashMap<String, Object>();
        for (String pair : text.split("&")) {
            String[] parts = pair.split("=", 2);
            parsed.put(URLDecoder.decode(parts[0], WINDOWS_31J),
                    URLDecoder.decode(parts.length == 2 ? parts[1] : "", WINDOWS_31J));
        }
        return parsed;
    }
}
