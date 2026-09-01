package io.github.patelsa032766.gmopayments.domain;

/**
 * Distribution channel supplied by the insurance application.
 *
 * <p>The short codes are intentionally modeled as a closed domain type rather
 * than free-form strings. Adding a channel therefore requires an explicit code
 * and eligibility review instead of silently widening payment availability.</p>
 */
public enum DistributionChannel {
    PA,
    IA,
    FI
}
