package io.github.patelsa032766.gmopayments.gmo;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Environment-backed GMO connection settings.
 *
 * <p>Credentials and deployment URLs are intentionally outside SQLite's
 * publishable business configuration. A configuration export can therefore be
 * shared without accidentally exporting a Shop password or a developer's
 * Cloudflare hostname.</p>
 */
@ConfigurationProperties(prefix = "gmo")
public class GmoProperties {
    private boolean liveCallsEnabled;
    private String openapiBaseUrl = "https://stg.openapi.mul-pay.jp";
    private String protocolBaseUrl = "https://pt01.mul-pay.jp/payment";
    private String mpTokenJsUrl = "https://stg.static.mul-pay.jp/payment/js/mp-token.js";
    private String openapiVersion = "";
    private String shopId = "";
    private String shopPass = "";
    private String siteId = "";
    private String sitePass = "";
    private String publicBaseUrl = "https://payments.example.com";
    private String browserReturnBaseUrl = "";
    private String openapiWebhookUrl = "";
    private String protocolNotificationUrl = "";
    private boolean webhooksEnabled;
    private String webhookIngressToken = "";
    private String webhookCsrfSecret = "";
    private final Retry retry = new Retry();
    private final Merchant merchant = new Merchant();

    public boolean isLiveCallsEnabled() { return liveCallsEnabled; }
    public void setLiveCallsEnabled(boolean value) { this.liveCallsEnabled = value; }
    public String getOpenapiBaseUrl() { return openapiBaseUrl; }
    public void setOpenapiBaseUrl(String value) { this.openapiBaseUrl = value; }
    public String getProtocolBaseUrl() { return protocolBaseUrl; }
    public void setProtocolBaseUrl(String value) { this.protocolBaseUrl = value; }
    public String getMpTokenJsUrl() { return mpTokenJsUrl; }
    public void setMpTokenJsUrl(String value) { this.mpTokenJsUrl = value; }
    public String getOpenapiVersion() { return openapiVersion; }
    public void setOpenapiVersion(String value) { this.openapiVersion = value; }
    public String getShopId() { return shopId; }
    public void setShopId(String value) { this.shopId = value; }
    public String getShopPass() { return shopPass; }
    public void setShopPass(String value) { this.shopPass = value; }
    public String getSiteId() { return siteId; }
    public void setSiteId(String value) { this.siteId = value; }
    public String getSitePass() { return sitePass; }
    public void setSitePass(String value) { this.sitePass = value; }
    public String getPublicBaseUrl() { return stripTrailingSlash(publicBaseUrl); }
    public void setPublicBaseUrl(String value) { this.publicBaseUrl = value; }
    public String getBrowserReturnBaseUrl() { return browserReturnBaseUrl; }
    public void setBrowserReturnBaseUrl(String value) { this.browserReturnBaseUrl = value; }
    public String getOpenapiWebhookUrl() { return openapiWebhookUrl; }
    public void setOpenapiWebhookUrl(String value) { this.openapiWebhookUrl = value; }
    public String getProtocolNotificationUrl() { return protocolNotificationUrl; }
    public void setProtocolNotificationUrl(String value) { this.protocolNotificationUrl = value; }
    public boolean isWebhooksEnabled() { return webhooksEnabled; }
    public void setWebhooksEnabled(boolean value) { this.webhooksEnabled = value; }
    public String getWebhookIngressToken() { return webhookIngressToken; }
    public void setWebhookIngressToken(String value) { this.webhookIngressToken = value; }
    public String getWebhookCsrfSecret() { return webhookCsrfSecret; }
    public void setWebhookCsrfSecret(String value) { this.webhookCsrfSecret = value; }
    public Retry getRetry() { return retry; }
    public Merchant getMerchant() { return merchant; }

    public String browserReturnBaseUrl() {
        return isBlank(browserReturnBaseUrl) ? getPublicBaseUrl() : stripTrailingSlash(browserReturnBaseUrl);
    }

    public String resolvedOpenapiWebhookUrl() {
        if (!webhooksEnabled) return null;
        return isBlank(openapiWebhookUrl)
                ? getPublicBaseUrl() + "/webhooks/gmo/openapi"
                : openapiWebhookUrl.trim();
    }

    public String resolvedProtocolNotificationUrl() {
        if (!webhooksEnabled) return null;
        return isBlank(protocolNotificationUrl)
                ? getPublicBaseUrl() + "/webhooks/gmo/protocol"
                : protocolNotificationUrl.trim();
    }

    /**
     * Secret used to derive a non-reversible, per-order CSRF value. A separate
     * deployment secret is preferred; falling back to the already-private shop
     * password keeps a local sandbox secure without another mandatory secret.
     */
    String resolvedWebhookCsrfSecret() {
        return isBlank(webhookCsrfSecret) ? shopPass : webhookCsrfSecret.trim();
    }

    public void requireOpenApiCredentials() {
        if (isBlank(shopId) || isBlank(shopPass)) {
            throw new IllegalStateException("GMO_SHOP_ID and GMO_SHOP_PASS are required when live calls are enabled");
        }
    }

    public void requireProtocolCredentials() {
        requireOpenApiCredentials();
        if (isBlank(siteId) || isBlank(sitePass)) {
            throw new IllegalStateException("GMO_SITE_ID and GMO_SITE_PASS are required for idPass operations");
        }
    }

    private static boolean isBlank(String value) { return value == null || value.isBlank(); }
    private static String stripTrailingSlash(String value) {
        if (value == null) return "";
        return value.trim().replaceAll("/+$", "");
    }

    /** Merchant presentation fields copied from the working Flask integration. */
    public static class Merchant {
        private String name = "Example Insurance";
        private String nameKana = "サンプルホケン";
        private String nameShort = "サンプル";
        private String nameAlphabet = "EXAMPLE INSURANCE";
        private String contactName = "Payment Support";
        private String contactEmail = "support@example.com";
        private String contactUrl = "https://example.com/support";
        private String contactPhone = "0312345678";
        private String contactOpeningHours = "10:00-18:00";

        public String getName() { return name; }
        public void setName(String value) { this.name = value; }
        public String getNameKana() { return nameKana; }
        public void setNameKana(String value) { this.nameKana = value; }
        public String getNameShort() { return nameShort; }
        public void setNameShort(String value) { this.nameShort = value; }
        public String getNameAlphabet() { return nameAlphabet; }
        public void setNameAlphabet(String value) { this.nameAlphabet = value; }
        public String getContactName() { return contactName; }
        public void setContactName(String value) { this.contactName = value; }
        public String getContactEmail() { return contactEmail; }
        public void setContactEmail(String value) { this.contactEmail = value; }
        public String getContactUrl() { return contactUrl; }
        public void setContactUrl(String value) { this.contactUrl = value; }
        public String getContactPhone() { return contactPhone; }
        public void setContactPhone(String value) { this.contactPhone = value; }
        public String getContactOpeningHours() { return contactOpeningHours; }
        public void setContactOpeningHours(String value) { this.contactOpeningHours = value; }
    }

    /**
     * Retry policy for read-only GMO inquiries only. Financial requests are
     * intentionally excluded because a timeout can hide a successful charge.
     */
    public static class Retry {
        private int safeReadMaxAttempts = 3;
        private long initialDelayMs = 200;
        private long maxDelayMs = 2_000;

        public int getSafeReadMaxAttempts() { return safeReadMaxAttempts; }
        public void setSafeReadMaxAttempts(int value) { this.safeReadMaxAttempts = value; }
        public long getInitialDelayMs() { return initialDelayMs; }
        public void setInitialDelayMs(long value) { this.initialDelayMs = value; }
        public long getMaxDelayMs() { return maxDelayMs; }
        public void setMaxDelayMs(long value) { this.maxDelayMs = value; }
    }
}
