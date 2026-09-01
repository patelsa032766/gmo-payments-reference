# GMO Insurance Checkout UI Mock - Session Handoff

Last updated: 2026-09-01
Current browser-cache version: `v=20260901-28`

Approval status: locked for implementation on 1 September 2026. Runtime checksums and baseline change rules are recorded in `BASELINE_V28.md`.

## Purpose

This directory contains a static UI/UX prototype for a future GMO insurance checkout built with Java and Angular. It is separate from the existing Flask payment demo and does not call GMO. Its purpose is to validate the customer journey, eligibility behavior, payment-method setup, and visual direction before the production frontend and backend are built.

Project directory:

```text
/Users/Samir/Documents/GMOPaymentsJava/ui-mock
```

## Run The Mock

No dependencies or build step are required.

```bash
python3 -m http.server 8081 --directory "/Users/Samir/Documents/GMOPaymentsJava/ui-mock"
```

Open:

```text
http://127.0.0.1:8081/?v=20260901-28
```

If Python reports `OSError: [Errno 48] Address already in use`, a mock server is already listening on port `8081`. Check it with:

```bash
lsof -nP -iTCP:8081 -sTCP:LISTEN
```

Stop that process before starting another server, or use a different port. The server observed during this session was Python PID `9851`, but do not assume that PID remains valid in a later session.

Optional JavaScript syntax check:

```bash
node --check /Users/Samir/Documents/GMOPaymentsJava/ui-mock/app.js
```

When changing `styles.css` or `app.js`, advance the query-string version on both asset references in `index.html`. This avoids a stale browser cache.

## Files

| File | Responsibility |
| --- | --- |
| `index.html` | Four workspace tabs, checkout structure, configuration release/test controls, operations/MIT shells, roles, and the shared confirmation dialog. |
| `styles.css` | Shared responsive visual system, compact checkout layouts, payment accordions, configuration UI, and confirmation styling. |
| `app.js` | Checkout state, translations, eligibility, ordering/enablement, payment validation/outcomes, configuration draft/publish behavior, workspace routing, and generic public-address resolution. |
| `ops-mock.css` | Responsive transaction, timeline, webhook, SFTP, MIT, and action-preview styling. |
| `ops-mock.js` | Operational fixtures, transaction grouping, filters, roles, city time zones, timelines, webhook/SFTP views, complete simulated lifecycle actions, MIT outcomes, and dialogs. |
| `README.md` | Current run instructions, workspace summary, safety boundaries, and validation commands. |

## Current Snapshot

- The mock is static HTML, CSS, and JavaScript; it is not Angular.
- The default customer experience is a clean Checkout page.
- Configuration is on a separate top-level tab rather than a permanent right-side panel.
- The mock supports English and Japanese customer-facing copy.
- The policy name is `Annuity` in English and `年金保険` in Japanese.
- The default policy is monthly and the default amount is JPY 10,000.
- The active customer journey is `Payment → Confirmation`.
- Card, PayPay, real-time bank debit (`口座直結決済`), and the combined first-premium Furikomi + future Koza Furikae journey are shown for the default monthly policy.
- The provider interaction is simulated with a short processing delay; no GMO request is made.
- API/Webhooks and MIT are now separate top-level prototype tabs.
- Operational data is clearly identified as fixture data; financial/reconciliation actions complete only in browser memory and append sanitized events to the original transaction thread.
- Operations are grouped by transaction, with a selectable lifecycle timeline on the left and its paired technical exchange on the right.
- The transaction is a durable root thread: refunds, chargebacks, disputes, and later reconciliation events remain attached to it.
- MIT review now shows the final operator confirmation and method-specific GMO operation rather than ending with only a Close button.
- The selected-instrument summary on the right also shows the current execution mode and stays synchronized with the form.
- MIT charge and saved-method removal journeys can now be completed end to end using session-only simulated state.
- Every customer with saved methods has one Primary method and may have one distinct Backup; these roles are marked only and do not yet drive payment execution.

## Combined Koza Enrollment And First Premium In Version 28

### Customer behavior

- The monthly Checkout now includes `Furikomi + Koza Furikae` / `初回銀行振込＋口座振替` as a distinct method.
- The customer selects the method once, supplies bank-registration details once, and presses one action: `Register bank account and get transfer instructions`.
- The expanded method makes the sequence explicit without creating a second customer choice: future monthly premiums use Koza Furikae; the first premium uses Furikomi.
- After confirmed registration, Confirmation displays two separate states: `Koza Furikae registered` and `Bank transfer due`.
- The first premium is labelled `Amount due`, never `Amount paid`, and the page shows the virtual-account bank, branch, account type, account number, account name, exact transfer reference, and deadline.
- Cancellation or registration failure remains on Payment and does not issue a transfer account. An unknown result blocks duplicate submission; the mock inquiry confirms the registration before issuing instructions.
- The same configuration switch controls whether this customer method is eligible and whether operators can submit later monthly Koza batches.

### Required production orchestration

The customer sees one journey, but the backend must preserve two ordered, linked provider operations:

1. Call `BankAccountEntry`, redirect to the returned registration URL, and confirm the bank-account result using the browser return, asynchronous registration result, or `BankAccountTranResult` inquiry.
2. Only after registration is confirmed, call the Furikomi virtual-account entry/execution APIs and return the first-premium instructions.

Do not infer successful registration solely from the customer reaching a return URL: the customer can close the bank page, browser state can be ambiguous, and GMO also supports later result delivery/inquiry. Do not create or expose the virtual account while the registration result is unknown. Persist a local enrollment workflow that links its mandate registration and first-payment transaction to the application. Store provider references and sanitized evidence; never raw bank credentials.

Official references reviewed:

- [Online registration flow](https://docs.gmo-pg.com/mulpay/docs/payment-method/account/accounttrans/entry/online/entry-flow)
- [Online registration API list](https://docs.gmo-pg.com/mulpay/docs/payment-method/account/accounttrans/entry/online/api-list)
- [BankAccountEntry](https://docs.gmo-pg.com/mulpay/apis/protocol-type/idpass/bank-account-entry)
- [Furikomi virtual-account flow](https://docs.gmo-pg.com/mulpay/docs/payment-method/cash/virtualaccount/flow-protocol)

## Koza Furikae Select Monthly Batch In Version 27

### Product boundary

The mock now treats the two bank-debit products as separate products throughout customer, configuration, MIT, transaction, and technical-inspector views:

| Product | Japanese label | Production code | Behavior |
| --- | --- | --- | --- |
| Real-time bank debit | `口座直結決済` | `bank_direct_realtime` | Register a supported account and debit immediately in Checkout or an individual MIT request. |
| Koza Furikae Select | `口座振替（セレクト）` | `koza_furikae_select` | Submit registered mandates for a scheduled monthly debit and await a later bank result. |

Do not share command handlers, provider-operation enums, or settlement-state assumptions between these products. In particular, real-time debit success may be treated as paid, while Koza request acceptance must never be treated as paid.

### Operator batch flow

- `MIT Transactions` now has `Individual payment` and `Monthly Koza Furikae batch` internal views.
- The monthly view shows the selected debit cycle, request cutoff, actual bank-debit date, expected result date, batch reference, candidate mandates, eligibility, amounts, selected total, and per-item states.
- A registered mandate is a prerequisite. The ineligible fixture demonstrates an incomplete bank-account registration that cannot be selected.
- The batch reference is a local grouping for operator visibility. It is not a GMO financial transaction.
- Reviewing the batch lists every selected customer and makes the fan-out explicit.
- Confirming creates one `EntryTranBankaccount` and one `ExecTranBankaccount` request, one Order ID, one Access ID, one idempotency record, and one durable transaction thread per selected row.
- The simulated synchronous state is `REQSUCCESS`, displayed as `Debit scheduled` / `Request accepted`. No row is displayed as paid at submission time.
- Reloading the page restores the draft fixtures; the mock never calls GMO.

### Asynchronous result handling

- `Apply async results` simulates independent protocol notifications after the debit date.
- Fixtures cover `PAYSUCCESS` with debit-result code `0` and `PAYFAIL` with code `1` (insufficient funds). A third accepted request remains scheduled because no result notification has arrived.
- Each notification updates the original transaction thread using its Order ID; no separate unrelated payment row is created.
- The failed result is marked `Debit failed`, requires attention, and retains the registered mandate for a possible later monthly run.
- The technical inspector shows the inbound form-encoded notification and the required HTTP 200/body `0` acknowledgement.
- Recent protocol deliveries are added to the Webhook setup view during the browser session.
- If Koza notifications are disabled, accepted requests remain pending. SFTP reconciliation stays independent and may later supply corroborating or missing result evidence.

### Configuration and schedule

- Configuration contains independent `Koza Furikae registration + batches` and `Koza async notifications` switches. The first controls both the combined Checkout journey and later monthly-batch submission.
- A compact product comparison repeats the Japanese names, execution timing, and stable internal codes.
- The fixture represents the September 2026 27th cycle: target date 27 September, actual bank debit 28 September, submission cutoff 10 September, and expected result return 1 October.
- Production must ingest or configure the current GMO schedule; it must not hardcode the fixture dates.

### Production persistence model implied by the mock

- `payment_instrument` stores the registered mandate reference and `product_code`; never raw bank credentials.
- `debit_batch` stores the local monthly cycle, target date, cutoff, expected-result date, totals, and operator audit fields.
- `debit_batch_item` links one batch to one durable `payment_transaction` and preserves the requested amount and submission state.
- `payment_transaction` stores the per-debit Order ID, Access ID, provider reference, current canonical state, and batch reference.
- `payment_event` is append-only evidence for the local queue command, Entry response, Exec response, async notification, inquiry, and reconciliation events.
- `inbound_message` stores the deduplication key/hash, sanitized payload, receive time, acknowledgement state, and linkage outcome before the receiver acknowledges GMO.
- A batch summary is derived from its item transactions; it must not overwrite item results or act as the financial ledger row.

### Official references reviewed

- [Processing flow](https://docs.gmo-pg.com/mulpay/docs/payment-method/account/accounttrans/payment/select/flow-protocol)
- [API list](https://docs.gmo-pg.com/mulpay/docs/payment-method/account/accounttrans/payment/select/api-list)
- [EntryTranBankaccount](https://docs.gmo-pg.com/mulpay/apis/protocol-type/idpass/entry-tran-bankaccount)
- [ExecTranBankaccount](https://docs.gmo-pg.com/mulpay/apis/protocol-type/idpass/exec-tran-bankaccount)
- [Result notification](https://docs.gmo-pg.com/mulpay/apis/protocol-type/idpass/bank-account-select-notification)
- [Debit-result codes](https://docs.gmo-pg.com/mulpay/docs/payment-method/account/accounttrans/payment/transfer-result)
- [Debit calendar](https://docs.gmo-pg.com/mulpay/docs/payment-method/account/accounttrans/calendar-select)

## Final Pre-Build Workflow Pass In Version 25

- Checkout test outcomes cover success, decline, provider cancellation, successful bank registration followed by debit failure, and timeout/unknown result.
- Customer-visible failures remain on the payment page. Unknown outcomes show a neutral pending state, block a duplicate request, and provide `Check payment status`; the fixture inquiry then advances to Confirmation.
- MIT test outcomes cover success, decline, and timeout/unknown result. Unknown MIT transactions enter the shared transaction console with `Inquiry required before retry`.
- Transaction actions are now complete session-only flows rather than dead-end previews: inquiry, safe retry, capture, void, partial/full refund, discrepancy resolution, and dispute-review recording.
- Inquiry converts a timed-out/unknown transaction to `Retry ready` only after the fixture confirms that no payment exists. Retry is blocked before this state.
- Capture, void, refunds, retries, and reconciliation decisions append operator/API events to the existing durable transaction thread and update its canonical summary.
- Discrepancy resolution requires a canonical result and operator note. The original API, webhook, inquiry, and SFTP evidence is never removed.
- The operations and MIT headers support Payment operator, Configuration administrator, and Read-only auditor states. Auditor action controls are disabled while history and sanitized exchanges remain visible.
- Configuration displays Active versus Draft changes, a version, publisher, and timestamp. Any control change creates a draft preview; `Discard draft` restores the last published snapshot and `Publish changes` activates a new version.
- All new behavior resets on reload and makes no network, webhook, SFTP, or GMO request.
- Browser validation covered desktop and 390 px mobile layouts plus the full unknown-result, inquiry/retry, capture, refund, discrepancy, permissions, configuration publish, and MIT decline paths.

## Primary And Backup Payment Preferences In Version 23

- The last registered fixture method is Primary by default; earlier available methods may be marked Backup.
- Aiko starts with PayPay as Primary and Visa as Backup. Single-method customers have only a Primary.
- Saved-method options display `Primary`, `Backup`, or `Available` alongside the masked method label.
- The right-side selected-instrument facts show the selected method's preference role.
- A compact payment-preference block always shows the customer's Primary and optional Backup.
- `Manage` opens a focused dialog with one required Primary selector and one optional Backup selector; the same method cannot occupy both roles.
- Saving updates session-only markings and selects the new Primary by default. Reloading restores fixture defaults.
- No recurring-payment selection, fallback, retry, or transaction-execution behavior uses these roles yet.
- Timestamps use a city selector backed by IANA time zones and never show raw GMT-offset choices.
- No GMO credentials, Cloudflare configuration, Kanjiai configuration, secrets, or `.env` files belong in this mock.

## Complete Operator MIT Flow In Version 22

- Charge flow: prepare → review → confirm → processing → simulated payment/authorization result → view transaction thread.
- A completed simulated charge is inserted at the top of recent MIT history for the current browser session.
- The generated transaction thread contains the operator command and paired simulated GMO request/response evidence.
- Immediate Card, PayPay, and real-time bank-debit flows finish as `Paid`; authorization mode finishes as `Authorized` with capture as the next step.
- Saved-method removal now lists each affected reference, uses a destructive confirmation, removes the customer's active methods for the session, and preserves all transaction history.
- Prototype results and identifiers are explicitly labelled simulated and reset on page reload; no GMO request is made.

## Operator MIT Confirmation In Version 19

- Renamed the first MIT action from `Preview charge` to `Review charge`.
- The final confirmation displays the selected customer, saved instrument, amount, execution mode, merchant reference, idempotency behavior, and exact GMO operation family.
- The primary action adapts to `Confirm and charge`, `Confirm and authorize`, or `Confirm and debit` according to the payment method and execution mode.
- The confirmation explicitly states that one GMO payment request will be submitted in the implemented portal.
- Clicking the primary action in this static mock opens a prototype-boundary message; it does not contact GMO or fabricate a transaction result.

## Durable Transaction Threads In Version 18

- The root transaction row persists for the complete financial lifecycle rather than representing only the original authorization or charge.
- Child resources such as refunds and chargebacks keep their own provider resource IDs and explicitly link back to the root thread ID.
- The timeline shows the source and linked resource on every modeled adjustment event; the inspector repeats the root-to-resource linkage above the technical exchange.
- Transaction search includes the root thread, provider payment, refund, chargeback, event title, and event-source identifiers.
- A PayPay fixture demonstrates a partial refund appended after settlement, including original, refunded, and net-settled amounts.
- A Card fixture demonstrates a chargeback and evidence deadline appended after the original capture, with the root transaction marked for attention.
- Production persistence should treat lifecycle events as append-only evidence and derive the canonical transaction status and financial totals from those linked records.

## Transaction Inspector Refinement In Version 16

- Transaction detail now uses a Stripe-inspired two-pane composition rather than placing raw technical data inside the business timeline.
- The left pane is a newest-first transaction lifecycle. Selecting an event changes the inspector without leaving the transaction.
- The right pane is a sticky technical inspector with method, endpoint, result, duration, direction, and sanitized JSON.
- Two explicit steps pair the initiating message with its outcome: outbound request/inbound response for GMO APIs, inbound event/acknowledgement for webhooks, browser return/acknowledgement for redirects, inbound file record/reconciliation result for SFTP, and reconciliation input/ledger result for internal state changes.
- The inspector stacks below the lifecycle on narrower screens and retains the request/response stepping model.
- Fixture credentials remain masked and no control sends a live request or invents a result.

## Operational Mockups Added In Version 15

### API & Webhooks

- Replaced the Flask-style event-row concept with a Stripe-inspired transaction list.
- Each row represents one payment and shows amount, status, method, CIT/MIT, customer, reference, latest activity, and reconciliation state.
- Filters cover search, status, method, initiation type, and attention-required transactions.
- Selecting a transaction opens compact facts followed by the split lifecycle and technical inspector.
- Timeline fixtures include checkout, synchronous API, browser return, OpenAPI webhook, protocol notification, inquiry, retry, SFTP, reconciliation, and operator sources.
- Sanitized fixture payloads appear only for the selected event and selected exchange step.
- Capture, inquiry, and retry controls open a preview dialog and do not mutate fixture state or claim GMO success.
- Internal views are `Transactions`, `Webhook setup`, and `SFTP imports`; no fifth top-level workspace was added.

### Webhook and public-address configuration

- Added a generic public base URL plus optional browser-return, OpenAPI-webhook, and protocol-notification overrides.
- The checked-in default is `https://payments.example.com`; no actual KanjiAI or Cloudflare address is present.
- Values entered in the static prototype are stored only in browser local storage.
- The UI shows resolved callback addresses and labels the source as `Generic default` or `Local browser`.
- The production design remains environment override > gitignored local configuration > SQLite runtime value > generic default.
- Browser returns are intentionally independent of webhook enablement because PayPay and bank redirects still need a public return address.
- The Webhook setup view exposes the enabled state, resolved addresses, recent deliveries, acknowledgements, deduplication, and disabled-mode behavior.

### SFTP imports

- SFTP is represented as an independent reconciliation source whether webhooks are enabled or disabled.
- Fixture runs show `.zip.ok` readiness, checksum, accepted/duplicate/rejected/conflict counts, parser/import state, and remote cleanup.
- Import, backfill, replay, and run-details actions are preview-only.

### MIT Transactions

- Added customer and live-ready instrument selection for Card, PayPay, and real-time bank debit.
- Card and PayPay expose immediate payment or authorize/capture-later modes; real-time bank debit is immediate debit only.
- Amount, merchant reference, selected-instrument metadata, and recent MIT transactions are visible without exposing sensitive account data.
- Charge and clear-saved-method controls stop at an explicit review dialog saying nothing was sent to GMO.
- Selecting a recent MIT transaction opens the shared transaction timeline in API & Webhooks.

### Time zones and responsive behavior

- Added Tokyo, Osaka, Sapporo, Singapore, Sydney, London, Paris, New York, Chicago, and Los Angeles choices.
- The selector stores a city identifier while formatting with its IANA time zone, including daylight-saving behavior where relevant.
- Timestamps render as `date, time · City`, not `GMT+/-`.
- The selected city is persisted in browser local storage.
- Desktop and 390 px mobile layouts were verified without horizontal document overflow.

## Changes Since The Previous Handoff

### Configuration and eligibility

- Moved Experience Controls from the right side of Checkout to a separate `Configuration` tab.
- Added English/日本語 selection in Configuration.
- Added per-method enable/disable switches in the payment-method ordering list.
- Disabled methods are omitted from Checkout rather than displayed as unavailable.
- Method eligibility continues to honor configured amount thresholds, distribution channel, and eKYC rules.
- Method ordering continues to control the customer-facing order.
- One-time policies show all enabled and otherwise eligible methods.
- Monthly policies currently show recurring-capable methods only: Card, PayPay, and real-time bank debit.
- The separate customer future-source journey remains behind `ENABLE_SEPARATE_FUTURE_SOURCE_FLOW = false`; the operator Koza batch is implemented independently.

### Customer-facing simplification

- Replaced the boxed four-cell application summary with a compact two-column label/value layout.
- On narrow screens the summary becomes four compact rows with no horizontal overflow.
- Removed the large `Choose how you would like to pay` introduction block.
- Removed the customer-facing eligibility explanation that exposed channel, eKYC, and amount inputs.
- Removed the `N available` badge.
- Removed the persistent card-tokenization footer.
- Kept security reassurance inside the selected Card accordion, where it is relevant.
- Removed recurring-capability badges from methods when the policy is monthly because every visible method is recurring-capable.
- Reworked accordion copy to be customer-facing rather than implementation/prototype language.
- Reduced visual weight throughout; text uses regular or medium weight rather than harsh bold styling.

### Application summary and labels

- Application summary fields are:
  - Application: `APP-20260821-001`
  - Policy: `Annuity`
  - Due today: `JPY 10,000`
  - Payment plan: `Monthly policy`
- The payment heading is now `Choose a payment method`.
- Progress labels are now `Payment` and `Confirmation`.
- Both the active and completed progress circles use a filled violet treatment with white numbers. This fixes the previously weak outlined Confirmation circle.

### Payment and confirmation flow

- Removed the old customer-facing Review step and the final confirmation modal.
- Payment must succeed before the flow advances from the first page.
- The bottom action is method-specific:
  - Card and PayPay: `Pay JPY 10,000`
  - Real-time bank debit: `Register account and pay JPY 10,000`
- The action shows a short `Processing payment…` state in the prototype.
- Card validates number, expiry, CVC, and cardholder name before processing.
- Real-time bank debit now collects bank, account type, account number, and account-holder name.
- Missing Card or bank details produce an inline error on the Payment page and do not advance.
- `state.paymentError` and the `#payment-error` region are available for provider/authentication failures so those errors can also remain on the Payment page.
- A simulated successful result creates a GMO-style reference and advances to Confirmation.
- Confirmation shows:
  - Payment-success status
  - Application number
  - Payment method
  - Amount paid
  - Reference number
  - `Return to application` action
- The confirmation details use a compact two-column label/value layout on desktop and one column on mobile.

## Agreed Production Payment Behavior

The mock only simulates these operations. The intended production sequence is:

### Card

1. Collect card details through the approved GMO browser integration.
2. Authorize the first payment.
3. On successful authorization, retain the reusable GMO token/reference for future monthly charges.
4. Capture before policy issuance.
5. If validation or authorization fails, keep the customer on Payment and show the error inline.

Capture timing does not need to be explained in the customer-facing mock.

### PayPay

1. Hand the customer to the PayPay/GMO authorization experience.
2. Return to Checkout after approval.
3. Retain the reusable authorization/reference for future monthly payments.
4. Capture before policy issuance.
5. Cancellation or authorization failure returns to Payment with an inline error.

### Real-time bank debit (`口座直結決済`)

1. Register the bank account through GMO/the bank journey.
2. Receive the reusable account token/reference.
3. Make a back-to-back real-time direct-debit call for the first payment.
4. Keep registration or debit errors on the Payment page.
5. Advance only after the debit result is successful.

### Amount behavior

- Amount-change messaging was deliberately dropped for now.
- The mock treats the displayed due-today amount as the amount being paid.
- Do not add higher/lower premium adjustment disclosures until the product behavior is defined.

## Payment Catalog In The Mock

Current method configuration near the top of `app.js`:

| Method | Recurring | Current prototype eligibility / limits |
| --- | --- | --- |
| Credit or debit card | Yes | PA, IA, FI; JPY 1 to 1,000,000 |
| PayPay | Yes | PA, IA; JPY 1 to 500,000 |
| Real-time bank debit | Yes | PA, IA, FI; JPY 1 to 300,000; non-eKYC cap JPY 50,000 |
| Convenience store (Kombini) | No | PA, IA, FI; JPY 1 to 299,999 |
| Pay-easy | No | PA, IA; JPY 1 to 300,000 |
| Bank transfer (Furikomi) | No | PA, FI; JPY 1 to 1,000,000 |

These are prototype rules only. The Java backend should own thresholds, enabled methods, channel/eKYC rules, payment ordering, and customer-facing configuration.

## Configuration Tab

The current Configuration tab contains:

- Distribution channel: PA / IA / FI
- Due-today amount presets
- Checkout language: English / 日本語
- eKYC switch
- Monthly-policy switch
- Global webhooks switch
- Visual-theme controls
- Payment-method ordering
- Per-method On/Off switches

Configuration inputs are intentionally not explained on the customer Checkout page. Customers see only methods that are enabled and eligible.

## Internationalization

- `uiCopy.en` and `uiCopy.ja` in `app.js` contain customer-facing strings.
- `applyLanguage()` updates elements carrying `data-i18n`.
- Method names and descriptions have English and Japanese variants in `catalog`.
- Dynamic action labels, confirmation copy, validation messages, and application labels are localized.
- When adding customer-facing copy, add both English and Japanese strings.
- Configuration/admin copy is still primarily English.

## Retained and Deferred Code

The customer-facing separate future-source journey remains commented out through a feature flag rather than deleted:

```js
const ENABLE_SEPARATE_FUTURE_SOURCE_FLOW = false;
```

Consequences while the flag is false:

- Monthly policies show only reusable methods.
- `#future-use` is hidden.
- `#screen-future` is unreachable in the normal flow.
- The active progress indicator has two steps.
- Legacy `saveForFuture`, recurring-enrollment, and future-source functions remain in `app.js` for later reactivation/refactoring.

The combined Koza registration + first-premium Furikomi journey is implemented independently of this legacy flag. Do not enable the generic separate-source flow merely to expose Koza; doing so would incorrectly turn the approved single customer journey back into two customer choices.

## One-Time Policy Behavior

Turning off `Monthly policy` in Configuration makes all enabled and eligible methods available, including Kombini, Pay-easy, and Furikomi.

The older instruction-generation and optional-delivery code still exists. The new confirmation renderer distinguishes instruction methods from immediately processed methods, but this one-time branch has not received the same end-to-end product review as the monthly Card/PayPay/real-time bank-debit path.

Before treating the one-time branch as final, re-check:

- When instructions are created
- Optional Email/LINE/SMS behavior
- Conditional contact validation
- Confirmation wording and issued instruction details
- Provider status semantics for deferred/offline payments

## Important Code Landmarks

`app.js`:

- `catalog`: method labels, recurring capability, channels, and thresholds.
- `ENABLE_SEPARATE_FUTURE_SOURCE_FLOW`: keeps the future-source flow inactive.
- `uiCopy`: English and Japanese strings.
- `state`: configuration, payment drafts, error state, processing state, and simulated result.
- `isEligible()` / `getEligibleMethods()`: enabled-method, threshold, channel, eKYC, and monthly filtering.
- `methodCard()` / `renderMethods()`: accordion display and selection.
- `cardEntry()`, `paypayEntry()`, `bankDirectEntry()`, `kozaFurikaeEntry()`: current monthly-method experiences.
- `validateInitialCard()` / `validateInitialBank()` / `validateInitialKoza()`: first-page validation.
- `createPaymentResult()`: creates either an immediate-payment result or the linked Koza-registration/Furikomi-instruction result after the outcome is confirmed.
- Bottom `#first-continue` handler: simulated provider processing and transition to Confirmation.
- `renderConfirmation()`: action label, inline error region, and confirmation receipt data.
- `renderProgress()`: Payment/Confirmation states.

`index.html`:

- `#checkout-panel`: customer experience.
- `#configuration-panel`: prototype controls.
- `#screen-first`: payment-method selection and method-specific setup.
- `#payment-error`: first-page provider error region.
- `#screen-review`: internal legacy ID; it now renders the customer-facing Confirmation page.

`styles.css`:

- `.application-card`: compact summary layout.
- `.method-card` and `.method-accordion`: payment selection and expanded setup.
- `.confirmation-hero` and `.confirmation-details`: success presentation.
- `.progress-item.is-complete b, .progress-item.is-active b`: filled progress circles.

`ops-mock.js`:

- `transactions`: transaction-level fixtures with nested source events.
- Nested events may identify a linked `Payment`, `Refund`, or `Chargeback` resource while remaining under one root transaction.
- `cityZones` / `formatTime()`: city-labelled IANA time-zone display.
- `renderTransactionList()` / `renderTransactionDetail()`: transaction list and split lifecycle/inspector drill-down.
- `technicalExchange()` / `renderTechnicalInspector()`: event-specific request/response, acknowledgement, file, and reconciliation pairs.
- `filteredTransactions()`: searches both root and linked lifecycle identifiers.
- `renderWebhookSetup()` / `renderSftpImports()`: integration and file-reconciliation views.
- `renderMit()` / `renderKozaBatch()`: individual saved-instrument execution, monthly batch preparation, per-item linkage, asynchronous-result simulation, and MIT history.
- `showPreview()`: shared non-mutating action review dialog.

`ops-mock.css`:

- `.transaction-list`, `.transaction-row`, and `.transaction-detail-head`: transaction-first operations UI.
- `.transaction-detail-grid`: desktop lifecycle/inspector split that stacks responsively.
- `.event-timeline`: selectable chronological source evidence.
- `.technical-inspector`, `.exchange-step-tabs`, and `.exchange-payload`: paired technical exchange viewer.
- `.mit-layout`: compact charge preparation and instrument summary.
- `.prototype-dialog`: explicit preview-only action boundary.

## Known Limitations / Next Work

1. Replace the simulated 650 ms success path with real GMO adapter calls in the eventual Java/Angular implementation.
2. Map real Card, PayPay, bank-registration, and debit failures into the first-page error region.
3. Decide the exact production status semantics for authorization versus capture while retaining the simple customer-facing success language agreed in this session.
4. Review the one-time instruction-method branch separately.
5. Review and reactivate the customer-facing future-source flow only when Koza mandate enrollment is added; the operator batch must remain independent.
6. Replace placeholder application/payment references with backend values.
7. Have product/legal provide final payment terms and consent wording before production.
8. Replace fixture-only operations with authenticated APIs only after the operational mockups are approved.
9. Validate actual GMO SFTP transaction-delivery samples before freezing parser field mappings.

## Recommended Acceptance Checks

### Default monthly policy

1. Confirm Card, PayPay, real-time bank debit, and the combined Furikomi + Koza Furikae method are shown.
2. Confirm disabled methods disappear from Checkout.
3. Confirm order changes in Configuration affect Checkout.
4. Confirm channel, amount, and eKYC rules affect eligibility.
5. Select Card with empty fields, press Pay, and confirm the inline error remains on Payment.
6. Enter valid Card test data, press Pay, and confirm the flow reaches Confirmation.
7. Select PayPay, press Pay, and confirm the simulated approval reaches Confirmation.
8. Select real-time bank debit with missing account data and confirm the inline error.
9. Enter account data and confirm registration/payment reaches Confirmation.
10. Confirm the success page shows method, amount, application, and reference.
11. Confirm Payment is complete and Confirmation is active; both progress circles should be filled violet.
12. Select `Furikomi + Koza Furikae`, submit empty registration details, and confirm inline validation stays on Payment.
13. Complete the registration fixture and confirm one action advances directly to a completion page showing `Koza Furikae registered`, `Bank transfer due`, `Amount due`, and the full Furikomi instructions.
14. Simulate cancelled, failed, and unknown outcomes; confirm no virtual account appears before a successful registration result.

### One-time policy

1. Turn off Monthly policy.
2. Confirm all enabled and eligible methods are shown.
3. Re-check the instruction-generation branch before presenting it as production-final.

### Language and responsive behavior

1. Switch English/日本語 and verify dynamic labels and errors update.
2. Test desktop and approximately 375 px width.
3. Confirm the summary and confirmation details collapse to one-column rows.
4. Confirm there is no horizontal document overflow.

### API, webhook, and SFTP operations

1. Confirm the list contains one row per transaction rather than one row per event.
2. Filter by status, method, CIT/MIT, and needs-attention state.
3. Open a transaction and confirm API, webhook, inquiry, retry, and SFTP sources appear in the left lifecycle.
4. Select events and step between the two exchange tabs in the right inspector; confirm no sensitive card, account, or credential values are exposed.
5. Search for `RFND-PP-604192` and confirm it opens the original PayPay transaction thread.
6. Open `TXN-CARD-46012873` and confirm capture, chargeback, and evidence-deadline events remain in one lifecycle.
7. Change Tokyo to London or New York and confirm timestamps use the city label rather than a raw GMT offset.
8. Open Webhook setup and confirm generic resolved URLs, source, delivery status, and acknowledgement details.
9. Toggle webhooks and confirm SFTP remains independently available.
10. Open SFTP imports and confirm `.zip.ok`, checksum, deduplication, rejected-row, conflict, and cleanup data.
11. Complete capture, void, refund, inquiry, safe retry, discrepancy, and dispute-review fixtures; confirm each result stays in the original thread.
12. Switch to Read-only auditor and confirm operational action controls are disabled.

### MIT Transactions

1. Select each fixture customer and confirm only their live-ready saved methods appear.
2. Confirm Card and PayPay support immediate payment or authorize/capture-later.
3. Confirm real-time bank debit exposes immediate debit only and is labelled `口座直結決済`, not Koza Furikae.
4. Change the amount and verify the prepared amount updates.
5. Run success, decline, and timeout/unknown test outcomes and confirm each creates a clearly marked session-only result.
6. For an unknown result, open the generated transaction and confirm inquiry is required before retry.
7. Remove saved methods and confirm current references disappear while transaction history remains available.
8. Open a recent MIT transaction and confirm it uses the shared transaction timeline.
9. Open `Monthly Koza Furikae batch` and confirm the schedule, local batch reference, registered mandates, one ineligible registration, selected total, and product distinction are visible.
10. Review and submit the batch; confirm each selected row receives its own transaction-thread link and remains `Request accepted`, not `Paid`.
11. Apply asynchronous results; confirm the batch shows one Paid, one Debit failed, and one Request accepted item with no result message yet.
12. Open the failed item and confirm EntryTranBankaccount, ExecTranBankaccount/REQSUCCESS, and PAYFAIL/code `1` appear in the same timeline.
13. In the technical inspector, confirm the notification acknowledgement is HTTP 200 with body `0` and all credentials/mandates are masked.
14. Disable Koza async notifications in Configuration and confirm accepted requests cannot apply result fixtures; SFTP remains available.

## Production Architecture Direction

Proposed future repository modules:

```text
gmo-checkout-reference/
  frontend/                  # Angular customer and operations workspaces
  backend/
    domain/                  # Pure Java payment/configuration domain
    application/             # Use cases, ports, and state transitions
    adapter-gmo/             # GMO OpenAPI/protocol clients
    adapter-sftp/            # File transport, staging, parsing, and checkpoints
    adapter-persistence/     # SQLite, JPA, and Flyway
    adapter-web/             # REST, webhooks, security, and errors
    bootstrap/               # Spring Boot assembly and Angular hosting
  docs/
  .env.example
  README.md
```

Important production notes:

- Keep GMO-specific API code behind adapters/interfaces.
- Do not expose merchant secrets to Angular.
- Cards must use the approved GMO browser-side integration; the application should receive a token/reference, not PAN/CVC.
- Treat PayPay and bank redirects/callbacks as user-navigation flows, not embedded arbitrary iframes.
- Webhook enablement belongs in backend configuration and determines whether callback fields are passed to GMO.
- Persist immutable webhook/API event history. API responses and webhooks should be distinct events rather than overwriting each other.
- Keep SFTP enabled independently of webhooks and reconcile all sources at the transaction level.
- Store reusable Card, PayPay, and bank references against the application for monthly charges.
- Model `bank_direct_realtime` and `koza_furikae_select` as different provider products with different commands and state machines.
- Persist a Koza batch as an operator grouping plus one `debit_batch_item` and one durable payment transaction per submitted mandate.
- Treat `REQSUCCESS` as accepted/scheduled, `SEND` as processing, `PAYSUCCESS` as paid, and `PAYFAIL` as failed; never derive Paid from request acceptance.
- Store and deduplicate Koza protocol notifications before returning HTTP 200/body `0`, then apply them idempotently to the matching Order ID/thread.
- Load the current GMO request/debit/result calendar into validated configuration rather than hardcoding mock fixture dates.

## Suggested New-Session Prompt

```text
Review version 28 of the static UI mock at /Users/Samir/Documents/GMOPaymentsJava/ui-mock.
Read SESSION_HANDOFF.md first. Checkout, Configuration, API & Webhooks, and MIT
Transactions are separate tabs. Operations are grouped by transaction and use
session-only simulated fixtures. Review failure/unknown outcomes, lifecycle actions,
configuration publishing, roles, transaction timelines, webhook/public-URL configuration,
SFTP imports, city-based time zones, individual MIT execution, the combined customer
Koza registration + first-premium Furikomi journey, and the monthly Koza Furikae
batch with per-debit threads and async results. Keep every result clearly
marked simulated. The mock is approved and locked; begin the agreed Java 21 /
Angular 22 / Spring Boot / SQLite modular implementation without changing it.
```

## Current Scope Boundary

Version 28 is accepted and locked. Begin the Java/Angular/Spring Boot/SQLite implementation from this behavior baseline, following the repository-level `README.md`, `docs/ARCHITECTURE.md`, and `docs/BUILD_HANDOFF.md`. Application code belongs outside `ui-mock`; runtime changes to this mock require a newly versioned proposal and approval. Never add live credentials, tunnel configuration, or downloaded GMO/SFTP data to tracked files.
