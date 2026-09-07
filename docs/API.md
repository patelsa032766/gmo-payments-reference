# REST API Guide

Application endpoints use `/api/v1`. Webhook endpoints intentionally live at `/webhooks/gmo` because GMO calls them directly. JSON errors use RFC Problem Details and never echo provider secrets or unsanitized payloads.

## Command conventions

- Customer payment, MIT, and capture commands require a stable `Idempotency-Key` header.
- Operator mutations require `X-Operator-Token`; optionally supply `X-Operator-Id` for audit attribution.
- Repeating an idempotency key with the same fingerprint returns the original result. Reusing it for different input is rejected.
- Enum-like payment method input uses public values: `card`, `paypay`, `bankDirect`, `kozaFurikae`, `kombini`, `payeasy`, and `furikomi`.
- Timestamps are UTC ISO-8601. Angular renders the operator-selected time zone.
- Card commands accept an MP token only. Raw PAN/CVC field names are rejected defensively.

## Checkout eligibility

```http
GET /api/v1/checkout/options?channel=PA&amountJpy=10000&monthly=true&ekycVerified=true&language=en
```

| Parameter | Values | Effect |
| --- | --- | --- |
| `channel` | `PA`, `IA`, `FI` | Distribution-channel rule |
| `amountJpy` | Positive integer | Minimum/maximum thresholds |
| `monthly` | Boolean | Monthly plans receive recurring-capable methods only |
| `ekycVerified` | Boolean | Real-time bank-debit eligibility ceiling |
| `language` | `en`, `ja` | Published customer label and description |

The backend returns only eligible methods, already ordered. Checkout must not duplicate policy logic. The payment command repeats the enabled, amount, plan, channel, and eKYC checks against the same published release, so directly constructing a request cannot bypass the options policy.

## Start a checkout application

```http
POST /api/v1/checkout/applications
Content-Type: application/json

{ "templateApplicationNumber": "APP-20260829-022" }
```

Configuration selects a reusable synthetic template. This command copies its
customer, amount, plan, channel, and policy into a new persisted application
whose number follows `APP-yyyyMMdd-NNN`. It must be called once when a new
checkout journey begins—not again for each payment attempt or provider return.
`GET /api/v1/checkout/applications/{applicationNumber}` restores the exact
application after a browser handoff.

## Browser payment configuration

```http
GET /api/v1/checkout/browser-configuration
```

Returns safe browser values such as simulation/live mode, MP Token script URL, and public Shop ID where needed. Passwords are never returned.

## Submit a checkout payment

```http
POST /api/v1/checkout/applications/APP-20260906-001/payments
Idempotency-Key: 4a2910e8-40fc-41aa-a913-4be2d9383ad1
Content-Type: application/json

{
  "method": "card",
  "details": {
    "token": "browser-generated-mp-token",
    "holderName": "AIKO TANAKA"
  }
}
```

The response includes `transactionId`, canonical `state`, `method`, `requiresAttention`, optional `nextAction`, and method-specific instructions. `REDIRECT` or `FORM_POST` means the browser must complete provider registration. The customer then returns through the configured callback and Angular reloads the result with:

```http
GET /api/v1/checkout/payments/{transactionId}
```

### Method detail fields

| Method | Important details | Result model |
| --- | --- | --- |
| Card | `token`, `holderName` | Published CIT policy chooses `AUTH` or immediate `CAPTURE`; a successful monthly-plan charge is stored, while a one-time plan stops after the charge |
| PayPay | No sensitive account input | One-time plans call `/wallet/charge`; monthly plans run recurring consent, inquiry, then a separately identified first on-file charge |
| Real-time bank debit | Bank/account registration fields required by enabled contract | Registration form post, inquiry, immediate debit |
| Koza Furikae | Registration bank fields | Registration form post and inquiry create a no-charge mandate row; a distinct linked Furikomi row contains the first-premium amount and instructions |
| Kombini | Customer/contact and store code | Instructions issued |
| Pay-easy | Customer/contact fields | Instructions issued |
| Furikomi | Customer/contact fields | Bank-transfer instructions issued |

The actual field matrix varies by the GMO product contract and supported bank. Validate the enabled sandbox product documentation before live mode.

## Configuration workspace

```http
GET    /api/v1/configuration/active
GET    /api/v1/configuration/workspace
GET    /api/v1/configuration/experience
PUT    /api/v1/configuration/experience
PUT    /api/v1/configuration/draft
POST   /api/v1/configuration/draft/publish
DELETE /api/v1/configuration/draft
```

The experience resource returns predefined customer/application templates and persists the selected template, due-today amount, payment plan (`ONE_TIME` or `MONTHLY`), checkout language, and `operatorTokenRequired`. The selected plan is not decorative: it is copied to each new application, passed into checkout eligibility, and revalidated when payment submission is reserved. `MONTHLY` exposes reusable methods and the monthly-only combined Koza Furikae registration plus first-premium Furikomi journey. `ONE_TIME` excludes that mandate-registration product and can expose ordinary Furikomi instead. When the saved operator flag is enabled, configuration changes, capture, MIT payments, payment-order changes, Koza batches, and manual SFTP reconciliation all require `X-Operator-Token`.

Example experience update:

```json
{
  "applicationNumber": "APP-20260821-001",
  "amountJpy": 10000,
  "paymentPlan": "MONTHLY",
  "operatorTokenRequired": false,
  "checkoutLanguage": "en"
}
```

The experience `PUT` may set `operatorTokenRequired` to `false` without a
credential so a fresh local demonstration can opt out of operator prompts. The
Angular client persists that transition before publishing the associated draft,
so the rest of the save follows the newly disabled policy. This bypass applies
to every operator mutation and is local-testing functionality only. Re-enabling
it requires a valid environment-backed token; production identity and
authorization must replace this shared-token model.

A draft request contains the complete ordered method collection; each item includes `code`, `enabled`, `recurring`, `monthlyOnly`, `minimumAmountJpy`, `maximumAmountJpy`, `citExecutionMode`, and display order. `citExecutionMode` is merchant policy—not customer input—and is meaningful for Card and PayPay. Publishing is atomic: the previous release retires and the draft becomes the one published release.

## Operator transaction threads

```http
GET /api/v1/operations/transactions
GET /api/v1/operations/transactions/{transactionId}
```

The list returns current projections, including `transactionRole`, requested `amountJpy`, and cumulative `settledAmountJpy`. The detail response returns the selected transaction's complete linked family, ordered events, and sanitized provider exchanges. One lifecycle event may own several ordered exchanges—for example, real-time Bank Direct performs `SearchBankDirect`, amount-bearing `EntryTranBankDirect`, and `ExecTranBankDirect`. The operator UI exposes each call rather than collapsing the event to its first exchange. Every later refund, chargeback, webhook, inquiry, retry, browser return, and SFTP match remains in that family.

### Capture an authorization

```http
POST /api/v1/operations/transactions/TXN-CARD-123/capture
X-Operator-Token: local-operator-token
Idempotency-Key: c9f17020-d873-40b2-84c5-d73b7e8fa404
```

The action is available only when the selected Card or PayPay transaction is `AUTHORIZED`. It sends GMO `/order/capture`, updates the original transaction projection, and appends `CAPTURE_REQUESTED`, `PAYMENT_CAPTURED` (or failure/unknown evidence), and the sanitized provider exchange to that same thread. The operator UI requires a separate confirmation because this is a financial write. A timeout becomes `UNKNOWN`; do not send another capture until inquiry establishes the provider state.

### Recover a missed Koza notification

```http
POST /api/v1/operations/transactions/TXN-KOZA-123/refresh
X-Operator-Token: local-operator-token
```

For a Koza transaction in `SCHEDULED`, `PROCESSING`, or `UNKNOWN`, this performs
GMO's read-only `SearchTradeMulti.idPass` request with `PayType=28`. A
`PAYSUCCESS` response advances both the payment transaction and its debit batch
item to `PAID`; `PAYFAIL` advances them to `FAILED`. The request and sanitized
response are appended as `INQUIRY` evidence. No `inbound_message` or webhook
event is fabricated, so operators can distinguish recovery from delivery.

## Stored instruments and preferences

```http
GET /api/v1/mit/instruments
```

```http
PUT /api/v1/mit/customers/CUST-10042/preferences
X-Operator-Token: local-operator-token
Content-Type: application/json

{
  "primaryInstrumentId": "PI-CARD-10042",
  "backupInstrumentId": "PI-PAYPAY-10042"
}
```

There must be one active Primary and at most one different active Backup. SQLite partial unique indexes enforce the invariant as well as application validation. A backup may be omitted.

## Individual MIT

```http
POST /api/v1/mit/payments
X-Operator-Token: local-operator-token
Idempotency-Key: 9bcc5fd7-c770-4183-af15-ea82d984541a
Content-Type: application/json

{
  "instrumentId": "PI-CARD-10042",
  "amountJpy": 12500,
  "merchantReference": "PREMIUM-2026-09-10042",
  "details": { "authorizationMode": "CAPTURE" }
}
```

Card and PayPay let the operator choose `AUTH` (“Authorize, capture later”) or `CAPTURE` (“Immediate sale”). The backend rejects `AUTH` for every other method even if a caller bypasses Angular. Real-time bank debit performs an individual immediate debit. Koza instruments use the dedicated single-request or batch endpoints below because their result is asynchronous.

## Future Koza debit by API

```http
POST /api/v1/mit/koza-debits
X-Operator-Token: local-operator-token
Content-Type: application/json

{
  "instrumentId": "PM-KOZA_FURIKAE_SELECT-...",
  "amountJpy": 20000,
  "merchantReference": "PREMIUM-202610-CUST-10044",
  "targetDate": "20261027"
}
```

This submits one `EntryTranBankaccount` plus `ExecTranBankaccount` pair and returns the local payment thread. `SCHEDULED`/`REQSUCCESS` means GMO accepted the debit request; it does not mean funds were collected. The linked transaction becomes `PAID` only after `PAYSUCCESS`, or `FAILED` after `PAYFAIL`, from the configured protocol notification or reconciliation source.

GMO limits the customer-facing `Remarks` billing description to 15 permitted
upper-case ASCII/kana characters. Java derives a safe short description from
the merchant reference and sends the complete reference separately in
`ClientField1`. The MIT page generates a collision-resistant reference for each
new submission; a reference already reserved in SQLite is never silently reused.
In live-call mode, prototype seed instruments are omitted and rejected by the
provider adapter—only mandates verified through the current sandbox enrollment
flow can be submitted.

## Monthly Koza batch

The batch accepts the same amount-bearing request items as the single API. Each item becomes an independent `RECURRING_DEBIT` transaction linked to its mandate; the batch is only an operational grouping.

```http
POST /api/v1/mit/koza-batches
X-Operator-Token: local-operator-token
X-Operator-Id: payment-operator
Content-Type: application/json

{
  "batchReference": "KOZA-2026-09",
  "cycleYear": 2026,
  "cycleMonth": 9,
  "targetDate": "20260927",
  "submissionCutoffAt": "2026-09-20T08:00:00Z",
  "expectedResultDate": "2026-09-29",
  "items": [
    { "instrumentId": "PI-KOZA-10042", "amountJpy": 10000 }
  ]
}
```

The command creates one batch and one root payment transaction per item. Provider acceptance means scheduled/processing, not paid. Later asynchronous GMO notifications or SFTP rows transition each original item thread.

## Webhooks

OpenAPI JSON:

```http
POST /webhooks/gmo/openapi
Content-Type: application/json
```

Each originating OpenAPI request supplies a per-order `merchant.csrfToken`;
GMO echoes it in the JSON body and the receiver verifies it. A trusted edge may
alternatively inject `X-Webhook-Ingress-Token`.

Cash notifications use GMO's product-specific event contract. For example:

```json
{
  "accessId": "provider-access-reference",
  "event": "CASH_PAID",
  "csrfToken": "per-order-token"
}
```

Because this payload omits `orderId`, the receiver resolves `accessId` to the
persisted provider order before validating the CSRF token. When a generic
`status` field is absent, `event` is the provider status. A `CASH_PAID`
notification is deliberately not treated as proof that the requested total was
paid: the backend performs a retry-safe `/order/inquiry`, persists GMO's
cumulative deposit amount, and maps the thread to `PARTIALLY_PAID` or `PAID`.
The durable fingerprint includes that sanitized inquiry result because GMO may
reuse an identical `CASH_PAID` envelope for later partial deposits. A changed
cumulative amount is therefore a new state-bearing message; a retry with the
same authoritative cumulative amount remains idempotent.

Legacy protocol notification:

```http
POST /webhooks/gmo/protocol
X-Webhook-Ingress-Token: injected-by-trusted-edge
Content-Type: application/x-www-form-urlencoded
```

When disabled, the endpoints return 404. When enabled, an invalid OpenAPI CSRF/edge credential or invalid protocol edge credential returns 401. Valid messages are sanitized, payload-hash deduplicated, durably stored, linked by provider/application reference, and acknowledged only after durable receipt. The protocol endpoint returns the literal body `0` on success.

Browser returns are separate:

- `POST /webhooks/gmo/protocol/return/bank-direct`
- `POST /webhooks/gmo/protocol/return/koza-furikae`
- `GET /api/v1/gmo/returns/paypay?p={provider-envelope}`
- `GET /api/v1/gmo/returns/paypay-registration?p={provider-envelope}`

They locate an existing reservation and invoke authoritative inquiry/continuation. The browser payload alone never marks a payment paid.

The two idPass form returns use GMO's Windows-31J percent encoding. They are
decoded from a size-bounded raw request body rather than through the servlet
container's UTF-8 form binding. Angular continues to submit UTF-8 JSON; encoding
conversion occurs only at the GMO protocol boundary.

## SFTP import

```http
POST /api/v1/reconciliation/sftp/import
X-Operator-Token: local-operator-token
X-Operator-Id: payment-operator
```

Returns a poll report with discovered/imported/skipped/failed counts. If SFTP is disabled, no remote connection is attempted.

## Health

```http
GET /actuator/health
```

SQLite participates in aggregate health. Public production deployments should expose only the health detail appropriate to their environment.
