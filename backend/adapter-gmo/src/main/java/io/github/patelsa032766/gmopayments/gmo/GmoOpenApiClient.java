package io.github.patelsa032766.gmopayments.gmo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.patelsa032766.gmopayments.domain.ProviderCallEvidence;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/** Minimal GMO OpenAPI transport shared by Card, PayPay, and cash adapters. */
@Component
public class GmoOpenApiClient {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};
    private final GmoProperties properties;
    private final GmoSafeReadRetryExecutor retries;
    private final RestClient client;

    public GmoOpenApiClient(GmoProperties properties, GmoSafeReadRetryExecutor retries) {
        this.properties = properties;
        this.retries = retries;
        this.client = RestClient.builder().baseUrl(properties.getOpenapiBaseUrl()).build();
    }

    public GmoHttpResult post(String path, Map<String, Object> payload, boolean financialWrite) {
        return post(path, payload, financialWrite, null);
    }

    public GmoHttpResult post(String path, Map<String, Object> payload, boolean financialWrite,
                              String idempotencyKey) {
        properties.requireOpenApiCredentials();
        if (financialWrite) return invoke(path, payload, true, idempotencyKey);
        return retries.execute(() -> invoke(path, payload, false, null));
    }

    private GmoHttpResult invoke(String path, Map<String, Object> payload, boolean financialWrite,
                                 String idempotencyKey) {
        Instant started = Instant.now();
        try {
            return client.post().uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        headers.setBasicAuth(properties.getShopId(), properties.getShopPass());
                        if (!properties.getOpenapiVersion().isBlank()) {
                            headers.set("X-MP-Version", properties.getOpenapiVersion());
                        }
                        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                            headers.set("Idempotency-Key", idempotencyKey);
                        }
                    })
                    .body(payload)
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        Map<String, Object> body = decode(response.getBody().readAllBytes());
                        long duration = Duration.between(started, Instant.now()).toMillis();
                        if (status < 200 || status >= 300) {
                            boolean transientResponse = status == 429 || status == 502
                                    || status == 503 || status == 504;
                            var evidence = new ProviderCallEvidence("OPENAPI", operation(path), path,
                                    status, safeInt(duration), GmoSanitizer.sanitize(payload),
                                    GmoSanitizer.sanitize(body), financialWrite && status >= 500
                                            ? "OUTCOME_UNKNOWN" : "REJECTED");
                            throw new GmoProviderException("GMO OpenAPI returned HTTP " + status,
                                    status, financialWrite && status >= 500,
                                    !financialWrite && transientResponse,
                                    GmoSanitizer.sanitize(body), evidence, null);
                        }
                        return new GmoHttpResult(status, duration, body, GmoSanitizer.sanitize(body));
                    });
        } catch (GmoProviderException exception) {
            throw exception;
        } catch (RestClientException exception) {
            // For writes, transport failure is UNKNOWN—not a retry instruction.
            var evidence = new ProviderCallEvidence("OPENAPI", operation(path), path,
                    null, safeInt(Duration.between(started, Instant.now()).toMillis()),
                    GmoSanitizer.sanitize(payload), Map.of(),
                    financialWrite ? "OUTCOME_UNKNOWN" : "TRANSPORT_FAILURE");
            throw new GmoProviderException("GMO OpenAPI request did not complete conclusively", null,
                    financialWrite, !financialWrite, Map.of(), evidence, exception);
        }
    }

    private static String operation(String path) {
        String[] parts = path.replaceFirst("^/", "").split("/");
        StringBuilder name = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            name.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1).replace("-", ""));
        }
        return name.toString();
    }

    private static int safeInt(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private static Map<String, Object> decode(byte[] body) {
        if (body == null || body.length == 0) return Map.of();
        try {
            return JSON.readValue(body, MAP);
        } catch (Exception exception) {
            return Map.of("unparsedResponse", "[NON_JSON_RESPONSE]");
        }
    }
}
