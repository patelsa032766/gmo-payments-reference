package io.github.patelsa032766.gmopayments.application.service;

import io.github.patelsa032766.gmopayments.application.port.InboundMessageRepository;
import io.github.patelsa032766.gmopayments.domain.InboundMessageResult;
import io.github.patelsa032766.gmopayments.domain.InboundPaymentMessage;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class InboundMessageServiceTest {

    @Test
    void identicalCashCallbacksAreDistinctWhenAuthoritativeCumulativeAmountChanges() {
        var repository = new HashDeduplicatingRepository();
        var settledAmount = new AtomicLong(5_000);
        var service = new InboundMessageService(repository, () -> true, message -> {
            Map<String, Object> enriched = new LinkedHashMap<>(message.sanitizedPayload());
            enriched.put("authoritativeInquiry", Map.of(
                    "orderReference", Map.of("amount", "20000", "status", "TRADING"),
                    "cashResult", Map.of("depositAmount", String.valueOf(settledAmount.get()))));
            enriched.put("requestedAmountJpy", 20_000L);
            enriched.put("settledAmountJpy", settledAmount.get());
            return new InboundPaymentMessage(message.sourceFamily(), message.externalEventKey(),
                    message.payloadHash(), message.providerOrderId(), message.providerAccessId(),
                    settledAmount.get() < 20_000 ? "PARTIALLY_PAID" : "PAID", enriched,
                    message.receivedAt());
        });
        Map<String, Object> terseCallback = Map.of(
                "accessId", "access-123", "event", "CASH_PAID", "csrfToken", "secret");

        InboundMessageResult firstDeposit = service.receive("OPENAPI", terseCallback);
        settledAmount.set(20_000);
        InboundMessageResult completedDeposit = service.receive("OPENAPI", terseCallback);
        InboundMessageResult providerRetry = service.receive("OPENAPI", terseCallback);

        assertThat(firstDeposit.duplicate()).isFalse();
        assertThat(completedDeposit.duplicate()).isFalse();
        assertThat(providerRetry.duplicate()).isTrue();
        assertThat(repository.messages).hasSize(2);
    }

    private static final class HashDeduplicatingRepository implements InboundMessageRepository {
        private final Map<String, InboundPaymentMessage> messages = new LinkedHashMap<>();

        @Override
        public InboundMessageResult receive(InboundPaymentMessage message, boolean applyStateChanges) {
            boolean duplicate = messages.putIfAbsent(message.payloadHash(), message) != null;
            return new InboundMessageResult("message-" + messages.size(), duplicate, true,
                    applyStateChanges, "transaction-1", "APPLIED");
        }
    }
}
