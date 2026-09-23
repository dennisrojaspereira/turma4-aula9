package com.techpix.fraud.internal.strangler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.techpix.fraud.FraudMode;
import com.techpix.fraud.internal.strangler.ShadowComparator.Outcome;
import com.techpix.support.AbstractIntegrationTest;
import com.techpix.support.FakeFraudService;
import com.techpix.support.FakeFraudService.Behaviour;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Parallel Run: o legado decide, o novo roda em shadow, o comparador conta.
 * As divergências aqui são intencionais: o Fraud Service falso responde o que o teste mandar.
 */
class ParallelRunIT extends AbstractIntegrationTest {

    static final FakeFraudService FRAUD_SERVICE = new FakeFraudService();

    @DynamicPropertySource
    static void remote(DynamicPropertyRegistry registry) {
        registry.add("techpix.fraud.remote.url", FRAUD_SERVICE::url);
        registry.add("techpix.fraud.remote.read-timeout-ms", () -> 3000);
        registry.add("techpix.fraud.parallel.shadow-timeout-ms", () -> 400);
    }

    @Autowired
    FraudModeConfig mode;
    @Autowired
    ShadowComparator comparator;

    @BeforeEach
    void setUp() {
        FRAUD_SERVICE.reset();
        comparator.reset();
        mode.change(FraudMode.PARALLEL);
    }

    @AfterEach
    void rollback() {
        mode.change(FraudMode.LEGACY);
    }

    @Test
    void legacyDecidesAndShadowIsComparedAsMatch() {
        // Legado (SIMPLE): pagamento comum -> score 0 APPROVED. O falso responde igual.
        FRAUD_SERVICE.respond(Behaviour.ok(0, "APPROVED"));
        UUID payer = openAccount("PR Match", "1000.00");
        UUID payee = openAccount("PR Match Payee", "0.00");

        ResponseEntity<Map> response = pay(payer, payee, "10.00", "d");

        assertThat(response.getBody().get("status")).isEqualTo("APPROVED");
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(comparator.count(Outcome.MATCH)).isEqualTo(1));
        assertThat(FRAUD_SERVICE.calls()).isEqualTo(1);
    }

    @Test
    void decisionMismatchIsRecordedButLegacyStillWins() {
        // Divergencia intencional: o novo rejeita, o legado aprova. O cliente ve a decisao do legado.
        FRAUD_SERVICE.respond(Behaviour.ok(95, "REJECTED"));
        UUID payer = openAccount("PR Mismatch", "1000.00");
        UUID payee = openAccount("PR Mismatch Payee", "0.00");

        ResponseEntity<Map> response = pay(payer, payee, "10.00", "d");

        assertThat(response.getBody().get("status")).isEqualTo("APPROVED");
        assertThat(response.getBody().get("fraudScore")).isEqualTo(0);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(comparator.count(Outcome.DECISION_MISMATCH)).isEqualTo(1));

        Map summary = http.getForObject("/admin/fraud/parallel-run", Map.class);
        assertThat(summary.get("total")).isEqualTo(1);
        assertThat(((java.util.List<?>) summary.get("lastDivergences"))).hasSize(1);
        Map divergence = (Map) ((java.util.List<?>) summary.get("lastDivergences")).get(0);
        assertThat(divergence.get("legacyScore")).isEqualTo(0);
        assertThat(divergence.get("newScore")).isEqualTo(95);
    }

    @Test
    void scoreMismatchWithSameDecisionIsAMilderDivergence() {
        FRAUD_SERVICE.respond(Behaviour.ok(20, "APPROVED"));
        UUID payer = openAccount("PR Score", "1000.00");
        UUID payee = openAccount("PR Score Payee", "0.00");

        pay(payer, payee, "10.00", "d");

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(comparator.count(Outcome.SCORE_MISMATCH)).isEqualTo(1));
    }

    @Test
    void shadowErrorNeverAffectsThePayment() {
        FRAUD_SERVICE.respond(Behaviour.error(500));
        UUID payer = openAccount("PR Error", "1000.00");
        UUID payee = openAccount("PR Error Payee", "0.00");

        ResponseEntity<Map> response = pay(payer, payee, "10.00", "d");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("status")).isEqualTo("APPROVED");
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(comparator.count(Outcome.ERROR)).isEqualTo(1));
    }

    @Test
    void slowShadowDoesNotSlowThePaymentAndIsRecordedAsTimeout() {
        FRAUD_SERVICE.respond(Behaviour.ok(0, "APPROVED").withDelay(1_500));
        UUID payer = openAccount("PR Slow", "1000.00");
        UUID payee = openAccount("PR Slow Payee", "0.00");

        long start = System.currentTimeMillis();
        ResponseEntity<Map> response = pay(payer, payee, "10.00", "d");
        long elapsed = System.currentTimeMillis() - start;

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(elapsed).isLessThan(1_000);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(comparator.count(Outcome.TIMEOUT)).isEqualTo(1));
    }
}
