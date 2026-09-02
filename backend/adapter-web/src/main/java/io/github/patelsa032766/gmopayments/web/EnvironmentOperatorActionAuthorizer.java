package io.github.patelsa032766.gmopayments.web;

import io.github.patelsa032766.gmopayments.application.port.OperatorActionAuthorizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Environment-backed, constant-time operator token check; blank means read-only. */
@Component
public final class EnvironmentOperatorActionAuthorizer implements OperatorActionAuthorizer {
    private final String expected;
    public EnvironmentOperatorActionAuthorizer(@Value("${operator.api-token:}") String expected) { this.expected=expected; }
    @Override public boolean authorized(String presentedToken) {
        if(expected==null || expected.isBlank() || presentedToken==null || presentedToken.isBlank()) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), presentedToken.getBytes(StandardCharsets.UTF_8));
    }
}
