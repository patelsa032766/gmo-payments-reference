package io.github.patelsa032766.gmopayments;

import io.github.patelsa032766.gmopayments.application.port.CheckoutConfigurationRepository;
import io.github.patelsa032766.gmopayments.application.port.PaymentOperationsRepository;
import io.github.patelsa032766.gmopayments.application.port.PaymentCommandRepository;
import io.github.patelsa032766.gmopayments.application.port.PaymentGateway;
import io.github.patelsa032766.gmopayments.application.port.BrowserPaymentConfigurationProvider;
import io.github.patelsa032766.gmopayments.application.port.InboundMessageConfigurationProvider;
import io.github.patelsa032766.gmopayments.application.port.InboundMessageRepository;
import io.github.patelsa032766.gmopayments.application.service.BrowserPaymentConfigurationService;
import io.github.patelsa032766.gmopayments.application.service.CheckoutPaymentService;
import io.github.patelsa032766.gmopayments.application.service.CheckoutEligibilityService;
import io.github.patelsa032766.gmopayments.application.service.PaymentOperationsQueryService;
import io.github.patelsa032766.gmopayments.application.service.InboundMessageService;
import io.github.patelsa032766.gmopayments.application.port.ConfigurationAdministrationRepository;
import io.github.patelsa032766.gmopayments.application.service.ConfigurationAdministrationService;
import io.github.patelsa032766.gmopayments.application.port.MitCommandRepository;
import io.github.patelsa032766.gmopayments.application.service.MitPaymentService;
import io.github.patelsa032766.gmopayments.application.port.PaymentInstrumentPreferenceRepository;
import io.github.patelsa032766.gmopayments.application.service.PaymentInstrumentPreferenceService;
import io.github.patelsa032766.gmopayments.application.port.KozaBatchRepository;
import io.github.patelsa032766.gmopayments.application.service.KozaBatchService;
import io.github.patelsa032766.gmopayments.application.port.ReconciliationFileSource;
import io.github.patelsa032766.gmopayments.application.port.ReconciliationImportRepository;
import io.github.patelsa032766.gmopayments.application.service.ReconciliationImportService;
import io.github.patelsa032766.gmopayments.application.service.BrowserReturnService;
import io.github.patelsa032766.gmopayments.application.port.CaptureCommandRepository;
import io.github.patelsa032766.gmopayments.application.service.CapturePaymentService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Explicitly assembles framework-free application services. This is the only
 * module allowed to decide which adapter implements an application port.
 */
@Configuration
public class ApplicationUseCaseConfiguration {
    @Bean
    CheckoutEligibilityService checkoutEligibilityService(CheckoutConfigurationRepository repository) {
        return new CheckoutEligibilityService(repository);
    }

    @Bean
    PaymentOperationsQueryService paymentOperationsQueryService(PaymentOperationsRepository repository) {
        return new PaymentOperationsQueryService(repository);
    }

    @Bean
    CheckoutPaymentService checkoutPaymentService(PaymentCommandRepository repository, PaymentGateway gateway) {
        return new CheckoutPaymentService(repository, gateway);
    }

    @Bean
    BrowserPaymentConfigurationService browserPaymentConfigurationService(
            BrowserPaymentConfigurationProvider provider) {
        return new BrowserPaymentConfigurationService(provider);
    }

    @Bean
    InboundMessageService inboundMessageService(InboundMessageRepository repository,
                                                InboundMessageConfigurationProvider configuration) {
        return new InboundMessageService(repository, configuration);
    }

    @Bean ConfigurationAdministrationService configurationAdministrationService(ConfigurationAdministrationRepository repository) {
        return new ConfigurationAdministrationService(repository);
    }

    @Bean MitPaymentService mitPaymentService(MitCommandRepository repository, PaymentGateway gateway) {
        return new MitPaymentService(repository, gateway);
    }
    @Bean CapturePaymentService capturePaymentService(CaptureCommandRepository repository, PaymentGateway gateway) {
        return new CapturePaymentService(repository, gateway);
    }
    @Bean PaymentInstrumentPreferenceService paymentInstrumentPreferenceService(PaymentInstrumentPreferenceRepository repository){return new PaymentInstrumentPreferenceService(repository);}
    @Bean KozaBatchService kozaBatchService(KozaBatchRepository batches,MitCommandRepository commands,PaymentGateway gateway){return new KozaBatchService(batches,commands,gateway);}

    @Bean
    ReconciliationImportService reconciliationImportService(ReconciliationFileSource source,
                                                             ReconciliationImportRepository repository) {
        return new ReconciliationImportService(source, repository);
    }

    @Bean
    BrowserReturnService browserReturnService(PaymentCommandRepository repository, PaymentGateway gateway) {
        return new BrowserReturnService(repository, gateway);
    }
}
