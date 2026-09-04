package io.github.patelsa032766.gmopayments.gmo;

import io.github.patelsa032766.gmopayments.application.port.PaymentGateway;
import io.github.patelsa032766.gmopayments.domain.PaymentExecutionContext;
import io.github.patelsa032766.gmopayments.domain.PaymentContinuationResult;
import io.github.patelsa032766.gmopayments.domain.PaymentGatewayResult;
import io.github.patelsa032766.gmopayments.domain.PaymentNextAction;
import io.github.patelsa032766.gmopayments.domain.ProviderCallEvidence;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

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
    public PaymentContinuationResult executeCheckout(PaymentExecutionContext context, Map<String, Object> details) {
        if (!properties.isLiveCallsEnabled()) return single(simulate(context));
        return switch (context.method()) {
            case CARD -> card(context, details);
            case PAYPAY -> payPay(context, details);
            case BANK_DIRECT_REALTIME -> single(bankDirectRegistration(context, details));
            case KOZA_FURIKAE_SELECT -> single(kozaRegistration(context, details));
            case KOMBINI -> single(cash(context, details, "KONBINI"));
            case PAYEASY -> single(cash(context, details, "PAYEASY"));
            case FURIKOMI -> single(cash(context, details, "BANK_TRANSFER_GMO_AOZORA"));
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

    @Override
    public PaymentGatewayResult capture(PaymentExecutionContext context, String providerAccessId,
                                        String providerOrderId) {
        if (context.method() != io.github.patelsa032766.gmopayments.domain.PaymentMethodCode.CARD
                && context.method() != io.github.patelsa032766.gmopayments.domain.PaymentMethodCode.PAYPAY) {
            throw new IllegalArgumentException("Only Card and PayPay authorizations can be captured");
        }
        var request = requests.orderCapture(providerAccessId, providerOrderId);
        if (!properties.isLiveCallsEnabled()) {
            return new PaymentGatewayResult("PAID", "SALES", providerOrderId, providerAccessId,
                    "PAYMENT_CAPTURED", "Authorization captured", false, PaymentNextAction.none(),
                    Map.of(), "OPENAPI", "OrderCapture", "/order/capture", 201, 0,
                    request, Map.of("orderReference", Map.of("status", "SALES",
                            "accessId", providerAccessId)));
        }
        var response = openApi.post("/order/capture", request, true,
                providerIdempotency(context, "capture"));
        return openApiResult(context, response, request, "/order/capture", "OrderCapture",
                "Authorization captured");
    }

    @Override
    public PaymentContinuationResult continueCheckout(PaymentExecutionContext context,
                                                      Map<String, Object> browserReturn) {
        if (!properties.isLiveCallsEnabled()) {
            return new PaymentContinuationResult(simulate(context), List.of());
        }
        return switch (context.method()) {
            case PAYPAY -> completePayPay(context, browserReturn);
            case BANK_DIRECT_REALTIME -> completeBankDirect(context, browserReturn);
            case KOZA_FURIKAE_SELECT -> completeKozaRegistration(context, browserReturn);
            default -> throw new IllegalArgumentException("This method has no browser-return continuation");
        };
    }

    /**
     * Verifies the PayPay consent result and immediately authorizes the first
     * premium against the newly registered on-file wallet.
     *
     * <p>The callback envelope is only a locator. GMO's order inquiry is the
     * authority for the {@code REGISTER} state. The financial write happens
     * only after that server-to-server check succeeds.</p>
     */
    private PaymentContinuationResult completePayPay(PaymentExecutionContext context,
                                                      Map<String, Object> browserReturn) {
        String registrationAccessId = firstObject(browserReturn, "accessId", "AccessID", "AccessId");
        if (blank(registrationAccessId)) {
            throw new GmoProviderException("PayPay return omitted the GMO access ID",
                    400, false, false, Map.of(), null);
        }

        List<ProviderCallEvidence> exchanges = new ArrayList<>();
        var inquiryRequest = requests.orderInquiry(registrationAccessId);
        var inquiry = openApi.post("/order/inquiry", inquiryRequest, false);
        String registrationStatus = status(inquiry.rawPayload(), "UNPROCESSED").toUpperCase();
        exchanges.add(evidence("OPENAPI", "OrderInquiry", "/order/inquiry",
                inquiryRequest, inquiry, registrationStatus));

        if (!"REGISTER".equals(registrationStatus)) {
            String state = switch (registrationStatus) {
                case "REQSUCCESS", "AUTHPROCESS", "UNPROCESSED" -> "REGISTRATION_PENDING";
                default -> "FAILED";
            };
            var outcome = new PaymentGatewayResult(state, registrationStatus,
                    context.applicationNumber(), registrationAccessId,
                    "PAYPAY_REGISTRATION_RESULT",
                    "REGISTRATION_PENDING".equals(state)
                            ? "PayPay account authorization is still being confirmed"
                            : "PayPay account authorization was not completed",
                    false, PaymentNextAction.none(), Map.of(), "OPENAPI", "OrderInquiry",
                    "/order/inquiry", inquiry.statusCode(), safeInt(inquiry.durationMs()),
                    GmoSanitizer.sanitize(inquiryRequest), inquiry.sanitizedPayload());
            return new PaymentContinuationResult(outcome, exchanges);
        }

        var chargeRequest = requests.savedPayPayCharge(facts(context), context.customerCode(),
                context.executionMode().name());
        var charge = openApi.post("/wallet/on-file/charge", chargeRequest, true,
                providerIdempotency(context, "paypay-first-auth"));
        var chargeOutcome = openApiResult(context, charge, chargeRequest,
                "/wallet/on-file/charge", "WalletOnFileCharge",
                "PayPay registered and first premium authorized");
        exchanges.add(evidence("OPENAPI", "WalletOnFileCharge", "/wallet/on-file/charge",
                chargeRequest, charge, chargeOutcome.canonicalState()));
        var outcome = new PaymentGatewayResult(chargeOutcome.canonicalState(),
                registrationStatus + "/" + chargeOutcome.providerStatus(),
                chargeOutcome.providerOrderId(), chargeOutcome.providerAccessId(),
                "PAYPAY_REGISTERED_AND_AUTHORIZED",
                "PayPay registered and first premium authorized",
                chargeOutcome.requiresAttention(), chargeOutcome.nextAction(),
                chargeOutcome.instructions(), chargeOutcome.transport(),
                chargeOutcome.providerOperation(), chargeOutcome.endpoint(),
                chargeOutcome.httpStatus(), chargeOutcome.durationMs(),
                chargeOutcome.sanitizedRequest(), chargeOutcome.sanitizedResponse());
        return new PaymentContinuationResult(outcome, exchanges);
    }

    private PaymentContinuationResult completeBankDirect(PaymentExecutionContext context,
                                                         Map<String, Object> browserReturn) {
        validateBankDirectReturn(context, browserReturn);
        String returnedStatus = value(browserReturn, "Status", value(browserReturn, "status", "FAIL"))
                .toUpperCase();
        if (!"REGISTER".equals(returnedStatus)) {
            var outcome = new PaymentGatewayResult("FAILED", returnedStatus,
                    context.applicationNumber(), firstObject(browserReturn, "TranID", "TranId"),
                    "BANK_DIRECT_REGISTRATION_FAILED", "Bank account registration was not completed",
                    false, PaymentNextAction.none(), Map.of(), "BROWSER_RETURN", "BankDirectStart",
                    null, 200, 0, Map.of(), GmoSanitizer.sanitize(browserReturn));
            return new PaymentContinuationResult(outcome, List.of());
        }

        List<ProviderCallEvidence> exchanges = new ArrayList<>();
        var inquiryRequest = requests.bankDirectInquiry(context.customerCode());
        var inquiry = idPass.post("SearchBankDirect.idPass", inquiryRequest, false);
        exchanges.add(evidence("IDPASS", "SearchBankDirect", "SearchBankDirect.idPass",
                inquiryRequest, inquiry, "REGISTERED"));

        var entryRequest = requests.bankDirectEntry(facts(context));
        var entry = idPass.post("EntryTranBankDirect.idPass", entryRequest, true);
        exchanges.add(evidence("IDPASS", "EntryTranBankDirect", "EntryTranBankDirect.idPass",
                entryRequest, entry, "REGISTERED"));
        String accessId = first(entry.rawPayload(), "AccessID", "AccessId");
        String accessPass = first(entry.rawPayload(), "AccessPass", "AccessPASS");
        if (blank(accessId) || blank(accessPass)) {
            throw new GmoProviderException("GMO bank-direct entry omitted access credentials",
                    entry.statusCode(), false, false, entry.sanitizedPayload(), null);
        }

        var executionRequest = requests.bankDirectExecution(facts(context), accessId, accessPass);
        var execution = idPass.post("ExecTranBankDirect.idPass", executionRequest, true);
        String providerStatus = status(execution.rawPayload(), "PROCESSING");
        String canonical = canonicalState(providerStatus);
        exchanges.add(evidence("IDPASS", "ExecTranBankDirect", "ExecTranBankDirect.idPass",
                executionRequest, execution, canonical));
        var outcome = new PaymentGatewayResult(canonical, providerStatus, context.applicationNumber(), accessId,
                "BANK_DIRECT_DEBIT_RESULT", "Bank account registered and immediate debit submitted",
                "UNKNOWN".equals(canonical), PaymentNextAction.none(), Map.of(), "IDPASS",
                "ExecTranBankDirect", "ExecTranBankDirect.idPass", execution.statusCode(),
                safeInt(execution.durationMs()), GmoSanitizer.sanitize(asObjectMap(executionRequest)),
                execution.sanitizedPayload());
        return new PaymentContinuationResult(outcome, exchanges);
    }

    private PaymentContinuationResult completeKozaRegistration(PaymentExecutionContext context,
                                                               Map<String, Object> browserReturn) {
        String transactionId = firstObject(browserReturn, "TranID", "TranId", "TransactionID", "transactionId");
        var inquiryRequest = requests.kozaRegistrationInquiry(transactionId);
        var inquiry = idPass.post("BankAccountTranResult.idPass", inquiryRequest, false);
        String registrationResult = first(inquiry.rawPayload(), "Result", "Status", "result", "status");
        if (blank(registrationResult)) registrationResult = "PEND";
        registrationResult = registrationResult.toUpperCase();
        List<ProviderCallEvidence> exchanges = new ArrayList<>();
        exchanges.add(evidence("IDPASS", "BankAccountTranResult", "BankAccountTranResult.idPass",
                inquiryRequest, inquiry, registrationResult));

        if (!"SUCCESS".equals(registrationResult)) {
            String state = "PEND".equals(registrationResult) ? "REGISTRATION_PENDING" : "FAILED";
            var outcome = new PaymentGatewayResult(state, registrationResult, context.applicationNumber(),
                    transactionId, "KOZA_REGISTRATION_RESULT",
                    "PEND".equals(registrationResult)
                            ? "Koza Furikae registration is still pending"
                            : "Koza Furikae registration was not completed",
                    false, PaymentNextAction.none(), Map.of(), "IDPASS", "BankAccountTranResult",
                    "BankAccountTranResult.idPass", inquiry.statusCode(), safeInt(inquiry.durationMs()),
                    GmoSanitizer.sanitize(asObjectMap(inquiryRequest)), inquiry.sanitizedPayload());
            return new PaymentContinuationResult(outcome, exchanges);
        }

        // The recurring mandate is now confirmed. The first premium is a
        // distinct Furikomi transaction, issued immediately in the same user
        // journey as required by the checkout design.
        var cashRequest = requests.cashCharge(facts(context), "BANK_TRANSFER_GMO_AOZORA", "",
                properties.getMerchant().getContactEmail(), properties.getMerchant().getContactPhone(), "");
        var cash = openApi.post("/cash/charge", cashRequest, true,
                providerIdempotency(context, "koza-first-furikomi"));
        Map<String, Object> instructions = nested(cash.rawPayload(), "cashResult");
        String cashStatus = status(cash.rawPayload(), "TRADING");
        exchanges.add(evidence("OPENAPI", "CashCharge", "/cash/charge", cashRequest, cash,
                "INSTRUCTIONS_ISSUED"));
        var outcome = new PaymentGatewayResult("MANDATE_REGISTERED_TRANSFER_DUE",
                registrationResult + "/" + cashStatus, context.applicationNumber(),
                accessId(cash.rawPayload()), "KOZA_REGISTERED_FURIKOMI_ISSUED",
                "Koza Furikae registered and first-premium transfer instructions issued", false,
                PaymentNextAction.none(), instructions, "OPENAPI", "CashCharge", "/cash/charge",
                cash.statusCode(), safeInt(cash.durationMs()), GmoSanitizer.sanitize(cashRequest),
                cash.sanitizedPayload());
        return new PaymentContinuationResult(outcome, exchanges);
    }

    private void validateBankDirectReturn(PaymentExecutionContext context, Map<String, Object> fields) {
        String transactionId = firstObject(fields, "TranID", "TranId");
        String siteId = firstObject(fields, "SiteID", "SiteId");
        String memberId = firstObject(fields, "MemberID", "MemberId");
        String status = firstObject(fields, "Status", "status");
        if (!properties.getSiteId().equals(siteId) || !context.customerCode().equals(memberId)) {
            throw new GmoProviderException("GMO bank-direct return did not match the reserved customer",
                    400, false, false, Map.of(), null);
        }
        if ("REGISTER".equalsIgnoreCase(status)) {
            String checkString = firstObject(fields, "CheckString", "checkString");
            String expected = sha256(transactionId + siteId + memberId + status);
            if (blank(checkString) || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                    checkString.toLowerCase().getBytes(StandardCharsets.US_ASCII))) {
                throw new GmoProviderException("GMO bank-direct return integrity check failed",
                        400, false, false, Map.of(), null);
            }
        }
    }

    private static ProviderCallEvidence evidence(String transport, String operation, String endpoint,
                                                 Map<String, ?> request, GmoHttpResult response,
                                                 String outcome) {
        return new ProviderCallEvidence(transport, operation, endpoint, response.statusCode(),
                safeInt(response.durationMs()), GmoSanitizer.sanitize(new LinkedHashMap<>(request)),
                response.sanitizedPayload(), outcome);
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /**
     * Stable per-local-operation key used by GMO OpenAPI's idempotency layer.
     * Different steps in one checkout receive different keys; repeating the
     * same reserved step after a client disconnect receives the same key.
     */
    private static String providerIdempotency(PaymentExecutionContext context, String operation) {
        return sha256(context.transactionId() + "|" + operation).substring(0, 32);
    }

    private static String firstObject(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            String value = string(source.get(key));
            if (!blank(value)) return value.trim();
        }
        return null;
    }

    private PaymentGatewayResult savedCard(PaymentExecutionContext context, Map<String, Object> instrument,
                                           Map<String, Object> command) {
        String mode = value(command, "authorizationMode", "CAPTURE").toUpperCase();
        var payload = requests.savedCardCharge(facts(context), required(instrument, "memberId"),
                value(instrument, "cardType", "CREDIT_CARD"), value(instrument, "cardId",
                        value(instrument, "instrumentReference", "")),
                value(instrument, "cardholderName", ""), mode);
        var result = openApi.post("/credit/on-file/charge", payload, true,
                providerIdempotency(context, "card-mit"));
        return openApiResult(context, result, payload, "/credit/on-file/charge",
                "CreditOnFileCharge", "Stored card payment submitted");
    }

    private PaymentGatewayResult savedPayPay(PaymentExecutionContext context, Map<String, Object> instrument,
                                             Map<String, Object> command) {
        String mode = value(command, "authorizationMode", "CAPTURE").toUpperCase();
        var payload = requests.savedPayPayCharge(facts(context), required(instrument, "memberId"), mode);
        var result = openApi.post("/wallet/on-file/charge", payload, true,
                providerIdempotency(context, "paypay-mit"));
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

    private PaymentContinuationResult card(PaymentExecutionContext context, Map<String, Object> details) {
        String token = required(details, "token");
        String holder = required(details, "holderName").toUpperCase();
        String authorizationMode = value(details, "authorizationMode", "AUTH").toUpperCase();
        var chargeRequest = requests.cardCharge(facts(context), token, holder, authorizationMode);
        var charge = openApi.post("/credit/charge", chargeRequest, true,
                providerIdempotency(context, "card-first-auth"));
        var chargeOutcome = openApiResult(context, charge, chargeRequest, "/credit/charge",
                "CreditCharge", "Card authorization submitted");
        var exchanges = new ArrayList<ProviderCallEvidence>();
        exchanges.add(evidence("OPENAPI", "CreditCharge", "/credit/charge",
                chargeRequest, charge, chargeOutcome.canonicalState()));

        // Only a successful authorization/capture may be used as the referrer
        // for GMO's store-card operation.
        if (!"AUTHORIZED".equals(chargeOutcome.canonicalState())
                && !"PAID".equals(chargeOutcome.canonicalState())) {
            return new PaymentContinuationResult(chargeOutcome, exchanges);
        }
        String accessId = chargeOutcome.providerAccessId();
        if (blank(accessId)) {
            var attention = withAttention(chargeOutcome,
                    "Card payment succeeded but the reusable-card reference was missing");
            return new PaymentContinuationResult(attention, exchanges);
        }

        var storeRequest = requests.storeCard(accessId, context.customerCode(), context.customerName());
        try {
            var stored = openApi.post("/credit/storeCard", storeRequest, true,
                    providerIdempotency(context, "card-store"));
            exchanges.add(evidence("OPENAPI", "CreditStoreCard", "/credit/storeCard",
                    storeRequest, stored, "STORED"));
            Map<String, Object> combined = new LinkedHashMap<>();
            combined.put("charge", charge.sanitizedPayload());
            combined.put("storedCard", stored.sanitizedPayload());
            var outcome = new PaymentGatewayResult(chargeOutcome.canonicalState(),
                    chargeOutcome.providerStatus(), chargeOutcome.providerOrderId(), accessId,
                    "CARD_AUTHORIZED_AND_STORED", "Card authorized and saved for recurring payments",
                    false, chargeOutcome.nextAction(), chargeOutcome.instructions(), "OPENAPI",
                    "CreditStoreCard", "/credit/storeCard", stored.statusCode(),
                    safeInt(stored.durationMs()), GmoSanitizer.sanitize(storeRequest), combined);
            return new PaymentContinuationResult(outcome, exchanges);
        } catch (GmoProviderException exception) {
            // The financial authorization is already conclusive. A failure to
            // store the reusable card must not mislabel that charge as failed
            // or cause it to be sent again.
            exchanges.add(new ProviderCallEvidence("OPENAPI", "CreditStoreCard", "/credit/storeCard",
                    exception.statusCode(), null, GmoSanitizer.sanitize(storeRequest),
                    exception.sanitizedPayload(), exception.outcomeUnknown()
                            ? "STORE_OUTCOME_UNKNOWN" : "STORE_FAILED"));
            var attention = withAttention(chargeOutcome,
                    "Card authorized; reusable-card setup requires operator review");
            return new PaymentContinuationResult(attention, exchanges);
        }
    }

    private PaymentContinuationResult payPay(PaymentExecutionContext context, Map<String, Object> details) {
        var request = requests.payPayRecurringRegistration(facts(context));
        var result = openApi.post("/wallet/authorizeAccount", request, true,
                providerIdempotency(context, "paypay-register"));
        Map<String, Object> order = nested(result.rawPayload(), "orderReference");
        String accessId = string(order.get("accessId"));
        String redirect = first(nested(result.rawPayload(), "redirectInformation"), "redirectUrl");
        if (blank(accessId) || blank(redirect)) {
            throw new GmoProviderException("GMO PayPay authorization omitted browser handoff fields",
                    result.statusCode(), false, false, result.sanitizedPayload(), null);
        }
        var outcome = new PaymentGatewayResult("REGISTRATION_PENDING",
                string(order.getOrDefault("status", "REQSUCCESS")),
                context.applicationNumber(), accessId, "PAYPAY_REGISTRATION_STARTED",
                "PayPay account authorization started", false,
                new PaymentNextAction("REDIRECT", redirect, Map.of()), Map.of(), "OPENAPI",
                "WalletAuthorizeAccount", "/wallet/authorizeAccount", result.statusCode(),
                safeInt(result.durationMs()), GmoSanitizer.sanitize(request), result.sanitizedPayload());
        return new PaymentContinuationResult(outcome, List.of(evidence("OPENAPI",
                "WalletAuthorizeAccount", "/wallet/authorizeAccount", request, result,
                "REGISTRATION_PENDING")));
    }

    private static PaymentContinuationResult single(PaymentGatewayResult result) {
        var exchange = new ProviderCallEvidence(result.transport(), result.providerOperation(),
                result.endpoint(), result.httpStatus(), result.durationMs(), result.sanitizedRequest(),
                result.sanitizedResponse(), result.canonicalState());
        return new PaymentContinuationResult(result, List.of(exchange));
    }

    private static PaymentGatewayResult withAttention(PaymentGatewayResult source, String summary) {
        return new PaymentGatewayResult(source.canonicalState(), source.providerStatus(),
                source.providerOrderId(), source.providerAccessId(),
                "RECURRING_INSTRUMENT_SETUP_REVIEW", summary, true, source.nextAction(),
                source.instructions(), source.transport(), source.providerOperation(), source.endpoint(),
                source.httpStatus(), source.durationMs(), source.sanitizedRequest(),
                source.sanitizedResponse());
    }

    private PaymentGatewayResult cash(PaymentExecutionContext context, Map<String, Object> details,
                                      String cashType) {
        var payload = requests.cashCharge(facts(context), cashType, value(details, "nameKana", ""),
                value(details, "email", properties.getMerchant().getContactEmail()),
                value(details, "phone", properties.getMerchant().getContactPhone()),
                value(details, "konbiniCode", "LAWSON"));
        var result = openApi.post("/cash/charge", payload, true,
                providerIdempotency(context, "cash-" + cashType.toLowerCase()));
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
            case CARD, PAYPAY -> context.executionMode() == io.github.patelsa032766.gmopayments.domain.PaymentExecutionMode.AUTH
                    ? "AUTHORIZED" : "PAID";
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
            case "REQSUCCESS", "TRADING", "AUTHPROCESS", "UNPROCESSED" -> "PROCESSING";
            case "REGISTER" -> "REGISTERED";
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
