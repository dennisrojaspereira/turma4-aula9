package com.techpix.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techpix.support.AbstractIntegrationTest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Payment publica fatos. Um Kafka real (Testcontainers) recebe. Este teste é o consumidor.
 * Repare no que Payment NÃO envia: rejection_reason, saldo, ledger. Só o que outros contextos precisam.
 */
class PaymentEventsIT extends AbstractIntegrationTest {

    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka-native:3.8.0");

    static {
        KAFKA.start();
    }

    @DynamicPropertySource
    static void kafka(DynamicPropertyRegistry registry) {
        registry.add("techpix.events.enabled", () -> "true");
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Test
    void approvedAndRejectedPaymentsArePublishedAsFacts() throws Exception {
        UUID alice = openAccount("Events Alice", "1000.00");
        UUID bob = openAccount("Events Bob", "0.00");

        ResponseEntity<Map> approved = pay(alice, bob, "12.34", "phone-1");
        ResponseEntity<Map> rejected = pay(alice, alice, "1.00", "phone-1");     // same-party: REJECTED
        UUID approvedId = UUID.fromString((String) approved.getBody().get("id"));
        UUID rejectedId = UUID.fromString((String) rejected.getBody().get("id"));

        List<JsonNode> events = consumeAll(alice);

        JsonNode approvedEvent = events.stream().filter(e -> e.get("paymentId").asText().equals(approvedId.toString())).findFirst().orElseThrow();
        assertThat(approvedEvent.get("type").asText()).isEqualTo("PaymentApproved");
        assertThat(approvedEvent.get("amount").decimalValue()).isEqualByComparingTo("12.34");
        assertThat(approvedEvent.get("deviceId").asText()).isEqualTo("phone-1");
        assertThat(approvedEvent.get("payerAccountOpenedAt").asText()).isNotBlank();
        assertThat(approvedEvent.has("rejectionReason")).isFalse();

        JsonNode rejectedEvent = events.stream().filter(e -> e.get("paymentId").asText().equals(rejectedId.toString())).findFirst().orElseThrow();
        assertThat(rejectedEvent.get("type").asText()).isEqualTo("PaymentRejected");

        // eventId deterministico: o mesmo fato tem sempre o mesmo id.
        UUID expected = UUID.nameUUIDFromBytes((approvedId + ":PaymentApproved").getBytes());
        assertThat(approvedEvent.get("eventId").asText()).isEqualTo(expected.toString());
    }

    @Test
    void replayPublishesTheSameEventIdAgain() throws Exception {
        UUID alice = openAccount("Replay Alice", "1000.00");
        UUID bob = openAccount("Replay Bob", "0.00");
        UUID paymentId = UUID.fromString((String) pay(alice, bob, "5.00", "d").getBody().get("id"));

        http.postForEntity("/admin/events/replay/" + paymentId, null, Map.class);

        List<JsonNode> events = consumeAll(alice);
        List<String> ids = events.stream().filter(e -> e.get("paymentId").asText().equals(paymentId.toString()))
                .map(e -> e.get("eventId").asText()).toList();
        assertThat(ids).hasSize(2);
        assertThat(ids.get(0)).isEqualTo(ids.get(1));
    }

    private static List<JsonNode> consumeAll(UUID payerKey) throws Exception {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        ObjectMapper json = new ObjectMapper();
        List<JsonNode> found = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of("payment-events"));
            long deadline = System.currentTimeMillis() + 15_000;
            while (System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (payerKey.toString().equals(record.key())) {
                        found.add(json.readTree(record.value()));
                    }
                }
                if (!found.isEmpty() && System.currentTimeMillis() > deadline - 12_000) {
                    break;
                }
            }
        }
        return found;
    }
}
