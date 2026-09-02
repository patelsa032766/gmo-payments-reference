package io.github.patelsa032766.gmopayments.application.port;

import io.github.patelsa032766.gmopayments.domain.InboundPaymentMessage;
import io.github.patelsa032766.gmopayments.domain.InboundMessageResult;

/**
 * Durable boundary for asynchronous provider messages.
 *
 * <p>The implementation must deduplicate and persist the message before it
 * reports success.  This is what makes it safe for the web adapter to return
 * GMO's acknowledgement without losing the evidence during a process crash.</p>
 */
public interface InboundMessageRepository {
    InboundMessageResult receive(InboundPaymentMessage message, boolean applyStateChanges);
}
