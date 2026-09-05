package io.github.patelsa032766.gmopayments.web;

import io.github.patelsa032766.gmopayments.application.port.OperatorActionAuthorizer;
import io.github.patelsa032766.gmopayments.application.service.CheckoutExperienceService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Applies the local-demo operator security policy consistently to every
 * operator mutation.
 *
 * <p>The boolean lives in SQLite because it is a demo convenience setting; the
 * credential itself remains environment-backed and is never persisted. A
 * production deployment must keep protection enabled and replace the shared
 * token with real identity and role-based authorization.</p>
 */
@Component
public final class OperatorActionGuard {
    private final CheckoutExperienceService experience;
    private final OperatorActionAuthorizer authorizer;

    public OperatorActionGuard(CheckoutExperienceService experience,
                               OperatorActionAuthorizer authorizer) {
        this.experience = experience;
        this.authorizer = authorizer;
    }

    public void requireAuthorized(String presentedToken) {
        if (!experience.get().operatorTokenRequired()) return;
        if (!authorizer.authorized(presentedToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "A valid operator credential is required");
        }
    }
}
