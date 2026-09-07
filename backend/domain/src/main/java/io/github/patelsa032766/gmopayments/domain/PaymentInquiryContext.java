package io.github.patelsa032766.gmopayments.domain;

/**
 * Provider identifiers and immutable transaction facts needed for a safe,
 * read-only status inquiry. No provider password is carried in this object.
 */
public record PaymentInquiryContext(
        PaymentExecutionContext execution,
        String providerOrderId,
        String providerAccessId) {
}
