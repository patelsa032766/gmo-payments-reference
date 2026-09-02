package io.github.patelsa032766.gmopayments.application.port;

/** Protects operator mutations that alter payment or configuration state. */
public interface OperatorActionAuthorizer {
    boolean authorized(String presentedToken);
}
