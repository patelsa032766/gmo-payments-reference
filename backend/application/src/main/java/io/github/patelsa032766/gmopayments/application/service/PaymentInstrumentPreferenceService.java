package io.github.patelsa032766.gmopayments.application.service;

import io.github.patelsa032766.gmopayments.application.port.PaymentInstrumentPreferenceRepository;
import io.github.patelsa032766.gmopayments.domain.PaymentInstrumentSnapshot;
import java.util.List;

public final class PaymentInstrumentPreferenceService {
    private final PaymentInstrumentPreferenceRepository repository;
    public PaymentInstrumentPreferenceService(PaymentInstrumentPreferenceRepository repository){this.repository=repository;}
    public List<PaymentInstrumentSnapshot> set(String customer,String primary,String backup){
        if(customer==null||customer.isBlank()||primary==null||primary.isBlank())throw new IllegalArgumentException("Customer and primary instrument are required");
        if(primary.equals(backup))throw new IllegalArgumentException("Primary and backup must be different");
        return repository.setPreferences(customer,primary,backup==null||backup.isBlank()?null:backup);
    }
}
