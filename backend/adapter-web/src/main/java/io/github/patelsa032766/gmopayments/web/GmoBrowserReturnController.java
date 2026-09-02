package io.github.patelsa032766.gmopayments.web;

import io.github.patelsa032766.gmopayments.application.service.BrowserReturnService;
import io.github.patelsa032766.gmopayments.domain.PaymentMethodCode;
import io.github.patelsa032766.gmopayments.domain.PaymentSubmissionResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Customer-browser return routes for registration products.
 *
 * <p>These routes are intentionally separate from webhook ingestion. They do
 * not require the edge webhook token because a customer's browser must reach
 * them; instead, the use case links a high-entropy provider transaction ID and
 * performs a server-to-server GMO inquiry before changing financial state.</p>
 */
@RestController
@RequestMapping("/webhooks/gmo/protocol/return")
public final class GmoBrowserReturnController {
    private static final int MAX_FIELDS = 32;
    private static final int MAX_VALUE_LENGTH = 512;
    private final BrowserReturnService returns;
    private final String customerAppBaseUrl;

    public GmoBrowserReturnController(BrowserReturnService returns,
                                      @Value("${gmo.customer-app-base-url:http://127.0.0.1:4200}")
                                      String customerAppBaseUrl) {
        this.returns = returns;
        this.customerAppBaseUrl = customerAppBaseUrl.replaceAll("/+$", "");
    }

    @PostMapping(path = "/bank-direct", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    ResponseEntity<Void> bankDirect(@RequestBody MultiValueMap<String, String> form) {
        return complete(PaymentMethodCode.BANK_DIRECT_REALTIME, form);
    }

    @PostMapping(path = "/koza-furikae", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    ResponseEntity<Void> kozaFurikae(@RequestBody MultiValueMap<String, String> form) {
        return complete(PaymentMethodCode.KOZA_FURIKAE_SELECT, form);
    }

    private ResponseEntity<Void> complete(PaymentMethodCode method, MultiValueMap<String, String> form) {
        Map<String, Object> fields = safeFields(form);
        PaymentSubmissionResult result = returns.complete(method, fields);
        URI destination = UriComponentsBuilder.fromUriString(customerAppBaseUrl)
                .path("/checkout")
                .queryParam("paymentReturn", result.transactionId())
                .build().encode().toUri();
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .header(HttpHeaders.LOCATION, destination.toString()).build();
    }

    private static Map<String, Object> safeFields(MultiValueMap<String, String> form) {
        if (form.size() > MAX_FIELDS) throw new IllegalArgumentException("Too many browser-return fields");
        Map<String, Object> values = new LinkedHashMap<>();
        form.forEach((key, candidates) -> {
            if (key == null || key.isBlank() || key.length() > 80) {
                throw new IllegalArgumentException("Invalid browser-return field name");
            }
            String value = candidates == null || candidates.isEmpty() ? "" : candidates.getLast();
            if (value != null && value.length() > MAX_VALUE_LENGTH) {
                throw new IllegalArgumentException("Browser-return field is too long: " + key);
            }
            values.put(key, value == null ? "" : value);
        });
        return values;
    }
}
