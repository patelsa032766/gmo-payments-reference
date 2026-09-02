package io.github.patelsa032766.gmopayments.web;

import io.github.patelsa032766.gmopayments.application.port.OperatorActionAuthorizer;
import io.github.patelsa032766.gmopayments.application.service.ReconciliationImportService;
import io.github.patelsa032766.gmopayments.domain.ReconciliationPollReport;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Protected operator trigger for the same idempotent poll used by the scheduler. */
@RestController
@RequestMapping("/api/v1/reconciliation/sftp")
public final class ReconciliationController {
    private final ReconciliationImportService service;
    private final OperatorActionAuthorizer authorizer;

    public ReconciliationController(ReconciliationImportService service,
                                    OperatorActionAuthorizer authorizer) {
        this.service = service;
        this.authorizer = authorizer;
    }

    @PostMapping("/import")
    ReconciliationPollReport importReadyFiles(
            @RequestHeader(name = "X-Operator-Token", required = false) String token,
            @RequestHeader(name = "X-Operator-Id", defaultValue = "payment-operator") String actor) {
        if (!authorizer.authorized(token)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return service.poll(actor);
    }
}
