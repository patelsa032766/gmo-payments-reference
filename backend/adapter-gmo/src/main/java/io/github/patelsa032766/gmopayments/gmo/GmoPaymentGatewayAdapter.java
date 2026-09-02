package io.github.patelsa032766.gmopayments.gmo;

import io.github.patelsa032766.gmopayments.application.port.PaymentGateway;
import io.github.patelsa032766.gmopayments.domain.PaymentExecutionContext;
import io.github.patelsa032766.gmopayments.domain.PaymentGatewayResult;
import io.github.patelsa032766.gmopayments.domain.PaymentNextAction;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Executes customer checkout against GMO or a deterministic local adapter.
 *
 * <p>Simulation is the public-clone default. Live sandbox traffic requires the
 * explicit {@code GMO_LIVE_CALLS_ENABLED=true} gate in addition to credentials,
 * preventing a developer from charging a sandbox merely by opening the UI.</p>
 */
@Component
public class GmoPaymentGatewayAdapter implements PaymentGateway {
    private final GmoProperties properties;
    private final GmoRequestFactory requests;
    private final GmoOpenApiClient openApi;
    private final GmoIdPassClient idPass;

    public GmoPaymentGatewayAdapter(GmoProperties properties, GmoRequestFactory requests,
                                    GmoOpenApiClient openApi, GmoIdPassClient idPass) {
        this.properties = properties;
        this.requests = requests;
        this.openApi = openApi;
        this.idPass = idPass;
    }

    @Override
    public PaymentGatewayResult executeCheckout(PaymentExecutionContext context, Map<String, Object> details) {
        if (!properties.isLiveCallsEnabled()) return simulate(context);
        return switch (context.method()) {
            case CARD -> card(context, details);
            case PAYPAY -> payPay(context, details);
            case BANK_DIRECT_REALTIME -> bankDirectRegistration(context, details);
            case KOZA_FURIKAE_SELECT -> kozaRegistration(context, details);
            case KOMBINI -> cash(context, details, "KONBINI");
            case PAYEASY -> cash(context, details, "PAYEASY");
            case FURIKOMI -> cash(context, details, "BANK_TRANSFER_GMO_AOZORA");
        };
    }

    @Override
    public PaymentGatewayResult executeMit(PaymentExecutionContext context, Map<String, Object> instrument,
                                           Map<String, Object> command) {
        if (!properties.isLiveCallsEnabled()) {
            String mode = value(command, "authorizationMode", "CAPTURE").toUpperCase();
            String state = "AUTH".equals(mode) ? "AUTHORIZED" : "PAID";
            return new PaymentGatewayResult(state, state, context.applicationNumber(),
                    "simulated-" + context.transactionId(), "MIT_SIMULATED",
                    "Recurring payment completed", false, PaymentNextAction.none(), Map.of(),
                    "OPENAPI", "SimulatedMitCharge", null, 200, 0,
                    Map.of("instrument", instrument.getOrDefault("maskedDisplay", "[MASKED]"), "mode", mode),
                    Map.of("status", state));
        }
        return switch (context.method()) {
            case CARD -> savedCard(context, instrument, command);
            case PAYPAY -> savedPayPay(context, instrument, command);
            case BANK_DIRECT_REALTIME -> savedBankDirect(context, instrument);
            case KOZA_FURIKAE_SELECT -> throw new IllegalArgumentException(
                    "Koza Furikae is submitted through the monthly batch workflow");
            default -> throw new IllegalArgumentException("This payment method does not support MIT");
        };
    }

    @Override
    public PaymentGatewayResult executeKozaDebit(PaymentExecutionContext context,
                                                Map<String, Object> instrument,
                                                String targetDate, String remarks) {
        if (!properties.isLiveCallsEnabled()) {
            return new PaymentGatewayResult("SCHEDULED", "REQSUCCESS", context.applicationNumber(),
                    "simulated-" + context.transactionId(), "KOZA_REQUEST_ACCEPTED",
                    "Monthly Koza Furikae debit scheduled", false, PaymentNextAction.none(),
                    Map.of("targetDate", targetDate), "IDPASS", "SimulatedKozaDebit", null, 200, 0,
                    Map.of("memberId", "[MASKED]", "targetDate", targetDate), Map.of("Status", "REQSUCCESS"));
        }
        var entry = requests.kozaBatchEntry(context.applicationNumber(), context.amountJpy());
        var entryResult = idPass.post("EntryTranBankaccount.idPass", entry, true);
        String accessId = first(entryResult.rawPayload(), "AccessID", "AccessId");
        String accessPass = first(entryResult.rawPayload(), "AccessPass", "AccessPASS");
        if (blank(accessId) || blank(accessPass)) {
            throw new GmoProviderException("GMO Koza entry omitted access credentials",
                    entryResult.statusCode(), false, false, entryResult.sanitizedPayload(), null);
        }
        var execution = requests.kozaBatchExecution(context.applicationNumber(), accessId, accessPass,
                required(instrument, "memberId"), targetDate, remarks);
        var result = idPass.post("ExecTranBankaccount.idPass", execution, true);
        String providerStatus = status(result.rawPayload(), "REQSUCCESS");
        String canonical = "REQSUCCESS".equalsIgnoreCase(providerStatus) ? "SCHEDULED" : canonicalState(providerStatus);
        return new PaymentGatewayResult(canonical, providerStatus, context.applicationNumber(), accessId,
                "KOZA_REQUEST_ACCEPTED", "Monthly Koza Furikae debit submitted", "UNKNOWN".equals(canonical),
                PaymentNextAction.none(), Map.of("targetDate", targetDate), "IDPASS", "ExecTranBankaccount",
                "ExecTranBankaccount.idPass", result.statusCode(), safeInt(result.durationMs()),
                GmoSanitizer.sanitize(asObjectMap(execution)), result.sanitizedPayload());
    }

    private PaymentGatewayResult savedCard(PaymentExecutionContext context, Map<String, Object> instrument,
                                           Map<String, Object> command) {
        String mode = value(command, "authorizationMode", "CAPTURE").toUpperCase();
        var payload = requests.savedCardCharge(facts(context), required(instrument, "memberId"),
                value(instrument, "cardType", "CREDIT_CARD"), value(instrument, "cardId",
                        value(instrument, "instrumentReference", "")),
                value(instrument, "cardholderName", ""), mode);
        var result = openApi.post("/credit/on-file/charge", payload, true);
        return openApiResult(context, result, payload, "/credit/on-file/charge",
                "CreditOnFileCharge", "Stored card payment submitted");
    }

    private PaymentGatewayResult savedPayPay(PaymentExecutionContext context, Map<String, Object> instrument,
                                             Map<String, Object> command) {
        String mode = value(command, "authorizationMode", "CAPTURE").toUpperCase();
        var payload = requests.savedPayPayCharge(facts(context), required(instrument, "memberId"), mode);
        var result = openApi.post("/wallet/on-file/charge", payload, true);
        return openApiResult(context, result, payload, "/wallet/on-file/charge",
                "WalletOnFileCharge", "Stored PayPay payment submitted");
    }

    private PaymentGatewayResult savedBankDirect(PaymentExecutionContext context,
                                                 Map<String, Object> instrument) {
        var entry = requests.bankDirectEntry(facts(context));
        var entryResult = idPass.post("EntryTranBankDirect.idPass", entry, true);
        String accessId = first(entryResult.rawPayload(), "AccessID", "AccessId");
        String accessPass = first(entryResult.rawPayload(), "AccessPass", "AccessPASS");
        if (blank(accessId) || blank(accessPass)) {
            throw new GmoProviderException("GMO bank-direct entry omitted access credentials",
                    entryResult.statusCode(), false, false, entryResult.sanitizedPayload(), null);
        }
        var execution = requests.bankDirectExecution(facts(context), accessId, accessPass);
        var result = idPass.post("ExecTranBankDirect.idPass", execution, true);
        String providerStatus = status(result.rawPayload(), "PROCESSING");
        String canonical = canonicalState(providerStatus);
        return new PaymentGatewayResult(canonical, providerStatus, context.applicationNumber(), accessId,
                "BANK_DIRECT_MIT_RESULT", "Stored bank account debit submitted", "UNKNOWN".equals(canonical),
                PaymentNextAction.none(), Map.of(), "IDPASS", "ExecTranBankDirect",
                "ExecTranBankDirect.idPass", result.statusCode(), safeInt(result.durationMs()),
                GmoSanitizer.sanitize(asObjectMap(execution)), result.sanitizedPayload());
    }

    private PaymentGatewayResult card(PaymentExecutionContext context, Map<String, Object> details) {
        String token = required(details, "token");
        String holder = required(details, "holderName").toUpperCase();
        String authorizationMode = value(details, "authorizationMode", "AUTH").toUpperCase();
        var payload = requests.cardCharge(facts(context), token, holder, authorizationMode);
        var result = openApi.post("/credit/charge", payload, true);
        return openApiResult(context, result, payload, "/credit/charge", "CreditCharge",
                "Card authorization submitted");
    }

    private PaymentGatewayResult payPay(PaymentExecutionContext context, Map<String, Object> details) {
        String authorizationMode = value(details, "authorizationMode", "AUTH").toUpperCase();
        var payload = requests.payPayCharge(facts(context), authorizationMode);
        var result = openApi.post("/wallet/charge", payload, true);
        return openApiResult(context, result, payload, "/wallet/charge", "WalletCharge",
                "PayPay authorization submitted");
    }

    private PaymentGatewayResult cash(PaymentExecutionContext context, Map<String, Object> details,
                                      String cashType) {
        var payload = requests.cashCharge(facts(context), cashType, value(details, "nameKana", ""),
                value(details, "email", properties.getMerchant().getContactEmail()),
                value(details, "phone", properties.getMerchant().getContactPhone()),
                value(details, "konbiniCode", "LAWSON"));
        var result = openApi.post("/cash/charge", payload, true);
        Map<String, Object> instructions = nested(result.rawPayload(), "cashResult");
        return new PaymentGatewayResult("INSTRUCTIONS_ISSUED", status(result.rawPayload(), "REQSUCCESS"),
                context.applicationNumber(), accessId(result.rawPayload()), "INSTRUCTIONS_ISSUED",
                "Payment instructions issued", false, PaymentNextAction.none(), instructions,
                "OPENAPI", "CashCharge", "/cash/charge", result.statusCode(), safeInt(result.durationMs()),
                GmoSanitizer.sanitize(payload), result.sanitizedPayload());
    }

    private PaymentGatewayResult bankDirectRegistration(PaymentExecutionContext context,
                                                         Map<String, Object> details) {
        String accountName = required(details, "accountNameKana").trim();
        String[] names = accountName.split("[ 　]+", 2);
        var payload = requests.bankDirectRegistration(facts(context), required(details, "bankCode"),
                names[0], names.length == 2 ? names[1] : names[0]);
        var result = idPass.post("BankDirectRegist.idPass", payload, true);
        return registrationResult(context, payload, result, "BankDirectRegist",
                "Real-time bank debit registration started");
    }

    private PaymentGatewayResult kozaRegistration(PaymentExecutionContext context,
                                                   Map<String, Object> details) {
        var account = new GmoRequestFactory.KozaAccount(
                required(details, "bankCode"), value(details, "branchCode", ""),
                value(details, "accountType", "1"), value(details, "accountNumber", ""),
                required(details, "accountNameKana"), value(details, "accountNameKanji", ""),
                value(details, "consumerDevice", "pc"));
        var payload = requests.kozaRegistration(facts(context), account);
        var result = idPass.post("BankAccountEntry.idPass", payload, true);
        return registrationResult(context, payload, result, "BankAccountEntry",
                "Koza Furikae registration started");
    }

    private PaymentGatewayResult registrationResult(PaymentExecutionContext context,
                                                     Map<String, String> request,
                                                     GmoHttpResult result,
                                                     String operation,
                                                     String summary) {
        String startUrl = first(result.rawPayload(), "StartURL", "StartUrl");
        String transactionId = first(result.rawPayload(), "TranID", "TranId");
        String token = first(result.rawPayload(), "Token", "token");
        if (blank(startUrl) || blank(transactionId) || blank(token)) {
            throw new GmoProviderException("GMO registration response omitted browser handoff fields",
                    result.statusCode(), false, false, result.sanitizedPayload(), null);
        }
        var transientFields = new LinkedHashMap<String, String>();
        transientFields.put("TranID", transactionId);
        transientFields.put("Token", token);
        return new PaymentGatewayResult("REGISTRATION_PENDING", status(result.rawPayload(), "REGISTERING"),
                context.applicationNumber(), transactionId, "REGISTRATION_STARTED", summary, false,
                new PaymentNextAction("FORM_POST", startUrl, transientFields), Map.of(), "IDPASS",
                operation, operation + ".idPass", result.statusCode(), safeInt(result.durationMs()),
                GmoSanitizer.sanitize(asObjectMap(request)), result.sanitizedPayload());
    }

    private PaymentGatewayResult openApiResult(PaymentExecutionContext context, GmoHttpResult result,
                                                Map<String, Object> request, String endpoint,
                                                String operation, String summary) {
        Map<String, Object> orderReference = nested(result.rawPayload(), "orderReference");
        String providerStatus = String.valueOf(orderReference.getOrDefault("status", "PROCESSING"));
        String canonical = canonicalState(providerStatus);
        String redirect = first(result.rawPayload(), "redirectUrl");
        if (blank(redirect)) redirect = first(nested(result.rawPayload(), "redirectInformation"), "redirectUrl");
        PaymentNextAction action = blank(redirect)
                ? PaymentNextAction.none() : new PaymentNextAction("REDIRECT", redirect, Map.of());
        return new PaymentGatewayResult(canonical, providerStatus, context.applicationNumber(),
                string(orderReference.get("accessId")), "PROVIDER_RESULT", summary,
                "UNKNOWN".equals(canonical), action, Map.of(), "OPENAPI", operation, endpoint,
                result.statusCode(), safeInt(result.durationMs()), GmoSanitizer.sanitize(request),
                result.sanitizedPayload());
    }

    private PaymentGatewayResult simulate(PaymentExecutionContext context) {
        Map<String, Object> instructions = switch (context.method()) {
            case KOMBINI -> Map.of("store", "Lawson", "receiptNumber", "9210-0000-1234", "deadline", "2026-09-10T15:00:00+09:00");
            case PAYEASY -> Map.of("収納機関番号", "58021", "customerNumber", "10000012345678900000", "confirmationNumber", "123456");
            case FURIKOMI -> furikomiInstructions(context);
            case KOZA_FURIKAE_SELECT -> furikomiInstructions(context);
            default -> Map.of();
        };
        String state = switch (context.method()) {
            case CARD, PAYPAY -> "AUTHORIZED";
            case BANK_DIRECT_REALTIME -> "PAID";
            case KOZA_FURIKAE_SELECT -> "MANDATE_REGISTERED_TRANSFER_DUE";
            case KOMBINI, PAYEASY, FURIKOMI -> "INSTRUCTIONS_ISSUED";
        };
        return new PaymentGatewayResult(state, state, context.applicationNumber(),
                "simulated-" + context.transactionId(), "SIMULATED_RESULT",
                switch (context.method()) {
                    case KOZA_FURIKAE_SELECT -> "Koza Furikae registered; first-premium transfer instructions issued";
                    case BANK_DIRECT_REALTIME -> "Bank account registered and immediate debit completed";
                    case KOMBINI, PAYEASY, FURIKOMI -> "Payment instructions issued";
                    default -> "Payment authorization completed";
                }, false, PaymentNextAction.none(), instructions, "OPENAPI", "SimulatedCheckout",
                null, 200, 0, Map.of("mode", "SIMULATED"), Map.of("status", state));
    }

    private static Map<String, Object> furikomiInstructions(PaymentExecutionContext context) {
        return Map.of("bank", "GMO Aozora Net Bank", "branch", "Insurance Premiums / 503",
                "accountType", "Ordinary", "accountNumber", "3352017",
                "accountName", "GMO INSURANCE COLLECTIONS", "transferReference",
                context.applicationNumber().replace("-", ""), "amountJpy", context.amountJpy(),
                "deadline", "2026-09-10T15:00:00+09:00");
    }

    private static GmoRequestFactory.CheckoutFacts facts(PaymentExecutionContext context) {
        return new GmoRequestFactory.CheckoutFacts(context.applicationNumber(), context.customerCode(),
                context.customerName(), context.agentName(), context.companyName(), context.amountJpy(),
                context.initiationType());
    }

    private static String canonicalState(String status) {
        return switch (status.toUpperCase()) {
            case "AUTH", "AUTHORIZED" -> "AUTHORIZED";
            case "SALES", "CAPTURE", "PAYSUCCESS", "PAID" -> "PAID";
            case "REQSUCCESS", "TRADING" -> "PROCESSING";
            case "PAYFAIL", "FAILED", "EXPIRED", "CANCEL" -> "FAILED";
            default -> "PROCESSING";
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nested(Map<String, Object> source, String key) {
        Object value = source.get(key);
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static Map<String, Object> asObjectMap(Map<String, String> source) {
        return new LinkedHashMap<>(source);
    }

    private static String accessId(Map<String, Object> payload) {
        return string(nested(payload, "orderReference").get("accessId"));
    }

    private static String status(Map<String, Object> payload, String fallback) {
        Map<String, Object> reference = nested(payload, "orderReference");
        String status = first(reference, "status", "Status");
        if (blank(status)) status = first(payload, "Status", "status");
        return blank(status) ? fallback : status;
    }

    private static String first(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            String value = string(source.get(key));
            if (!blank(value)) return value;
        }
        return null;
    }

    private static String required(Map<String, Object> details, String key) {
        String value = string(details.get(key));
        if (blank(value)) throw new IllegalArgumentException(key + " is required for this payment method");
        return value;
    }

    private static String value(Map<String, Object> details, String key, String fallback) {
        String value = string(details.get(key));
        return blank(value) ? fallback : value;
    }

    private static String string(Object value) { return value == null ? null : String.valueOf(value); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static int safeInt(long value) { return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value; }
}
