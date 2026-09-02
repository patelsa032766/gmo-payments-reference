package io.github.patelsa032766.gmopayments.domain;

import java.util.Map;

/** Reserved MIT transaction plus the sanitized stored-instrument facts required by its adapter. */
public record MitExecutionReservation(PaymentExecutionContext context, Map<String,Object> instrumentFacts, boolean replayed) {}
