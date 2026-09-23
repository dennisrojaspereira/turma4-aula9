package com.techpix.fraudservice.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.techpix.fraudservice.support.AbstractFraudEventsIT;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

/**
 * O JOIN voltou, de outra forma: Payment conta, Fraud escuta, a visão local cresce.
 * Consistência eventual: o teste precisa ESPERAR. Antes, um SELECT via o dado no mesmo instante.
 */
class PaymentEventsConsumerIT extends AbstractFraudEventsIT {

    static final Instant NOW = Instant.now();

    @Test
    void approvedFactLandsInTheLocalView() {
        UUID payer = UUID.randomUUID();
        UUID payee = quietPayee();
        UUID paymentId = UUID.randomUUID();

        publish(approved(UUID.randomUUID(), paymentId, payer, payee, "42.00", NOW));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(historyCount(payer, "APPROVED")).isEqualTo(1));
        Map activity = http.getForObject("/admin/fraud/accounts/" + payer + "/activity", Map.class);
        assertThat(activity.get("approvedCount")).isEqualTo(1);
    }

    @Test
    void velocityBecomesVisibleOnlyAfterTheEventsArrive() {
        UUID payer = UUID.randomUUID();
        UUID payee = quietPayee();

        // Antes dos eventos: Fraud e cego. So new-account (nunca viu a conta).
        ResponseEntity<Map> blind = evaluate(payer, payee, "20.00", NOW);
        assertThat(blind.getBody().get("triggeredRules")).isEqualTo(List.of("new-account"));

        for (int i = 0; i < 12; i++) {
            publish(approved(UUID.randomUUID(), UUID.randomUUID(), payer, payee, "20.00", NOW.minus(Duration.ofMinutes(5 + i))));
        }
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(historyCount(payer, "APPROVED")).isEqualTo(12));

        // Depois: velocity dispara, e new-account nao (payerAccountOpenedAt veio no evento).
        ResponseEntity<Map> informed = evaluate(payer, payee, "20.00", NOW);
        assertThat(informed.getBody().get("triggeredRules")).isEqualTo(List.of("payer-history"));
        assertThat(informed.getBody().get("score")).isEqualTo(20);
    }

    @Test
    void evaluationRegistersInFlightPaymentsBeforeAnyEvent() {
        UUID payer = UUID.randomUUID();
        UUID payee = quietPayee();
        for (int i = 0; i < 6; i++) {
            evaluate(payer, payee, "1.00", NOW);
        }
        // Seis avaliacoes em um minuto: high-frequency (>= 5) dispara pela propria visao de Fraud, sem Kafka.
        ResponseEntity<Map> seventh = evaluate(payer, payee, "1.00", NOW);
        assertThat((List<String>) seventh.getBody().get("triggeredRules")).contains("high-frequency");
        assertThat(historyCount(payer, "EVALUATED")).isEqualTo(7);
    }
}
