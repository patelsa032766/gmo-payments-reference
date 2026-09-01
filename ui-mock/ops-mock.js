(() => {
  // Keep the two bank products visibly and technically separate. Real-time Bank
  // Direct completes an immediate debit; Koza Furikae Select schedules a debit
  // whose financial result arrives later through a protocol notification.
  const REALTIME_BANK_METHOD = "Real-time bank debit";
  const KOZA_METHOD = "Koza Furikae · monthly batch";
  const cityZones = [
    { id: "tokyo", label: "Tokyo", zone: "Asia/Tokyo" },
    { id: "osaka", label: "Osaka", zone: "Asia/Tokyo" },
    { id: "sapporo", label: "Sapporo", zone: "Asia/Tokyo" },
    { id: "singapore", label: "Singapore", zone: "Asia/Singapore" },
    { id: "sydney", label: "Sydney", zone: "Australia/Sydney" },
    { id: "london", label: "London", zone: "Europe/London" },
    { id: "paris", label: "Paris", zone: "Europe/Paris" },
    { id: "new-york", label: "New York", zone: "America/New_York" },
    { id: "chicago", label: "Chicago", zone: "America/Chicago" },
    { id: "los-angeles", label: "Los Angeles", zone: "America/Los_Angeles" },
  ];

  const transactions = [
    {
      id: "TXN-CARD-13919584",
      amount: 10000,
      status: "Authorized",
      tone: "info",
      method: "Card",
      initiation: "CIT",
      customer: "Aiko Tanaka",
      customerId: "CUST-10042",
      application: "APP-20260821-001",
      updated: "2026-08-30T17:18:42Z",
      created: "2026-08-30T17:18:39Z",
      reconciliation: "Consistent",
      needsAttention: false,
      instrument: "Visa •••• 4242",
      orderId: "APP-20260821-001-CARD",
      accessId: "3f42a19f2e7348f0a915",
      transactionId: "GMO-CARD-13919584",
      authorization: "Approved",
      capture: "Awaiting issuance event",
      sources: ["API"],
      events: [
        { at: "2026-08-30T17:18:39Z", source: "Checkout", title: "Authorization requested", detail: "Customer submitted a GMO browser token for JPY 10,000.", tone: "neutral", payload: { method: "card", amount: 10000, token: "[REDACTED]", idempotencyKey: "2dad…e471" } },
        { at: "2026-08-30T17:18:42Z", source: "GMO API", title: "Authorization approved", detail: "The reusable card reference was retained. Capture remains blocked until the issuance event.", tone: "success", payload: { status: "AUTH", accessId: "3f42…a915", cardReference: "[MASKED]" } },
      ],
    },
    {
      id: "TXN-PP-41892105",
      amount: 12500,
      status: "Partially refunded",
      tone: "info",
      method: "PayPay",
      initiation: "MIT",
      customer: "Aiko Tanaka",
      customerId: "CUST-10042",
      application: "MIT-20260830-004",
      updated: "2026-08-30T18:24:51Z",
      created: "2026-08-30T16:08:10Z",
      reconciliation: "Payment and refund matched",
      needsAttention: false,
      instrument: "PayPay recurring account",
      orderId: "MIT-20260830-004",
      accessId: "PP-ACCESS-41892105",
      transactionId: "GMO-PP-41892105",
      authorization: "Approved",
      capture: "Captured",
      refundedAmount: 2500,
      netAmount: 10000,
      sources: ["API", "Webhook", "Refund"],
      events: [
        { at: "2026-08-30T16:08:10Z", source: "MIT console", title: "Immediate payment requested", detail: "An operator prepared a PayPay MIT sales request.", tone: "neutral", resource: { type: "Payment", id: "GMO-PP-41892105" }, payload: { customerId: "CUST-10042", mode: "CAPTURE", amount: 12500 } },
        { at: "2026-08-30T16:08:13Z", source: "GMO API", title: "Payment accepted", detail: "The synchronous response recorded the transaction as processing.", tone: "success", resource: { type: "Payment", id: "GMO-PP-41892105" }, payload: { status: "PAYSUCCESS", orderId: "MIT-20260830-004" } },
        { at: "2026-08-30T16:08:18Z", source: "Webhook", title: "Settlement confirmed", detail: "The OpenAPI webhook matched the existing transaction and confirmed payment.", tone: "success", resource: { type: "Payment", id: "GMO-PP-41892105" }, payload: { status: "CAPTURE", eventKey: "evt_pp_41892105", payloadHash: "4f67…26b1" } },
        { at: "2026-08-30T18:24:46Z", source: "GMO API", kind: "refund", title: "Partial refund accepted", detail: "JPY 2,500 was accepted against the original PayPay payment without creating a separate transaction thread.", tone: "success", resource: { type: "Refund", id: "RFND-PP-604192" }, payload: { status: "REFUND_ACCEPTED", refundId: "RFND-PP-604192", amount: 2500, originalTransactionId: "GMO-PP-41892105" } },
        { at: "2026-08-30T18:24:51Z", source: "Webhook", kind: "refund", title: "Partial refund confirmed", detail: "The refund webhook updated the same payment thread and reduced the net settled amount to JPY 10,000.", tone: "success", resource: { type: "Refund", id: "RFND-PP-604192" }, payload: { status: "REFUNDED_PARTIAL", refundId: "RFND-PP-604192", amount: 2500, originalTransactionId: "GMO-PP-41892105", eventKey: "evt_rf_604192" } },
      ],
    },
    {
      id: "TXN-DD-77180422",
      amount: 10000,
      status: "Paid",
      tone: "success",
      method: REALTIME_BANK_METHOD,
      initiation: "CIT",
      customer: "Haruto Sato",
      customerId: "CUST-10043",
      application: "APP-20260829-018",
      updated: "2026-08-30T02:51:10Z",
      created: "2026-08-30T02:50:32Z",
      reconciliation: "Protocol matched",
      needsAttention: false,
      instrument: "Mizuho •••• 0198",
      orderId: "APP-20260829-018-DD",
      accessId: "DD-ACCESS-77180422",
      transactionId: "GMO-DD-77180422",
      authorization: "Account registered",
      capture: "Debited",
      sources: ["API", "Protocol notify"],
      events: [
        { at: "2026-08-30T02:50:32Z", source: "Bank return", title: "Account registration completed", detail: "The browser returned after bank authorization.", tone: "success", payload: { registrationStatus: "REGISTER", memberId: "[MASKED]" } },
        { at: "2026-08-30T02:50:35Z", source: "GMO API", title: "Immediate debit submitted", detail: "EntryTran and ExecTran completed back-to-back.", tone: "success", payload: { status: "CAPTURE", amount: 10000 } },
        { at: "2026-08-30T02:51:10Z", source: "Protocol notify", title: "Debit result matched", detail: "The protocol notification confirmed the same settled state.", tone: "success", payload: { Status: "CAPTURE", OrderID: "APP-20260829-018-DD" } },
      ],
    },
    {
      id: "TXN-KB-22041003",
      amount: 7500,
      status: "Instructions issued",
      tone: "pending",
      method: "Kombini",
      initiation: "CIT",
      customer: "Yuina Nakamura",
      customerId: "CUST-10044",
      application: "APP-20260829-022",
      updated: "2026-08-29T23:42:08Z",
      created: "2026-08-29T23:42:08Z",
      reconciliation: "Awaiting settlement",
      needsAttention: false,
      instrument: "Lawson instructions",
      orderId: "APP-20260829-022-KB",
      accessId: "KB-ACCESS-22041003",
      transactionId: "GMO-KB-22041003",
      authorization: "Not applicable",
      capture: "Awaiting customer payment",
      sources: ["API"],
      events: [
        { at: "2026-08-29T23:42:08Z", source: "GMO API", title: "Convenience-store instructions issued", detail: "The customer received a masked receipt reference and payment deadline.", tone: "neutral", payload: { store: "LAWSON", receiptNo: "[MASKED]", status: "REQSUCCESS" } },
      ],
    },
    {
      id: "TXN-CARD-88172064",
      amount: 25000,
      status: "Retry required",
      tone: "failed",
      method: "Card",
      initiation: "MIT",
      customer: "Ken Ito",
      customerId: "CUST-10045",
      application: "MIT-20260830-003",
      updated: "2026-08-30T15:32:16Z",
      created: "2026-08-30T15:31:57Z",
      reconciliation: "Manual review",
      needsAttention: true,
      instrument: "Mastercard •••• 5100",
      orderId: "MIT-20260830-003",
      accessId: "CARD-ACCESS-88172064",
      transactionId: "GMO-CARD-88172064",
      authorization: "Unknown after timeout",
      capture: "Not started",
      sources: ["API", "Inquiry"],
      events: [
        { at: "2026-08-30T15:31:57Z", source: "MIT console", title: "Authorization submitted", detail: "An operator requested an authorization for JPY 25,000.", tone: "neutral", payload: { mode: "AUTH", amount: 25000, idempotencyKey: "1f03…a18d" } },
        { at: "2026-08-30T15:32:02Z", source: "GMO API", title: "Connection timed out", detail: "The result is unknown, so the application did not submit another financial request.", tone: "attention", payload: { classification: "UNKNOWN_OUTCOME", retryable: false } },
        { at: "2026-08-30T15:32:16Z", source: "Inquiry", title: "Transaction not yet confirmed", detail: "A follow-up inquiry could not establish a final state. Operator review is required.", tone: "failed", payload: { status: "NOT_FOUND", nextAction: "MANUAL_REVIEW" } },
      ],
    },
    {
      id: "TXN-BT-51809271",
      amount: 85000,
      status: "Paid",
      tone: "success",
      method: "Furikomi",
      initiation: "CIT",
      customer: "Emi Watanabe",
      customerId: "CUST-10046",
      application: "APP-20260828-009",
      updated: "2026-08-30T01:05:11Z",
      created: "2026-08-28T08:14:22Z",
      reconciliation: "SFTP matched",
      needsAttention: false,
      instrument: "Virtual account •••• 7041",
      orderId: "APP-20260828-009-BT",
      accessId: "BT-ACCESS-51809271",
      transactionId: "GMO-BT-51809271",
      authorization: "Not applicable",
      capture: "Transfer received",
      sources: ["API", "SFTP"],
      events: [
        { at: "2026-08-28T08:14:22Z", source: "GMO API", title: "Virtual account issued", detail: "Bank-transfer instructions were created for this payment.", tone: "neutral", payload: { status: "REQSUCCESS", account: "[MASKED]" } },
        { at: "2026-08-30T01:02:44Z", source: "SFTP", title: "Settlement record imported", detail: "The daily multi-payment file reported the transfer as paid.", tone: "success", payload: { file: "0009999099999_multi_01_d_20260829.zip", row: 148, status: "PAID", sha256: "bf2c…45d0" } },
        { at: "2026-08-30T01:05:11Z", source: "Reconciliation", title: "Ledger updated", detail: "The SFTP record matched the application and advanced the canonical state.", tone: "success", payload: { previous: "INSTRUCTIONS_ISSUED", current: "PAID" } },
      ],
    },
    {
      id: "TXN-PP-09173460",
      amount: 18000,
      status: "Needs review",
      tone: "attention",
      method: "PayPay",
      initiation: "CIT",
      customer: "Riku Kobayashi",
      customerId: "CUST-10047",
      application: "APP-20260830-007",
      updated: "2026-08-30T14:48:33Z",
      created: "2026-08-30T14:45:06Z",
      reconciliation: "Sources disagree",
      needsAttention: true,
      instrument: "PayPay account",
      orderId: "APP-20260830-007-PP",
      accessId: "PP-ACCESS-09173460",
      transactionId: "GMO-PP-09173460",
      authorization: "Approved",
      capture: "Status conflict",
      sources: ["API", "Webhook", "Inquiry"],
      events: [
        { at: "2026-08-30T14:45:06Z", source: "GMO API", title: "Authorization approved", detail: "The synchronous API response reported an approved authorization.", tone: "success", payload: { status: "AUTH" } },
        { at: "2026-08-30T14:46:19Z", source: "Webhook", title: "Cancellation reported", detail: "A later webhook reported cancellation for the same transaction.", tone: "attention", payload: { status: "CANCEL", eventKey: "evt_pp_09173460" } },
        { at: "2026-08-30T14:48:33Z", source: "Inquiry", title: "Conflict remains unresolved", detail: "The inquiry response did not establish a safe capture state. Manual review is required.", tone: "failed", payload: { status: "UNKNOWN", conflictId: "RC-00041" } },
      ],
    },
    {
      id: "TXN-DD-63011289",
      amount: 9500,
      status: "Paid",
      tone: "success",
      method: REALTIME_BANK_METHOD,
      initiation: "MIT",
      customer: "Haruto Sato",
      customerId: "CUST-10043",
      application: "MIT-20260829-011",
      updated: "2026-08-30T01:06:03Z",
      created: "2026-08-29T12:20:14Z",
      reconciliation: "SFTP verified",
      needsAttention: false,
      instrument: "Mizuho •••• 0198",
      orderId: "MIT-20260829-011",
      accessId: "DD-ACCESS-63011289",
      transactionId: "GMO-DD-63011289",
      authorization: "Account active",
      capture: "Debited",
      sources: ["API", "SFTP"],
      events: [
        { at: "2026-08-29T12:20:14Z", source: "GMO API", title: "Direct debit accepted", detail: "The stored bank mandate was charged through the live debit path.", tone: "success", payload: { status: "CAPTURE", amount: 9500 } },
        { at: "2026-08-30T01:06:03Z", source: "SFTP", title: "Daily record verified payment", detail: "The SFTP row agreed with the API result; no state change was needed.", tone: "success", payload: { file: "0009999099999_multi_01_d_20260829.zip", row: 212, status: "PAID" } },
      ],
    },
    {
      id: "TXN-CARD-46012873",
      amount: 32000,
      status: "Dispute open",
      tone: "attention",
      method: "Card",
      initiation: "CIT",
      customer: "Mio Hayashi",
      customerId: "CUST-10048",
      application: "APP-20260820-014",
      updated: "2026-08-30T19:12:06Z",
      created: "2026-08-20T07:40:11Z",
      reconciliation: "Chargeback response required",
      needsAttention: true,
      instrument: "Visa •••• 1881",
      orderId: "APP-20260820-014-CARD",
      accessId: "CARD-ACCESS-46012873",
      transactionId: "GMO-CARD-46012873",
      authorization: "Approved",
      capture: "Captured",
      disputedAmount: 32000,
      sources: ["API", "Webhook", "Chargeback"],
      events: [
        { at: "2026-08-20T07:40:11Z", source: "GMO API", title: "Payment captured", detail: "The original card payment was captured and became the root of this durable transaction thread.", tone: "success", resource: { type: "Payment", id: "GMO-CARD-46012873" }, payload: { status: "CAPTURE", amount: 32000 } },
        { at: "2026-08-30T19:12:02Z", source: "Webhook", kind: "chargeback", title: "Chargeback opened", detail: "A chargeback for the captured amount was linked to the original payment thread.", tone: "attention", resource: { type: "Chargeback", id: "DSP-CARD-780041" }, payload: { status: "DISPUTE_OPEN", disputeId: "DSP-CARD-780041", originalTransactionId: "GMO-CARD-46012873", amount: 32000, reason: "FRAUDULENT" } },
        { at: "2026-08-30T19:12:06Z", source: "Reconciliation", kind: "chargeback", title: "Evidence deadline recorded", detail: "The dispute deadline and ownership were added to the same thread for operational follow-up.", tone: "attention", resource: { type: "Chargeback", id: "DSP-CARD-780041" }, payload: { disputeId: "DSP-CARD-780041", evidenceDue: "2026-09-06", owner: "Payments operations", state: "ACTION_REQUIRED" } },
      ],
    },
  ];

  const webhookDeliveries = [
    { endpoint: "OpenAPI", transaction: "TXN-PP-41892105", result: "Applied", tone: "success", received: "2026-08-30T16:08:18Z", acknowledgement: "204 in 38 ms" },
    { endpoint: "Protocol", transaction: "TXN-DD-77180422", result: "Applied", tone: "success", received: "2026-08-30T02:51:10Z", acknowledgement: "0 in 24 ms" },
    { endpoint: "OpenAPI", transaction: "TXN-PP-09173460", result: "Conflict", tone: "attention", received: "2026-08-30T14:46:19Z", acknowledgement: "204 in 41 ms" },
    { endpoint: "OpenAPI", transaction: "Duplicate delivery", result: "Deduplicated", tone: "neutral", received: "2026-08-29T22:10:07Z", acknowledgement: "204 in 17 ms" },
  ];

  const sftpRuns = [
    { id: "SFTP-20260830-001", window: "29 Aug 2026", file: "0009999099999_multi_01_d_20260829.zip", status: "Completed", tone: "success", accepted: 212, duplicate: 6, rejected: 0, conflicts: 0, received: "2026-08-30T01:01:18Z", checksum: "bf2c…45d0", cleanup: "Remote files removed" },
    { id: "SFTP-20260829-001", window: "28 Aug 2026", file: "0009999099999_multi_01_d_20260828.zip", status: "Partial", tone: "attention", accepted: 196, duplicate: 3, rejected: 2, conflicts: 1, received: "2026-08-29T01:02:41Z", checksum: "71ae…804f", cleanup: "Retained for review" },
    { id: "SFTP-20260828-001", window: "27 Aug 2026", file: "0009999099999_multi_01_d_20260827.zip", status: "Completed", tone: "success", accepted: 184, duplicate: 4, rejected: 0, conflicts: 0, received: "2026-08-28T01:00:53Z", checksum: "901b…a27c", cleanup: "Remote files removed" },
  ];

  const mitCustomers = [
    {
      id: "CUST-10042",
      name: "Aiko Tanaka",
      instruments: [
        { id: "PI-CARD-1042", method: "Card", label: "Visa •••• 4242", detail: "Expires 12/29", member: "GMO member •••042", mode: "AUTH_OR_CAPTURE", preference: "backup" },
        { id: "PI-PP-1042", method: "PayPay", label: "PayPay recurring account", detail: "Authorized 21 Aug 2026", member: "GMO member •••042", mode: "AUTH_OR_CAPTURE", preference: "primary" },
      ],
    },
    {
      id: "CUST-10043",
      name: "Haruto Sato",
      instruments: [
        { id: "PI-DD-1043", method: REALTIME_BANK_METHOD, label: "Mizuho •••• 0198", detail: "Immediate debit account · 口座直結決済", member: "GMO member •••043", mode: "CAPTURE", preference: "primary" },
      ],
    },
    {
      id: "CUST-10045",
      name: "Ken Ito",
      instruments: [
        { id: "PI-CARD-1045", method: "Card", label: "Mastercard •••• 5100", detail: "Expires 08/28", member: "GMO member •••045", mode: "AUTH_OR_CAPTURE", preference: "primary" },
      ],
    },
  ];

  // The local batch is an operator grouping only. Submission creates one GMO
  // EntryTranBankaccount/ExecTranBankaccount pair and one durable transaction
  // thread per included row; a batch must never collapse these into one payment.
  const kozaBatch = {
    id: "KZ-202609-27-001",
    cycle: "September 2026 · 27th cycle",
    targetDate: "27 Sep 2026",
    actualDebitDate: "28 Sep 2026",
    requestCutoff: "10 Sep 2026",
    expectedResults: "1 Oct 2026",
    state: "Draft",
    rows: [
      { id: "KZ-ROW-001", include: true, eligible: true, customerId: "CUST-10042", customer: "Aiko Tanaka", application: "ANN-10042-202609", instrument: "MUFG •••• 2468", mandate: "BANK-MEMBER-•••042", amount: 12500, resultScenario: "success", status: "Mandate ready", tone: "success", transactionId: null },
      { id: "KZ-ROW-002", include: true, eligible: true, customerId: "CUST-10045", customer: "Ken Ito", application: "ANN-10045-202609", instrument: "SMBC •••• 5100", mandate: "BANK-MEMBER-•••045", amount: 25000, resultScenario: "failed", status: "Mandate ready", tone: "success", transactionId: null },
      { id: "KZ-ROW-003", include: true, eligible: true, customerId: "CUST-10049", customer: "Naomi Suzuki", application: "ANN-10049-202609", instrument: "Mizuho •••• 9041", mandate: "BANK-MEMBER-•••049", amount: 9800, resultScenario: "pending", status: "Mandate ready", tone: "success", transactionId: null },
      { id: "KZ-ROW-004", include: false, eligible: false, customerId: "CUST-10050", customer: "Sora Yamamoto", application: "ANN-10050-202609", instrument: "Resona •••• 1102", mandate: "Registration pending", amount: 16400, resultScenario: "pending", status: "Not eligible", tone: "attention", transactionId: null, reason: "Bank-account registration has not completed." },
    ],
  };

  const opsState = {
    section: "transactions",
    selectedTransactionId: null,
    selectedEventIndex: 0,
    inspectorStep: "request",
    search: "",
    status: "all",
    method: "all",
    initiation: "all",
    attentionOnly: false,
    timeZoneId: window.localStorage.getItem("gmo-mock-timezone") || "tokyo",
    selectedCustomerId: "CUST-10042",
    selectedInstrumentId: "PI-PP-1042",
    mitSection: "individual",
    executionMode: "CAPTURE",
    mitScenario: "success",
    role: "operator",
    pendingMitAction: null,
    pendingTransactionAction: null,
    pendingClearCustomerId: null,
    pendingPriorityChange: null,
    pendingKozaBatch: null,
  };

  const escapeHtml = (value) => String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");

  const selectedZone = () => cityZones.find((item) => item.id === opsState.timeZoneId) || cityZones[0];
  const money = (value) => `JPY ${Number(value).toLocaleString("en-US")}`;
  const statusPill = (label, tone) => `<span class="status-pill is-${escapeHtml(tone)}">${escapeHtml(label)}</span>`;
  const formatTime = (value) => `${new Intl.DateTimeFormat("en-GB", {
    timeZone: selectedZone().zone,
    day: "2-digit",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value))} · ${selectedZone().label}`;

  const canOperate = () => opsState.role !== "auditor";
  const permissionAttributes = () => canOperate() ? "" : 'disabled title="Read-only auditors cannot submit operational actions."';
  const actionButton = (label, action, transaction, primary = false, danger = false) => `
    <button class="compact-button ${primary ? "is-primary" : ""} ${danger ? "is-danger" : ""}" type="button"
      data-preview-action="${escapeHtml(action)}" data-preview-transaction="${transaction.id}" ${permissionAttributes()}>${escapeHtml(label)}</button>`;

  function populateTimeZones() {
    const select = document.querySelector("#operations-timezone");
    select.innerHTML = cityZones.map((item) => `<option value="${item.id}" ${item.id === opsState.timeZoneId ? "selected" : ""}>${item.label}</option>`).join("");
  }

  function filteredTransactions() {
    const query = opsState.search.trim().toLowerCase();
    return transactions.filter((transaction) => {
      const linkedLifecycleTerms = transaction.events.flatMap((event) => [event.title, event.source, event.resource?.type, event.resource?.id]);
      const searchable = [transaction.id, transaction.transactionId, transaction.customer, transaction.customerId, transaction.application, transaction.batchReference, transaction.method, transaction.status, ...linkedLifecycleTerms].join(" ").toLowerCase();
      return (!query || searchable.includes(query))
        && (opsState.status === "all" || transaction.tone === opsState.status)
        && (opsState.method === "all" || transaction.method === opsState.method)
        && (opsState.initiation === "all" || transaction.initiation === opsState.initiation)
        && (!opsState.attentionOnly || transaction.needsAttention);
    });
  }

  function renderTransactionList() {
    const results = filteredTransactions();
    return `
      <div class="ops-toolbar">
        <label class="ops-search-wrap"><span class="is-hidden">Search transactions</span><input id="transaction-search" class="ops-filter" type="search" value="${escapeHtml(opsState.search)}" placeholder="Search customer, application, transaction…" aria-label="Search transactions" /></label>
        <select id="status-filter" class="ops-filter" aria-label="Status"><option value="all">All statuses</option><option value="success" ${opsState.status === "success" ? "selected" : ""}>Paid</option><option value="info" ${opsState.status === "info" ? "selected" : ""}>Authorized / adjusted</option><option value="pending" ${opsState.status === "pending" ? "selected" : ""}>Pending</option><option value="failed" ${opsState.status === "failed" ? "selected" : ""}>Failed</option><option value="attention" ${opsState.status === "attention" ? "selected" : ""}>Needs review / dispute</option></select>
        <select id="method-filter" class="ops-filter" aria-label="Payment method"><option value="all">All methods</option>${[...new Set(transactions.map((item) => item.method))].map((method) => `<option value="${method}" ${opsState.method === method ? "selected" : ""}>${method}</option>`).join("")}</select>
        <select id="initiation-filter" class="ops-filter" aria-label="Initiation type"><option value="all">CIT &amp; MIT</option><option value="CIT" ${opsState.initiation === "CIT" ? "selected" : ""}>Customer initiated</option><option value="MIT" ${opsState.initiation === "MIT" ? "selected" : ""}>Merchant initiated</option></select>
        <label class="attention-filter"><input id="attention-filter" type="checkbox" ${opsState.attentionOnly ? "checked" : ""} /> Needs attention</label>
      </div>
      <p class="result-count">${results.length} transaction${results.length === 1 ? "" : "s"} · Times shown for ${escapeHtml(selectedZone().label)}</p>
      <div class="transaction-list">
        <div class="transaction-head"><span>Amount</span><span>Status</span><span>Method</span><span>Customer</span><span>Reference</span><span>Updated</span><span></span></div>
        ${results.map((transaction) => `
          <button class="transaction-row" type="button" data-transaction-id="${transaction.id}">
            <span class="transaction-cell amount-cell">${money(transaction.amount)}</span>
            <span class="transaction-cell status-cell">${statusPill(transaction.status, transaction.tone)}${transaction.needsAttention ? `<small>${escapeHtml(transaction.reconciliation)}</small>` : ""}</span>
            <span class="transaction-cell method-cell">${escapeHtml(transaction.method)}<small>${transaction.initiation}</small></span>
            <span class="transaction-cell customer-cell">${escapeHtml(transaction.customer)}<small>${transaction.customerId}</small></span>
            <span class="transaction-cell reference-cell">${transaction.application}<small>${transaction.id}</small></span>
            <span class="transaction-cell updated-cell">${formatTime(transaction.updated)}<small>${escapeHtml(transaction.reconciliation)}</small></span>
            <span class="row-chevron">›</span>
          </button>
        `).join("") || `<div class="ops-empty">No transactions match these filters.</div>`}
      </div>
    `;
  }

  function apiEndpointFor(event, transaction) {
    if (event.source === "Inquiry") return `/order/inquiry/${transaction.accessId}`;
    if (event.kind === "refund") return transaction.method === "PayPay" ? "/wallet/refund" : "/credit/refund";
    if (transaction.method === "Card") return transaction.initiation === "MIT" ? "/credit/on-file/charge" : "/credit/charge";
    if (transaction.method === "PayPay") return transaction.initiation === "MIT" ? "/wallet/on-file/charge" : "/wallet/charge";
    if (event.endpoint) return event.endpoint;
    if (transaction.method === REALTIME_BANK_METHOD) return "/payment/ExecTranBankDirect.idPass";
    if (transaction.method === KOZA_METHOD) return "/payment/ExecTranBankaccount.idPass";
    if (transaction.method === "Kombini") return "/cash/charge";
    if (transaction.method === "Furikomi") return "/bank-transfer/charge";
    return "/payment/operation";
  }

  function technicalExchange(event, transaction) {
    const commonRequest = {
      headers: {
        "X-MP-Version": "pinned-version",
        "Idempotency-Key": "[MASKED]",
      },
      body: {
        orderId: transaction.orderId,
        amount: transaction.amount,
        currency: "JPY",
        paymentMethod: transaction.method,
        credential: "[REDACTED]",
        ...(event.requestPayload || {}),
      },
    };

    if (event.kind === "refund" && event.source === "GMO API") {
      return {
        requestLabel: "Outbound refund",
        responseLabel: "Inbound response",
        requestDirection: "Application → GMO",
        responseDirection: "GMO → Application",
        method: "POST",
        endpoint: apiEndpointFor(event, transaction),
        result: "200",
        duration: "287 ms",
        request: {
          headers: commonRequest.headers,
          body: {
            originalTransactionId: transaction.transactionId,
            refundId: event.resource.id,
            amount: event.payload.amount,
            currency: "JPY",
          },
        },
        response: { status: "RECEIVED", body: event.payload },
      };
    }

    if (["GMO API", "Inquiry"].includes(event.source)) {
      const timedOut = event.title.toLowerCase().includes("timed out");
      return {
        requestLabel: "Outbound request",
        responseLabel: "Inbound response",
        requestDirection: "Application → GMO",
        responseDirection: "GMO → Application",
        method: event.source === "Inquiry" ? "GET" : "POST",
        endpoint: apiEndpointFor(event, transaction),
        result: timedOut ? "Timeout" : event.tone === "failed" ? "Unresolved" : "200",
        duration: timedOut ? "5,000 ms" : event.source === "Inquiry" ? "511 ms" : "342 ms",
        request: commonRequest,
        response: timedOut
          ? { transport: { outcome: "TIMEOUT", responseReceived: false }, applicationDecision: "INQUIRE_BEFORE_RETRY" }
          : { status: event.tone === "failed" ? "UNRESOLVED" : "RECEIVED", body: event.payload },
      };
    }

    if (["Webhook", "Protocol notify", "Bank return"].includes(event.source)) {
      const protocol = event.source === "Protocol notify";
      return {
        requestLabel: event.source === "Bank return" ? "Browser return" : "Inbound event",
        responseLabel: "Acknowledgement",
        requestDirection: `${event.source} → Application`,
        responseDirection: "Application → Sender",
        method: event.source === "Bank return" ? "GET" : "POST",
        endpoint: protocol ? "/api/v1/webhooks/gmo/protocol" : event.source === "Bank return" ? "/checkout/provider-return" : "/api/v1/webhooks/gmo/openapi",
        result: protocol ? "200 · body 0" : event.source === "Bank return" ? "302" : "204",
        duration: protocol ? "24 ms" : event.source === "Bank return" ? "31 ms" : "38 ms",
        request: { headers: { "Content-Type": protocol ? "application/x-www-form-urlencoded" : "application/json" }, body: event.payload },
        response: protocol ? { httpStatus: 200, body: "0", storedBeforeAcknowledgement: true } : { httpStatus: event.source === "Bank return" ? 302 : 204, deduplicated: true, storedBeforeAcknowledgement: true },
      };
    }

    if (event.source === "SFTP") {
      return {
        requestLabel: "Inbound file record",
        responseLabel: "Reconciliation result",
        requestDirection: "GMO SFTP → Import staging",
        responseDirection: "Reconciliation → Ledger",
        method: "SFTP",
        endpoint: event.payload.file || "Transaction delivery file",
        result: "Imported",
        duration: "Batch",
        request: { encoding: "Shift-JIS", readinessMarkerRequired: true, record: event.payload },
        response: { matchedTransaction: transaction.id, previousState: "PENDING", resultingState: transaction.status.toUpperCase().replaceAll(" ", "_"), conflict: transaction.needsAttention },
      };
    }

    if (event.source === "Reconciliation") {
      return {
        requestLabel: "Reconciliation input",
        responseLabel: "Ledger result",
        requestDirection: "Source evidence → Reconciliation",
        responseDirection: "Reconciliation → Ledger",
        method: "INTERNAL",
        endpoint: "payment-state-reconciliation",
        result: event.tone === "success" ? "Applied" : "Review",
        duration: "12 ms",
        request: { transactionId: transaction.id, evidence: event.payload },
        response: { canonicalStatus: transaction.status, reconciliation: transaction.reconciliation, auditEventCreated: true },
      };
    }

    return {
      requestLabel: "Recorded command",
      responseLabel: "Application result",
      requestDirection: `${event.source} → Payment application`,
      responseDirection: "Payment application → Operation queue",
      method: "INTERNAL",
      endpoint: event.source === "MIT console" ? "mit-charge-command" : "checkout-payment-command",
      result: "Recorded",
      duration: "8 ms",
      request: { transactionId: transaction.id, command: event.payload },
      response: { accepted: true, operationState: "RECORDED", financialResultClaimed: false },
    };
  }

  function renderTechnicalInspector(event, transaction, eventIndex, eventCount) {
    const exchange = technicalExchange(event, transaction);
    const resource = event.resource || { type: "Payment", id: transaction.transactionId };
    const viewingRequest = opsState.inspectorStep === "request";
    const payload = viewingRequest ? exchange.request : exchange.response;
    const direction = viewingRequest ? exchange.requestDirection : exchange.responseDirection;
    return `
      <aside class="technical-inspector" aria-label="Technical exchange inspector">
        <header class="inspector-header">
          <div>
            <p class="eyebrow">Technical exchange</p>
            <h3>${escapeHtml(event.title)}</h3>
            <p>${escapeHtml(event.source)} · Event ${eventIndex + 1} of ${eventCount}</p>
          </div>
          <span class="fixture-label">Sanitized fixture</span>
        </header>
        <div class="inspector-linkage">
          <span>Thread <code>${escapeHtml(transaction.id)}</code></span>
          <span aria-hidden="true">←</span>
          <span>${escapeHtml(resource.type)} <code>${escapeHtml(resource.id)}</code></span>
        </div>
        <dl class="exchange-meta">
          <div><dt>Method</dt><dd>${escapeHtml(exchange.method)}</dd></div>
          <div><dt>Endpoint</dt><dd><code>${escapeHtml(exchange.endpoint)}</code></dd></div>
          <div><dt>Result</dt><dd>${escapeHtml(exchange.result)}</dd></div>
          <div><dt>Duration</dt><dd>${escapeHtml(exchange.duration)}</dd></div>
        </dl>
        <div class="exchange-step-tabs" role="tablist" aria-label="Request and response">
          <button type="button" role="tab" data-inspector-step="request" aria-selected="${viewingRequest}" class="${viewingRequest ? "is-selected" : ""}"><span>1</span>${escapeHtml(exchange.requestLabel)}</button>
          <button type="button" role="tab" data-inspector-step="response" aria-selected="${!viewingRequest}" class="${!viewingRequest ? "is-selected" : ""}"><span>2</span>${escapeHtml(exchange.responseLabel)}</button>
        </div>
        <div class="exchange-direction"><span aria-hidden="true">${viewingRequest ? "→" : "←"}</span>${escapeHtml(direction)}</div>
        <pre class="exchange-payload">${escapeHtml(JSON.stringify(payload, null, 2))}</pre>
      </aside>
    `;
  }

  function renderTransactionDetail(transaction) {
    const captureAvailable = transaction.status === "Authorized";
    const voidAvailable = transaction.status === "Authorized";
    const refundable = transaction.method !== KOZA_METHOD && ["Paid", "Partially refunded"].includes(transaction.status) && !transaction.disputedAmount && (transaction.netAmount ?? transaction.amount) > 0;
    const retryAvailable = ["Retry required", "Retry ready"].includes(transaction.status);
    const resolutionAvailable = transaction.reconciliation === "Sources disagree";
    const timelineEvents = [...transaction.events].reverse();
    const selectedIndex = Math.min(opsState.selectedEventIndex, timelineEvents.length - 1);
    const selectedEvent = timelineEvents[selectedIndex];
    return `
      <button class="detail-back" type="button" data-detail-back>← Back to transactions</button>
      <section class="transaction-detail">
        <header class="transaction-detail-head">
          <div>
            ${statusPill(transaction.status, transaction.tone)}
            <h2>${money(transaction.amount)}</h2>
            <p>${escapeHtml(transaction.method)} · ${transaction.initiation} · ${escapeHtml(transaction.application)} · Thread <code>${escapeHtml(transaction.id)}</code></p>
          </div>
          <div class="detail-actions">
            ${actionButton("Run inquiry", "Run inquiry", transaction)}
            ${transaction.disputedAmount ? actionButton("Mark reviewed", "Mark dispute reviewed", transaction) : ""}
            ${resolutionAvailable ? actionButton("Resolve discrepancy", "Resolve discrepancy", transaction, true) : ""}
            ${retryAvailable ? actionButton("Retry", "Retry payment", transaction, transaction.status === "Retry ready") : ""}
            ${voidAvailable ? actionButton("Void", "Void authorization", transaction, false, true) : ""}
            ${captureAvailable ? actionButton("Capture", "Capture authorization", transaction, true) : ""}
            ${refundable ? actionButton("Refund", "Refund payment", transaction, false, true) : ""}
          </div>
        </header>
        <dl class="detail-facts">
          <div><dt>Customer</dt><dd>${escapeHtml(transaction.customer)}<br><code>${transaction.customerId}</code></dd></div>
          <div><dt>Payment method</dt><dd>${escapeHtml(transaction.instrument)}</dd></div>
          <div><dt>${transaction.method === KOZA_METHOD ? "Account mandate" : "Authorization"}</dt><dd>${escapeHtml(transaction.authorization)}</dd></div>
          <div><dt>${transaction.method === KOZA_METHOD ? "Scheduled debit" : "Capture / debit"}</dt><dd>${escapeHtml(transaction.capture)}</dd></div>
          <div><dt>GMO transaction</dt><dd><code>${transaction.transactionId}</code></dd></div>
          <div><dt>Order ID</dt><dd><code>${transaction.orderId}</code></dd></div>
          <div><dt>Access ID</dt><dd><code>${transaction.accessId}</code></dd></div>
          <div><dt>Reconciliation</dt><dd>${escapeHtml(transaction.reconciliation)}</dd></div>
          ${transaction.batchReference ? `<div><dt>Monthly batch</dt><dd><code>${escapeHtml(transaction.batchReference)}</code></dd></div><div><dt>Target debit date</dt><dd>${escapeHtml(transaction.targetDate)}</dd></div>` : ""}
          ${transaction.refundedAmount ? `<div><dt>Refunded</dt><dd>${money(transaction.refundedAmount)}</dd></div><div><dt>Net settled</dt><dd>${money(transaction.netAmount)}</dd></div>` : ""}
          ${transaction.disputedAmount ? `<div><dt>Disputed</dt><dd>${money(transaction.disputedAmount)}</dd></div>` : ""}
        </dl>
        <div class="transaction-detail-grid">
          <section class="business-timeline" aria-labelledby="transaction-timeline-title">
            <div class="timeline-heading-row">
              <div><h3 id="transaction-timeline-title" class="timeline-title">Transaction timeline</h3><p>Newest first · Select an event to inspect its exchange.</p></div>
            </div>
            <div class="event-timeline">
              ${timelineEvents.map((event, index) => `
                <article class="timeline-event is-${event.tone} ${index === selectedIndex ? "is-selected" : ""}">
                  <span class="timeline-dot" aria-hidden="true"></span>
                  <button class="timeline-event-button" type="button" data-event-index="${index}" aria-pressed="${index === selectedIndex}">
                    <span class="timeline-event-head"><strong>${escapeHtml(event.title)}</strong><time datetime="${event.at}">${formatTime(event.at)}</time></span>
                    <span class="timeline-event-detail">${escapeHtml(event.detail)}</span>
                    <span class="timeline-meta"><span class="source-pill">${escapeHtml(event.source)}</span>${event.resource ? `<span class="linked-resource-pill">${escapeHtml(event.resource.type)} · ${escapeHtml(event.resource.id)}</span>` : ""}<span class="inspect-hint">Inspect exchange →</span></span>
                  </button>
                </article>
              `).join("")}
            </div>
          </section>
          ${renderTechnicalInspector(selectedEvent, transaction, selectedIndex, timelineEvents.length)}
        </div>
      </section>
    `;
  }

  function renderWebhookSetup() {
    const config = window.checkoutPrototype.integrationConfig();
    return `
      <section class="ops-section-intro">
        <div><h2>Webhook receivers</h2><p>URLs are resolved from generic configuration. A local Cloudflare or custom-domain address is stored only in local configuration and is never added to the distributed source.</p></div>
        <span class="health-label ${config.webhooksEnabled ? "is-on" : "is-off"}">${config.webhooksEnabled ? "Enabled" : "Disabled"}</span>
      </section>
      <dl class="endpoint-list">
        <div><dt>Configuration source</dt><dd>${escapeHtml(config.source)}</dd></div>
        <div><dt>Public base</dt><dd><code>${escapeHtml(config.publicBaseUrl || "Not configured")}</code></dd></div>
        <div><dt>OpenAPI webhook</dt><dd><code>${escapeHtml(config.openApiWebhookUrl)}</code></dd></div>
        <div><dt>Protocol notification</dt><dd><code>${escapeHtml(config.protocolNotificationUrl)}</code></dd></div>
        <div><dt>Browser return</dt><dd><code>${escapeHtml(config.browserReturnUrl)}</code></dd></div>
        <div><dt>Disabled behavior</dt><dd>Acknowledge unexpected delivery; do not apply state</dd></div>
      </dl>
      <div class="detail-actions" style="justify-content:flex-start">
        <button class="compact-button is-primary" type="button" data-open-configuration>Open configuration</button>
        <button class="compact-button" type="button" data-toggle-webhooks>${config.webhooksEnabled ? "Disable webhooks" : "Enable webhooks"}</button>
        <button class="compact-button" type="button" data-preview-action="Send webhook test">Preview test event</button>
      </div>
      <p class="config-source-note">The real receiver stores and deduplicates the event before acknowledging it. This prototype does not send a request.</p>
      <h3 class="section-label">Recent deliveries</h3>
      <div class="delivery-list">
        ${webhookDeliveries.map((delivery) => `
          <div class="delivery-row">
            <div>${escapeHtml(delivery.endpoint)}<small>${escapeHtml(delivery.transaction)}</small></div>
            <div>${statusPill(delivery.result, delivery.tone)}</div>
            <div>${escapeHtml(delivery.acknowledgement)}</div>
            <div>${formatTime(delivery.received)}</div>
            <span class="row-chevron">›</span>
          </div>
        `).join("")}
      </div>
    `;
  }

  function renderSftpImports() {
    const latest = sftpRuns[0];
    return `
      <section class="ops-section-intro">
        <div><h2>SFTP transaction files</h2><p>Daily GMO transaction delivery runs independently of webhooks. Ready files are downloaded only after the matching <code>.zip.ok</code> marker appears.</p></div>
        <span class="health-label is-on">Enabled</span>
      </section>
      <dl class="sftp-summary">
        <div><dt>Last successful import</dt><dd>${formatTime(latest.received)}</dd></div>
        <div><dt>Schedule</dt><dd>Previous Tokyo calendar day · 10:00 Tokyo</dd></div>
        <div><dt>Last ready file</dt><dd>${escapeHtml(latest.file)}</dd></div>
        <div><dt>Transport</dt><dd>Host key verified · Private key authentication</dd></div>
      </dl>
      <div class="detail-actions" style="justify-content:flex-start">
        <button class="compact-button is-primary" type="button" data-preview-action="Run SFTP import">Preview import</button>
        <button class="compact-button" type="button" data-preview-action="Create SFTP backfill">Preview date backfill</button>
      </div>
      <h3 class="section-label">Import history</h3>
      <div class="sftp-run-list">
        ${sftpRuns.map((run) => `
          <div class="sftp-run-row">
            <div>${escapeHtml(run.id)}<small>${escapeHtml(run.file)}</small></div>
            <div>${statusPill(run.status, run.tone)}</div>
            <div>${run.accepted} accepted<small>${run.duplicate} duplicate · ${run.rejected} rejected</small></div>
            <div>${formatTime(run.received)}<small>${escapeHtml(run.cleanup)}</small></div>
            <button class="compact-button" type="button" data-preview-sftp="${run.id}">View</button>
          </div>
        `).join("")}
      </div>
    `;
  }

  function renderOperationsContent() {
    const content = document.querySelector("#operations-content");
    if (!content) return;
    if (opsState.section === "transactions") {
      const selected = transactions.find((transaction) => transaction.id === opsState.selectedTransactionId);
      content.innerHTML = selected ? renderTransactionDetail(selected) : renderTransactionList();
    } else if (opsState.section === "webhooks") {
      content.innerHTML = renderWebhookSetup();
    } else {
      content.innerHTML = renderSftpImports();
    }
  }

  function currentMitCustomer() {
    const availableCustomers = mitCustomers.filter((customer) => customer.instruments.length > 0);
    return availableCustomers.find((customer) => customer.id === opsState.selectedCustomerId) || availableCustomers[0];
  }

  function primaryMitInstrument(customer) {
    return customer?.instruments.find((instrument) => instrument.preference === "primary") || customer?.instruments.at(-1);
  }

  function backupMitInstrument(customer) {
    return customer?.instruments.find((instrument) => instrument.preference === "backup");
  }

  function currentMitInstrument() {
    const customer = currentMitCustomer();
    return customer?.instruments.find((instrument) => instrument.id === opsState.selectedInstrumentId) || primaryMitInstrument(customer);
  }

  function kozaSelectedRows() {
    return kozaBatch.rows.filter((row) => row.eligible && row.include);
  }

  function renderKozaBatch() {
    const config = window.checkoutPrototype.kozaConfig();
    const selected = kozaSelectedRows();
    const total = selected.reduce((sum, row) => sum + row.amount, 0);
    const submitted = kozaBatch.state !== "Draft";
    const resultCounts = kozaBatch.rows.reduce((counts, row) => {
      if (row.status === "Paid") counts.paid += 1;
      if (row.status === "Debit failed") counts.failed += 1;
      if (["Processing", "Request accepted"].includes(row.status)) counts.pending += 1;
      return counts;
    }, { paid: 0, failed: 0, pending: 0 });

    if (!config.batchEnabled) {
      return `<section class="koza-disabled"><p class="eyebrow">Koza Furikae Select · 口座振替（セレクト）</p><h2>Monthly batches are disabled</h2><p>Enable “Koza Furikae monthly batches” in Configuration to prepare scheduled debit requests.</p><button class="compact-button is-primary" type="button" data-open-configuration>Open configuration</button></section>`;
    }

    return `
      <section class="bank-product-banner">
        <div><span class="product-kicker is-deferred">Deferred settlement</span><h2>Koza Furikae Select <small>口座振替（セレクト）</small></h2><p>Monthly requests use registered mandates. GMO request acceptance schedules the debit; it does not mean the customer has paid.</p></div>
        <div class="product-state">${statusPill(kozaBatch.state, kozaBatch.state === "Draft" ? "neutral" : kozaBatch.state === "Results received" ? "info" : "pending")}<small>Local batch group</small></div>
      </section>
      <section class="koza-schedule" aria-label="Koza Furikae schedule">
        <div><span>Monthly cycle</span><strong>${escapeHtml(kozaBatch.cycle)}</strong></div>
        <div><span>Submit requests by</span><strong>${escapeHtml(kozaBatch.requestCutoff)}</strong></div>
        <div><span>Actual bank debit</span><strong>${escapeHtml(kozaBatch.actualDebitDate)}</strong></div>
        <div><span>Results expected</span><strong>${escapeHtml(kozaBatch.expectedResults)}</strong></div>
      </section>
      <div class="koza-explainer"><span aria-hidden="true">i</span><p>The portal submits one <code>EntryTranBankaccount</code> and <code>ExecTranBankaccount</code> request per selected row. <code>REQSUCCESS</code> means scheduled. Later <code>PAYSUCCESS</code> or <code>PAYFAIL</code> notifications update each original transaction thread.</p></div>
      <section class="koza-batch-card">
        <div class="koza-batch-heading">
          <div><p class="eyebrow">Monthly payment requests</p><h2>${escapeHtml(kozaBatch.id)}</h2><p>${submitted ? "Each submitted debit now has its own transaction thread." : "Select mandates to include in this month’s operator run."}</p></div>
          <div class="koza-batch-total"><span>${submitted ? "Submitted total" : "Selected total"}</span><strong>${money(total)}</strong><small>${selected.length} debit request${selected.length === 1 ? "" : "s"}</small></div>
        </div>
        ${submitted ? `<div class="koza-result-summary"><div><span>Paid</span><strong>${resultCounts.paid}</strong></div><div><span>Failed</span><strong>${resultCounts.failed}</strong></div><div><span>Awaiting result</span><strong>${resultCounts.pending}</strong></div></div>` : ""}
        <div class="koza-table" role="table" aria-label="Monthly Koza Furikae requests">
          <div class="koza-row koza-head" role="row"><span></span><span>Customer / application</span><span>Registered mandate</span><span>Amount</span><span>Status</span><span></span></div>
          ${kozaBatch.rows.map((row) => `
            <div class="koza-row ${row.eligible ? "" : "is-ineligible"}" role="row">
              <span><input type="checkbox" data-koza-row="${row.id}" ${row.include ? "checked" : ""} ${!row.eligible || submitted ? "disabled" : ""} aria-label="Include ${escapeHtml(row.customer)}" /></span>
              <span>${escapeHtml(row.customer)}<small>${escapeHtml(row.customerId)} · ${escapeHtml(row.application)}</small></span>
              <span>${escapeHtml(row.instrument)}<small>${escapeHtml(row.mandate)}</small></span>
              <span>${money(row.amount)}</span>
              <span>${statusPill(row.status, row.tone)}${row.reason ? `<small>${escapeHtml(row.reason)}</small>` : ""}</span>
              <span>${row.transactionId ? `<button class="row-link" type="button" data-koza-transaction="${row.transactionId}" aria-label="View transaction thread">›</button>` : ""}</span>
            </div>
          `).join("")}
        </div>
        <div class="koza-actions">
          <div><span>Target date ${escapeHtml(kozaBatch.targetDate)}</span><small>Production loads the applicable GMO calendar; these are documented fixture dates.</small></div>
          ${!submitted ? `<button class="primary-button" type="button" data-koza-review ${selected.length ? "" : "disabled"} ${permissionAttributes()}>Review monthly batch</button>` : `<button class="primary-button" type="button" data-koza-apply-results ${kozaBatch.state === "Results received" || !config.notificationsEnabled ? "disabled" : ""} ${permissionAttributes()}>Apply async results</button>`}
        </div>
        ${submitted && !config.notificationsEnabled ? `<p class="koza-notification-warning">Async notifications are disabled in Configuration. Requests remain pending until notification processing or reconciliation is enabled.</p>` : ""}
      </section>
    `;
  }

  function renderMit() {
    const content = document.querySelector("#mit-content");
    if (!content) return;
    content.innerHTML = `
      <nav class="mit-subnav" aria-label="MIT payment type">
        <button class="${opsState.mitSection === "individual" ? "is-selected" : ""}" type="button" data-mit-section="individual">Individual payment</button>
        <button class="${opsState.mitSection === "koza" ? "is-selected" : ""}" type="button" data-mit-section="koza">Monthly Koza Furikae batch</button>
      </nav>
      <div id="mit-mode-content"></div>
    `;
    const modeContent = document.querySelector("#mit-mode-content");
    if (opsState.mitSection === "koza") {
      modeContent.innerHTML = renderKozaBatch();
      return;
    }
    const availableCustomers = mitCustomers.filter((customer) => customer.instruments.length > 0);
    if (!availableCustomers.length) {
      modeContent.innerHTML = `<div class="ops-empty">No customers currently have an active saved payment method.</div>`;
      return;
    }
    const customer = currentMitCustomer();
    const instrument = currentMitInstrument();
    const primaryInstrument = primaryMitInstrument(customer);
    const backupInstrument = backupMitInstrument(customer);
    if (instrument.mode === "CAPTURE") opsState.executionMode = "CAPTURE";
    const history = transactions.filter((transaction) => transaction.initiation === "MIT");
    modeContent.innerHTML = `
      <div class="mit-product-note"><span class="product-kicker">Immediate or authorization</span><p>Individual MIT uses Card, PayPay, or real-time bank debit (<span lang="ja">口座直結決済</span>). It is separate from the scheduled Koza Furikae batch.</p></div>
      <section class="mit-layout">
        <form id="mit-preview-form" class="mit-form">
          <label class="mit-field is-wide"><span>Customer</span><select id="mit-customer">${availableCustomers.map((item) => `<option value="${item.id}" ${item.id === customer.id ? "selected" : ""}>${escapeHtml(item.id)} · ${escapeHtml(item.name)}</option>`).join("")}</select></label>
          <label class="mit-field is-wide"><span>Saved payment method</span><select id="mit-instrument">${customer.instruments.map((item) => `<option value="${item.id}" ${item.id === instrument.id ? "selected" : ""}>${escapeHtml(item.label)} · ${item.preference === "primary" ? "Primary" : item.preference === "backup" ? "Backup" : "Available"}</option>`).join("")}</select></label>
          <label class="mit-field"><span>Amount (JPY)</span><input id="mit-amount" type="number" min="1" step="1" value="10000" /></label>
          <label class="mit-field"><span>Execution</span><select id="mit-execution">${instrument.mode === "AUTH_OR_CAPTURE" ? `<option value="CAPTURE" ${opsState.executionMode === "CAPTURE" ? "selected" : ""}>Immediate payment</option><option value="AUTH" ${opsState.executionMode === "AUTH" ? "selected" : ""}>Authorize, capture later</option>` : `<option value="CAPTURE">Immediate debit</option>`}</select></label>
          <label class="mit-field"><span>Test outcome</span><select id="mit-scenario"><option value="success" ${opsState.mitScenario === "success" ? "selected" : ""}>Success</option><option value="declined" ${opsState.mitScenario === "declined" ? "selected" : ""}>Declined</option><option value="unknown" ${opsState.mitScenario === "unknown" ? "selected" : ""}>Timeout / unknown</option></select></label>
          <label class="mit-field is-wide"><span>Merchant reference</span><input id="mit-reference" type="text" value="SUPPORT-${new Date().toISOString().slice(0, 10).replaceAll("-", "")}-001" /></label>
          <div class="mit-form-actions">
            <button class="primary-button" type="submit" ${permissionAttributes()}>Review charge</button>
            <button class="secondary-button" type="button" data-preview-clear ${permissionAttributes()}>Remove saved methods</button>
          </div>
        </form>
        <aside class="instrument-summary">
          <p class="eyebrow">Selected instrument</p>
          <h2>${escapeHtml(instrument.label)}</h2>
          <p>${escapeHtml(instrument.detail)}</p>
          <dl class="mit-instrument-facts">
            <div><dt>Customer</dt><dd>${escapeHtml(customer.name)}</dd></div>
            <div><dt>Status</dt><dd>${statusPill("Live ready", "success")}</dd></div>
            <div><dt>Method</dt><dd>${escapeHtml(instrument.method)}</dd></div>
            <div><dt>Member reference</dt><dd>${escapeHtml(instrument.member)}</dd></div>
            <div><dt>Preference</dt><dd>${statusPill(instrument.preference === "primary" ? "Primary" : instrument.preference === "backup" ? "Backup" : "Available", instrument.preference === "primary" ? "info" : "neutral")}</dd></div>
            <div class="is-wide"><dt>Execution</dt><dd id="mit-instrument-execution">${escapeHtml(instrument.method === REALTIME_BANK_METHOD ? "Immediate debit · 口座直結決済" : opsState.executionMode === "AUTH" ? "Authorize, capture later" : "Immediate payment")}</dd></div>
          </dl>
          <section class="payment-preference" aria-labelledby="payment-preference-title">
            <div class="payment-preference-head"><div><h3 id="payment-preference-title">Payment preference</h3><p>Marked for future recurring-payment routing.</p></div>${customer.instruments.length > 1 ? `<button class="compact-button" type="button" data-manage-priority ${permissionAttributes()}>Manage</button>` : ""}</div>
            <div class="preference-row"><span class="preference-rank">1</span><div><small>Primary</small><strong>${escapeHtml(primaryInstrument.label)}</strong></div></div>
            <div class="preference-row"><span class="preference-rank is-backup">2</span><div><small>Backup</small><strong>${backupInstrument ? escapeHtml(backupInstrument.label) : "Not set"}</strong></div></div>
          </section>
          <div class="mit-amount-preview"><span>Prepared amount</span><strong id="mit-amount-preview-value">JPY 10,000</strong></div>
        </aside>
      </section>
      <h2 class="section-label">Recent MIT transactions</h2>
      <div class="mit-history">
        ${history.map((transaction) => `
          <button class="mit-history-row" type="button" data-mit-history="${transaction.id}">
            <span>${escapeHtml(transaction.customer)}<small>${escapeHtml(transaction.application)}</small></span>
            <span>${money(transaction.amount)}</span>
            <span>${escapeHtml(transaction.method)}</span>
            <span>${statusPill(transaction.status, transaction.tone)}<small>${formatTime(transaction.updated)}</small></span>
            <span class="row-chevron">›</span>
          </button>
        `).join("")}
      </div>
    `;
  }

  function showPreview(title, content, options = {}) {
    const dialog = document.querySelector("#prototype-action-dialog");
    document.querySelector("#prototype-dialog-title").textContent = title;
    document.querySelector("#prototype-dialog-content").innerHTML = content;
    document.querySelector("#prototype-dialog-eyebrow").textContent = options.eyebrow || "Preview only";
    document.querySelector("#prototype-dialog-note").textContent = options.note || "Nothing was sent to GMO.";
    document.querySelector("#prototype-dialog-cancel").textContent = options.cancelLabel || "Close";
    const confirmButton = document.querySelector("#prototype-dialog-confirm");
    confirmButton.hidden = !options.confirmLabel;
    confirmButton.textContent = options.confirmLabel || "Confirm";
    confirmButton.dataset.dialogAction = options.confirmAction || "";
    confirmButton.classList.toggle("is-danger", options.confirmTone === "danger");
    const dismissible = options.dismissible !== false;
    dialog.dataset.dismissible = String(dismissible);
    document.querySelector(".dialog-close").hidden = !dismissible;
    document.querySelector("#prototype-dialog-cancel").hidden = !dismissible;
    if (!dialog.open) dialog.showModal();
  }

  function openTransactionThread(transactionId) {
    document.querySelector("#prototype-action-dialog").close();
    opsState.section = "transactions";
    opsState.selectedTransactionId = transactionId;
    opsState.selectedEventIndex = 0;
    opsState.inspectorStep = "request";
    document.querySelectorAll("[data-operations-section]").forEach((item) => item.classList.toggle("is-selected", item.dataset.operationsSection === "transactions"));
    window.checkoutPrototype.setWorkspaceView("operations");
    renderOperationsContent();
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  function buildSimulatedMitTransaction(intent) {
    const now = new Date();
    const requestAt = new Date(now.getTime() - 420).toISOString();
    const completedAt = now.toISOString();
    const suffix = String(now.getTime()).slice(-8);
    const isAuthorization = intent.executionMode === "AUTH";
    const isBankDebit = intent.instrument.method === REALTIME_BANK_METHOD;
    const outcome = intent.scenario || "success";
    const status = outcome === "declined" ? "Declined" : outcome === "unknown" ? "Unknown outcome" : isAuthorization ? "Authorized" : "Paid";
    const tone = outcome === "declined" ? "failed" : outcome === "unknown" ? "attention" : isAuthorization ? "info" : "success";
    const rootId = `TXN-MOCK-${suffix}`;
    const application = `MIT-MOCK-${suffix}`;
    const providerId = `GMO-MOCK-${suffix}`;
    const resultTitle = outcome === "declined"
      ? isBankDebit ? "Immediate debit declined" : "Payment declined"
      : outcome === "unknown" ? "Connection timed out"
      : isAuthorization ? "Authorization approved" : isBankDebit ? "Immediate debit completed" : "Payment accepted";
    return {
      id: rootId,
      amount: intent.amount,
      status,
      tone,
      method: intent.instrument.method,
      initiation: "MIT",
      customer: intent.customer.name,
      customerId: intent.customer.id,
      application,
      updated: completedAt,
      created: requestAt,
      reconciliation: outcome === "unknown" ? "Inquiry required before retry" : "Simulated synchronous result",
      needsAttention: outcome === "unknown",
      instrument: intent.instrument.label,
      orderId: application,
      accessId: `MOCK-ACCESS-${suffix}`,
      transactionId: providerId,
      authorization: outcome === "declined" ? "Declined" : outcome === "unknown" ? "Unknown after timeout" : "Approved",
      capture: outcome !== "success" ? "Not started" : isAuthorization ? "Awaiting capture" : isBankDebit ? "Debited" : "Captured",
      sources: ["MIT console", "GMO API"],
      isSimulated: true,
      events: [
        {
          at: requestAt,
          source: "MIT console",
          title: isAuthorization ? "Authorization submitted" : isBankDebit ? "Immediate debit submitted" : "Immediate payment submitted",
          detail: `${money(intent.amount)} was confirmed by the operator using ${intent.instrument.label}.`,
          tone: "neutral",
          resource: { type: "Payment", id: providerId },
          payload: { merchantReference: intent.reference, executionMode: intent.executionMode, amount: intent.amount, instrumentId: intent.instrument.id, idempotencyKey: "[MASKED]" },
        },
        {
          at: completedAt,
          source: "GMO API",
          title: resultTitle,
          detail: outcome === "unknown"
            ? "No response was received. The result remains unknown and another financial request is blocked until inquiry completes."
            : `The simulated GMO response returned ${status.toUpperCase()} and was attached to this transaction thread.`,
          tone,
          resource: { type: "Payment", id: providerId },
          payload: outcome === "unknown"
            ? { classification: "UNKNOWN_OUTCOME", responseReceived: false, nextAction: "INQUIRE_BEFORE_RETRY", simulated: true }
            : { status: outcome === "declined" ? "DECLINED" : isAuthorization ? "AUTH" : "CAPTURE", accessId: `MOCK-ACCESS-${suffix}`, simulated: true },
        },
      ],
    };
  }

  function completeSimulatedMitCharge() {
    const intent = opsState.pendingMitAction;
    if (!intent) return;
    const transaction = buildSimulatedMitTransaction(intent);
    transactions.unshift(transaction);
    opsState.pendingMitAction = null;
    renderMit();
    const successful = ["Authorized", "Paid"].includes(transaction.status);
    const unknown = transaction.status === "Unknown outcome";
    showPreview(successful ? transaction.status === "Authorized" ? "Authorization successful" : "Payment successful" : unknown ? "Payment result unknown" : "Payment declined", `
      <div class="result-state ${successful ? "is-success" : unknown ? "is-attention" : "is-failed"}"><span aria-hidden="true">${successful ? "✓" : unknown ? "…" : "×"}</span><div><strong>${escapeHtml(transaction.status)}</strong><p>${money(transaction.amount)} · ${escapeHtml(transaction.instrument)}</p></div></div>
      <div class="preview-facts">
        <div><span>Transaction thread</span><strong>${escapeHtml(transaction.id)}</strong></div>
        <div><span>Merchant reference</span><strong>${escapeHtml(intent.reference)}</strong></div>
        <div><span>Customer</span><strong>${escapeHtml(transaction.customer)}</strong></div>
        <div><span>Next step</span><strong>${transaction.status === "Authorized" ? "Capture before the authorization expires" : unknown ? "Run inquiry before any retry" : transaction.status === "Declined" ? "Ask the customer for another method" : "No operator action required"}</strong></div>
      </div>
    `, {
      eyebrow: "Simulated GMO result",
      note: unknown ? "A duplicate financial request is blocked until inquiry completes." : "Prototype result — no live GMO request was made.",
      cancelLabel: "Stay on MIT",
      confirmLabel: "View transaction",
      confirmAction: `view-transaction:${transaction.id}`,
    });
  }

  function buildKozaTransaction(row, index) {
    const now = new Date();
    const suffix = `${String(now.getTime()).slice(-7)}${index + 1}`;
    const queuedAt = new Date(now.getTime() - 900 + index * 30).toISOString();
    const entryAt = new Date(now.getTime() - 600 + index * 30).toISOString();
    const acceptedAt = new Date(now.getTime() - 300 + index * 30).toISOString();
    const orderId = `KZ-${row.customerId.replace("CUST-", "")}-20260927`;
    const providerId = `GMO-KZ-${suffix}`;
    return {
      id: `TXN-KZ-${suffix}`,
      amount: row.amount,
      status: "Debit scheduled",
      tone: "pending",
      method: KOZA_METHOD,
      initiation: "MIT",
      customer: row.customer,
      customerId: row.customerId,
      application: row.application,
      batchReference: kozaBatch.id,
      targetDate: kozaBatch.targetDate,
      updated: acceptedAt,
      created: queuedAt,
      reconciliation: "Awaiting asynchronous debit result",
      needsAttention: false,
      instrument: `${row.instrument} · 口座振替 mandate`,
      orderId,
      accessId: `KZ-ACCESS-${suffix}`,
      transactionId: providerId,
      authorization: "Registered mandate active",
      capture: `Scheduled · bank debit ${kozaBatch.actualDebitDate}`,
      sources: ["MIT batch", "GMO API"],
      isSimulated: true,
      events: [
        {
          at: queuedAt,
          source: "MIT batch",
          title: "Monthly debit queued",
          detail: `${money(row.amount)} was approved for ${kozaBatch.id}. This local grouping is not itself a financial transaction.`,
          tone: "neutral",
          resource: { type: "Koza debit", id: providerId },
          payload: { batchReference: kozaBatch.id, customerId: row.customerId, mandate: "[MASKED]", targetDate: "2026-09-27", amount: row.amount },
        },
        {
          at: entryAt,
          source: "GMO API",
          endpoint: "/payment/EntryTranBankaccount.idPass",
          title: "Koza transaction registered",
          detail: "GMO accepted the per-customer transaction registration for the selected debit cycle.",
          tone: "success",
          resource: { type: "Koza debit", id: providerId },
          requestPayload: { OrderID: orderId, JobCd: "CAPTURE", Amount: row.amount },
          payload: { AccessID: `KZ-ACCESS-${suffix}`, AccessPass: "[REDACTED]" },
        },
        {
          at: acceptedAt,
          source: "GMO API",
          endpoint: "/payment/ExecTranBankaccount.idPass",
          title: "Debit request accepted — not paid",
          detail: `GMO returned REQSUCCESS. The debit is scheduled for the ${kozaBatch.cycle}; the application now waits for the bank result.`,
          tone: "pending",
          resource: { type: "Koza debit", id: providerId },
          requestPayload: { AccessID: `KZ-ACCESS-${suffix}`, MemberID: "[MASKED]", TargetDate: "27", Remarks: kozaBatch.id },
          payload: { Status: "REQSUCCESS", OrderID: orderId, TargetDate: "27" },
        },
      ],
    };
  }

  function completeKozaBatchSubmission() {
    const rows = kozaSelectedRows();
    const created = rows.map((row, index) => {
      const transaction = buildKozaTransaction(row, index);
      row.transactionId = transaction.id;
      row.status = "Request accepted";
      row.tone = "pending";
      return transaction;
    });
    transactions.unshift(...created);
    kozaBatch.state = "Results pending";
    opsState.pendingKozaBatch = null;
    renderMit();
    showPreview("Monthly debit requests accepted", `
      <div class="result-state is-success"><span aria-hidden="true">✓</span><div><strong>${created.length} requests scheduled</strong><p>${money(created.reduce((sum, item) => sum + item.amount, 0))} · ${escapeHtml(kozaBatch.id)}</p></div></div>
      <p>No payment is marked paid yet. Each customer debit has its own Order ID, Access ID, idempotency record, and transaction thread.</p>
      <div class="preview-facts"><div><span>GMO state</span><strong>REQSUCCESS</strong></div><div><span>Next state</span><strong>Async bank result</strong></div><div><span>Actual debit</span><strong>${escapeHtml(kozaBatch.actualDebitDate)}</strong></div><div><span>Expected results</span><strong>${escapeHtml(kozaBatch.expectedResults)}</strong></div></div>
    `, { eyebrow: "Simulated request acceptance", note: "Scheduled is not paid. No live GMO request was made.", cancelLabel: "Return to batch" });
  }

  function completeKozaAsyncResults() {
    const now = new Date();
    const updatedTransactions = [];
    kozaBatch.rows.filter((row) => row.transactionId).forEach((row, index) => {
      const transaction = transactions.find((item) => item.id === row.transactionId);
      if (!transaction) return;
      const at = new Date(now.getTime() + index * 40).toISOString();
      const basePayload = { ShopID: "[MASKED]", OrderID: transaction.orderId, MemberID: "[MASKED]", TargetDate: "27" };
      if (row.resultScenario === "success") {
        row.status = "Paid";
        row.tone = "success";
        transaction.status = "Paid";
        transaction.tone = "success";
        transaction.capture = "Debited by bank";
        transaction.reconciliation = "Protocol result matched";
        transaction.events.push({ at, source: "Protocol notify", title: "Monthly bank debit succeeded", detail: "The asynchronous Koza Furikae result reported a successful debit and advanced this original request thread to Paid.", tone: "success", resource: { type: "Koza debit", id: transaction.transactionId }, payload: { ...basePayload, Status: "PAYSUCCESS", DebitResultCode: "0" } });
      } else if (row.resultScenario === "failed") {
        row.status = "Debit failed";
        row.tone = "failed";
        row.reason = "Insufficient funds · result code 1";
        transaction.status = "Debit failed";
        transaction.tone = "failed";
        transaction.capture = "Failed · insufficient funds";
        transaction.reconciliation = "Customer follow-up required";
        transaction.needsAttention = true;
        transaction.events.push({ at, source: "Protocol notify", title: "Monthly bank debit failed", detail: "The asynchronous result reported insufficient funds. The mandate remains registered; operations can decide whether to include it in a later monthly run.", tone: "failed", resource: { type: "Koza debit", id: transaction.transactionId }, payload: { ...basePayload, Status: "PAYFAIL", DebitResultCode: "1" } });
      } else {
        // No result message has arrived for this request. Absence of a message is
        // not a state transition, so the accepted request remains scheduled.
        row.status = "Request accepted";
        row.tone = "pending";
        transaction.status = "Debit scheduled";
        transaction.tone = "pending";
        transaction.capture = `Scheduled · bank debit ${kozaBatch.actualDebitDate}`;
        return;
      }
      transaction.updated = at;
      if (!transaction.sources.includes("Protocol notify")) transaction.sources.push("Protocol notify");
      webhookDeliveries.unshift({ endpoint: "Protocol · Koza Furikae", transaction: transaction.id, result: row.resultScenario === "failed" ? "Applied failure" : row.resultScenario === "success" ? "Applied success" : "Pending", tone: row.tone, received: at, acknowledgement: "0 in 26 ms" });
      updatedTransactions.push(transaction);
    });
    kozaBatch.state = "Results received";
    renderMit();
    const failed = updatedTransactions.find((transaction) => transaction.status === "Debit failed");
    showPreview("Asynchronous results applied", `
      <div class="result-state is-attention"><span aria-hidden="true">↺</span><div><strong>2 final results · 1 still awaiting a message</strong><p>One paid, one failed, and one remains scheduled.</p></div></div>
      <p>Every protocol message was stored and acknowledged before its canonical status was applied to the same transaction thread.</p>
      <div class="preview-facts"><div><span>PAYSUCCESS</span><strong>1</strong></div><div><span>PAYFAIL</span><strong>1</strong></div><div><span>No result message yet</span><strong>1</strong></div><div><span>Acknowledgement</span><strong>HTTP 200 · body 0</strong></div></div>
    `, { eyebrow: "Simulated protocol notifications", note: "Prototype results — no live GMO messages were received.", cancelLabel: "Return to batch", confirmLabel: failed ? "View failed transaction" : "" , confirmAction: failed ? `view-transaction:${failed.id}` : "" });
  }

  function completeSavedMethodRemoval() {
    const customer = mitCustomers.find((item) => item.id === opsState.pendingClearCustomerId);
    if (!customer) return;
    const removed = customer.instruments.map((instrument) => instrument.label);
    customer.instruments.splice(0, customer.instruments.length);
    opsState.pendingClearCustomerId = null;
    const nextCustomer = mitCustomers.find((item) => item.instruments.length > 0);
    if (nextCustomer) {
      opsState.selectedCustomerId = nextCustomer.id;
      opsState.selectedInstrumentId = primaryMitInstrument(nextCustomer).id;
      opsState.executionMode = "CAPTURE";
    }
    renderMit();
    showPreview("Saved payment methods removed", `
      <div class="result-state is-success"><span aria-hidden="true">✓</span><div><strong>${removed.length} method${removed.length === 1 ? "" : "s"} removed</strong><p>${escapeHtml(customer.id)} · ${escapeHtml(customer.name)}</p></div></div>
      <p>Future MIT use is blocked until the customer registers another payment method. Existing transaction and audit history remains available.</p>
    `, {
      eyebrow: "Simulated account update",
      note: "Prototype state resets when the page is reloaded.",
      cancelLabel: "Close",
    });
  }

  function completePriorityChange() {
    const pending = opsState.pendingPriorityChange;
    if (!pending) return;
    const customer = mitCustomers.find((item) => item.id === pending.customerId);
    if (!customer) return;
    customer.instruments.forEach((instrument) => {
      instrument.preference = instrument.id === pending.primaryId ? "primary" : instrument.id === pending.backupId ? "backup" : "available";
    });
    const primary = customer.instruments.find((instrument) => instrument.id === pending.primaryId);
    const backup = customer.instruments.find((instrument) => instrument.id === pending.backupId);
    opsState.selectedCustomerId = customer.id;
    opsState.selectedInstrumentId = primary.id;
    opsState.executionMode = primary.mode === "CAPTURE" ? "CAPTURE" : opsState.executionMode;
    opsState.pendingPriorityChange = null;
    renderMit();
    showPreview("Payment preference updated", `
      <div class="result-state is-success"><span aria-hidden="true">✓</span><div><strong>Preference saved</strong><p>${escapeHtml(customer.id)} · ${escapeHtml(customer.name)}</p></div></div>
      <div class="preview-facts">
        <div><span>Primary</span><strong>${escapeHtml(primary.label)}</strong></div>
        <div><span>Backup</span><strong>${backup ? escapeHtml(backup.label) : "Not set"}</strong></div>
      </div>
      <p>This preference is marked for future recurring-payment routing. It does not change how the current mock executes a payment.</p>
    `, {
      eyebrow: "Session preference",
      note: "Prototype state resets when the page is reloaded.",
      cancelLabel: "Close",
    });
  }

  function previewTransactionAction(action, transactionId) {
    const transaction = transactions.find((item) => item.id === transactionId);
    if (!transaction) return;
    if (!canOperate()) {
      showPreview("Action unavailable", `<p>Read-only auditors can inspect transaction history and sanitized exchanges, but cannot submit inquiries or financial actions.</p>`, {
        eyebrow: "Permission required",
        note: "Switch to Payment operator or Configuration administrator to continue.",
      });
      return;
    }

    if (action === "Retry payment" && transaction.status !== "Retry ready") {
      showPreview("Inquiry required before retry", `
        <p>The previous request has an uncertain result. Retrying now could create a duplicate charge.</p>
        <div class="confirmation-impact"><span>Required next step</span><strong>Run inquiry and establish that no payment exists</strong></div>
      `, { eyebrow: "Retry blocked", note: "The original idempotency record remains protected." });
      return;
    }

    const remaining = transaction.netAmount ?? transaction.amount;
    const actionFields = action === "Refund payment" ? `
      <label class="action-field"><span>Refund amount (JPY)</span><input id="transaction-action-amount" type="number" min="1" max="${remaining}" value="${remaining}" /></label>
      <label class="action-field"><span>Reason</span><select id="transaction-action-reason"><option>Customer request</option><option>Duplicate payment</option><option>Policy cancelled</option><option>Service adjustment</option></select></label>
    ` : action === "Resolve discrepancy" ? `
      <label class="action-field"><span>Canonical result</span><select id="transaction-action-resolution"><option value="Cancelled">Cancelled — no capture</option><option value="Authorized">Authorized</option><option value="Paid">Paid</option></select></label>
      <label class="action-field"><span>Resolution note</span><textarea id="transaction-action-note" rows="3" required>Verified GMO inquiry and provider evidence.</textarea></label>
    ` : action === "Mark dispute reviewed" ? `
      <label class="action-field"><span>Review note</span><textarea id="transaction-action-note" rows="3">Evidence ownership confirmed with Payments Operations.</textarea></label>
    ` : "";

    const descriptions = {
      "Run inquiry": "Retrieve GMO's latest state without submitting another financial request.",
      "Capture authorization": "Capture the approved authorization and append the result to this payment thread.",
      "Void authorization": "Release the authorization before capture. This cannot be captured afterward.",
      "Refund payment": "Submit a refund against the remaining settled amount.",
      "Retry payment": "Submit a new idempotent attempt after inquiry confirmed that no payment exists.",
      "Resolve discrepancy": "Choose the canonical ledger state and retain all conflicting source evidence.",
      "Mark dispute reviewed": "Record operator ownership while the chargeback remains open.",
    };
    opsState.pendingTransactionAction = { action, transactionId };
    showPreview(action, `
      <p>${escapeHtml(descriptions[action] || "Review and confirm this transaction action.")}</p>
      <div class="preview-facts">
        <div><span>Transaction</span><strong>${escapeHtml(transaction.id)}</strong></div>
        <div><span>Current state</span><strong>${escapeHtml(transaction.status)}</strong></div>
        <div><span>Amount</span><strong>${money(transaction.amount)}</strong></div>
        <div><span>Operator</span><strong>Payment operator</strong></div>
      </div>
      ${actionFields}
    `, {
      eyebrow: action.includes("Refund") || action.includes("Void") ? "Financial action" : "Operator action",
      note: "The simulated result will be appended to the existing transaction thread.",
      cancelLabel: "Cancel",
      confirmLabel: action,
      confirmAction: "complete-transaction-action",
      confirmTone: action.includes("Refund") || action.includes("Void") ? "danger" : "",
    });
  }

  function completeTransactionAction() {
    const pending = opsState.pendingTransactionAction;
    if (!pending) return;
    const transaction = transactions.find((item) => item.id === pending.transactionId);
    if (!transaction) return;
    const now = new Date();
    const acceptedAt = new Date(now.getTime() - 220).toISOString();
    const completedAt = now.toISOString();
    const eventBase = { resource: { type: "Payment", id: transaction.transactionId } };
    let resultTitle = `${pending.action} complete`;
    let resultDetail = "The simulated action was recorded on the existing thread.";

    if (pending.action === "Run inquiry") {
      if (["Retry required", "Unknown outcome"].includes(transaction.status)) {
        transaction.status = "Retry ready";
        transaction.tone = "attention";
        transaction.authorization = "No payment found";
        transaction.reconciliation = "Inquiry confirms retry is safe";
        transaction.needsAttention = true;
        transaction.events.push({ ...eventBase, at: completedAt, source: "Inquiry", title: "No payment found; retry is safe", detail: "GMO inquiry established that the timed-out request did not create a payment. A new idempotent attempt is now allowed.", tone: "success", payload: { status: "NOT_FOUND", retryAllowed: true, priorIdempotencyKeyRetired: true } });
        resultDetail = "No payment exists. The Retry action is now available.";
      } else {
        transaction.events.push({ ...eventBase, at: completedAt, source: "Inquiry", title: "Current payment state confirmed", detail: `GMO inquiry confirmed the canonical ${transaction.status.toLowerCase()} state.`, tone: "success", payload: { status: transaction.status.toUpperCase().replaceAll(" ", "_"), changed: false } });
      }
    } else if (pending.action === "Capture authorization") {
      transaction.events.push({ ...eventBase, at: acceptedAt, source: "Operator", title: "Capture submitted", detail: `The operator confirmed capture of ${money(transaction.amount)}.`, tone: "neutral", payload: { amount: transaction.amount, idempotencyKey: "[MASKED]" } });
      transaction.events.push({ ...eventBase, at: completedAt, source: "GMO API", title: "Capture completed", detail: "GMO confirmed capture against the existing authorization.", tone: "success", payload: { status: "CAPTURE", amount: transaction.amount } });
      transaction.status = "Paid";
      transaction.tone = "success";
      transaction.capture = "Captured";
      transaction.reconciliation = "Consistent";
      transaction.needsAttention = false;
      resultDetail = "The authorization was captured successfully.";
    } else if (pending.action === "Void authorization") {
      transaction.events.push({ ...eventBase, at: completedAt, source: "GMO API", title: "Authorization voided", detail: "The unused authorization was cancelled before capture.", tone: "success", payload: { status: "VOID", amount: transaction.amount } });
      transaction.status = "Cancelled";
      transaction.tone = "neutral";
      transaction.capture = "Cancelled before capture";
      transaction.reconciliation = "Consistent";
      transaction.needsAttention = false;
      resultDetail = "The authorization was released and can no longer be captured.";
    } else if (pending.action === "Refund payment") {
      const maximum = transaction.netAmount ?? transaction.amount;
      const amount = Math.min(Math.max(1, Number(pending.amount || maximum)), maximum);
      const previousRefund = transaction.refundedAmount || 0;
      transaction.refundedAmount = previousRefund + amount;
      transaction.netAmount = transaction.amount - transaction.refundedAmount;
      const full = transaction.netAmount === 0;
      const refundId = `RFND-MOCK-${String(now.getTime()).slice(-6)}`;
      transaction.events.push({ at: acceptedAt, source: "Operator", kind: "refund", title: "Refund submitted", detail: `${money(amount)} refund submitted with reason “${pending.reason}”.`, tone: "neutral", resource: { type: "Refund", id: refundId }, payload: { amount, reason: pending.reason, idempotencyKey: "[MASKED]" } });
      transaction.events.push({ at: completedAt, source: "GMO API", kind: "refund", title: full ? "Full refund completed" : "Partial refund completed", detail: `${money(amount)} was returned and linked to the original payment thread.`, tone: "success", resource: { type: "Refund", id: refundId }, payload: { status: full ? "REFUNDED" : "REFUNDED_PARTIAL", amount, originalTransactionId: transaction.transactionId } });
      transaction.status = full ? "Refunded" : "Partially refunded";
      transaction.tone = "info";
      transaction.reconciliation = "Payment and refund matched";
      resultDetail = `${money(amount)} was refunded; ${money(transaction.netAmount)} remains settled.`;
    } else if (pending.action === "Retry payment") {
      transaction.events.push({ ...eventBase, at: acceptedAt, source: "Operator", title: "Retry submitted", detail: "The operator submitted a new authorization after the safe-retry inquiry.", tone: "neutral", payload: { priorAttempt: transaction.id, idempotencyKey: "[NEW MASKED KEY]" } });
      transaction.events.push({ ...eventBase, at: completedAt, source: "GMO API", title: "Retry authorized", detail: "GMO approved the new authorization. Capture is available as the next action.", tone: "success", payload: { status: "AUTH", retried: true } });
      transaction.status = "Authorized";
      transaction.tone = "info";
      transaction.authorization = "Approved on retry";
      transaction.capture = "Awaiting capture";
      transaction.reconciliation = "Retry completed after inquiry";
      transaction.needsAttention = false;
      resultDetail = "The retry was authorized and is ready for capture.";
    } else if (pending.action === "Resolve discrepancy") {
      const resolution = pending.resolution || "Cancelled";
      transaction.events.push({ ...eventBase, at: completedAt, source: "Reconciliation", title: "Discrepancy resolved", detail: `${pending.note} Canonical state set to ${resolution}.`, tone: "success", payload: { priorConflict: "RC-00041", canonicalStatus: resolution.toUpperCase(), reason: pending.note, resolvedBy: "Payment operator" } });
      transaction.status = resolution;
      transaction.tone = resolution === "Paid" ? "success" : resolution === "Authorized" ? "info" : "neutral";
      transaction.capture = resolution === "Paid" ? "Captured" : resolution === "Authorized" ? "Awaiting capture" : "Cancelled";
      transaction.reconciliation = "Resolved manually · evidence retained";
      transaction.needsAttention = false;
      resultDetail = `The canonical state is now ${resolution}; all source evidence remains in the thread.`;
    } else if (pending.action === "Mark dispute reviewed") {
      transaction.events.push({ at: completedAt, source: "Operator", kind: "chargeback", title: "Chargeback review recorded", detail: pending.note, tone: "neutral", resource: { type: "Chargeback", id: "DSP-CARD-780041" }, payload: { reviewedBy: "Payment operator", note: pending.note, disputeStillOpen: true } });
      transaction.reconciliation = "Chargeback reviewed · evidence pending";
      resultDetail = "Review ownership was recorded; the dispute remains open.";
    }

    transaction.updated = completedAt;
    opsState.pendingTransactionAction = null;
    opsState.selectedEventIndex = 0;
    renderOperationsContent();
    showPreview(resultTitle, `
      <div class="result-state is-success"><span aria-hidden="true">✓</span><div><strong>${escapeHtml(transaction.status)}</strong><p>${escapeHtml(resultDetail)}</p></div></div>
      <div class="preview-facts"><div><span>Transaction thread</span><strong>${escapeHtml(transaction.id)}</strong></div><div><span>Audit</span><strong>Operator and timestamp recorded</strong></div></div>
    `, { eyebrow: "Simulated result", note: "No live GMO request was made.", cancelLabel: "Close", confirmLabel: "Return to thread", confirmAction: `view-transaction:${transaction.id}` });
  }

  function previewSftpRun(runId) {
    const run = sftpRuns.find((item) => item.id === runId);
    showPreview("SFTP import details", `
      <p>This fixture shows the metadata retained for a real, resumable SFTP import.</p>
      <div class="preview-facts">
        <div><span>Run</span><strong>${escapeHtml(run.id)}</strong></div>
        <div><span>Status</span><strong>${escapeHtml(run.status)}</strong></div>
        <div><span>Window</span><strong>${escapeHtml(run.window)}</strong></div>
        <div><span>Checksum</span><strong>${escapeHtml(run.checksum)}</strong></div>
        <div><span>Accepted</span><strong>${run.accepted}</strong></div>
        <div><span>Conflicts</span><strong>${run.conflicts}</strong></div>
        <div><span>Remote cleanup</span><strong>${escapeHtml(run.cleanup)}</strong></div>
      </div>
    `);
  }

  document.querySelector("#operations-timezone").addEventListener("change", (event) => {
    opsState.timeZoneId = event.target.value;
    window.localStorage.setItem("gmo-mock-timezone", opsState.timeZoneId);
    renderOperationsContent();
    renderMit();
  });

  ["operations-role", "mit-role"].forEach((id) => {
    document.querySelector(`#${id}`).addEventListener("change", (event) => {
      opsState.role = event.target.value;
      document.querySelector("#operations-role").value = opsState.role;
      document.querySelector("#mit-role").value = opsState.role;
      renderOperationsContent();
      renderMit();
    });
  });

  document.querySelectorAll("[data-operations-section]").forEach((button) => {
    button.addEventListener("click", () => {
      opsState.section = button.dataset.operationsSection;
      opsState.selectedTransactionId = null;
      document.querySelectorAll("[data-operations-section]").forEach((item) => item.classList.toggle("is-selected", item === button));
      renderOperationsContent();
    });
  });

  document.querySelector("#operations-content").addEventListener("input", (event) => {
    if (event.target.id === "transaction-search") {
      opsState.search = event.target.value;
      renderOperationsContent();
      const search = document.querySelector("#transaction-search");
      search.focus();
      search.setSelectionRange(search.value.length, search.value.length);
    }
  });

  document.querySelector("#operations-content").addEventListener("change", (event) => {
    if (event.target.id === "status-filter") opsState.status = event.target.value;
    if (event.target.id === "method-filter") opsState.method = event.target.value;
    if (event.target.id === "initiation-filter") opsState.initiation = event.target.value;
    if (event.target.id === "attention-filter") opsState.attentionOnly = event.target.checked;
    renderOperationsContent();
  });

  document.querySelector("#operations-content").addEventListener("click", (event) => {
    const transactionButton = event.target.closest("[data-transaction-id]");
    const timelineEventButton = event.target.closest("[data-event-index]");
    const inspectorStepButton = event.target.closest("[data-inspector-step]");
    const previewButton = event.target.closest("[data-preview-action]");
    const sftpButton = event.target.closest("[data-preview-sftp]");
    if (transactionButton) {
      opsState.selectedTransactionId = transactionButton.dataset.transactionId;
      opsState.selectedEventIndex = 0;
      opsState.inspectorStep = "request";
      renderOperationsContent();
      window.scrollTo({ top: 0, behavior: "smooth" });
    } else if (timelineEventButton) {
      opsState.selectedEventIndex = Number(timelineEventButton.dataset.eventIndex);
      opsState.inspectorStep = "request";
      renderOperationsContent();
    } else if (inspectorStepButton) {
      opsState.inspectorStep = inspectorStepButton.dataset.inspectorStep;
      renderOperationsContent();
    } else if (event.target.closest("[data-detail-back]")) {
      opsState.selectedTransactionId = null;
      opsState.selectedEventIndex = 0;
      opsState.inspectorStep = "request";
      renderOperationsContent();
    } else if (previewButton) {
      previewTransactionAction(previewButton.dataset.previewAction, previewButton.dataset.previewTransaction);
    } else if (sftpButton) {
      previewSftpRun(sftpButton.dataset.previewSftp);
    } else if (event.target.closest("[data-open-configuration]")) {
      window.checkoutPrototype.setWorkspaceView("configuration");
    } else if (event.target.closest("[data-toggle-webhooks]")) {
      const config = window.checkoutPrototype.integrationConfig();
      window.checkoutPrototype.setWebhooksEnabled(!config.webhooksEnabled);
    }
  });

  document.querySelector("#mit-content").addEventListener("change", (event) => {
    if (event.target.matches("[data-koza-row]")) {
      const row = kozaBatch.rows.find((item) => item.id === event.target.dataset.kozaRow);
      if (row && row.eligible && kozaBatch.state === "Draft") row.include = event.target.checked;
      renderMit();
    } else if (event.target.id === "mit-customer") {
      opsState.selectedCustomerId = event.target.value;
      opsState.selectedInstrumentId = primaryMitInstrument(currentMitCustomer()).id;
      opsState.executionMode = "CAPTURE";
      renderMit();
    } else if (event.target.id === "mit-instrument") {
      opsState.selectedInstrumentId = event.target.value;
      opsState.executionMode = "CAPTURE";
      renderMit();
    } else if (event.target.id === "mit-execution") {
      opsState.executionMode = event.target.value;
      const executionSummary = document.querySelector("#mit-instrument-execution");
      if (executionSummary) executionSummary.textContent = opsState.executionMode === "AUTH" ? "Authorize, capture later" : "Immediate payment";
    } else if (event.target.id === "mit-scenario") {
      opsState.mitScenario = event.target.value;
    }
  });

  document.querySelector("#mit-content").addEventListener("input", (event) => {
    if (event.target.id === "mit-amount") {
      const amount = Number(event.target.value || 0);
      document.querySelector("#mit-amount-preview-value").textContent = money(amount);
    }
  });

  document.querySelector("#mit-content").addEventListener("submit", (event) => {
    if (event.target.id !== "mit-preview-form") return;
    event.preventDefault();
    const customer = currentMitCustomer();
    const instrument = currentMitInstrument();
    const amount = Number(document.querySelector("#mit-amount").value || 0);
    const reference = document.querySelector("#mit-reference").value.trim();
    const isAuthorization = opsState.executionMode === "AUTH";
    const executionLabel = isAuthorization ? "Authorize, capture later" : instrument.method === REALTIME_BANK_METHOD ? "Immediate debit · 口座直結決済" : "Immediate payment";
    const operation = instrument.method === "Card" ? "POST /credit/on-file/charge" : instrument.method === "PayPay" ? "POST /wallet/on-file/charge" : "EntryTranBankDirect → ExecTranBankDirect";
    const confirmVerb = isAuthorization ? "authorize" : instrument.method === REALTIME_BANK_METHOD ? "debit" : "charge";
    opsState.pendingMitAction = { customer, instrument, amount, reference, executionMode: opsState.executionMode, scenario: opsState.mitScenario };
    showPreview("Confirm MIT charge", `
      <p>Confirming submits one merchant-initiated payment using the customer's saved GMO reference.</p>
      <div class="confirmation-impact"><span>GMO operation</span><code>${escapeHtml(operation)}</code></div>
      <div class="preview-facts">
        <div><span>Customer</span><strong>${escapeHtml(customer.id)} · ${escapeHtml(customer.name)}</strong></div>
        <div><span>Instrument</span><strong>${escapeHtml(instrument.label)}</strong></div>
        <div><span>Amount</span><strong>${money(amount)}</strong></div>
        <div><span>Execution</span><strong>${escapeHtml(executionLabel)}</strong></div>
        <div><span>Test outcome</span><strong>${escapeHtml(opsState.mitScenario === "success" ? "Success" : opsState.mitScenario === "declined" ? "Declined" : "Timeout / unknown")}</strong></div>
        <div><span>Merchant reference</span><strong>${escapeHtml(reference)}</strong></div>
        <div><span>Idempotency</span><strong>Generated and persisted before submission</strong></div>
      </div>
    `, {
      eyebrow: "Final confirmation",
      note: "One GMO payment request will be submitted.",
      cancelLabel: "Cancel",
      confirmLabel: `Confirm and ${confirmVerb} ${money(amount)}`,
      confirmAction: "mit-send",
    });
  });

  document.querySelector("#mit-content").addEventListener("click", (event) => {
    const sectionButton = event.target.closest("[data-mit-section]");
    const kozaTransaction = event.target.closest("[data-koza-transaction]");
    const history = event.target.closest("[data-mit-history]");
    if (sectionButton) {
      opsState.mitSection = sectionButton.dataset.mitSection;
      renderMit();
    } else if (kozaTransaction) {
      openTransactionThread(kozaTransaction.dataset.kozaTransaction);
    } else if (event.target.closest("[data-koza-review]")) {
      const rows = kozaSelectedRows();
      opsState.pendingKozaBatch = { rowIds: rows.map((row) => row.id) };
      showPreview("Confirm monthly Koza Furikae batch", `
        <p>This local batch will submit one separate GMO debit request per selected mandate. Acceptance schedules the debit; it does not mark any row paid.</p>
        <div class="confirmation-impact"><span>Per selected row</span><code>EntryTranBankaccount → ExecTranBankaccount</code></div>
        <div class="preview-facts"><div><span>Batch group</span><strong>${escapeHtml(kozaBatch.id)}</strong></div><div><span>Debit requests</span><strong>${rows.length}</strong></div><div><span>Total</span><strong>${money(rows.reduce((sum, row) => sum + row.amount, 0))}</strong></div><div><span>Target date</span><strong>${escapeHtml(kozaBatch.targetDate)}</strong></div><div><span>Submit by</span><strong>${escapeHtml(kozaBatch.requestCutoff)}</strong></div><div><span>Expected result</span><strong>${escapeHtml(kozaBatch.expectedResults)}</strong></div></div>
        <ul class="removal-list">${rows.map((row) => `<li><span>${escapeHtml(row.customer)}</span><small>${money(row.amount)} · ${escapeHtml(row.instrument)}</small></li>`).join("")}</ul>
      `, { eyebrow: "Final batch confirmation", note: `${rows.length} independent payment threads will be created.`, cancelLabel: "Cancel", confirmLabel: `Submit ${rows.length} debit requests`, confirmAction: "koza-submit" });
    } else if (event.target.closest("[data-koza-apply-results]")) {
      showPreview("Apply asynchronous Koza results?", `
        <p>This simulates two independent protocol result notifications arriving after the debit date. One accepted request remains scheduled because no result message has arrived for it.</p>
        <div class="confirmation-impact"><span>Receiver response</span><code>HTTP 200 · body 0</code></div>
        <div class="preview-facts"><div><span>Success</span><strong>PAYSUCCESS · code 0</strong></div><div><span>Failure</span><strong>PAYFAIL · code 1</strong></div><div><span>Still scheduled</span><strong>No result message yet</strong></div><div><span>Linkage</span><strong>Original Order ID and thread</strong></div></div>
      `, { eyebrow: "Async result simulation", note: "Messages are applied independently and idempotently.", cancelLabel: "Cancel", confirmLabel: "Apply result messages", confirmAction: "koza-results" });
    } else if (event.target.closest("[data-open-configuration]")) {
      window.checkoutPrototype.setWorkspaceView("configuration");
    } else if (history) {
      openTransactionThread(history.dataset.mitHistory);
    } else if (event.target.closest("[data-manage-priority]")) {
      const customer = currentMitCustomer();
      const primary = primaryMitInstrument(customer);
      const backup = backupMitInstrument(customer);
      showPreview("Manage payment preference", `
        <p>Choose one Primary method and, optionally, one different Backup. This only marks the preference for now.</p>
        <div class="priority-editor">
          <label><span>Primary</span><select id="priority-primary">${customer.instruments.map((instrument) => `<option value="${instrument.id}" ${instrument.id === primary.id ? "selected" : ""}>${escapeHtml(instrument.label)}</option>`).join("")}</select></label>
          <label><span>Backup</span><select id="priority-backup"><option value="">No backup</option>${customer.instruments.map((instrument) => `<option value="${instrument.id}" ${instrument.id === primary.id ? "disabled" : ""} ${instrument.id === backup?.id ? "selected" : ""}>${escapeHtml(instrument.label)}</option>`).join("")}</select></label>
        </div>
        <div class="confirmation-impact"><span>Execution behavior</span><strong>Marking only — no automatic fallback yet</strong></div>
      `, {
        eyebrow: "Payment preference",
        note: "One Primary is required; Backup is optional.",
        cancelLabel: "Cancel",
        confirmLabel: "Save preference",
        confirmAction: "save-priority",
      });
    } else if (event.target.closest("[data-preview-clear]")) {
      const customer = currentMitCustomer();
      opsState.pendingClearCustomerId = customer.id;
      showPreview("Remove saved payment methods?", `
        <p>The following reusable references will be removed. Existing payments, refunds, disputes, and audit records will be preserved.</p>
        <ul class="removal-list">${customer.instruments.map((instrument) => `<li><span>${escapeHtml(instrument.label)}</span><small>${escapeHtml(instrument.method)} · ${escapeHtml(instrument.member)}</small></li>`).join("")}</ul>
        <div class="preview-facts">
          <div><span>Customer</span><strong>${escapeHtml(customer.id)} · ${escapeHtml(customer.name)}</strong></div>
          <div><span>Methods affected</span><strong>${customer.instruments.length}</strong></div>
          <div><span>History</span><strong>Preserved</strong></div>
          <div><span>Future MIT use</span><strong>Blocked until registered again</strong></div>
        </div>
      `, {
        eyebrow: "Destructive action",
        note: "This cannot be undone without customer registration.",
        cancelLabel: "Cancel",
        confirmLabel: `Remove ${customer.instruments.length} method${customer.instruments.length === 1 ? "" : "s"}`,
        confirmAction: "remove-methods",
        confirmTone: "danger",
      });
    }
  });

  document.querySelectorAll("[data-dialog-close]").forEach((button) => {
    button.addEventListener("click", () => document.querySelector("#prototype-action-dialog").close());
  });

  document.querySelector("#prototype-action-dialog").addEventListener("cancel", (event) => {
    if (event.currentTarget.dataset.dismissible === "false") event.preventDefault();
  });

  document.querySelector("#prototype-action-dialog").addEventListener("change", (event) => {
    if (event.target.id !== "priority-primary") return;
    const backupSelect = document.querySelector("#priority-backup");
    [...backupSelect.options].forEach((option) => {
      option.disabled = option.value !== "" && option.value === event.target.value;
    });
    if (backupSelect.value === event.target.value) backupSelect.value = "";
  });

  document.querySelector("[data-dialog-confirm]").addEventListener("click", (event) => {
    const action = event.currentTarget.dataset.dialogAction;
    if (action === "save-priority") {
      const customer = currentMitCustomer();
      const primaryId = document.querySelector("#priority-primary").value;
      let backupId = document.querySelector("#priority-backup").value;
      if (backupId === primaryId) backupId = "";
      opsState.pendingPriorityChange = { customerId: customer.id, primaryId, backupId };
      completePriorityChange();
    } else if (action === "koza-submit") {
      showPreview("Submitting monthly debit requests", `<div class="processing-state"><span class="processing-spinner" aria-hidden="true"></span><div><strong>Submitting each request</strong><p>Persisting each transaction before its EntryTranBankaccount and ExecTranBankaccount calls.</p></div></div>`, { eyebrow: "Processing", note: "Do not close this window.", dismissible: false });
      window.setTimeout(completeKozaBatchSubmission, 850);
    } else if (action === "koza-results") {
      showPreview("Applying protocol notifications", `<div class="processing-state"><span class="processing-spinner" aria-hidden="true"></span><div><strong>Storing asynchronous messages</strong><p>Deduplicating, acknowledging, and updating each original transaction thread.</p></div></div>`, { eyebrow: "Processing", note: "Do not close this window.", dismissible: false });
      window.setTimeout(completeKozaAsyncResults, 800);
    } else if (action === "mit-send") {
      showPreview("Submitting payment", `
        <div class="processing-state"><span class="processing-spinner" aria-hidden="true"></span><div><strong>Waiting for GMO</strong><p>Submitting the idempotent payment request and recording its response.</p></div></div>
      `, {
        eyebrow: "Processing",
        note: "Do not close this window.",
        dismissible: false,
      });
      window.setTimeout(completeSimulatedMitCharge, 750);
    } else if (action === "complete-transaction-action") {
      const pending = opsState.pendingTransactionAction;
      if (!pending) return;
      if (pending.action === "Refund payment") {
        pending.amount = Number(document.querySelector("#transaction-action-amount").value || 0);
        pending.reason = document.querySelector("#transaction-action-reason").value;
      }
      if (["Resolve discrepancy", "Mark dispute reviewed"].includes(pending.action)) {
        pending.note = document.querySelector("#transaction-action-note").value.trim() || "Reviewed by operator.";
      }
      if (pending.action === "Resolve discrepancy") pending.resolution = document.querySelector("#transaction-action-resolution").value;
      showPreview("Submitting action", `
        <div class="processing-state"><span class="processing-spinner" aria-hidden="true"></span><div><strong>Waiting for GMO</strong><p>Persisting the command before submission and appending the result to the transaction thread.</p></div></div>
      `, { eyebrow: "Processing", note: "Do not close this window.", dismissible: false });
      window.setTimeout(completeTransactionAction, 650);
    } else if (action === "remove-methods") {
      showPreview("Removing saved methods", `
        <div class="processing-state"><span class="processing-spinner" aria-hidden="true"></span><div><strong>Updating reusable references</strong><p>Removing the selected GMO references and recording the account audit event.</p></div></div>
      `, {
        eyebrow: "Processing",
        note: "Do not close this window.",
        dismissible: false,
      });
      window.setTimeout(completeSavedMethodRemoval, 550);
    } else if (action.startsWith("view-transaction:")) {
      openTransactionThread(action.split(":")[1]);
    }
  });

  window.renderOperationsMock = () => {
    populateTimeZones();
    if (window.checkoutPrototype.workspaceView === "operations") renderOperationsContent();
    if (window.checkoutPrototype.workspaceView === "mit") renderMit();
  };

  populateTimeZones();
})();
