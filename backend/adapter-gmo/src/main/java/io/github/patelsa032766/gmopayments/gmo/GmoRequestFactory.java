package io.github.patelsa032766.gmopayments.gmo;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps provider-independent checkout facts into GMO-specific payloads.
 *
 * <p>This is the only class that knows both the OpenAPI JSON shape and the
 * idPass field names used by the reference application. Keeping those names
 * here prevents controllers and application use cases from becoming coupled to
 * a provider contract.</p>
 */
@Component
public class GmoRequestFactory {
    private final GmoProperties properties;

    public GmoRequestFactory(GmoProperties properties) {
        this.properties = properties;
    }

    public Map<String, Object> cardCharge(CheckoutFacts facts, String mpToken, String holderName,
                                           String authorizationMode) {
        return Map.of(
                "merchant", merchant("/api/v1/gmo/returns/card?application=" + facts.applicationNumber()),
                "order", order(facts, facts.initiationType()),
                "payer", Map.of("name", facts.customerName()),
                "creditInformation", Map.of(
                        "tokenizedCard", Map.of("type", "MP_TOKEN", "token", mpToken,
                                "cardholderName", holderName),
                        "creditChargeOptions", Map.of("authorizationMode", authorizationMode,
                                "useTds2", false, "paymentMethod", "ONE_TIME")));
    }

    public Map<String, Object> savedCardCharge(CheckoutFacts facts, String memberId, String cardType,
                                                String cardId, String holderName, String authorizationMode) {
        var card = new LinkedHashMap<String, Object>();
        card.put("memberId", memberId);
        card.put("type", cardType);
        if (!blank(cardId)) card.put("cardId", cardId);
        if (!blank(holderName)) card.put("cardholderName", holderName);
        return Map.of(
                "merchant", merchant(null),
                "order", order(facts, "MIT"),
                "payer", Map.of("name", facts.customerName(), "accountId", facts.customerCode()),
                "creditOnfileInformation", Map.of(
                        "onfileCard", card,
                        "creditChargeOptions", Map.of("authorizationMode", authorizationMode,
                                "useTds2", false, "paymentMethod", "ONE_TIME")));
    }

    /**
     * Registers the card used by a successful charge as an on-file card.
     *
     * <p>GMO deliberately accepts the successful charge access ID rather than
     * raw PAN data here. This keeps the Java service outside the path of
     * sensitive card details and lets the tokenized checkout authorize and
     * save the card in one customer journey.</p>
     */
    public Map<String, Object> storeCard(String chargeAccessId, String memberId, String memberName) {
        return Map.of(
                "merchant", merchant(null),
                "creditStoringInformation", Map.of(
                        "referrer", Map.of("accessId", chargeAccessId),
                        "onfileCardOptions", Map.of(
                                "memberId", memberId,
                                "memberName", memberName,
                                "createNewMember", true,
                                "setDefault", true)));
    }

    public Map<String, Object> payPayCharge(CheckoutFacts facts, String authorizationMode) {
        return Map.of(
                "merchant", merchant("/api/v1/gmo/returns/paypay?application=" + facts.applicationNumber()),
                "order", order(facts, facts.initiationType()),
                "payer", Map.of("name", facts.customerName(), "accountId", facts.customerCode()),
                "walletInformation", Map.of("walletType", "PAYPAY",
                        "walletChargeOptions", Map.of("authorizationMode", authorizationMode)));
    }

    public Map<String, Object> payPayRecurringRegistration(CheckoutFacts facts) {
        var order = new LinkedHashMap<String, Object>();
        order.put("orderId", facts.applicationNumber());
        order.put("transactionType", "MIT");
        order.put("clientFields", clientFields(facts));
        return Map.of(
                "merchant", merchant("/api/v1/gmo/returns/paypay-registration?application="
                        + facts.applicationNumber()),
                "order", order,
                "payer", Map.of("name", facts.customerName(), "accountId", facts.customerCode()),
                "walletAuthorizationInformation", Map.of(
                        "walletType", "PAYPAY",
                        "walletAuthorizationOptions", Map.of("memberId", facts.customerCode(),
                                "memberName", facts.customerName(), "createNewMember", true)));
    }

    /** Safe, read-only verification used after an OpenAPI browser callback. */
    public Map<String, Object> orderInquiry(String accessId) {
        return Map.of("accessId", accessId);
    }

    /** GMO accepts either identifier; supplying both strengthens traceability. */
    public Map<String, Object> orderCapture(String accessId, String orderId) {
        var payload = new LinkedHashMap<String, Object>();
        if (!blank(accessId)) payload.put("accessId", accessId);
        if (!blank(orderId)) payload.put("orderId", orderId);
        return payload;
    }

    public Map<String, Object> savedPayPayCharge(CheckoutFacts facts, String memberId,
                                                  String authorizationMode) {
        return Map.of(
                "merchant", merchant(null),
                "order", order(facts, "MIT"),
                "payer", Map.of("name", facts.customerName(), "accountId", facts.customerCode()),
                "walletOnfileInformation", Map.of(
                        "walletChargeOptions", Map.of("authorizationMode", authorizationMode),
                        "onfileWallet", Map.of("memberId", memberId, "type", "PAYPAY")));
    }

    public Map<String, Object> cashCharge(CheckoutFacts facts, String cashType,
                                           String nameKana, String email, String phone,
                                           String konbiniCode) {
        var payer = new LinkedHashMap<String, Object>();
        payer.put("name", facts.customerName());
        if (!blank(nameKana)) payer.put("nameKana", nameKana);
        if (!blank(email)) payer.put("email", email);
        if (!blank(phone)) payer.put("phones", java.util.List.of(Map.of("number", digits(phone))));

        var cash = new LinkedHashMap<String, Object>();
        cash.put("cashType", cashType);
        if (!blank(email) && !"BANK_TRANSFER_GMO_AOZORA".equals(cashType)) {
            cash.put("customerMailAddress", email);
        }
        if ("KONBINI".equals(cashType)) {
            cash.put("cashOptions", Map.of("konbiniCode", normalizeKonbini(konbiniCode)));
        }
        return Map.of("merchant", merchant(null), "order", order(facts, "CIT"),
                "payer", payer, "cashInformation", cash);
    }

    /** Registration for immediate 口座直結決済; not Koza Furikae Select. */
    public Map<String, String> bankDirectRegistration(CheckoutFacts facts, String bankCode,
                                                       String accountNameLast, String accountNameFirst) {
        return linkedFields(
                "SiteID", properties.getSiteId(), "SitePass", properties.getSitePass(),
                "ShopID", properties.getShopId(), "ShopPass", properties.getShopPass(),
                "MemberID", facts.customerCode(), "RetURL", properties.browserReturnBaseUrl()
                        + "/webhooks/gmo/protocol/return/bank-direct",
                "BankCode", bankCode, "AccountNameLast", accountNameLast,
                "AccountNameFirst", accountNameFirst);
    }

    public Map<String, String> bankDirectEntry(CheckoutFacts facts) {
        var fields = linkedFields(
                "ShopID", properties.getShopId(), "ShopPass", properties.getShopPass(),
                "SiteID", properties.getSiteId(), "SitePass", properties.getSitePass(),
                "MemberID", facts.customerCode(), "OrderID", facts.applicationNumber(),
                "Amount", Long.toString(facts.amountJpy()));
        addClientFields(fields, facts);
        return fields;
    }

    /** Server-side verification that an active direct-debit account exists. */
    public Map<String, String> bankDirectInquiry(String memberId) {
        return linkedFields(
                "SiteID", properties.getSiteId(),
                "SitePass", properties.getSitePass(),
                "MemberID", memberId);
    }

    public Map<String, String> bankDirectExecution(CheckoutFacts facts, String accessId, String accessPass) {
        var fields = linkedFields(
                "ShopID", properties.getShopId(), "ShopPass", properties.getShopPass(),
                "AccessID", accessId, "AccessPass", accessPass,
                "OrderID", facts.applicationNumber());
        addClientFields(fields, facts);
        fields.put("ClientFieldFlag", "1");
        return fields;
    }

    /**
     * Online mandate registration for 口座振替（セレクト）.
     * Bank-dependent fields are optional because GMO's supported-bank matrix
     * determines which values are collected before versus during handoff.
     */
    public Map<String, String> kozaRegistration(CheckoutFacts facts, KozaAccount account) {
        var fields = linkedFields(
                "SiteID", properties.getSiteId(), "SitePass", properties.getSitePass(),
                "MemberID", facts.customerCode(), "MemberName", facts.customerName(),
                "CreateMember", "1", "RetURL", properties.browserReturnBaseUrl()
                        + "/webhooks/gmo/protocol/return/koza-furikae",
                "BankCode", account.bankCode(), "ConsumerDevice", account.consumerDevice());
        putIfPresent(fields, "BranchCode", account.branchCode());
        putIfPresent(fields, "AccountType", account.accountType());
        putIfPresent(fields, "AccountNumber", account.accountNumber());
        putIfPresent(fields, "AccountName", account.accountNameKana());
        putIfPresent(fields, "AccountNameKanji", account.accountNameKanji());
        return fields;
    }

    public Map<String, String> kozaRegistrationInquiry(String transactionId) {
        return linkedFields("SiteID", properties.getSiteId(), "SitePass", properties.getSitePass(),
                "TranID", transactionId);
    }

    /** One GMO transaction is created for each selected monthly debit item. */
    public Map<String, String> kozaBatchEntry(String orderId, long amountJpy) {
        return linkedFields("ShopID", properties.getShopId(), "ShopPass", properties.getShopPass(),
                "OrderID", orderId, "Amount", Long.toString(amountJpy));
    }

    public Map<String, String> kozaBatchExecution(String orderId, String accessId, String accessPass,
                                                   String memberId, String targetDate, String remarks) {
        return linkedFields(
                "AccessID", accessId, "AccessPass", accessPass, "OrderID", orderId,
                "SiteID", properties.getSiteId(), "SitePass", properties.getSitePass(),
                "MemberID", memberId, "TargetDate", targetDate, "Remarks", remarks,
                "CheckMode", "NOCHECK_ACCOUNT");
    }

    private Map<String, Object> merchant(String callbackPath) {
        var merchant = new LinkedHashMap<String, Object>();
        var source = properties.getMerchant();
        merchant.put("name", source.getName());
        merchant.put("nameKana", source.getNameKana());
        merchant.put("nameShort", source.getNameShort());
        merchant.put("nameAlphabet", source.getNameAlphabet());
        merchant.put("contactName", source.getContactName());
        merchant.put("contactEmail", source.getContactEmail());
        merchant.put("contactUrl", source.getContactUrl());
        merchant.put("contactPhone", source.getContactPhone());
        merchant.put("contactOpeningHours", source.getContactOpeningHours());
        if (properties.resolvedOpenapiWebhookUrl() != null) {
            merchant.put("webhookUrl", properties.resolvedOpenapiWebhookUrl());
        }
        if (callbackPath != null) merchant.put("callbackUrl", properties.browserReturnBaseUrl() + callbackPath);
        return merchant;
    }

    private static Map<String, Object> order(CheckoutFacts facts, String initiationType) {
        return Map.of("orderId", facts.applicationNumber(), "amount", Long.toString(facts.amountJpy()),
                "currency", "JPY", "transactionType", initiationType,
                "clientFields", clientFields(facts));
    }

    private static Map<String, String> clientFields(CheckoutFacts facts) {
        return Map.of("clientField1", facts.applicationNumber(),
                "clientField2", facts.customerName() + " / " + facts.customerCode(),
                "clientField3", facts.agentName() + " / " + facts.companyName());
    }

    private static void addClientFields(Map<String, String> fields, CheckoutFacts facts) {
        fields.put("ClientField1", facts.applicationNumber());
        fields.put("ClientField2", facts.customerName() + " / " + facts.customerCode());
        fields.put("ClientField3", facts.agentName() + " / " + facts.companyName());
    }

    private static Map<String, String> linkedFields(String... pairs) {
        var fields = new LinkedHashMap<String, String>();
        for (int index = 0; index < pairs.length; index += 2) fields.put(pairs[index], pairs[index + 1]);
        return fields;
    }

    private static void putIfPresent(Map<String, String> fields, String key, String value) {
        if (!blank(value)) fields.put(key, value);
    }

    private static String normalizeKonbini(String value) {
        if (value == null) return "";
        return switch (value.trim().toUpperCase()) {
            case "SEVENELEVEN", "7ELEVEN", "7-ELEVEN" -> "SEVEN_ELEVEN";
            default -> value.trim().toUpperCase();
        };
    }

    private static String digits(String value) { return value.replaceAll("[^0-9]", ""); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }

    public record CheckoutFacts(String applicationNumber, String customerCode, String customerName,
                                String agentName, String companyName, long amountJpy,
                                String initiationType) {}

    public record KozaAccount(String bankCode, String branchCode, String accountType,
                              String accountNumber, String accountNameKana,
                              String accountNameKanji, String consumerDevice) {}
}
