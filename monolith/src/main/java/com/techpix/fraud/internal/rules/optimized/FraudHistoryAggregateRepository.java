package com.techpix.fraud.internal.rules.optimized;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Consultas agregadas: o banco conta, soma e calcula médias. A aplicação recebe uma linha.
 * <p>
 * Com o índice (payer_account_id, created_at), cada consulta toca apenas as linhas do pagador.
 */
@Repository
public class FraudHistoryAggregateRepository {

    private final JdbcClient jdbc;

    public FraudHistoryAggregateRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public PayerHistorySnapshot payerSnapshot(UUID payerAccountId, Instant at, UUID excludingPaymentId) {
        return jdbc.sql("""
                SELECT
                    count(*) FILTER (WHERE created_at >= :minute)                          AS last_minute,
                    count(*) FILTER (WHERE created_at >= :hour)                            AS last_hour,
                    count(*) FILTER (WHERE created_at >= :day)                             AS last_day,
                    coalesce(sum(amount) FILTER (WHERE created_at >= :week), 0)            AS last_week_total,
                    count(*)                                                               AS total,
                    coalesce(avg(amount), 0)                                               AS average_amount,
                    count(*) FILTER (WHERE extract(hour FROM created_at AT TIME ZONE 'UTC') = :hourOfDay) AS same_hour,
                    count(*) FILTER (WHERE status = 'REJECTED' AND created_at >= :day)     AS rejected_last_day
                FROM payments
                WHERE payer_account_id = :payer AND id <> :self
                """)
                .param("payer", payerAccountId)
                .param("self", excludingPaymentId)
                .param("minute", Timestamp.from(at.minus(Duration.ofMinutes(1))))
                .param("hour", Timestamp.from(at.minus(Duration.ofHours(1))))
                .param("day", Timestamp.from(at.minus(Duration.ofHours(24))))
                .param("week", Timestamp.from(at.minus(Duration.ofDays(7))))
                .param("hourOfDay", at.atZone(ZoneOffset.UTC).getHour())
                .query((rs, i) -> new PayerHistorySnapshot(
                        rs.getLong("last_minute"),
                        rs.getLong("last_hour"),
                        rs.getLong("last_day"),
                        rs.getBigDecimal("last_week_total"),
                        rs.getLong("total"),
                        rs.getBigDecimal("average_amount"),
                        rs.getLong("same_hour"),
                        rs.getLong("rejected_last_day")))
                .single();
    }

    public PayeeHistorySnapshot payeeSnapshot(UUID payeeAccountId, Instant at, UUID excludingPaymentId) {
        return jdbc.sql("""
                SELECT
                    count(*) FILTER (WHERE status = 'REJECTED' AND created_at >= :month)              AS rejected_month,
                    count(DISTINCT payer_account_id) FILTER (WHERE created_at >= :hour AND id <> :self) AS payers_hour
                FROM payments
                WHERE payee_account_id = :payee AND created_at >= :month
                """)
                .param("payee", payeeAccountId)
                .param("self", excludingPaymentId)
                .param("month", Timestamp.from(at.minus(Duration.ofDays(30))))
                .param("hour", Timestamp.from(at.minus(Duration.ofHours(1))))
                .query((rs, i) -> new PayeeHistorySnapshot(rs.getLong("rejected_month"), rs.getLong("payers_hour")))
                .single();
    }

    /** Toda a blacklist, para o cache em memória. Uma consulta a cada refresh, zero por pagamento. */
    public java.util.List<String> allBlacklistKeys() {
        return jdbc.sql("SELECT kind || ':' || value FROM fraud_blacklist").query(String.class).list();
    }

    static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
