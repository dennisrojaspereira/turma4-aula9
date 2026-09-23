package com.techpix.fraudservice.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.techpix.fraudservice.support.AbstractFraudStoreIT;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

/** Database per Service: o Fraud Service funciona sem nenhuma tabela do monólito. */
class FraudStoreIT extends AbstractFraudStoreIT {

    static final Instant NOW = Instant.parse("2026-03-01T15:00:00Z");

    @Test
    void schemaIsOwnedByFraudServiceAndHasNoPaymentTables() {
        List<String> tables = jdbc.sql("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY 1")
                .query(String.class).list();
        assertThat(tables).contains("fraud_evaluations", "fraud_blacklist", "payment_history", "processed_events", "flyway_schema_history");
        assertThat(tables).doesNotContain("payments", "accounts", "ledger_entries", "notifications");
    }

    @Test
    void theJoinIsGone() {
        // Antes: SELECT ... FROM payments WHERE payer_account_id = ... Agora essa tabela nao existe aqui.
        assertThatThrownBy(() -> jdbc.sql("SELECT count(*) FROM payments").query(Long.class).single())
                .hasMessageContaining("payments");
    }

    @Test
    void evaluatesWithAnEmptyLocalViewAndIsBlindToHistory() {
        UUID payer = UUID.randomUUID();
        UUID payee = UUID.randomUUID();

        ResponseEntity<Map> response = evaluate(payer, payee, "50.00", "phone", NOW);

        // Sem historico, nenhuma regra de velocity dispara. So new-account (+20): Fraud nunca viu esta conta.
        assertThat(response.getBody().get("decision")).isEqualTo("APPROVED");
        assertThat(response.getBody().get("triggeredRules")).isEqualTo(List.of("new-account"));
        assertThat(response.getBody().get("rulesEvaluated")).isEqualTo(13);
    }

    @Test
    void localViewFeedsTheRulesOnceSomeoneFillsIt() {
        UUID payer = UUID.randomUUID();
        UUID payee = UUID.randomUUID();
        for (int i = 0; i < 12; i++) {
            jdbc.sql("""
                    INSERT INTO payment_history (payment_id, payer_account_id, payee_account_id, amount, device_id, status, payer_opened_at, occurred_at, updated_at)
                    VALUES (:id, :payer, :payee, 20.00, 'phone', 'APPROVED', :opened, :at, :at)
                    """)
                    .param("id", UUID.randomUUID()).param("payer", payer).param("payee", payee)
                    .param("opened", java.sql.Timestamp.from(NOW.minus(Duration.ofDays(400))))
                    .param("at", java.sql.Timestamp.from(NOW.minus(Duration.ofMinutes(5 + i)))).update();
        }

        ResponseEntity<Map> response = evaluate(payer, payee, "20.00", "phone", NOW);

        // Mesma decisao que o teste equivalente sobre o schema legado (FraudEvaluationIT): velocity +20.
        assertThat(response.getBody().get("score")).isEqualTo(20);
        assertThat(response.getBody().get("triggeredRules")).isEqualTo(List.of("payer-history"));
    }
}
