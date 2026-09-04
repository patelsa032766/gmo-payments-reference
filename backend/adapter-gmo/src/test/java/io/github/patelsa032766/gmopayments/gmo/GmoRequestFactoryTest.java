package io.github.patelsa032766.gmopayments.gmo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GmoRequestFactoryTest {
    private GmoProperties properties;
    private GmoRequestFactory factory;
    private GmoRequestFactory.CheckoutFacts facts;

    @BeforeEach
    void setUp() {
        properties = new GmoProperties();
        properties.setShopId("shop");
        properties.setShopPass("shop-pass");
        properties.setSiteId("site");
        properties.setSitePass("site-pass");
        properties.setPublicBaseUrl("https://public.example");
        properties.setWebhooksEnabled(false);
        factory = new GmoRequestFactory(properties);
        facts = new GmoRequestFactory.CheckoutFacts("APP-1", "CUST-1", "Aiko Tanaka",
                "Agent A", "Company A", 10_000, "CIT");
    }

    @Test
    void omitsWebhookUrlWhenWebhooksAreDisabled() {
        var payload = factory.payPayRecurringRegistration(facts);
        assertThat(payload.get("merchant").toString()).doesNotContain("webhookUrl");
        assertThat(payload.toString()).contains(
                "https://public.example/api/v1/gmo/returns/paypay-registration");
    }

    @Test
    void recurringPayPayStartsWithConsentAndUsesAnAmountFreeMitOrder() {
        var payload = factory.payPayRecurringRegistration(facts);

        assertThat(payload.toString()).contains("walletAuthorizationInformation")
                .contains("createNewMember=true")
                .contains("transactionType=MIT")
                .doesNotContain("amount=");
        assertThat(factory.orderInquiry("access-123")).containsEntry("accessId", "access-123");
    }

    @Test
    void captureIdentifiesTheExistingAuthorizationWithoutPaymentData() {
        var payload = factory.orderCapture("access-123", "ORDER-123");

        assertThat(payload).containsEntry("accessId", "access-123")
                .containsEntry("orderId", "ORDER-123")
                .doesNotContainKeys("amount", "tokenizedCard", "memberId");
    }

    @Test
    void storeCardRefersToTheSuccessfulChargeWithoutCardData() {
        var payload = factory.storeCard("charge-access", "CUST-1", "Aiko Tanaka");

        assertThat(payload).containsKey("merchant");
        assertThat(payload.toString()).contains("accessId=charge-access")
                .contains("memberId=CUST-1")
                .contains("setDefault=true")
                .doesNotContain("cardNumber")
                .doesNotContain("tokenizedCard");
    }

    @Test
    void kozaRegistrationAndRealtimeDebitUseDifferentProviderContracts() {
        var koza = factory.kozaRegistration(facts, new GmoRequestFactory.KozaAccount(
                "0005", "001", "1", "1234567", "タナカアイコ", "田中愛子", "pc"));
        var realtime = factory.bankDirectRegistration(facts, "0005", "タナカ", "アイコ");

        assertThat(koza).containsEntry("CreateMember", "1").containsEntry("AccountNumber", "1234567");
        assertThat(koza).doesNotContainKey("ShopID");
        assertThat(realtime).containsKeys("ShopID", "AccountNameLast", "AccountNameFirst");
        assertThat(realtime).doesNotContainKey("CreateMember");
    }

    @Test
    void monthlyKozaExecutionUsesTheDocumentedSelectFields() {
        var fields = factory.kozaBatchExecution("KOZA-202609-001", "access", "pass",
                "CUST-1", "20260928", "9ガツホケンリョウ");
        assertThat(fields).containsEntry("MemberID", "CUST-1")
                .containsEntry("TargetDate", "20260928")
                .containsEntry("CheckMode", "NOCHECK_ACCOUNT");
    }
}
