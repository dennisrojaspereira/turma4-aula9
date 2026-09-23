package com.techpix.fraud.internal;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Fraud lê a tabela de pagamentos diretamente. No monólito isso é natural: é a mesma base,
 * o mesmo schema, a mesma transação. Guarde essa frase, ela vai voltar.
 * <p>
 * Cada método aqui foi adicionado quando uma regra nova precisou dele. Nenhum foi revisado
 * depois. Alguns carregam a tabela inteira do pagador para somar em Java. Isso é comum
 * em código que cresceu regra por regra.
 */
@Repository
public class FraudHistoryRepository {

    /** Linha de pagamento como Fraud a enxerga. */
    public record PaymentRow(UUID id, UUID payerAccountId, UUID payeeAccountId, BigDecimal amount,
                             String deviceId, String status, Instant createdAt) {
    }

    private final JdbcClient jdbc;

    public FraudHistoryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public long countByPayerSince(UUID payerAccountId, Instant since, UUID excludingPaymentId) {
        return jdbc.sql("""
                SELECT count(*) FROM payments
                WHERE payer_account_id = :payer AND created_at >= :since AND id <> :self
                """)
                .param("payer", payerAccountId)
                .param("since", Timestamp.from(since))
                .param("self", excludingPaymentId)
                .query(Long.class)
                .single();
    }

    public long countBetween(UUID payerAccountId, UUID payeeAccountId, UUID excludingPaymentId) {
        return jdbc.sql("""
                SELECT count(*) FROM payments
                WHERE payer_account_id = :payer AND payee_account_id = :payee AND id <> :self
                """)
                .param("payer", payerAccountId)
                .param("payee", payeeAccountId)
                .param("self", excludingPaymentId)
                .query(Long.class)
                .single();
    }

    /** Carrega todas as linhas do pagador desde um instante. Usado por velocity: soma feita em Java. */
    public List<PaymentRow> findByPayerSince(UUID payerAccountId, Instant since, UUID excludingPaymentId) {
        return jdbc.sql("""
                SELECT * FROM payments
                WHERE payer_account_id = :payer AND created_at >= :since AND id <> :self
                """)
                .param("payer", payerAccountId)
                .param("since", Timestamp.from(since))
                .param("self", excludingPaymentId)
                .query(this::row)
                .list();
    }

    /** Todo o histórico do pagador. Sem limite. "Precisamos da média de todos os tickets". */
    public List<PaymentRow> findAllByPayer(UUID payerAccountId, UUID excludingPaymentId) {
        return jdbc.sql("SELECT * FROM payments WHERE payer_account_id = :payer AND id <> :self")
                .param("payer", payerAccountId)
                .param("self", excludingPaymentId)
                .query(this::row)
                .list();
    }

    public long countDistinctPayersByDeviceSince(String deviceId, Instant since, UUID excludingPaymentId) {
        return jdbc.sql("""
                SELECT count(DISTINCT payer_account_id) FROM payments
                WHERE device_id = :device AND created_at >= :since AND id <> :self
                """)
                .param("device", deviceId)
                .param("since", Timestamp.from(since))
                .param("self", excludingPaymentId)
                .query(Long.class)
                .single();
    }

    public long countRejectedToPayeeSince(UUID payeeAccountId, Instant since) {
        return jdbc.sql("""
                SELECT count(*) FROM payments
                WHERE payee_account_id = :payee AND status = 'REJECTED' AND created_at >= :since
                """)
                .param("payee", payeeAccountId)
                .param("since", Timestamp.from(since))
                .query(Long.class)
                .single();
    }

    public long countDistinctPayersToPayeeSince(UUID payeeAccountId, Instant since, UUID excludingPaymentId) {
        return jdbc.sql("""
                SELECT count(DISTINCT payer_account_id) FROM payments
                WHERE payee_account_id = :payee AND created_at >= :since AND id <> :self
                """)
                .param("payee", payeeAccountId)
                .param("since", Timestamp.from(since))
                .param("self", excludingPaymentId)
                .query(Long.class)
                .single();
    }

    /** Uma consulta por pagamento. Chamado dentro de um loop: o clássico N+1. */
    public String decisionOf(UUID paymentId) {
        return jdbc.sql("SELECT decision FROM fraud_evaluations WHERE payment_id = :id")
                .param("id", paymentId)
                .query(String.class)
                .optional()
                .orElse("UNKNOWN");
    }

    public Instant accountCreatedAt(UUID accountId) {
        return jdbc.sql("SELECT created_at FROM accounts WHERE id = :id")
                .param("id", accountId)
                .query(Timestamp.class)
                .single()
                .toInstant();
    }

    public boolean isBlacklisted(String kind, String value) {
        return jdbc.sql("SELECT count(*) FROM fraud_blacklist WHERE kind = :kind AND value = :value")
                .param("kind", kind)
                .param("value", value)
                .query(Long.class)
                .single() > 0;
    }

    private PaymentRow row(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new PaymentRow(
                rs.getObject("id", UUID.class),
                rs.getObject("payer_account_id", UUID.class),
                rs.getObject("payee_account_id", UUID.class),
                rs.getBigDecimal("amount"),
                rs.getString("device_id"),
                rs.getString("status"),
                rs.getTimestamp("created_at").toInstant());
    }
}
