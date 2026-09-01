const catalog = {
  card: {
    label: "Credit or debit card",
    shortLabel: "Credit or debit card",
    description: "Visa, Mastercard, JCB, and American Express",
    jaLabel: "クレジットカード／デビットカード",
    jaShortLabel: "クレジットカード／デビットカード",
    jaDescription: "Visa、Mastercard、JCB、American Express",
    icon: "Card",
    recurring: true,
    min: 1,
    max: 1000000,
    channels: ["PA", "IA", "FI"],
  },
  paypay: {
    label: "PayPay",
    shortLabel: "PayPay",
    description: "Pay from your PayPay balance or linked account",
    jaLabel: "PayPay",
    jaShortLabel: "PayPay",
    jaDescription: "PayPay残高または連携口座からお支払い",
    icon: "PayPay",
    recurring: true,
    min: 1,
    max: 500000,
    channels: ["PA", "IA"],
  },
  bankDirect: {
    label: "Real-time bank debit",
    shortLabel: "Real-time bank debit",
    description: "Register a supported bank account and debit today",
    jaLabel: "口座直結決済",
    jaShortLabel: "口座直結決済",
    jaDescription: "対応する銀行口座を登録して、本日すぐに引き落とし",
    icon: "Bank",
    recurring: true,
    min: 1,
    max: 300000,
    nonEkycMax: 50000,
    channels: ["PA", "IA", "FI"],
  },
  kozaFurikae: {
    label: "Bank transfer today + monthly bank debit",
    shortLabel: "Furikomi + Koza Furikae",
    description: "Register monthly bank debit, then receive today’s bank-transfer instructions",
    jaLabel: "初回銀行振込＋口座振替",
    jaShortLabel: "銀行振込＋口座振替",
    jaDescription: "今後の口座振替を登録し、初回保険料の銀行振込先を受け取る",
    icon: "Bank",
    recurring: true,
    monthlyOnly: true,
    min: 1,
    max: 1000000,
    channels: ["PA", "IA", "FI"],
  },
  kombini: {
    label: "Convenience store",
    shortLabel: "Convenience-store instructions",
    description: "Receive a receipt and pay at a supported store",
    jaLabel: "コンビニ払い",
    jaShortLabel: "コンビニ払い番号",
    jaDescription: "お支払い番号を受け取り、対応店舗でお支払い",
    icon: "Store",
    recurring: false,
    min: 1,
    max: 299999,
    channels: ["PA", "IA", "FI"],
  },
  payeasy: {
    label: "Pay-easy",
    shortLabel: "Pay-easy instructions",
    description: "Pay through ATM or online banking using issued numbers",
    jaLabel: "ペイジー",
    jaShortLabel: "ペイジー支払い番号",
    jaDescription: "発行された番号を使ってATMまたはネットバンキングでお支払い",
    icon: "Pay-easy",
    recurring: false,
    min: 1,
    max: 300000,
    channels: ["PA", "IA"],
  },
  furikomi: {
    label: "Bank transfer",
    shortLabel: "Bank-transfer instructions",
    description: "Transfer to a one-time virtual account",
    jaLabel: "銀行振込",
    jaShortLabel: "銀行振込口座",
    jaDescription: "今回のお支払い専用の振込口座へ送金",
    icon: "Xfer",
    recurring: false,
    min: 1,
    max: 1000000,
    channels: ["PA", "FI"],
  },
};

const defaultOrder = ["card", "paypay", "bankDirect", "kozaFurikae", "kombini", "payeasy", "furikomi"];
const defaultEnabledMethods = Object.fromEntries(defaultOrder.map((id) => [id, true]));
// The approved Koza journey is modeled as its own combined checkout method.
// Keep the older generic "pay today with one method, then enroll another future
// source" journey inactive so it cannot turn the Koza path into two choices.
const ENABLE_SEPARATE_FUTURE_SOURCE_FLOW = false;
const uiCopy = {
  en: {
    secureCheckout: "Secure checkout",
    payment: "Payment",
    confirmation: "Confirmation",
    firstPayment: "First payment",
    futurePayments: "Future payments",
    review: "Review",
    application: "Application",
    policy: "Policy",
    dueToday: "Due today",
    paymentPlan: "Payment plan",
    homeProtection: "Annuity",
    choosePaymentTitle: "Choose how you would like to pay.",
    choosePaymentLead: "Your first policy payment is due today. We will only show payment methods available for this application.",
    paymentMethod: "Payment method",
    choosePaymentMethod: "Choose a payment method",
    availablePaymentMethods: "Available payment methods",
    paymentSuccessful: "Payment successful.",
    paymentSuccessLead: "Your first payment was successful. This payment method is ready for future monthly payments.",
    instructionsReady: "Payment instructions ready.",
    instructionsReadyLead: "Your payment instructions have been created. Complete the payment using the details provided.",
    amountPaid: "Amount paid",
    amountDue: "Amount due",
    transactionReference: "Reference",
    returnToApplication: "Return to application",
    selectPaymentMethod: "Select a payment method",
    processingPayment: "Processing payment…",
    payAmount: "Pay",
    registerAndPay: "Register account and pay",
    bankValidation: "Enter the bank account number and account holder name before continuing.",
    tokenizationNote: "When Card is selected, its sensitive details are tokenized directly with GMO and never stored by the insurance application.",
    reviewTitle: "Review your payment plan.",
    reviewLead: "Confirm today’s payment and, when required, the method saved for future payments.",
    payToday: "Pay today",
    monthlyPolicy: "Monthly policy",
    oneTimePayment: "One-time payment",
    recurringBadge: "Reusable for monthly payments",
    oneTimeBadge: "One-time payment only",
    noMethods: "No payment methods are available.",
    noMethodsHelp: "Enable a method or adjust the channel, eKYC status, or amount.",
    noEligibleMethod: "No eligible method",
    continue: "Continue",
    confirmPayment: "Confirm payment",
    back: "Back",
    paidToday: "Paid today",
    reusableRequired: "A reusable method is required",
    sameSourceMonthly: "This payment method will also be saved for monthly payments",
    cardDetails: "Secure card details",
    cardIntro: "Enter your card details to authorize the first payment.",
    cardNumber: "Card number",
    expiry: "Expiry",
    securityCode: "Security code",
    cardholderName: "Cardholder name",
    cardholderPlaceholder: "Name on card",
    cardNote: "Your card details are handled securely by GMO and are not stored by us.",
    securePayment: "Secure payment",
    secureRedirect: "Secure handoff",
    secureRegistration: "Secure registration",
    paypayApproval: "PayPay approval",
    continuePayPay: "Continue with PayPay",
    paypayIntro: "After you continue, we securely redirect you to PayPay to sign in and approve this payment.",
    redirect: "Redirect",
    paymentToday: "Payment today",
    nextStep: "Next step",
    paypayRoute: "Approve the payment in PayPay. You will return here automatically when it is complete.",
    paypayNote: "Your PayPay sign-in and approval are completed securely on PayPay.",
    bankAuthorization: "Real-time bank debit authorization",
    authorizeBank: "Authorize a bank account",
    bankIntro: "Register a supported bank account and debit today in real time.",
    bankRedirect: "Bank redirect",
    bank: "Bank",
    accountType: "Account type",
    accountHolder: "Account holder name",
    bankRoute: "Complete registration with your bank. After you return, today’s payment is debited immediately.",
    bankNote: "Your account details are handled securely by GMO and your bank.",
    kozaTitle: "Set up monthly bank debit",
    kozaAction: "Register bank account and get transfer instructions",
    kozaIntro: "Complete one bank-registration journey. After it succeeds, we will immediately show the Furikomi instructions for your first payment.",
    kozaStepOne: "Register your bank account securely for future monthly premiums.",
    kozaStepTwo: "After registration succeeds, receive the bank-transfer account for today’s first premium.",
    kozaNote: "Your first premium is paid by Furikomi. Future monthly premiums are collected by Koza Furikae.",
    kozaValidation: "Enter the branch, account number, and account holder name before continuing.",
    kozaProcessing: "Registering bank account and preparing transfer instructions…",
    kozaCompleteTitle: "Bank account registered. Transfer instructions ready.",
    kozaCompleteLead: "Your monthly bank debit is set up. Complete the first premium by transferring the amount below.",
    kozaPendingTitle: "We’re confirming your bank registration",
    kozaPendingLead: "No transfer account will be issued and no duplicate registration will be sent while the result is unknown.",
    checkKozaStatus: "Check registration status",
    branch: "Branch",
    accountNumber: "Account number",
    accountName: "Account name",
    futureMonthlyPayments: "Future monthly payments",
    firstPremium: "First premium",
    kozaRegistered: "Koza Furikae registered",
    bankTransferDue: "Bank transfer due",
    transferAmount: "Transfer amount",
    transferBy: "Transfer by",
    transferReference: "Transfer reference",
    transferNotice: "The first premium has not been paid yet. Use the exact transfer reference so the payment can be matched to this application.",
    instructions: "Instructions",
    issueInstructions: "Issue payment instructions",
    refreshInstructions: "Refresh instructions",
    contact: "Contact",
    convenienceStore: "Convenience store",
    cardValidation: "Enter a card number, expiry date, security code, and cardholder name before continuing.",
    paymentTerms: "By confirming, you accept the policy payment terms.",
    checkoutPrototype: "Checkout prototype",
    paymentReady: "Payment plan ready.",
    backToCheckout: "Back to checkout",
    dialogCopy: "This mock shows the payment plan that the Java checkout API would prepare for GMO.",
    sendInstructionsBy: "Send instructions by",
    issuedInstructions: "Instructions issued",
    sentBy: "Sent by",
    emailOrMobile: "Email address or mobile number",
    kombiniInstructions: "Convenience-store instructions",
    kombiniIntro: "Choose a store and receive a payment receipt for this one-time payment.",
    kombiniNote: "The customer completes payment at the store.",
    payeasyInstructions: "Pay-easy instructions",
    payeasyIntro: "Issue the numbers needed for payment through an ATM or online banking.",
    payeasyNote: "The customer completes payment at an ATM or through online banking.",
    transferInstructions: "Bank-transfer instructions",
    transferIntro: "Issue a one-time virtual account for this policy payment.",
    transferNote: "The customer completes the transfer from their bank.",
  },
  ja: {
    secureCheckout: "安全なチェックアウト",
    payment: "お支払い",
    confirmation: "完了",
    firstPayment: "初回支払い",
    futurePayments: "今後のお支払い",
    review: "確認",
    application: "申込番号",
    policy: "保険商品",
    dueToday: "本日のお支払い",
    paymentPlan: "支払プラン",
    homeProtection: "年金保険",
    choosePaymentTitle: "お支払い方法を選択してください。",
    choosePaymentLead: "初回保険料は本日お支払いとなります。このお申し込みで利用可能なお支払い方法のみ表示しています。",
    paymentMethod: "お支払い方法",
    choosePaymentMethod: "お支払い方法を選択してください",
    availablePaymentMethods: "利用可能なお支払い方法",
    paymentSuccessful: "お支払いが完了しました。",
    paymentSuccessLead: "初回のお支払いが完了しました。このお支払い方法は今後の月払いにも使用されます。",
    instructionsReady: "お支払い情報を発行しました。",
    instructionsReadyLead: "発行された情報を使用してお支払いを完了してください。",
    amountPaid: "お支払い金額",
    amountDue: "お支払い予定額",
    transactionReference: "受付番号",
    returnToApplication: "申込画面に戻る",
    selectPaymentMethod: "お支払い方法を選択",
    processingPayment: "お支払いを処理しています…",
    payAmount: "支払う",
    registerAndPay: "口座を登録して支払う",
    bankValidation: "口座番号と口座名義を入力してください。",
    tokenizationNote: "カード情報はGMOで直接トークン化され、保険会社のシステムには保存されません。",
    reviewTitle: "お支払い内容をご確認ください。",
    reviewLead: "今回のお支払いと、月払いに使用するお支払い方法をご確認ください。",
    payToday: "本日のお支払い",
    monthlyPolicy: "月払い",
    oneTimePayment: "一括払い",
    recurringBadge: "月払いに利用可能",
    oneTimeBadge: "今回のお支払いのみ",
    noMethods: "利用可能なお支払い方法がありません。",
    noMethodsHelp: "設定、販売チャネル、eKYC、または金額をご確認ください。",
    noEligibleMethod: "利用可能なお支払い方法なし",
    continue: "続行",
    confirmPayment: "支払いを確定",
    back: "戻る",
    paidToday: "本日支払い",
    reusableRequired: "月払いに利用できる方法が必要です",
    sameSourceMonthly: "このお支払い方法を今後の月払いにも使用します",
    cardDetails: "カード情報",
    cardIntro: "初回のお支払いを承認するため、カード情報を入力してください。",
    cardNumber: "カード番号",
    expiry: "有効期限",
    securityCode: "セキュリティコード",
    cardholderName: "カード名義",
    cardholderPlaceholder: "カード名義人",
    cardNote: "カード情報はGMOが安全に取り扱い、当社では保存しません。",
    securePayment: "安全なお支払い",
    secureRedirect: "安全な画面遷移",
    secureRegistration: "安全な口座登録",
    paypayApproval: "PayPayで承認",
    continuePayPay: "PayPayで続行",
    paypayIntro: "続行後、PayPayへ安全に移動し、ログインしてお支払いを承認します。",
    redirect: "画面遷移",
    paymentToday: "今回のお支払い",
    nextStep: "次の手順",
    paypayRoute: "PayPayでお支払いを承認してください。完了後、自動的にこの画面へ戻ります。",
    paypayNote: "PayPayへのログインと承認は、PayPayの画面で安全に行われます。",
    bankAuthorization: "口座直結決済の登録",
    authorizeBank: "銀行口座を登録",
    bankIntro: "対応する銀行口座を登録し、本日のお支払いをリアルタイムで引き落とします。",
    bankRedirect: "銀行認証",
    bank: "銀行",
    accountType: "口座種別",
    accountHolder: "口座名義",
    bankRoute: "銀行で口座登録を完了してください。戻った後、本日のお支払いを即時に引き落とします。",
    bankNote: "口座情報はGMOと金融機関が安全に取り扱います。",
    kozaTitle: "今後の口座振替を登録",
    kozaAction: "口座を登録して振込先を受け取る",
    kozaIntro: "銀行での口座登録を一度完了すると、初回保険料の銀行振込先を続けて表示します。",
    kozaStepOne: "今後の月払保険料に使用する銀行口座を安全に登録します。",
    kozaStepTwo: "登録完了後、初回保険料の銀行振込先を受け取ります。",
    kozaNote: "初回保険料は銀行振込、今後の月払保険料は口座振替でお支払いいただきます。",
    kozaValidation: "支店、口座番号、口座名義を入力してください。",
    kozaProcessing: "口座を登録し、振込先を準備しています…",
    kozaCompleteTitle: "口座登録が完了し、振込先を発行しました。",
    kozaCompleteLead: "今後の口座振替を設定しました。以下の口座へ初回保険料をお振り込みください。",
    kozaPendingTitle: "口座登録の結果を確認しています",
    kozaPendingLead: "結果が不明な間は、振込口座の発行や重複した口座登録は行いません。",
    checkKozaStatus: "口座登録状況を確認",
    branch: "支店",
    accountNumber: "口座番号",
    accountName: "口座名義",
    futureMonthlyPayments: "今後の月払保険料",
    firstPremium: "初回保険料",
    kozaRegistered: "口座振替の登録完了",
    bankTransferDue: "銀行振込待ち",
    transferAmount: "振込金額",
    transferBy: "振込期限",
    transferReference: "振込依頼人番号",
    transferNotice: "初回保険料はまだお支払い済みではありません。お申し込みとの照合のため、振込依頼人番号を正確に入力してください。",
    instructions: "お支払い情報",
    issueInstructions: "お支払い情報を発行",
    refreshInstructions: "お支払い情報を再発行",
    contact: "連絡先",
    convenienceStore: "コンビニ",
    cardValidation: "カード番号、有効期限、セキュリティコード、カード名義を入力してください。",
    paymentTerms: "確定すると、保険料のお支払い条件に同意したものとみなされます。",
    checkoutPrototype: "チェックアウト試作画面",
    paymentReady: "お支払い内容を確認しました。",
    backToCheckout: "チェックアウトに戻る",
    dialogCopy: "JavaチェックアウトAPIがGMO向けに準備するお支払い内容を表示しています。",
    sendInstructionsBy: "通知方法",
    issuedInstructions: "お支払い情報を発行しました",
    sentBy: "送信方法",
    emailOrMobile: "メールアドレスまたは携帯電話番号",
    kombiniInstructions: "コンビニ払い情報",
    kombiniIntro: "コンビニを選択し、今回のお支払いに必要な番号を発行します。",
    kombiniNote: "お客様は選択したコンビニでお支払いを完了します。",
    payeasyInstructions: "ペイジー支払い情報",
    payeasyIntro: "ATMまたはネットバンキングで必要な番号を発行します。",
    payeasyNote: "お客様はATMまたはネットバンキングでお支払いを完了します。",
    transferInstructions: "銀行振込口座",
    transferIntro: "今回のお支払い専用の振込口座を発行します。",
    transferNote: "お客様はご自身の銀行から振込を行います。",
  },
};
const defaultTheme = {
  accent: "#635bff",
  canvas: "#f6f8fd",
  font: "Avenir Next, Avenir, Segoe UI, sans-serif",
  baseSize: 16,
  headingScale: 100,
};

const state = {
  channel: "PA",
  amount: 10000,
  ekyc: true,
  monthly: true,
  webhooks: true,
  kozaFurikae: true,
  kozaNotifications: true,
  publicBaseUrl: window.localStorage.getItem("gmo-mock-public-base-url") || "https://payments.example.com",
  browserReturnBaseUrl: window.localStorage.getItem("gmo-mock-browser-return-base-url") || "",
  openApiWebhookUrl: window.localStorage.getItem("gmo-mock-openapi-webhook-url") || "",
  protocolNotificationUrl: window.localStorage.getItem("gmo-mock-protocol-notification-url") || "",
  language: "en",
  selectedMethod: null,
  saveForFuture: null,
  // A new applicant has no saved future-payment source until they enroll one.
  recurringMethod: null,
  // A source is selectable before it is enrolled. Review stays locked until the
  // appropriate GMO collection or redirect step has completed.
  recurringEnrollment: {},
  enrollmentErrors: {},
  bankDirectDraft: {
    bank: "Mizuho Bank",
    branch: "Tokyo Central / 001",
    accountType: "Ordinary",
    accountNumber: "",
    accountHolder: "",
  },
  kozaAccountDraft: {
    bank: "Mitsubishi UFJ Bank",
    branch: "Tokyo Central / 001",
    accountType: "Ordinary",
    accountNumber: "",
    accountHolder: "",
  },
  cardDraft: {
    number: "",
    expiry: "",
    cvc: "",
  },
  initialCardDraft: {
    number: "",
    expiry: "",
    cvc: "",
    holder: "",
  },
  initialCardError: "",
  initialBankError: "",
  initialKozaError: "",
  paymentError: "",
  paymentPending: false,
  paymentResult: null,
  processingPayment: false,
  checkoutOutcome: "success",
  theme: { ...defaultTheme },
  configRelease: {
    dirty: false,
    version: 12,
    publishedAt: "30 Aug 2026 at 10:14",
    publishedBy: "Y. Nakamura",
  },
  issuedInstructions: {},
  deliveryPreferences: {},
  screen: "first",
  workspaceView: "checkout",
  order: [...defaultOrder],
  enabledMethods: { ...defaultEnabledMethods },
};

const money = (value) => `JPY ${Number(value).toLocaleString("en-US")}`;
const t = (key) => uiCopy[state.language]?.[key] || uiCopy.en[key] || key;
const methodLabel = (id, short = false) => {
  const method = catalog[id];
  if (state.language === "ja") return short ? method.jaShortLabel : method.jaLabel;
  return short ? method.shortLabel : method.label;
};
const methodDescription = (id) => state.language === "ja" ? catalog[id].jaDescription : catalog[id].description;

function isEligible(id) {
  const method = catalog[id];
  if (!state.enabledMethods[id]) {
    return { eligible: false, reason: "Disabled in checkout configuration." };
  }
  if (!method.channels.includes(state.channel)) {
    return { eligible: false, reason: `Not available for the ${state.channel} channel.` };
  }
  if (method.monthlyOnly && !state.monthly) {
    return { eligible: false, reason: "Available only for recurring plans." };
  }
  if (id === "kozaFurikae" && !state.kozaFurikae) {
    return { eligible: false, reason: "Koza Furikae is disabled in configuration." };
  }
  if (state.amount < method.min || state.amount > method.max) {
    return {
      eligible: false,
      reason: `Available for amounts from ${money(method.min)} to ${money(method.max)}.`,
    };
  }
  if (id === "bankDirect" && !state.ekyc && state.amount > method.nonEkycMax) {
    return {
      eligible: false,
      reason: `Without eKYC, real-time bank debit is limited to ${money(method.nonEkycMax)}.`,
    };
  }
  return { eligible: true, reason: "" };
}

function getEligibleMethods() {
  return state.order.filter((id) => {
    if (!isEligible(id).eligible) return false;
    // While the separate future-source flow is commented out, monthly policies
    // can only begin with a source that is reusable for recurring payments.
    if (state.monthly && !ENABLE_SEPARATE_FUTURE_SOURCE_FLOW) {
      return catalog[id].recurring;
    }
    return true;
  });
}

function getReusableMethods() {
  return state.order.filter((id) => catalog[id].recurring && isEligible(id).eligible);
}

function iconClass(id) {
  if (["bankDirect", "kozaFurikae"].includes(id)) return "is-bank";
  if (["kombini", "payeasy", "furikomi"].includes(id)) return "is-cash";
  if (id === "paypay") return "is-paypay";
  return "";
}

function normalizePaymentSelections() {
  const eligible = getEligibleMethods();
  if (state.selectedMethod && !eligible.includes(state.selectedMethod)) {
    state.selectedMethod = null;
    state.saveForFuture = null;
  }

const reusableMethods = getReusableMethods();
if (state.recurringMethod && !reusableMethods.includes(state.recurringMethod)) {
  state.recurringMethod = null;
}

  if ((!state.monthly || !ENABLE_SEPARATE_FUTURE_SOURCE_FLOW) && state.screen === "future") {
    state.screen = "review";
  }
}

function needsFutureSetup() {
  if (!ENABLE_SEPARATE_FUTURE_SOURCE_FLOW) return false;
  const selected = catalog[state.selectedMethod];
  return Boolean(state.monthly && selected && (!selected.recurring || state.saveForFuture !== true));
}

function selectedFutureMethod() {
  if (!state.monthly) return null;
  return needsFutureSetup() ? catalog[state.recurringMethod] : catalog[state.selectedMethod];
}

function accordionPanel(id, label, title, description, badge, body, note) {
  return `
    <section id="method-details-${id}" class="method-accordion method-accordion-${id}" aria-label="${label}">
      <div class="accordion-heading">
        <div>
          <span class="secure-label">${title}</span>
          <p>${description}</p>
        </div>
        <span class="element-badge">${badge}</span>
      </div>
      ${body}
      <p class="iframe-note">${note}</p>
    </section>
  `;
}

function cardEntry() {
  const error = state.initialCardError
    ? `<p class="enrollment-error" role="alert">${state.initialCardError}</p>`
    : "";

  return accordionPanel(
    "card",
    t("cardDetails"),
    t("cardDetails"),
    t("cardIntro"),
    t("securePayment"),
    `
      <div class="gmo-card-form" aria-label="Card field layout">
        <label class="gmo-field gmo-field-wide">
          <span>${t("cardNumber")}</span>
          <input id="initial-card-number" value="${state.initialCardDraft.number}" inputmode="numeric" autocomplete="cc-number" placeholder="1234 1234 1234 1234" />
        </label>
        <label class="gmo-field">
          <span>${t("expiry")}</span>
          <input id="initial-card-expiry" value="${state.initialCardDraft.expiry}" inputmode="numeric" autocomplete="cc-exp" placeholder="MM / YY" />
        </label>
        <label class="gmo-field">
          <span>${t("securityCode")}</span>
          <input id="initial-card-cvc" value="${state.initialCardDraft.cvc}" inputmode="numeric" autocomplete="cc-csc" placeholder="CVC" />
        </label>
        <label class="gmo-field gmo-field-wide" for="initial-cardholder-name">
          <span>${t("cardholderName")}</span>
          <input id="initial-cardholder-name" type="text" value="${state.initialCardDraft.holder}" autocomplete="cc-name" placeholder="${t("cardholderPlaceholder")}" />
        </label>
      </div>
      ${error}
    `,
    t("cardNote"),
  );
}

function paypayEntry() {
  return accordionPanel(
    "paypay",
    t("paypayApproval"),
    t("continuePayPay"),
    t("paypayIntro"),
    t("secureRedirect"),
    `
      <div class="handoff-summary" aria-label="PayPay handoff summary">
        <div><span>${t("paymentToday")}</span><strong>${money(state.amount)}</strong></div>
        <div><span>${t("nextStep")}</span><strong>${t("paypayApproval")}</strong></div>
      </div>
      <div class="method-route">
        <span class="route-step" aria-hidden="true">1</span>
        <p>${t("paypayRoute")}</p>
      </div>
    `,
    t("paypayNote"),
  );
}

function bankDirectEntry() {
  const error = state.initialBankError
    ? `<p class="enrollment-error" role="alert">${state.initialBankError}</p>`
    : "";

  return accordionPanel(
    "bankDirect",
    t("bankAuthorization"),
    t("authorizeBank"),
    t("bankIntro"),
    t("secureRegistration"),
    `
      <div class="compact-fields" aria-label="Real-time bank debit setup details">
        <label class="compact-field">
          <span>${t("bank")}</span>
          <select id="initial-bank" aria-label="Bank">
            <option>Mizuho Bank</option>
            <option>Mitsubishi UFJ Bank</option>
            <option>Sumitomo Mitsui Banking Corporation</option>
          </select>
        </label>
        <label class="compact-field">
          <span>${t("accountType")}</span>
          <select id="initial-bank-account-type" aria-label="Account type">
            <option>Ordinary</option>
            <option>Current</option>
          </select>
        </label>
        <label class="compact-field">
          <span>${state.language === "ja" ? "口座番号" : "Account number"}</span>
          <input id="initial-bank-account-number" value="${state.bankDirectDraft.accountNumber}" inputmode="numeric" autocomplete="off" placeholder="1234567" />
        </label>
        <label class="compact-field">
          <span>${t("accountHolder")}</span>
          <input id="initial-bank-account-holder" type="text" value="${state.bankDirectDraft.accountHolder}" autocomplete="name" placeholder="${t("accountHolder")}" />
        </label>
      </div>
      <div class="method-route">
        <span class="route-step" aria-hidden="true">1</span>
        <p>${t("bankRoute")}</p>
      </div>
      ${error}
    `,
    t("bankNote"),
  );
}

/**
 * Renders the combined recurring-plan path requested for Koza Furikae.
 *
 * The customer makes one choice and presses one primary action. Production will
 * still perform two ordered provider operations behind that action: confirm the
 * online bank-account registration first, then create a one-time virtual account
 * for the initial Furikomi premium. Keeping both stages visible here prevents the
 * first transfer from being mistaken for a Koza debit or a completed payment.
 */
function kozaFurikaeEntry() {
  const draft = state.kozaAccountDraft;
  const error = state.initialKozaError
    ? `<p class="enrollment-error" role="alert">${state.initialKozaError}</p>`
    : "";

  return accordionPanel(
    "kozaFurikae",
    t("kozaTitle"),
    t("kozaTitle"),
    t("kozaIntro"),
    t("secureRegistration"),
    `
      <div class="combined-payment-flow" aria-label="Koza Furikae registration followed by first-premium bank transfer">
        <div class="combined-flow-step">
          <span class="combined-flow-number" aria-hidden="true">1</span>
          <div><small>${t("futureMonthlyPayments")}</small><strong>${t("kozaStepOne")}</strong></div>
        </div>
        <span class="combined-flow-arrow" aria-hidden="true">&rarr;</span>
        <div class="combined-flow-step">
          <span class="combined-flow-number" aria-hidden="true">2</span>
          <div><small>${t("firstPremium")}</small><strong>${t("kozaStepTwo")}</strong></div>
        </div>
      </div>
      <div class="compact-fields" aria-label="Koza Furikae bank-account registration details">
        <label class="compact-field">
          <span>${t("bank")}</span>
          <select id="initial-koza-bank" aria-label="${t("bank")}">
            <option ${draft.bank === "Mitsubishi UFJ Bank" ? "selected" : ""}>Mitsubishi UFJ Bank</option>
            <option ${draft.bank === "Sumitomo Mitsui Banking Corporation" ? "selected" : ""}>Sumitomo Mitsui Banking Corporation</option>
            <option ${draft.bank === "Mizuho Bank" ? "selected" : ""}>Mizuho Bank</option>
          </select>
        </label>
        <label class="compact-field">
          <span>${t("branch")}</span>
          <input id="initial-koza-branch" type="text" value="${draft.branch}" autocomplete="off" placeholder="Tokyo Central / 001" />
        </label>
        <label class="compact-field">
          <span>${t("accountType")}</span>
          <select id="initial-koza-account-type" aria-label="${t("accountType")}">
            <option ${draft.accountType === "Ordinary" ? "selected" : ""}>Ordinary</option>
            <option ${draft.accountType === "Current" ? "selected" : ""}>Current</option>
          </select>
        </label>
        <label class="compact-field">
          <span>${t("accountNumber")}</span>
          <input id="initial-koza-account-number" value="${draft.accountNumber}" inputmode="numeric" autocomplete="off" placeholder="1234567" />
        </label>
        <label class="compact-field compact-field-wide">
          <span>${t("accountHolder")}</span>
          <input id="initial-koza-account-holder" type="text" value="${draft.accountHolder}" autocomplete="name" placeholder="${t("accountHolder")}" />
        </label>
      </div>
      <div class="method-route">
        <span class="route-step" aria-hidden="true">&rarr;</span>
        <p>${t("kozaNote")}</p>
      </div>
      ${error}
    `,
    t("bankNote"),
  );
}

function deliveryChoices(id) {
  const selected = state.deliveryPreferences[id] || "email";
  return `
    <div class="delivery-choice" role="group" aria-label="Instruction delivery method">
      <span>${t("sendInstructionsBy")}</span>
      ${["email", "line", "sms"]
        .map(
          (delivery) => `
            <button class="delivery-option ${selected === delivery ? "is-selected" : ""}"
              type="button" data-delivery="${delivery}" data-instruction-method="${id}">
              ${delivery === "line" ? "LINE" : delivery.toUpperCase()}
            </button>`,
        )
        .join("")}
    </div>
  `;
}

function instructionDetails(id) {
  const details = {
    kombini: [
      ["Store", "Lawson"],
      ["Receipt number", "L-4792-8043"],
      ["Confirmation number", "347881"],
      ["Pay by", "Aug 26, 2026 23:59 JST"],
    ],
    payeasy: [
      ["Customer number", "EC00506245166115087"],
      ["Institution code", "58091"],
      ["Confirmation number", "557792"],
      ["Pay by", "Aug 26, 2026 23:59 JST"],
    ],
    furikomi: [
      ["Bank / branch", "GMO Aozora Net Bank / 503"],
      ["Account type", "Ordinary"],
      ["Account number", "3352017"],
      ["Account name", "TEST SHOP"],
    ],
  }[id];
  const delivery = (state.deliveryPreferences[id] || "email").toUpperCase();
  return `
    <section class="issued-instructions" aria-live="polite">
      <div class="issued-heading"><span class="issued-dot"></span><strong>${t("issuedInstructions")}</strong><span>${t("sentBy")} ${delivery}</span></div>
      <dl>${details.map(([term, value]) => `<div><dt>${term}</dt><dd>${value}</dd></div>`).join("")}</dl>
      <p>Prototype only. In production, GMO returns these values and the notification service delivers them through the selected channel.</p>
    </section>
  `;
}

function instructionEntry(id, title, description, fields, note) {
  const issued = Boolean(state.issuedInstructions[id]);
  return accordionPanel(
    id,
    title,
    title,
    description,
    t("instructions"),
    `
      <div class="compact-fields" aria-label="${title} details">${fields}</div>
      ${deliveryChoices(id)}
      <button class="issue-button" type="button" data-issue-instructions="${id}">
        ${issued ? t("refreshInstructions") : t("issueInstructions")}
      </button>
      ${issued ? instructionDetails(id) : ""}
    `,
    note,
  );
}

function kombiniEntry() {
  return instructionEntry(
    "kombini",
    t("kombiniInstructions"),
    t("kombiniIntro"),
    `
      <label class="compact-field"><span>${t("convenienceStore")}</span><select aria-label="${t("convenienceStore")}"><option>Lawson</option><option>FamilyMart</option><option>Ministop</option></select></label>
      <label class="compact-field"><span>${t("contact")}</span><input type="text" autocomplete="email" placeholder="${t("emailOrMobile")}" /></label>
    `,
    t("kombiniNote"),
  );
}

function payeasyEntry() {
  return instructionEntry(
    "payeasy",
    t("payeasyInstructions"),
    t("payeasyIntro"),
    `<label class="compact-field compact-field-wide"><span>${t("contact")}</span><input type="text" autocomplete="email" placeholder="${t("emailOrMobile")}" /></label>`,
    t("payeasyNote"),
  );
}

function furikomiEntry() {
  return instructionEntry(
    "furikomi",
    t("transferInstructions"),
    t("transferIntro"),
    `<label class="compact-field compact-field-wide"><span>${t("contact")}</span><input type="text" autocomplete="email" placeholder="${t("emailOrMobile")}" /></label>`,
    t("transferNote"),
  );
}

function methodEntry(id) {
  switch (id) {
    case "card":
      return cardEntry();
    case "paypay":
      return paypayEntry();
    case "bankDirect":
      return bankDirectEntry();
    case "kozaFurikae":
      return kozaFurikaeEntry();
    case "kombini":
      return kombiniEntry();
    case "payeasy":
      return payeasyEntry();
    case "furikomi":
      return furikomiEntry();
    default:
      return "";
  }
}

function methodCard(id) {
  const method = catalog[id];
  const result = isEligible(id);
  const selected = state.selectedMethod === id;
  const recurringBadge = method.recurring ? t("recurringBadge") : t("oneTimeBadge");
  const badge = state.monthly
    ? ""
    : `<span class="method-badge ${method.recurring ? "" : "is-onetime"}">${recurringBadge}</span>`;
  const methodDetails = selected && state.screen === "first" ? methodEntry(id) : "";

  return `
    <article class="method-card ${selected ? "is-selected" : ""} ${result.eligible ? "" : "is-disabled"}">
      <button class="method-card-trigger" data-method="${id}" type="button"
        ${result.eligible ? "" : "disabled"}
        aria-expanded="${String(selected && state.screen === "first")}"
        aria-controls="method-details-${id}">
        <span class="selection-circle" aria-hidden="true"></span>
        <span class="method-icon ${iconClass(id)}">${method.icon}</span>
        <span class="method-main">
          <span class="method-title">${methodLabel(id)}</span>
          <span class="method-description">${methodDescription(id)}</span>
          ${badge}
        </span>
        <span class="accordion-chevron" aria-hidden="true">${selected ? "&#8722;" : "+"}</span>
        ${result.eligible ? "" : `<span class="reason">${result.reason}</span>`}
      </button>
      ${methodDetails}
    </article>
  `;
}

function renderMethods() {
  const methods = document.querySelector("#payment-methods");
  const eligible = getEligibleMethods();

  methods.innerHTML = eligible.length
    ? eligible.map(methodCard).join("")
    : `<div class="empty-methods" role="status"><strong>${t("noMethods")}</strong><span>${t("noMethodsHelp")}</span></div>`;

  methods.querySelectorAll("[data-method]").forEach((element) => {
    element.addEventListener("click", () => {
      const nextMethod = element.dataset.method;
      const changedMethod = state.selectedMethod !== nextMethod;
      state.selectedMethod = nextMethod;
      if (changedMethod) {
        state.initialCardError = "";
        state.initialBankError = "";
        state.initialKozaError = "";
        state.paymentError = "";
        state.paymentPending = false;
        state.paymentResult = null;
        state.saveForFuture = catalog[nextMethod].recurring && state.monthly
          ? ENABLE_SEPARATE_FUTURE_SOURCE_FLOW ? null : true
          : false;
      }
      render();
    });
  });

  methods.querySelectorAll("[data-issue-instructions]").forEach((button) => {
    button.addEventListener("click", (event) => {
      event.stopPropagation();
      state.issuedInstructions[button.dataset.issueInstructions] = true;
      render();
    });
  });

  methods.querySelectorAll("[data-delivery]").forEach((button) => {
    button.addEventListener("click", (event) => {
      event.stopPropagation();
      state.deliveryPreferences[button.dataset.instructionMethod] = button.dataset.delivery;
      render();
    });
  });
}

function renderFutureUse() {
  const selected = catalog[state.selectedMethod];
  const section = document.querySelector("#future-use");

  // The complete save/separate-source panel is retained below but commented out
  // of the active experience until the separate future-source enrollment UX is reviewed.
  if (!ENABLE_SEPARATE_FUTURE_SOURCE_FLOW) {
    section.classList.add("is-hidden");
    return;
  }

  const choiceGroup = document.querySelector("#future-choice-group");
  const title = document.querySelector("#future-title");
  const description = document.querySelector("#future-description");
  const eyebrow = document.querySelector("#future-eyebrow");
  const showPanel = Boolean(state.monthly && selected);

  section.classList.toggle("is-hidden", !showPanel);
  if (!showPanel) return;

  if (!selected.recurring) {
    section.classList.add("is-informational");
    choiceGroup.classList.add("is-hidden");
    eyebrow.textContent = "Future monthly payments";
    title.textContent = `${selected.label} can be used for today.`;
    description.textContent =
      "Issue these payment instructions for today to unlock the separate reusable source you will choose for future monthly payments.";
    return;
  }

  section.classList.remove("is-informational");
  choiceGroup.classList.remove("is-hidden");
  eyebrow.textContent = "Future monthly payments";
  title.textContent = `Save ${selected.label} for future monthly payments?`;
  description.textContent =
      `Use ${selected.label} for today. Choose whether to save it for future installments, or select a separate payment source after today's payment.`;

  document.querySelectorAll("[data-save]").forEach((button) => {
    const shouldSave = button.dataset.save === "yes";
    button.classList.toggle("is-selected", shouldSave === state.saveForFuture);
    button.addEventListener("click", () => {
      state.saveForFuture = shouldSave;
      if (shouldSave) state.recurringMethod = state.selectedMethod;
      render();
    });
  });
}

function sourceLabel(id, context = "first") {
  if (!id) return t("noEligibleMethod");
  if (context === "future") {
    if (state.language === "ja") return methodLabel(id);
    return {
      card: "New credit or debit card",
      paypay: "New PayPay approval",
      bankDirect: "New real-time bank debit authorization",
    }[id] || methodLabel(id);
  }
  return methodLabel(id);
}

function futureEnrollmentBody(id) {
  const enrolled = Boolean(state.recurringEnrollment[id]);
  const completion = enrolled
    ? `<p class="enrollment-complete"><span aria-hidden="true">&#10003;</span> Reusable ${catalog[id].label} source is ready for future monthly payments.</p>`
    : "";
  const error = state.enrollmentErrors[id]
    ? `<p class="enrollment-error" role="alert">${state.enrollmentErrors[id]}</p>`
    : "";

  if (id === "card") {
    return `
      <div class="future-enrollment-card">
        <div class="gmo-card-form compact-gmo-form">
          <label class="gmo-field gmo-field-wide"><span>Card number</span><input id="future-card-number" value="${state.cardDraft.number}" inputmode="numeric" autocomplete="cc-number" placeholder="1234 1234 1234 1234" /></label>
          <label class="gmo-field"><span>Expiry</span><input id="future-card-expiry" value="${state.cardDraft.expiry}" inputmode="numeric" autocomplete="cc-exp" placeholder="MM / YY" /></label>
          <label class="gmo-field"><span>Security code</span><input id="future-card-cvc" value="${state.cardDraft.cvc}" inputmode="numeric" autocomplete="cc-csc" placeholder="CVC" /></label>
        </div>
        ${error}
        ${enrolled ? completion : `<button class="enrollment-button" type="button" data-complete-recurring="card">Save card for future payments</button>`}
      </div>
    `;
  }
  if (id === "paypay") {
    return `
      <div class="future-enrollment-card">
        <p>PayPay account credentials are entered only on PayPay's secure page. Start the approval now; after PayPay returns, this source is saved for future monthly payments.</p>
        <div class="handoff-summary" aria-label="PayPay recurring enrollment">
          <div><span>Source</span><strong>New PayPay account</strong></div>
          <div><span>Approval</span><strong>PayPay browser redirect</strong></div>
        </div>
        ${enrolled ? completion : `<button class="enrollment-button" type="button" data-complete-recurring="paypay">Continue to PayPay authorization <span>&rarr;</span></button>`}
        <span class="redirect-note">Secure browser redirect</span>
      </div>
    `;
  }
  return `
    <div class="future-enrollment-card">
      <p>Authorize a new bank account for future monthly direct-debit payments. This may be a different account from any first payment.</p>
      <div class="compact-fields">
        <label class="compact-field"><span>Bank</span><select id="future-bank-name"><option ${state.bankDirectDraft.bank === "Mizuho Bank" ? "selected" : ""}>Mizuho Bank</option><option ${state.bankDirectDraft.bank === "Mitsubishi UFJ Bank" ? "selected" : ""}>Mitsubishi UFJ Bank</option><option ${state.bankDirectDraft.bank === "Sumitomo Mitsui Banking Corporation" ? "selected" : ""}>Sumitomo Mitsui Banking Corporation</option></select></label>
        <label class="compact-field"><span>Branch</span><select id="future-bank-branch"><option ${state.bankDirectDraft.branch === "Tokyo Central / 001" ? "selected" : ""}>Tokyo Central / 001</option><option ${state.bankDirectDraft.branch === "Marunouchi / 005" ? "selected" : ""}>Marunouchi / 005</option><option ${state.bankDirectDraft.branch === "Shinjuku / 220" ? "selected" : ""}>Shinjuku / 220</option></select></label>
        <label class="compact-field"><span>Account type</span><select id="future-bank-account-type"><option ${state.bankDirectDraft.accountType === "Ordinary" ? "selected" : ""}>Ordinary</option><option ${state.bankDirectDraft.accountType === "Current" ? "selected" : ""}>Current</option></select></label>
        <label class="compact-field"><span>Account number</span><input id="future-bank-account-number" value="${state.bankDirectDraft.accountNumber}" inputmode="numeric" autocomplete="off" placeholder="7-digit account number" /></label>
        <label class="compact-field compact-field-wide"><span>Account holder name</span><input id="future-bank-account-holder" value="${state.bankDirectDraft.accountHolder}" autocomplete="name" placeholder="e.g. タナカ ハナコ" /></label>
      </div>
      ${error}
      ${enrolled ? completion : `<button class="enrollment-button" type="button" data-complete-recurring="bankDirect">Continue to bank authorization <span>&rarr;</span></button>`}
      <span class="redirect-note">Secure bank authorization redirect</span>
    </div>
  `;
}

function futureMethodCard(id) {
  const method = catalog[id];
  const selected = state.recurringMethod === id;
  return `
    <article class="future-method-card ${selected ? "is-selected" : ""}">
      <button class="future-method-trigger" data-recurring-method="${id}" type="button" aria-expanded="${selected}">
        <span class="selection-circle" aria-hidden="true"></span>
        <span class="method-icon ${iconClass(id)}">${method.icon}</span>
        <span class="method-main"><span class="method-title">${method.label}</span><span class="method-description">Set up a new source for future monthly payments.</span><span class="method-badge">Reusable monthly payment source</span></span>
        <span class="accordion-chevron" aria-hidden="true">${selected ? "&#8722;" : "+"}</span>
      </button>
      ${selected ? futureEnrollmentBody(id) : ""}
    </article>
  `;
}

function renderFutureSetup() {
  const selected = catalog[state.selectedMethod];
  const candidates = getReusableMethods();
  const futureMethod = catalog[state.recurringMethod];

  document.querySelector("#future-first-method").textContent =
    selected ? sourceLabel(state.selectedMethod) : "No eligible method";
  document.querySelector("#future-first-amount").textContent = money(state.amount);

  const holder = document.querySelector("#future-methods");
  holder.innerHTML = candidates.length
    ? candidates.map(futureMethodCard).join("")
    : '<p class="subtle">No reusable payment method matches these rules. Adjust the checkout policy.</p>';

  holder.querySelectorAll("[data-recurring-method]").forEach((element) => {
    element.addEventListener("click", () => {
      state.recurringMethod = element.dataset.recurringMethod;
      render();
    });
  });

  holder.querySelectorAll("[data-complete-recurring]").forEach((element) => {
    element.addEventListener("click", () => {
      const method = element.dataset.completeRecurring;
      if (method === "card") {
        const number = holder.querySelector("#future-card-number").value.replace(/\D/g, "");
        const expiry = holder.querySelector("#future-card-expiry").value.trim();
        const cvc = holder.querySelector("#future-card-cvc").value.replace(/\D/g, "");
        state.cardDraft = { number, expiry, cvc };
        if (number.length < 13 || !expiry || cvc.length < 3) {
          state.enrollmentErrors[method] =
            "Enter a card number, expiry date, and security code before saving this card.";
          render();
          return;
        }
      }
      if (method === "bankDirect") {
        const accountNumber = holder.querySelector("#future-bank-account-number").value.replace(/\D/g, "");
        const accountHolder = holder.querySelector("#future-bank-account-holder").value.trim();
        state.bankDirectDraft = {
          bank: holder.querySelector("#future-bank-name").value,
          branch: holder.querySelector("#future-bank-branch").value,
          accountType: holder.querySelector("#future-bank-account-type").value,
          accountNumber,
          accountHolder,
        };
        if (accountNumber.length < 4 || !accountHolder) {
          state.enrollmentErrors[method] = "Enter the account number and account holder name before continuing to the secure bank authorization.";
          render();
          return;
        }
      }
      delete state.enrollmentErrors[method];
      state.recurringEnrollment[method] = true;
      render();
    });
  });

  document.querySelector("#future-continue").disabled =
    !futureMethod || !state.recurringEnrollment[state.recurringMethod];
  document.querySelector("#future-continue").innerHTML = state.recurringEnrollment[state.recurringMethod]
    ? "Continue to review <span>&rarr;</span>"
    : "Complete setup to continue";
}

function renderConfirmation() {
  const selected = catalog[state.selectedMethod];
  const instructionsRequired = Boolean(
    selected && !selected.recurring && !state.issuedInstructions[state.selectedMethod],
  );

  document.querySelector("#summary-amount").textContent = money(state.amount);
  document.querySelector("#policy-schedule").textContent =
    state.monthly ? t("monthlyPolicy") : t("oneTimePayment");

  document.querySelector("#first-continue").disabled =
    state.processingPayment || !selected || instructionsRequired || (
      ENABLE_SEPARATE_FUTURE_SOURCE_FLOW &&
      state.monthly &&
      selected.recurring &&
      state.saveForFuture === null
    );
  const action = document.querySelector("#first-continue");
  if (state.processingPayment) {
    action.textContent = state.selectedMethod === "kozaFurikae"
      ? t("kozaProcessing")
      : t("processingPayment");
  } else if (!selected) {
    action.textContent = t("selectPaymentMethod");
  } else if (!selected.recurring) {
    action.innerHTML = `${t("continue")} <span>&rarr;</span>`;
  } else if (state.selectedMethod === "kozaFurikae") {
    action.innerHTML = `${t("kozaAction")} <span>&rarr;</span>`;
  } else if (state.selectedMethod === "bankDirect") {
    action.innerHTML = `${t("registerAndPay")} ${money(state.amount)} <span>&rarr;</span>`;
  } else {
    action.innerHTML = `${t("payAmount")} ${money(state.amount)} <span>&rarr;</span>`;
  }

  const paymentError = document.querySelector("#payment-error");
  paymentError.textContent = state.paymentError;
  paymentError.classList.toggle("is-hidden", !state.paymentError);

  const paymentPending = document.querySelector("#payment-pending");
  paymentPending.classList.toggle("is-hidden", !state.paymentPending);
  const pendingIsKoza = state.selectedMethod === "kozaFurikae";
  document.querySelector("#payment-pending-title").textContent = pendingIsKoza
    ? t("kozaPendingTitle")
    : "We’re confirming your payment";
  document.querySelector("#payment-pending-lead").textContent = pendingIsKoza
    ? t("kozaPendingLead")
    : "No second payment request will be sent while the result is unknown.";
  const statusButton = document.querySelector("#check-payment-status");
  statusButton.textContent = pendingIsKoza ? t("checkKozaStatus") : "Check payment status";
  statusButton.disabled = state.processingPayment;

  const result = state.paymentResult;
  if (!result) return;
  const usesKozaFurikae = result.kind === "koza-registration-furikomi";
  const usesInstructions = Boolean(selected && !selected.recurring);
  document.querySelector("#confirmation-title").textContent =
    t(usesKozaFurikae ? "kozaCompleteTitle" : usesInstructions ? "instructionsReady" : "paymentSuccessful");
  document.querySelector("#confirmation-lead").textContent =
    t(usesKozaFurikae ? "kozaCompleteLead" : usesInstructions ? "instructionsReadyLead" : "paymentSuccessLead");
  document.querySelector("#confirmation-method").textContent = sourceLabel(state.selectedMethod);
  document.querySelector("#confirmation-amount-label").textContent =
    t(usesKozaFurikae || usesInstructions ? "amountDue" : "amountPaid");
  document.querySelector("#confirmation-amount").textContent = money(result.amount);
  document.querySelector("#confirmation-reference").textContent = result.reference;

  const kozaConfirmation = document.querySelector("#koza-confirmation");
  kozaConfirmation.classList.toggle("is-hidden", !usesKozaFurikae);
  if (usesKozaFurikae) {
    document.querySelector("#koza-future-label").textContent = t("futureMonthlyPayments");
    document.querySelector("#koza-registration-status").textContent = t("kozaRegistered");
    document.querySelector("#koza-registration-reference").textContent = result.registrationReference;
    document.querySelector("#koza-first-label").textContent = t("firstPremium");
    document.querySelector("#koza-transfer-status").textContent = t("bankTransferDue");
    document.querySelector("#koza-transfer-heading").textContent = `${t("transferAmount")}: ${money(result.amount)}`;
    document.querySelector("#koza-transfer-bank-label").textContent = t("bank");
    document.querySelector("#koza-transfer-branch-label").textContent = t("branch");
    document.querySelector("#koza-transfer-type-label").textContent = t("accountType");
    document.querySelector("#koza-transfer-number-label").textContent = t("accountNumber");
    document.querySelector("#koza-transfer-name-label").textContent = t("accountName");
    document.querySelector("#koza-transfer-reference-label").textContent = t("transferReference");
    document.querySelector("#koza-transfer-by-label").textContent = t("transferBy");
    document.querySelector("#koza-transfer-bank").textContent = result.furikomi.bank;
    document.querySelector("#koza-transfer-branch").textContent = result.furikomi.branch;
    document.querySelector("#koza-transfer-type").textContent = result.furikomi.accountType;
    document.querySelector("#koza-transfer-number").textContent = result.furikomi.accountNumber;
    document.querySelector("#koza-transfer-name").textContent = result.furikomi.accountName;
    document.querySelector("#koza-transfer-reference").textContent = result.furikomi.transferReference;
    document.querySelector("#koza-transfer-by").textContent = result.furikomi.payBy;
    document.querySelector("#koza-transfer-notice").textContent = t("transferNotice");
  }
  document.querySelector("#confirmation-done").innerHTML = `${t("returnToApplication")} <span>&rarr;</span>`;
}

function renderScreens() {
  document.querySelectorAll(".checkout-screen").forEach((screen) => {
    screen.classList.toggle("is-hidden", screen.id !== `screen-${state.screen}`);
  });
}

function renderProgress() {
  const first = document.querySelector('[data-progress="first"]');
  const future = document.querySelector('[data-progress="future"]');
  const review = document.querySelector('[data-progress="review"]');

  document.querySelectorAll(".future-progress-only").forEach((item) => {
    item.classList.toggle("is-hidden", !ENABLE_SEPARATE_FUTURE_SOURCE_FLOW);
  });
  review.querySelector("b").textContent = ENABLE_SEPARATE_FUTURE_SOURCE_FLOW ? "3" : "2";

  [first, future, review].forEach((item) => {
    item.classList.remove("is-active", "is-complete", "is-skipped");
  });

  if (!ENABLE_SEPARATE_FUTURE_SOURCE_FLOW) {
    if (state.screen === "first") {
      first.classList.add("is-active");
    } else {
      first.classList.add("is-complete");
      review.classList.add("is-active");
    }
    return;
  }

  if (state.screen === "first") {
    first.classList.add("is-active");
    if (!state.monthly) future.classList.add("is-skipped");
    return;
  }

  first.classList.add("is-complete");
  if (state.screen === "future") {
    future.classList.add("is-active");
    return;
  }

  if (state.monthly) {
    future.classList.add("is-complete");
  } else {
    future.classList.add("is-skipped");
  }
  review.classList.add("is-active");
}

function renderOrder() {
  const list = document.querySelector("#method-order");
  list.innerHTML = state.order
    .map(
      (id, index) => `
        <li class="order-item ${state.enabledMethods[id] ? "" : "is-config-disabled"}">
          <span class="order-number">${index + 1}</span>
          <span class="order-method-copy">
            <span>${methodLabel(id)}</span>
            <small>${state.enabledMethods[id] ? "Shown when eligible" : "Hidden from checkout"}</small>
          </span>
          <button class="method-availability ${state.enabledMethods[id] ? "is-on" : ""}" type="button"
            role="switch" aria-checked="${String(state.enabledMethods[id])}"
            data-method-enabled="${id}" aria-label="${state.enabledMethods[id] ? "Disable" : "Enable"} ${catalog[id].label}">
            <span class="availability-dot" aria-hidden="true"></span>
            <span>${state.enabledMethods[id] ? "On" : "Off"}</span>
          </button>
          <span class="order-actions">
            <button type="button" data-move="up" data-index="${index}" ${index === 0 ? "disabled" : ""} aria-label="Move ${catalog[id].label} up">&#8593;</button>
            <button type="button" data-move="down" data-index="${index}" ${index === state.order.length - 1 ? "disabled" : ""} aria-label="Move ${catalog[id].label} down">&#8595;</button>
          </span>
        </li>
      `,
    )
    .join("");

  list.querySelectorAll("[data-move]").forEach((button) => {
    button.addEventListener("click", () => {
      const from = Number(button.dataset.index);
      const to = button.dataset.move === "up" ? from - 1 : from + 1;
      [state.order[from], state.order[to]] = [state.order[to], state.order[from]];
      render();
    });
  });

  list.querySelectorAll("[data-method-enabled]").forEach((button) => {
    button.addEventListener("click", () => {
      const id = button.dataset.methodEnabled;
      state.enabledMethods[id] = !state.enabledMethods[id];
      state.screen = "first";
      render();
    });
  });
}

function applyLanguage() {
  document.documentElement.lang = state.language === "ja" ? "ja" : "en";
  document.querySelectorAll("[data-i18n]").forEach((element) => {
    element.textContent = t(element.dataset.i18n);
  });
  document.querySelectorAll("[data-language]").forEach((button) => {
    const selected = button.dataset.language === state.language;
    button.classList.toggle("is-selected", selected);
    button.setAttribute("aria-pressed", String(selected));
  });
}

function renderWorkspaceView() {
  const layout = document.querySelector(".checkout-layout");
  const panels = {
    checkout: document.querySelector("#checkout-panel"),
    configuration: document.querySelector("#configuration-panel"),
    operations: document.querySelector("#operations-panel"),
    mit: document.querySelector("#mit-panel"),
  };

  layout.classList.toggle("is-config-view", state.workspaceView === "configuration");
  layout.classList.toggle("is-operations-view", ["operations", "mit"].includes(state.workspaceView));
  Object.entries(panels).forEach(([view, panel]) => {
    panel.classList.toggle("is-hidden", view !== state.workspaceView);
  });

  document.querySelectorAll("[data-workspace-view]").forEach((button) => {
    const selected = button.dataset.workspaceView === state.workspaceView;
    button.classList.toggle("is-selected", selected);
    button.setAttribute("aria-selected", String(selected));
  });

  window.renderOperationsMock?.();
}

function cleanExternalUrl(value) {
  return value.trim().replace(/\/+$/, "");
}

function resolvedIntegrationConfig() {
  const publicBaseUrl = cleanExternalUrl(state.publicBaseUrl);
  const returnBaseUrl = cleanExternalUrl(state.browserReturnBaseUrl) || publicBaseUrl;
  return {
    publicBaseUrl,
    browserReturnUrl: returnBaseUrl ? `${returnBaseUrl}/checkout/provider-return` : "Not configured",
    openApiWebhookUrl: cleanExternalUrl(state.openApiWebhookUrl)
      || (publicBaseUrl ? `${publicBaseUrl}/api/v1/webhooks/gmo/openapi` : "Not configured"),
    protocolNotificationUrl: cleanExternalUrl(state.protocolNotificationUrl)
      || (publicBaseUrl ? `${publicBaseUrl}/api/v1/webhooks/gmo/protocol` : "Not configured"),
    source: publicBaseUrl === "https://payments.example.com" && !state.browserReturnBaseUrl
      && !state.openApiWebhookUrl && !state.protocolNotificationUrl
      ? "Generic default"
      : "Local browser",
    webhooksEnabled: state.webhooks,
  };
}

function renderIntegrationConfig() {
  const fields = {
    "public-base-url": state.publicBaseUrl,
    "browser-return-base-url": state.browserReturnBaseUrl,
    "openapi-webhook-url": state.openApiWebhookUrl,
    "protocol-notification-url": state.protocolNotificationUrl,
  };
  Object.entries(fields).forEach(([id, value]) => {
    const field = document.querySelector(`#${id}`);
    if (field && document.activeElement !== field) field.value = value;
  });

  const config = resolvedIntegrationConfig();
  document.querySelector("#integration-source").textContent = config.source;
  document.querySelector("#resolved-integration-urls").innerHTML = `
    <div><span>Browser return</span><code>${config.browserReturnUrl}</code></div>
    <div><span>OpenAPI webhook</span><code>${config.openApiWebhookUrl}</code></div>
    <div><span>Protocol notification</span><code>${config.protocolNotificationUrl}</code></div>
  `;
}

function captureConfig() {
  return {
    channel: state.channel,
    amount: state.amount,
    ekyc: state.ekyc,
    monthly: state.monthly,
    webhooks: state.webhooks,
    kozaFurikae: state.kozaFurikae,
    kozaNotifications: state.kozaNotifications,
    publicBaseUrl: state.publicBaseUrl,
    browserReturnBaseUrl: state.browserReturnBaseUrl,
    openApiWebhookUrl: state.openApiWebhookUrl,
    protocolNotificationUrl: state.protocolNotificationUrl,
    language: state.language,
    checkoutOutcome: state.checkoutOutcome,
    order: [...state.order],
    enabledMethods: { ...state.enabledMethods },
    theme: { ...state.theme },
  };
}

let publishedConfig;

function renderConfigRelease() {
  const release = state.configRelease;
  const status = document.querySelector("#config-status");
  status.textContent = release.dirty ? "Draft changes" : "Active";
  status.classList.toggle("is-active", !release.dirty);
  status.classList.toggle("is-draft", release.dirty);
  document.querySelector("#config-version").textContent = `Version ${release.version}`;
  document.querySelector("#config-release-copy").textContent = release.dirty
    ? "Checkout is previewing this draft. Customers continue to receive the active version until it is published."
    : "The active configuration is serving Checkout. Changes below create a draft preview.";
  document.querySelector("#config-updated").textContent = `Published ${release.publishedAt} · ${release.publishedBy}`;
  document.querySelector("#discard-config").disabled = !release.dirty;
  document.querySelector("#publish-config").disabled = !release.dirty;
}

function markConfigDirty() {
  if (state.configRelease.dirty) return;
  state.configRelease.dirty = true;
  renderConfigRelease();
}

function restorePublishedConfig() {
  if (!publishedConfig) return;
  Object.assign(state, {
    ...publishedConfig,
    order: [...publishedConfig.order],
    enabledMethods: { ...publishedConfig.enabledMethods },
    theme: { ...publishedConfig.theme },
  });
  state.configRelease.dirty = false;
  state.screen = "first";
  document.querySelector("#amount").value = String(state.amount);
  document.querySelector("#checkout-outcome").value = state.checkoutOutcome;
  setSwitch(document.querySelector("#ekyc-toggle"), state.ekyc);
  setSwitch(document.querySelector("#monthly-toggle"), state.monthly);
  setSwitch(document.querySelector("#webhook-toggle"), state.webhooks);
  setSwitch(document.querySelector("#koza-batch-toggle"), state.kozaFurikae);
  setSwitch(document.querySelector("#koza-notification-toggle"), state.kozaNotifications);
  document.querySelector("#accent-color").value = state.theme.accent;
  document.querySelector("#canvas-color").value = state.theme.canvas;
  document.querySelector("#font-family").value = state.theme.font;
  document.querySelector("#base-size").value = state.theme.baseSize;
  document.querySelector("#heading-scale").value = state.theme.headingScale;
  applyTheme(state.theme);
  render();
}

function render() {
  normalizePaymentSelections();
  applyLanguage();
  renderWorkspaceView();
  renderScreens();
  renderProgress();
  renderMethods();
  renderFutureUse();
  renderFutureSetup();
  renderConfirmation();
  renderOrder();
  renderIntegrationConfig();
  renderConfigRelease();
}

function setScreen(screen) {
  state.screen = screen;
  render();
  window.scrollTo({ top: 0, behavior: "smooth" });
}

function setSwitch(button, enabled) {
  button.classList.toggle("is-on", enabled);
  button.setAttribute("aria-checked", String(enabled));
}

function applyTheme(theme) {
  const root = document.documentElement;
  root.style.setProperty("--violet", theme.accent);
  root.style.setProperty("--violet-dark", theme.accent);
  root.style.setProperty("--canvas", theme.canvas);
  root.style.setProperty("--font-body", theme.font);
  root.style.setProperty("--base-font-size", `${theme.baseSize}px`);
  root.style.setProperty("--heading-scale", String(theme.headingScale / 100));
  root.style.setProperty("--heading-size", `calc(clamp(1.7rem, 2.4vw, 2.15rem) * ${theme.headingScale / 100})`);
}

function resetTheme() {
  document.querySelector("#accent-color").value = defaultTheme.accent;
  document.querySelector("#canvas-color").value = defaultTheme.canvas;
  document.querySelector("#font-family").value = defaultTheme.font;
  document.querySelector("#base-size").value = defaultTheme.baseSize;
  document.querySelector("#heading-scale").value = defaultTheme.headingScale;
  updateTheme();
}

function updateTheme() {
  const theme = {
    accent: document.querySelector("#accent-color").value,
    canvas: document.querySelector("#canvas-color").value,
    font: document.querySelector("#font-family").value,
    baseSize: Number(document.querySelector("#base-size").value),
    headingScale: Number(document.querySelector("#heading-scale").value),
  };
  document.querySelector("#base-size-output").textContent = `${theme.baseSize} px`;
  document.querySelector("#heading-scale-output").textContent = `${theme.headingScale}%`;
  state.theme = theme;
  applyTheme(theme);
}

document.querySelectorAll("[data-language]").forEach((button) => {
  button.addEventListener("click", () => {
    state.language = button.dataset.language;
    render();
  });
});

document.querySelectorAll("[data-workspace-view]").forEach((button) => {
  button.addEventListener("click", () => {
    state.workspaceView = button.dataset.workspaceView;
    renderWorkspaceView();
    window.scrollTo({ top: 0, behavior: "smooth" });
  });
});

document.querySelectorAll("[data-channel]").forEach((button) => {
  button.addEventListener("click", () => {
    state.channel = button.dataset.channel;
    state.screen = "first";
    document.querySelectorAll("[data-channel]").forEach((item) => {
      item.classList.toggle("is-selected", item === button);
    });
    render();
  });
});

document.querySelector("#amount").addEventListener("change", (event) => {
  state.amount = Number(event.target.value);
  state.screen = "first";
  render();
});

document.querySelector("#checkout-outcome").addEventListener("change", (event) => {
  state.checkoutOutcome = event.target.value;
  state.paymentError = "";
  state.paymentPending = false;
  state.screen = "first";
  render();
});

document.querySelector("#ekyc-toggle").addEventListener("click", (event) => {
  state.ekyc = !state.ekyc;
  state.screen = "first";
  setSwitch(event.currentTarget, state.ekyc);
  render();
});

document.querySelector("#monthly-toggle").addEventListener("click", (event) => {
  state.monthly = !state.monthly;
  if (catalog[state.selectedMethod]?.recurring) {
    // A future-use decision is only meaningful while the policy has future payments.
    state.saveForFuture = state.monthly
      ? ENABLE_SEPARATE_FUTURE_SOURCE_FLOW ? null : true
      : false;
  }
  state.screen = "first";
  setSwitch(event.currentTarget, state.monthly);
  render();
});

document.querySelector("#webhook-toggle").addEventListener("click", (event) => {
  state.webhooks = !state.webhooks;
  setSwitch(event.currentTarget, state.webhooks);
  window.renderOperationsMock?.();
});

document.querySelector("#koza-batch-toggle").addEventListener("click", (event) => {
  state.kozaFurikae = !state.kozaFurikae;
  state.screen = "first";
  setSwitch(event.currentTarget, state.kozaFurikae);
  // This feature flag spans both sides of the approved product boundary: the
  // customer can enroll only when the later operator batch capability is on.
  // Re-render the whole mock so Checkout eligibility and MIT controls cannot
  // drift apart while an administrator previews a draft configuration.
  render();
});

document.querySelector("#koza-notification-toggle").addEventListener("click", (event) => {
  state.kozaNotifications = !state.kozaNotifications;
  setSwitch(event.currentTarget, state.kozaNotifications);
  window.renderOperationsMock?.();
});

const integrationFields = {
  "public-base-url": ["publicBaseUrl", "gmo-mock-public-base-url"],
  "browser-return-base-url": ["browserReturnBaseUrl", "gmo-mock-browser-return-base-url"],
  "openapi-webhook-url": ["openApiWebhookUrl", "gmo-mock-openapi-webhook-url"],
  "protocol-notification-url": ["protocolNotificationUrl", "gmo-mock-protocol-notification-url"],
};

Object.entries(integrationFields).forEach(([id, [stateKey, storageKey]]) => {
  document.querySelector(`#${id}`).addEventListener("input", (event) => {
    state[stateKey] = event.target.value;
    if (event.target.value) window.localStorage.setItem(storageKey, event.target.value);
    else window.localStorage.removeItem(storageKey);
    renderIntegrationConfig();
    window.renderOperationsMock?.();
  });
});

window.checkoutPrototype = {
  get workspaceView() { return state.workspaceView; },
  setWorkspaceView(view) {
    state.workspaceView = view;
    renderWorkspaceView();
    window.scrollTo({ top: 0, behavior: "smooth" });
  },
  integrationConfig: resolvedIntegrationConfig,
  kozaConfig() {
    return {
      batchEnabled: state.kozaFurikae,
      notificationsEnabled: state.kozaNotifications,
    };
  },
  setWebhooksEnabled(enabled) {
    state.webhooks = Boolean(enabled);
    setSwitch(document.querySelector("#webhook-toggle"), state.webhooks);
    renderIntegrationConfig();
    window.renderOperationsMock?.();
  },
};

function validateInitialCard() {
  const number = document.querySelector("#initial-card-number").value.replace(/\D/g, "");
  const expiry = document.querySelector("#initial-card-expiry").value.trim();
  const cvc = document.querySelector("#initial-card-cvc").value.replace(/\D/g, "");
  const holder = document.querySelector("#initial-cardholder-name").value.trim();

  state.initialCardDraft = { number, expiry, cvc, holder };
  if (number.length < 13 || !expiry || cvc.length < 3 || !holder) {
    state.initialCardError = t("cardValidation");
    render();
    return false;
  }

  state.initialCardError = "";
  return true;
}

function validateInitialBank() {
  const accountNumber = document.querySelector("#initial-bank-account-number").value.replace(/\D/g, "");
  const accountHolder = document.querySelector("#initial-bank-account-holder").value.trim();
  state.bankDirectDraft.accountNumber = accountNumber;
  state.bankDirectDraft.accountHolder = accountHolder;

  if (accountNumber.length < 4 || !accountHolder) {
    state.initialBankError = t("bankValidation");
    render();
    return false;
  }

  state.initialBankError = "";
  return true;
}

function validateInitialKoza() {
  const bank = document.querySelector("#initial-koza-bank").value;
  const branch = document.querySelector("#initial-koza-branch").value.trim();
  const accountType = document.querySelector("#initial-koza-account-type").value;
  const accountNumber = document.querySelector("#initial-koza-account-number").value.replace(/\D/g, "");
  const accountHolder = document.querySelector("#initial-koza-account-holder").value.trim();
  state.kozaAccountDraft = { bank, branch, accountType, accountNumber, accountHolder };

  if (!branch || accountNumber.length < 4 || !accountHolder) {
    state.initialKozaError = t("kozaValidation");
    render();
    return false;
  }

  state.initialKozaError = "";
  return true;
}

function paymentReference(method) {
  const prefix = { card: "CARD", paypay: "PP", bankDirect: "DD", kozaFurikae: "KZA" }[method] || "PAY";
  return `GMO-${prefix}-${String(Date.now()).slice(-8)}`;
}

/**
 * Builds the mock result only after GMO's outcome is known. The Koza path has
 * two linked provider resources: the reusable registration and the one-time
 * virtual account used for the first premium. Neither raw bank credentials nor
 * a false "paid" state is carried into the confirmation receipt.
 */
function createPaymentResult(method) {
  const suffix = String(Date.now()).slice(-8);
  if (method === "kozaFurikae") {
    return {
      kind: "koza-registration-furikomi",
      method,
      amount: state.amount,
      reference: `GMO-KZA-${suffix}`,
      registrationReference: `KZA-${suffix}`,
      furikomi: {
        bank: "GMO Aozora Net Bank",
        branch: "Insurance Premiums / 503",
        accountType: "Ordinary",
        accountNumber: "3352017",
        accountName: "GMO INSURANCE COLLECTIONS",
        transferReference: `APP${suffix}`,
        payBy: "10 Sep 2026, 15:00 JST",
      },
    };
  }

  return {
    method,
    amount: state.amount,
    reference: paymentReference(method),
  };
}

document.querySelector("#first-continue").addEventListener("click", async () => {
  if (!state.selectedMethod) return;
  if (state.selectedMethod === "card" && !validateInitialCard()) return;
  if (state.selectedMethod === "bankDirect" && !validateInitialBank()) return;
  if (state.selectedMethod === "kozaFurikae" && !validateInitialKoza()) return;

  state.paymentError = "";
  state.processingPayment = true;
  render();

  // The real checkout replaces this short pause with the GMO authorization or
  // bank-registration call. Any provider failure is written to paymentError and
  // rendered on this first screen instead of advancing.
  await new Promise((resolve) => window.setTimeout(resolve, 650));
  state.processingPayment = false;

  if (state.checkoutOutcome !== "success") {
    if (state.checkoutOutcome === "unknown") {
      state.paymentPending = true;
      state.paymentError = "";
    } else if (state.checkoutOutcome === "cancelled") {
      state.paymentError = state.selectedMethod === "kozaFurikae"
        ? "The bank registration was cancelled. No transfer account was issued. You can try again or choose another payment method."
        : "The provider approval was cancelled. No payment was taken. You can try again or choose another payment method.";
    } else if (state.checkoutOutcome === "debit-failed" && state.selectedMethod === "bankDirect") {
      state.paymentError = "Your bank account was registered, but the first debit could not be completed. Try again or choose another payment method.";
    } else if (state.selectedMethod === "kozaFurikae") {
      state.paymentError = "The monthly bank-debit registration could not be confirmed. No transfer account was issued. Check the details or choose another payment method.";
    } else {
      state.paymentError = "The payment was declined. Check the details or choose another payment method. No payment was taken.";
    }
    render();
    return;
  }

  state.paymentResult = createPaymentResult(state.selectedMethod);
  setScreen(needsFutureSetup() ? "future" : "review");
});

document.querySelector("#check-payment-status").addEventListener("click", async () => {
  if (!state.paymentPending || state.processingPayment) return;
  state.processingPayment = true;
  render();
  await new Promise((resolve) => window.setTimeout(resolve, 550));
  state.processingPayment = false;
  state.paymentPending = false;
  state.paymentResult = createPaymentResult(state.selectedMethod);
  setScreen(needsFutureSetup() ? "future" : "review");
});

document.querySelector("#future-back").addEventListener("click", () => setScreen("first"));
document.querySelector("#future-continue").addEventListener("click", () => {
  if (state.recurringMethod) setScreen("review");
});
document.querySelector("#confirmation-done").addEventListener("click", () => {
  state.selectedMethod = null;
  state.paymentResult = null;
  state.paymentError = "";
  setScreen("first");
});

document.querySelector("#accent-color").addEventListener("input", updateTheme);
document.querySelector("#canvas-color").addEventListener("input", updateTheme);
document.querySelector("#font-family").addEventListener("change", updateTheme);
document.querySelector("#base-size").addEventListener("input", updateTheme);
document.querySelector("#heading-scale").addEventListener("input", updateTheme);
document.querySelector("#reset-theme").addEventListener("click", resetTheme);

document.querySelector("#reset-order").addEventListener("click", () => {
  state.order = [...defaultOrder];
  state.enabledMethods = { ...defaultEnabledMethods };
  state.screen = "first";
  render();
});

document.querySelector("#configuration-panel").addEventListener("input", (event) => {
  if (event.target.matches("input, select")) markConfigDirty();
});

document.querySelector("#configuration-panel").addEventListener("change", (event) => {
  if (event.target.matches("input, select")) markConfigDirty();
});

document.querySelector("#configuration-panel").addEventListener("click", (event) => {
  if (event.target.closest("#publish-config, #discard-config")) return;
  if (event.target.closest("button")) window.setTimeout(markConfigDirty, 0);
});

document.querySelector("#publish-config").addEventListener("click", () => {
  state.configRelease.version += 1;
  state.configRelease.dirty = false;
  state.configRelease.publishedAt = new Intl.DateTimeFormat("en-GB", {
    day: "2-digit", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit",
  }).format(new Date());
  state.configRelease.publishedBy = "Configuration administrator";
  publishedConfig = captureConfig();
  renderConfigRelease();
});

document.querySelector("#discard-config").addEventListener("click", restorePublishedConfig);

applyTheme(defaultTheme);
publishedConfig = captureConfig();
render();
