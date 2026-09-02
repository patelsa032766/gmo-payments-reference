package io.github.patelsa032766.gmopayments.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.patelsa032766.gmopayments.application.service.BrowserReturnService;
import io.github.patelsa032766.gmopayments.domain.PaymentMethodCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Browser callbacks for GMO's JSON OpenAPI products.
 *
 * <p>GMO sends the opaque {@code p} query parameter as Base64URL-encoded JSON.
 * Its access ID is a locator, not proof that PayPay consent succeeded. The
 * application service always performs an authenticated GMO order inquiry
 * before storing the wallet or authorizing the first premium.</p>
 */
@RestController
@RequestMapping("/api/v1/gmo/returns")
public final class GmoOpenApiReturnController {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};
    private static final int MAX_CALLBACK_ENVELOPE = 2_048;

    private final BrowserReturnService returns;
    private final String customerAppBaseUrl;

    public GmoOpenApiReturnController(BrowserReturnService returns,
                                      @Value("${gmo.customer-app-base-url:http://127.0.0.1:4200}")
                                      String customerAppBaseUrl) {
        this.returns = returns;
        this.customerAppBaseUrl = customerAppBaseUrl.replaceAll("/+$", "");
    }

    @GetMapping("/paypay-registration")
    ResponseEntity<Void> payPayRegistration(@RequestParam("p") String encodedEnvelope) {
        Map<String, Object> fields = decodeEnvelope(encodedEnvelope);
        if (!"WALLET_APPROVAL_FINISHED".equals(fields.get("event"))) {
            throw new IllegalArgumentException("Unexpected GMO PayPay callback event");
        }
        var result = returns.complete(PaymentMethodCode.PAYPAY, fields);
        URI destination = UriComponentsBuilder.fromUriString(customerAppBaseUrl)
                .path("/checkout")
                .queryParam("paymentReturn", result.transactionId())
                .build().encode().toUri();
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .header(HttpHeaders.LOCATION, destination.toString()).build();
    }

    private static Map<String, Object> decodeEnvelope(String encoded) {
        if (encoded == null || encoded.isBlank() || encoded.length() > MAX_CALLBACK_ENVELOPE) {
            throw new IllegalArgumentException("Invalid GMO callback envelope");
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(encoded);
            Map<String, Object> source = JSON.readValue(decoded, MAP);
            Map<String, Object> safe = new LinkedHashMap<>();
            copyBounded(source, safe, "accessId", 128);
            copyBounded(source, safe, "event", 80);
            // Retained only in memory. If csrfToken is configured in future,
            // compare it here before calling the service; never persist it.
            copyBounded(source, safe, "csrfToken", 256);
            if (!safe.containsKey("accessId")) {
                throw new IllegalArgumentException("GMO callback omitted accessId");
            }
            return safe;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Malformed GMO callback envelope", exception);
        }
    }

    private static void copyBounded(Map<String, Object> source, Map<String, Object> target,
                                    String key, int maxLength) {
        Object value = source.get(key);
        if (value == null) return;
        String text = String.valueOf(value);
        if (text.isBlank() || text.length() > maxLength) {
            throw new IllegalArgumentException("Invalid GMO callback field: " + key);
        }
        target.put(key, text);
    }
}
