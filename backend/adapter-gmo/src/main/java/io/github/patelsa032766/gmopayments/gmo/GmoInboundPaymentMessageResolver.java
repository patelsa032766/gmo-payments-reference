package io.github.patelsa032766.gmopayments.gmo;

import io.github.patelsa032766.gmopayments.application.port.InboundPaymentMessageResolver;
import io.github.patelsa032766.gmopayments.domain.InboundPaymentMessage;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Adds authoritative cash totals to GMO's terse {@code CASH_PAID} callback. */
@Component
public final class GmoInboundPaymentMessageResolver implements InboundPaymentMessageResolver {
    private final GmoProperties properties;
    private final GmoRequestFactory requests;
    private final GmoOpenApiClient openApi;

    public GmoInboundPaymentMessageResolver(GmoProperties properties, GmoRequestFactory requests,
                                            GmoOpenApiClient openApi) {
        this.properties = properties;
        this.requests = requests;
        this.openApi = openApi;
    }

    @Override
    public InboundPaymentMessage resolve(InboundPaymentMessage message) {
        if (!properties.isLiveCallsEnabled() || !"OPENAPI".equalsIgnoreCase(message.sourceFamily())
                || !"CASH_PAID".equalsIgnoreCase(message.providerStatus())
                || blank(message.providerAccessId())) return message;

        Map<String, Object> request = requests.orderInquiry(message.providerAccessId());
        GmoHttpResult inquiry = openApi.post("/order/inquiry", request, false);
        long requested = numberAt(inquiry.rawPayload(), "orderReference", "amount");
        long settled = numberAt(inquiry.rawPayload(), "cashResult",
                "bankTransferPaymentInformation", "depositAmount");
        String providerState = textAt(inquiry.rawPayload(), "orderReference", "status");
        String resolvedState = settled > 0 && requested > 0 && settled < requested
                ? "PARTIALLY_PAID"
                : settled >= requested && requested > 0 ? "PAID" : providerState;

        var payload = new LinkedHashMap<>(message.sanitizedPayload());
        payload.put("authoritativeInquiry", inquiry.sanitizedPayload());
        payload.put("requestedAmountJpy", requested);
        payload.put("settledAmountJpy", settled);
        payload.put("remainingAmountJpy", Math.max(0, requested - settled));
        payload.put("resolvedProviderState", resolvedState);
        return new InboundPaymentMessage(message.sourceFamily(), message.externalEventKey(),
                message.payloadHash(), message.providerOrderId(), message.providerAccessId(),
                resolvedState, payload, message.receivedAt());
    }

    @SuppressWarnings("unchecked")
    private static Object at(Map<String, ?> source, String... path) {
        Object current = source;
        for (String key : path) {
            if (!(current instanceof Map<?, ?> map)) return null;
            current = map.get(key);
        }
        return current;
    }

    private static long numberAt(Map<String, ?> source, String... path) {
        Object value = at(source, path);
        if (value instanceof Number number) return number.longValue();
        try { return value == null ? 0 : Long.parseLong(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private static String textAt(Map<String, ?> source, String... path) {
        Object value = at(source, path);
        return value == null ? null : String.valueOf(value);
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
