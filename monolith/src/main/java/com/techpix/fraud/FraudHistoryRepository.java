package com.techpix.fraud;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Fraud lê a tabela de pagamentos diretamente. No monólito isso é natural: é a mesma base,
 * o mesmo schema, a mesma transação. Guarde essa frase, ela vai voltar.
 */
@Repository
public class FraudHistoryRepository {

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
}
