package io.github.patelsa032766.gmopayments.persistence;

import io.github.patelsa032766.gmopayments.application.port.CheckoutConfigurationRepository;
import io.github.patelsa032766.gmopayments.domain.ConfigurationRelease;
import io.github.patelsa032766.gmopayments.domain.DistributionChannel;
import io.github.patelsa032766.gmopayments.domain.PaymentMethodCode;
import io.github.patelsa032766.gmopayments.domain.PaymentMethodConfiguration;
import io.github.patelsa032766.gmopayments.domain.PaymentExecutionMode;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Reads exactly one published release and its ordered method rows.
 *
 * <p>The release id is read first and used for every method query so a future
 * draft/publish operation cannot mix method rows from two releases inside one
 * checkout decision.</p>
 */
@Repository
public class SQLiteCheckoutConfigurationRepository implements CheckoutConfigurationRepository {
    private final JdbcClient jdbc;

    public SQLiteCheckoutConfigurationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ConfigurationRelease findActiveRelease() {
        return findRelease("PUBLISHED").orElseThrow(() -> new IllegalStateException("No published checkout configuration exists"));
    }

    public Optional<ConfigurationRelease> findDraftRelease() { return findRelease("DRAFT"); }

    private Optional<ConfigurationRelease> findRelease(String status) {
        var release = jdbc.sql("""
                        SELECT id, version, published_at, published_by
                        FROM configuration_release
                        WHERE status = :status
                        ORDER BY version DESC
                        LIMIT 1
                        """).param("status", status)
                .query((rs, rowNum) -> new ReleaseRow(
                        rs.getLong("id"),
                        rs.getInt("version"),
                        rs.getString("published_at") == null ? null : Instant.parse(rs.getString("published_at")),
                        rs.getString("published_by")))
                .optional();
        if (release.isEmpty()) return Optional.empty();

        var methods = jdbc.sql("""
                        SELECT code, label_en, description_en, label_ja, description_ja,
                               enabled, recurring, monthly_only, min_amount_jpy,
                               max_amount_jpy, non_ekyc_max_amount_jpy, channels, display_order,
                               cit_execution_mode
                        FROM payment_method_configuration
                        WHERE release_id = :releaseId
                        ORDER BY display_order
                        """)
                .param("releaseId", release.get().id())
                .query((rs, rowNum) -> new PaymentMethodConfiguration(
                        PaymentMethodCode.fromApiValue(rs.getString("code")),
                        rs.getString("label_en"),
                        rs.getString("description_en"),
                        rs.getString("label_ja"),
                        rs.getString("description_ja"),
                        rs.getBoolean("enabled"),
                        rs.getBoolean("recurring"),
                        rs.getBoolean("monthly_only"),
                        rs.getLong("min_amount_jpy"),
                        rs.getLong("max_amount_jpy"),
                        nullableLong(rs, "non_ekyc_max_amount_jpy"),
                        parseChannels(rs.getString("channels")),
                        rs.getInt("display_order"),
                        PaymentExecutionMode.from(rs.getString("cit_execution_mode"))))
                .list();

        var row=release.get();
        return Optional.of(new ConfigurationRelease(row.id(), row.version(), row.publishedAt(), row.publishedBy(), methods));
    }

    private static Long nullableLong(java.sql.ResultSet resultSet, String column) throws java.sql.SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private static Set<DistributionChannel> parseChannels(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .map(DistributionChannel::valueOf)
                .collect(Collectors.toUnmodifiableSet());
    }

    private record ReleaseRow(long id, int version, Instant publishedAt, String publishedBy) {}
}
