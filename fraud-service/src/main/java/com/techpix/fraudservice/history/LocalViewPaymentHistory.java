package com.techpix.fraudservice.history;

import com.techpix.fraudservice.domain.AccountFacts;
import com.techpix.fraudservice.domain.PaymentHistory;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * O histórico de pagamentos como Fraud o enxerga, lido da visão local {@code payment_history}.
 * <p>
 * Comparar com {@code acl.LegacySchemaPaymentHistory}: mesmas consultas, outra tabela, outro dono.
 * Esta tabela é nossa. Ninguém fora do Fraud Service a altera. Uma migration de Payment não a afeta.
 * <p>
 * O preço: a tabela só sabe o que alguém contou para ela. Até o lab 19, ninguém conta nada.
 * Um Fraud Service com visão local vazia é um Fraud Service cego para velocity.
 */
@Component
@ConditionalOnProperty(name = "fraud.history-source", havingValue = "LOCAL_VIEW")
public class LocalViewPaymentHistory implements PaymentHistory, AccountFacts {

    private final JdbcClient jdbc;

    public LocalViewPaymentHistory(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PayerSnapshot payerSnapshot(UUID payerAccountId, Instant at, UUID excludingPaymentId) {
        return jdbc.sql("""
                SELECT
                    count(*) FILTER (WHERE occurred_at >= :minute)                      AS last_minute,
                    count(*) FILTER (WHERE occurred_at >= :hour)                        AS last_hour,
                    count(*) FILTER (WHERE occurred_at >= :day)                         AS last_day,
                    coalesce(sum(amount) FILTER (WHERE occurred_at >= :week), 0)        AS last_week_total,
                    count(*)                                                            AS total,
                    coalesce(avg(amount), 0)                                            AS average_amount,
                    count(*) FILTER (WHERE extract(hour FROM occurred_at AT TIME ZONE 'UTC') = :hourOfDay) AS same_hour,
                    count(*) FILTER (WHERE status = 'REJECTED' AND occurred_at >= :day) AS rejected_last_day
                FROM payment_history
                WHERE payer_account_id = :payer AND payment_id <> :self AND status <> 'REJECTED_BY_LEDGER'
                """)
                .param("payer", payerAccountId)
                .param("self", excludingPaymentId)
                .param("minute", Timestamp.from(at.minus(Duration.ofMinutes(1))))
                .param("hour", Timestamp.from(at.minus(Duration.ofHours(1))))
                .param("day", Timestamp.from(at.minus(Duration.ofHours(24))))
                .param("week", Timestamp.from(at.minus(Duration.ofDays(7))))
                .param("hourOfDay", at.atZone(ZoneOffset.UTC).getHour())
                .query((rs, i) -> new PayerSnapshot(
                        rs.getLong("last_minute"), rs.getLong("last_hour"), rs.getLong("last_day"),
                        rs.getBigDecimal("last_week_total"), rs.getLong("total"), rs.getBigDecimal("average_amount"),
                        rs.getLong("same_hour"), rs.getLong("rejected_last_day")))
                .single();
    }

    @Override
    public PayeeSnapshot payeeSnapshot(UUID payeeAccountId, Instant at, UUID excludingPaymentId) {
        return jdbc.sql("""
                SELECT
                    count(*) FILTER (WHERE status = 'REJECTED' AND occurred_at >= :month)                        AS rejected_month,
                    count(DISTINCT payer_account_id) FILTER (WHERE occurred_at >= :hour AND payment_id <> :self) AS payers_hour
                FROM payment_history
                WHERE payee_account_id = :payee AND occurred_at >= :month
                """)
                .param("payee", payeeAccountId)
                .param("self", excludingPaymentId)
                .param("month", Timestamp.from(at.minus(Duration.ofDays(30))))
                .param("hour", Timestamp.from(at.minus(Duration.ofHours(1))))
                .query((rs, i) -> new PayeeSnapshot(rs.getLong("rejected_month"), rs.getLong("payers_hour")))
                .single();
    }

    @Override
    public long countBetween(UUID payerAccountId, UUID payeeAccountId, UUID excludingPaymentId) {
        return jdbc.sql("SELECT count(*) FROM payment_history WHERE payer_account_id = :payer AND payee_account_id = :payee AND payment_id <> :self")
                .param("payer", payerAccountId).param("payee", payeeAccountId).param("self", excludingPaymentId)
                .query(Long.class).single();
    }

    @Override
    public long distinctPayersByDeviceSince(String deviceId, Instant since, UUID excludingPaymentId) {
        return jdbc.sql("SELECT count(DISTINCT payer_account_id) FROM payment_history WHERE device_id = :device AND occurred_at >= :since AND payment_id <> :self")
                .param("device", deviceId).param("since", Timestamp.from(since)).param("self", excludingPaymentId)
                .query(Long.class).single();
    }

    /** Fraud não é dono de contas. O que sabe de uma conta é o que os eventos de pagamento contaram. */
    @Override
    public Optional<Instant> accountOpenedAt(UUID accountId) {
        return jdbc.sql("SELECT min(payer_opened_at) FROM payment_history WHERE payer_account_id = :id")
                .param("id", accountId)
                .query(Timestamp.class)
                .optional()
                .map(Timestamp::toInstant);
    }
}
