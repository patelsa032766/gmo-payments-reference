package io.github.patelsa032766.gmopayments.gmo;

/** Provider-field conversion for the customer-facing bank-account Kana name. */
final class GmoBankAccountName {
    private GmoBankAccountName() {}

    /**
     * Converts the checkout's natural {@code given-name family-name} value to
     * GMO's separate family/last and given/first fields.
     *
     * <p>This intentionally mirrors the working Flask implementation. Both an
     * ASCII space and a Japanese full-width space are accepted. A single name
     * is repeated because GMO requires both fields and the sandbox accepts that
     * fallback; the Angular form normally supplies the complete name.</p>
     */
    static Parts split(String accountName) {
        String cleaned = accountName == null ? "" : accountName
                .replace('　', ' ').trim().replaceAll("\\s+", " ");
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("Account holder name (Kana) is required");
        }
        String[] names = cleaned.split(" ", 2);
        if (names.length == 1) return new Parts(names[0], names[0]);
        return new Parts(names[1], names[0]);
    }

    record Parts(String lastName, String firstName) {}
}
