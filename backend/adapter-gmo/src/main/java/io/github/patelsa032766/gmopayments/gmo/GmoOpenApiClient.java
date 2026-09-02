package io.github.patelsa032766.gmopayments.gmo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final RestClient client;

    public GmoOpenApiClient(GmoProperties properties) {
        this.properties = properties;
        this.client = RestClient.builder().baseUrl(properties.getOpenapiBaseUrl()).build();
    }

    public GmoHttpResult post(String path, Map<String, Object> payload, boolean financialWrite) {
        properties.requireOpenApiCredentials();
        Instant started = Instant.now();
        try {
            var response = client.post().uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        headers.setBasicAuth(properties.getShopId(), properties.getShopPass());
                        if (!properties.getOpenapiVersion().isBlank()) {
                            headers.set("X-MP-Version", properties.getOpenapiVersion());
                        }
                    })
                    .body(payload)
                    .retrieve()
                    .toEntity(byte[].class);
            Map<String, Object> body = decode(response.getBody());
            return new GmoHttpResult(response.getStatusCode().value(),
                    Duration.between(started, Instant.now()).toMillis(), body, GmoSanitizer.sanitize(body));
        } catch (RestClientException exception) {
            // For writes, transport failure is UNKNOWN—not a retry instruction.
            throw new GmoProviderException("GMO OpenAPI request did not complete conclusively", null,
                    financialWrite, !financialWrite, Map.of(), exception);
        }
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
