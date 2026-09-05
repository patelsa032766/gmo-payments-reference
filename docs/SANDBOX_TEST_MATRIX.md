# GMO Sandbox Validation Matrix

This document records controlled, live-sandbox validation separately from the
deterministic automated suite. It is evidence about one merchant sandbox at one
point in time, not a promise that another merchant contract enables the same
products or modes.

Last run: **5 September 2026**  
Application: Java 21 / Spring Boot implementation  
Provider mode: `GMO_LIVE_CALLS_ENABLED=true`  
Public return origin: deployment-local Cloudflare route (the hostname and
tunnel configuration remain outside the public repository)

No production cards, bank accounts, or customers were used. Test amounts and
references below exist only in the local SQLite runtime and GMO sandbox. The
runtime database is ignored by Git.

## Result vocabulary

- **Completed** means GMO returned a conclusive financial status.
- **Accepted / handoff required** means GMO created the request and returned a
  browser redirect or form-post. A person must complete the external PayPay or
  bank page before financial success can be asserted.
- **Provider rejected** means the Java adapter reached GMO and persisted GMO's
  conclusive error response. It is not a simulation or transport failure.
- **Not applicable** means the payment method does not offer that execution
  mode by design.

## Customer-initiated payment (CIT)

| Method | Immediate sale | Authorization + later capture | Recurring setup / continuation | Evidence |
| --- | --- | --- | --- | --- |
| Card | **Completed.** `/credit/charge` returned `CAPTURE`; recurring plans then called `/credit/storeCard`. Example `TXN-CARD-8D025861F292`. | **Completed.** `/credit/charge` returned `AUTH`, `/credit/storeCard` registered the card, and `/order/capture` returned `SALES` in the same thread. Example `TXN-CARD-150ED3D53428`. | A recurring application stores the successful card; a one-time application now deliberately stops after the charge. | Real HTTP 201 exchanges are visible under API & Webhooks. MP token generation remains browser-only; PAN/CVC never enters Spring or SQLite. |
| PayPay one-time | **Provider rejected in this merchant sandbox.** `/wallet/charge` with `CAPTURE` returned HTTP 400 `invalid_parameter`. Example `TXN-PAYPAY-6E6B64FA883B`. | **Accepted / handoff required.** `/wallet/charge` with `AUTH` returned `REQSUCCESS` and a PayPay sandbox redirect. Example `TXN-PAYPAY-056FB5E13527`. Capture becomes available only after customer approval produces `AUTH`. | Not applicable for a one-time plan; no reusable instrument is created. | The public schema lists both modes, but merchant/product enablement is authoritative. Do not translate a provider rejection into a simulated success. |
| PayPay recurring | Not a single operation: consent and the first charge are separate GMO orders. | **Accepted / handoff required.** `/wallet/authorizeAccount` returned `REQSUCCESS`. Example `TXN-PAYPAY-AD8238EE221D`. After a verified `REGISTER` callback, Java submits a distinct `/wallet/on-file/charge` order and can later capture an `AUTH`. | The already completed sandbox registration for `CUST-10044` was verified with read-only `/member/inquiry`; the registered acceptance code is not documented here. | The first charge now has its own `orderId`. A successful registration is retained even if that first charge fails, while checkout remains `FAILED` and attention-required. |
| Real-time bank debit (`口座直結決済`) | The payment-specific flow is registration followed by immediate debit; it has no card-style sale selector. | Not applicable. | **Accepted / handoff required.** `BankDirectRegist.idPass` returned a real `BankDirectStart.idPass` form handoff. Example `TXN-BANKDIRECT-98FE90E4FE04`. On verified return Java runs account inquiry, Entry, then Exec. | This remains distinct from Koza Furikae in codes, UI, storage, endpoints, and lifecycle. |
| Koza Furikae Select (`口座振替（セレクト）`) | Not applicable. The first premium is Furikomi, not an immediate Koza debit. | Not applicable. | **Accepted / handoff required.** `BankAccountEntry.idPass` returned a real `BankAccountStart.idPass` form handoff. Example `TXN-KOZAFURIKAE-11CD0794FFC8`. A successful return triggers verified mandate inquiry and first-premium Furikomi instructions. | Monthly-only. The browser return is a locator; Java verifies it server-to-server before provisioning the mandate. |
| Kombini | **Completed asynchronously.** `/cash/charge` returned `REQSUCCESS` with Lawson receipt/confirmation data. After payment in the GMO portal, `/order/inquiry` returned `PAYSUCCESS`; the `CASH_PAID` notification advanced the same thread to `PAID`. Example `TXN-KOMBINI-4978CD8A1FCF`. | Not applicable. | Not applicable. | Kana is normalized to remove ASCII/full-width spaces because GMO rejects spaces in `payer.nameKana`. |
| Pay-easy | **Instructions issued.** `/cash/charge` returned `REQSUCCESS`. Example `TXN-PAYEASY-5FF3BCAEAF80`. | Not applicable. | Not applicable. | Financial completion arrives later by webhook and/or reconciliation. |
| Furikomi | **Instructions issued.** `/cash/charge` returned `TRADING` and a GMO Aozora virtual account. Example `TXN-FURIKOMI-D4B4A35FABAD`. | Not applicable. | Not applicable. | One-time only in checkout; the command boundary now rejects it for monthly applications even if a client bypasses the UI. |

## Merchant-initiated payment (MIT)

| Method | Immediate sale | Authorization + capture | Result |
| --- | --- | --- | --- |
| Saved card | **Completed.** `/credit/on-file/charge` returned `CAPTURE`. Example `TXN-MIT-BE2EA9BAF43C`. | **Completed.** charge returned `AUTH`; `/order/capture` returned `SALES` on the same thread. Example `TXN-MIT-637DA92740B5`. | Both configured operator choices are real sandbox operations. |
| Saved PayPay | **Provider rejected in this merchant sandbox.** `/wallet/on-file/charge` with `CAPTURE` returned `invalid_parameter`. Example `TXN-MIT-6F438F4D1220`. | **Completed.** `/wallet/on-file/charge` returned `AUTH`; `/order/capture` returned `SALES`. Example `TXN-MIT-891538167F47`. | The verified sandbox member succeeds with AUTH/capture. The original preloaded `GMO-MEMBER-10042` fixture is not a real sandbox member and correctly returned `member_not_found`; fixture data must not be mistaken for provider registration. |
| Real-time bank debit | Method-specific immediate debit only. | Not applicable. | **Completed.** `EntryTranBankDirect.idPass` and `ExecTranBankDirect.idPass` are now both retained; Exec returned `CAPTURE`. Example `TXN-MIT-E6D22B8C996C`. |
| Koza monthly batch | Asynchronous scheduled debit only. | Not applicable. | The batch and one transaction per item were created. Entry was accepted, then Exec was conclusively rejected because the preloaded `GMO-MEMBER-10046` fixture is not a registered sandbox mandate. Example `TXN-KOZA-51B0DF5E47B1`. Both Entry and Exec exchanges are retained. Complete the real Koza browser registration before expecting `REQSUCCESS` and later `PAYSUCCESS`/`PAYFAIL`. |
| Kombini / Pay-easy / Furikomi | Not supported as saved-method MIT. | Not applicable. | These are customer instruction flows, not reusable payment instruments. |

## Webhook and callback reachability

The local Cloudflare ingress was checked at the exact public OpenAPI webhook
path. A valid, duplicate `CASH_PAID` delivery returned HTTP 200 and
`duplicate:true`, proving the hostname, restricted tunnel rule, authentication,
deduplication, and local backend were connected. GMO notifications must use:

- `POST /webhooks/gmo/openapi`
- `POST /webhooks/gmo/protocol`
- the documented browser-return endpoints under `/api/v1/gmo/returns` and
  `/webhooks/gmo/protocol/return`

The generic public distribution contains no KanjiAI hostname, Cloudflare tunnel
ID, credentials path, or token. Each deployment supplies its own public base
URL and edge configuration.

## Corrections made during this run

1. Checkout execution is plan-aware. One-time card/PayPay flows no longer create
   reusable instruments, while monthly flows do.
2. Recurring PayPay consent and the first charge use separate GMO order IDs.
3. A confirmed PayPay registration survives a failed first charge without
   falsely marking the premium paid.
4. Direct PayPay browser returns are verified with `/order/inquiry`.
5. Provider order IDs stored in SQLite now use GMO's actual order reference.
6. Cash payer Kana removes spaces before `/cash/charge`.
7. MIT failures retain sanitized outbound/inbound evidence.
8. Multi-step Bank Direct and Koza MIT threads retain every Entry/Exec exchange,
   including successful setup calls preceding a rejected financial call.
9. The backend repeats method enablement, threshold, plan, channel, and eKYC
   checks at command submission; the Angular list is not a security boundary.
10. One-time checkout no longer claims that reusable-capable methods will be
    available for future monthly payments.
11. Cash webhooks now authenticate and link by `accessId` when GMO omits
    `orderId`, and their `event` value drives the canonical status projection.

## Re-running safely

1. Start with simulation and run `./scripts/check.sh`.
2. Use a dedicated GMO sandbox and synthetic customers.
3. Enable one product at a time, use a unique idempotency key and merchant
   reference, and inspect its thread before continuing.
4. Treat redirects/form posts as pending until the customer completes them and
   Java verifies the provider result.
5. Do not automatically repeat `UNKNOWN` financial writes. Inquire first.
6. Never test MIT with the public seed references. Register or provider-inquire
   a real sandbox member/mandate first.

GMO's current OpenAPI reference is the authority for request fields and generic
capabilities; the merchant's contracted sandbox configuration is the authority
for which products and execution modes are actually accepted.
