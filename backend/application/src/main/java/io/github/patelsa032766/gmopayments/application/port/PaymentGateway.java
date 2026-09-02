package io.github.patelsa032766.gmopayments.application.port;

import io.github.patelsa032766.gmopayments.domain.PaymentExecutionContext;
import io.github.patelsa032766.gmopayments.domain.PaymentGatewayResult;

import java.util.Map;

/** Provider execution port. Implementations must never persist or log raw secrets. */
public interface PaymentGateway {
    PaymentGatewayResult executeCheckout(PaymentExecutionContext context, Map<String, Object> details);
    PaymentGatewayResult executeMit(PaymentExecutionContext context, Map<String, Object> instrumentFacts,
                                    Map<String, Object> commandDetails);
    PaymentGatewayResult executeKozaDebit(PaymentExecutionContext context, Map<String,Object> instrumentFacts,
                                          String targetDate, String remarks);
}
