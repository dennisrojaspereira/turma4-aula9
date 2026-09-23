package com.techpix.fraudservice.acl;

import com.techpix.fraudservice.config.FraudServiceProperties;
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
 * Anti-Corruption Layer: lê o schema do monólito e traduz para o vocabulário do domínio.
 * <p>
 * <b>Por que existe:</b> o Fraud Service ainda usa o banco do monólito. As tabelas {@code payments} e
 * {@code accounts} pertencem a Payment e Account. Seus nomes de coluna, seus status ({@code 'REJECTED'} como
 * string), seus tipos são decisões de outro time. Se as regras de risco conhecessem essas colunas, uma
 * migration em Payment quebraria o domínio de Fraud.
 * <p>
 * <b>O que faz:</b> concentra em UM lugar todo o conhecimento sobre o schema legado. As regras só veem
 * {@link PaymentHistory.PayerSnapshot} e {@link PaymentHistory.PayeeSnapshot}.
 * <p>
 * <b>O que não resolve:</b> o acoplamento continua existindo, só está isolado. Uma migration em Payment
 * ainda pode quebrar esta classe. A diferença é que quebra aqui, em um arquivo com "Legacy" no nome,
 * e não espalhado pelas regras. Ver lab 18.
 */
@Component
@ConditionalOnProperty(name = "fraud.history-source", havingValue = "LEGACY_SCHEMA", matchIfMissing = true)
public class LegacySchemaPaymentHistory implements PaymentHistory, AccountFacts {

    private final JdbcClient jdbc;

    public LegacySchemaPaymentHistory(JdbcClient jdbc, FraudServiceProperties props) {
        this.jdbc = jdbc;
    }

    @Override
    public PayerSnapshot payerSnapshot(UUID payerAccountId, Instant at, UUID excludingPaymentId) {
        return jdbc.sql("""
                SELECT
                    count(*) FILTER (WHERE created_at >= :minute)                      AS last_minute,
                    count(*) FILTER (WHERE created_at >= :hour)                        AS last_hour,
                    count(*) FILTER (WHERE created_at >= :day)                         AS last_day,
                    coalesce(sum(amount) FILTER (WHERE created_at >= :week), 0)        AS last_week_total,
                    count(*)                                                           AS total,
                    coalesce(avg(amount), 0)                                           AS average_amount,
                    count(*) FILTER (WHERE extract(hour FROM created_at AT TIME ZONE 'UTC') = :hourOfDay) AS same_hour,
                    count(*) FILTER (WHERE status = 'REJECTED' AND created_at >= :day) AS rejected_last_day
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
                    count(*) FILTER (WHERE status = 'REJECTED' AND created_at >= :month)                 AS rejected_month,
                    count(DISTINCT payer_account_id) FILTER (WHERE created_at >= :hour AND id <> :self)   AS payers_hour
                FROM payments
                WHERE payee_account_id = :payee AND created_at >= :month
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
        return jdbc.sql("SELECT count(*) FROM payments WHERE payer_account_id = :payer AND payee_account_id = :payee AND id <> :self")
                .param("payer", payerAccountId).param("payee", payeeAccountId).param("self", excludingPaymentId)
                .query(Long.class).single();
    }

    @Override
    public long distinctPayersByDeviceSince(String deviceId, Instant since, UUID excludingPaymentId) {
        return jdbc.sql("SELECT count(DISTINCT payer_account_id) FROM payments WHERE device_id = :device AND created_at >= :since AND id <> :self")
                .param("device", deviceId).param("since", Timestamp.from(since)).param("self", excludingPaymentId)
                .query(Long.class).single();
    }

    @Override
    public Optional<Instant> accountOpenedAt(UUID accountId) {
        return jdbc.sql("SELECT created_at FROM accounts WHERE id = :id")
                .param("id", accountId)
                .query(Timestamp.class)
                .optional()
                .map(Timestamp::toInstant);
    }
}
