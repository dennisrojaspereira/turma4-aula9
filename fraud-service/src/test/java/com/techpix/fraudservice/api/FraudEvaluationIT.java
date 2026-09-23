package com.techpix.fraudservice.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.techpix.fraudservice.acl.CachedBlacklist;
import com.techpix.fraudservice.support.AbstractFraudServiceIT;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** O serviço ponta a ponta: HTTP -> domínio -> ACL -> PostgreSQL com o schema legado. */
class FraudEvaluationIT extends AbstractFraudServiceIT {

    static final Instant NOW = Instant.parse("2026-03-01T15:00:00Z");

    @Autowired
    CachedBlacklist blacklist;

    @Test
    void evaluatesAgainstLegacyHistoryAndRecordsTheEvaluation() {
        UUID payer = legacyAccount(NOW.minus(Duration.ofDays(400)));
        UUID payee = legacyAccount(NOW.minus(Duration.ofDays(400)));
        for (int i = 0; i < 12; i++) {
            legacyPayment(payer, payee, "20.00", "phone-" + payer, "APPROVED", NOW.minus(Duration.ofMinutes(5 + i)));
        }

        ResponseEntity<Map> response = evaluate(payer, payee, "20.00", "phone-" + payer, NOW);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // 12 pagamentos na ultima hora: payer-history dispara velocity (+20). Nada mais.
        assertThat(response.getBody().get("score")).isEqualTo(20);
        assertThat(response.getBody().get("decision")).isEqualTo("APPROVED");
        assertThat(response.getBody().get("rulesEvaluated")).isEqualTo(13);
        Long stored = jdbc.sql("SELECT count(*) FROM fraud_evaluations WHERE payment_id = :id")
                .param("id", UUID.fromString((String) response.getBody().get("paymentId"))).query(Long.class).single();
        assertThat(stored).isEqualTo(1);
    }

    @Test
    void newAccountAndHighAmountAreRejected() {
        UUID payer = legacyAccount(NOW.minus(Duration.ofHours(2)));
        UUID payee = legacyAccount(NOW.minus(Duration.ofDays(400)));

        ResponseEntity<Map> response = evaluate(payer, payee, "5500.50", "phone-" + payer, NOW);

        // amount-limit (40) + new-account (20) + new-payee (25) = 85
        assertThat(response.getBody().get("score")).isEqualTo(85);
        assertThat(response.getBody().get("decision")).isEqualTo("REJECTED");
    }

    @Test
    void blacklistIsReadFromSharedTable() {
        jdbc.sql("INSERT INTO fraud_blacklist (id, kind, value, reason, created_at) VALUES (:id, 'DEVICE', 'stolen-phone', 't', :at)")
                .param("id", UUID.randomUUID()).param("at", Timestamp.from(NOW)).update();
        blacklist.refresh();
        UUID payer = legacyAccount(NOW.minus(Duration.ofDays(400)));
        UUID payee = legacyAccount(NOW.minus(Duration.ofDays(400)));

        ResponseEntity<Map> response = evaluate(payer, payee, "5.00", "stolen-phone", NOW);

        assertThat(response.getBody().get("decision")).isEqualTo("REJECTED");
        assertThat((Iterable<String>) response.getBody().get("triggeredRules")).contains("blacklist");
    }

    @Test
    void invalidRequestIsRejectedWith400() {
        ResponseEntity<Map> response = http.postForEntity("/fraud/evaluations", Map.of("amount", 10), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
