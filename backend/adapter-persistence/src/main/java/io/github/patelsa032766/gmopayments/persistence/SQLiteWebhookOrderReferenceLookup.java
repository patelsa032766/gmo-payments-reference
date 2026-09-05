package io.github.patelsa032766.gmopayments.persistence;

import io.github.patelsa032766.gmopayments.application.port.WebhookOrderReferenceLookup;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** SQLite lookup used only to authenticate an inbound OpenAPI webhook. */
@Repository
public class SQLiteWebhookOrderReferenceLookup implements WebhookOrderReferenceLookup {
    private final JdbcClient jdbc;

    public SQLiteWebhookOrderReferenceLookup(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<String> findProviderOrderId(String providerAccessId) {
        if (providerAccessId == null || providerAccessId.isBlank()) return Optional.empty();
        return jdbc.sql("""
                SELECT provider_order_id
                FROM payment_transaction
                WHERE provider_access_id=:accessId
                  AND provider_order_id IS NOT NULL
                  AND provider_order_id<>''
                ORDER BY id DESC
                LIMIT 1
                """).param("accessId", providerAccessId.trim())
                .query(String.class).optional();
    }
}
