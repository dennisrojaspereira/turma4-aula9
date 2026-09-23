package com.techpix.fraudservice.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.techpix.fraudservice.history.ProcessedEvents;
import com.techpix.fraudservice.support.AbstractFraudEventsIT;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * given the same event twice
 * when Fraud consumes
 * then state changes once
 * <p>
 * O primeiro teste mostra o problema (idempotência desligada); o segundo, a solução.
 */
class IdempotencyIT extends AbstractFraudEventsIT {

    @Autowired
    ProcessedEvents processedEvents;
    @Autowired
    MeterRegistry metrics;

    @AfterEach
    void restore() {
        processedEvents.setEnabled(true);
    }

    @Test
    void withoutIdempotencyADuplicateEventCountsTwice() {
        processedEvents.setEnabled(false);
        UUID payer = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Map<String, Object> fact = approved(eventId, UUID.randomUUID(), payer, quietPayee(), "100.00", Instant.now());

        publish(fact);
        publish(fact);   // redelivery: mesmo eventId

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(activityOf(payer).get("approvedCount")).isEqualTo(2));
        // payment_history e upsert por payment_id: fica 1 linha. account_activity e incremento: conta 2. Esse e o bug.
        assertThat(historyCount(payer, "APPROVED")).isEqualTo(1);
        assertThat(((Number) activityOf(payer).get("approvedAmountTotal")).doubleValue()).isEqualTo(200.0);
    }

    @Test
    void withIdempotencyTheSameEventChangesStateOnce() {
        processedEvents.setEnabled(true);
        double duplicatesBefore = metrics.counter("fraud.events.duplicates").count();
        UUID payer = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Map<String, Object> fact = approved(eventId, UUID.randomUUID(), payer, quietPayee(), "100.00", Instant.now());

        publish(fact);
        publish(fact);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(metrics.counter("fraud.events.duplicates").count()).isEqualTo(duplicatesBefore + 1));
        assertThat(activityOf(payer).get("approvedCount")).isEqualTo(1);
        assertThat(((Number) activityOf(payer).get("approvedAmountTotal")).doubleValue()).isEqualTo(100.0);
        Long processed = jdbc.sql("SELECT count(*) FROM processed_events WHERE event_id = :id").param("id", eventId).query(Long.class).single();
        assertThat(processed).isEqualTo(1);
    }

    private Map activityOf(UUID payer) {
        return http.getForObject("/admin/fraud/accounts/" + payer + "/activity", Map.class);
    }
}
