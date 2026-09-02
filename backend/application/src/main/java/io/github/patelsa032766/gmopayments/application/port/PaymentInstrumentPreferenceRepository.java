package io.github.patelsa032766.gmopayments.application.port;

import io.github.patelsa032766.gmopayments.domain.PaymentInstrumentSnapshot;
import java.util.List;

/** Atomically enforces one primary and at most one backup per customer. */
public interface PaymentInstrumentPreferenceRepository {
    List<PaymentInstrumentSnapshot> setPreferences(String customerCode,String primaryInstrumentId,String backupInstrumentId);
}
