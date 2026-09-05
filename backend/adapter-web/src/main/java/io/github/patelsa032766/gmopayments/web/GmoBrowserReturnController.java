package io.github.patelsa032766.gmopayments.web;

import io.github.patelsa032766.gmopayments.application.service.BrowserReturnService;
import io.github.patelsa032766.gmopayments.domain.PaymentMethodCode;
import io.github.patelsa032766.gmopayments.domain.PaymentSubmissionResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
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
    private static final int MAX_BODY_BYTES = 16_384;
    private final BrowserReturnService returns;
    private final String customerAppBaseUrl;

    public GmoBrowserReturnController(BrowserReturnService returns,
                                      @Value("${gmo.customer-app-base-url:http://127.0.0.1:4200}")
                                      String customerAppBaseUrl) {
        this.returns = returns;
        this.customerAppBaseUrl = customerAppBaseUrl.replaceAll("/+$", "");
    }

    @PostMapping(path = "/bank-direct", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    ResponseEntity<Void> bankDirect(HttpServletRequest request) throws IOException {
        return complete(PaymentMethodCode.BANK_DIRECT_REALTIME, rawBody(request));
    }

    @PostMapping(path = "/koza-furikae", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    ResponseEntity<Void> kozaFurikae(HttpServletRequest request) throws IOException {
        return complete(PaymentMethodCode.KOZA_FURIKAE_SELECT, rawBody(request));
    }

    /**
     * Read directly from the servlet stream. Using {@code @RequestBody byte[]}
     * is not sufficient for a form content type: Spring's form message
     * converter calls {@code request.getParameterMap()}, which makes Tomcat
     * attempt UTF-8 decoding before our Windows-31J decoder can run.
     */
    private static byte[] rawBody(HttpServletRequest request) throws IOException {
        return request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
    }

    private ResponseEntity<Void> complete(PaymentMethodCode method, byte[] body) {
        Map<String, Object> fields = Windows31JFormDecoder.decode(
                body, MAX_BODY_BYTES, MAX_FIELDS, MAX_VALUE_LENGTH);
        PaymentSubmissionResult result = returns.complete(method, fields);
        URI destination = UriComponentsBuilder.fromUriString(customerAppBaseUrl)
                .path("/checkout")
                .queryParam("paymentReturn", result.transactionId())
                .build().encode().toUri();
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .header(HttpHeaders.LOCATION, destination.toString()).build();
    }

}
