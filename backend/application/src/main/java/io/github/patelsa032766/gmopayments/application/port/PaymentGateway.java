package io.github.patelsa032766.gmopayments.application.port;

import io.github.patelsa032766.gmopayments.domain.PaymentExecutionContext;
import io.github.patelsa032766.gmopayments.domain.PaymentContinuationResult;
import io.github.patelsa032766.gmopayments.domain.PaymentGatewayResult;

import java.util.Map;

/** Provider execution port. Implementations must never persist or log raw secrets. */
public interface PaymentGateway {
    /**
     * Executes the customer-initiated payment and returns every provider call.
     *
     * <p>A checkout can be more than one provider operation. Card checkout,
     * for example, authorizes the premium and then registers that successful
     * transaction as a reusable card. Keeping the calls together prevents the
     * operator timeline from hiding either half of that business operation.</p>
     */
    PaymentContinuationResult executeCheckout(PaymentExecutionContext context, Map<String, Object> details);
    PaymentGatewayResult executeMit(PaymentExecutionContext context, Map<String, Object> instrumentFacts,
                                    Map<String, Object> commandDetails);
    PaymentGatewayResult executeKozaDebit(PaymentExecutionContext context, Map<String,Object> instrumentFacts,
                                          String targetDate, String remarks);

    /** Completes a registration-based checkout after the customer returns from GMO. */
    PaymentContinuationResult continueCheckout(PaymentExecutionContext context,
                                               Map<String, Object> browserReturn);
}
