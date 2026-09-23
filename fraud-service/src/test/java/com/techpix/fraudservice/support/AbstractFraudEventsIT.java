package com.techpix.fraudservice.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Fraud Service completo da etapa 13: banco próprio + Kafka real (Testcontainers), consumidor ligado.
 * O "Payment" aqui é o próprio teste, publicando fatos no tópico.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.flyway.enabled=true",
        "fraud.history-source=LOCAL_VIEW",
        "fraud.events.enabled=true",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer",
        "spring.kafka.producer.properties.spring.json.add.type.headers=false"
})
public abstract class AbstractFraudEventsIT {

    protected static final PostgreSQLContainer<?> FRAUD_DB = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("fraud_db").withUsername("fraud").withPassword("fraud");
    protected static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka-native:3.8.0");

    static {
        FRAUD_DB.start();
        KAFKA.start();
    }

    @DynamicPropertySource
    static void infra(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", FRAUD_DB::getJdbcUrl);
        registry.add("spring.datasource.username", FRAUD_DB::getUsername);
        registry.add("spring.datasource.password", FRAUD_DB::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    protected TestRestTemplate http;
    @Autowired
    protected JdbcClient jdbc;
    @Autowired
    protected KafkaTemplate<String, Object> kafka;

    /** Publica um fato como o monólito publicaria: mesmo tópico, mesma chave, mesmo formato JSON. */
    protected void publish(Map<String, Object> event) {
        kafka.send("payment-events", event.get("payerAccountId").toString(), event).join();
    }

    protected static Map<String, Object> approved(UUID eventId, UUID paymentId, UUID payer, UUID payee, String amount, Instant at) {
        return fact("PaymentApproved", eventId, paymentId, payer, payee, amount, at);
    }

    protected static Map<String, Object> rejected(UUID eventId, UUID paymentId, UUID payer, UUID payee, String amount, Instant at) {
        return fact("PaymentRejected", eventId, paymentId, payer, payee, amount, at);
    }

    private static Map<String, Object> fact(String type, UUID eventId, UUID paymentId, UUID payer, UUID payee, String amount, Instant at) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("eventId", eventId);
        m.put("type", type);
        m.put("paymentId", paymentId);
        m.put("payerAccountId", payer);
        m.put("payeeAccountId", payee);
        m.put("amount", new BigDecimal(amount));
        m.put("deviceId", "phone-" + payer);
        m.put("payerAccountOpenedAt", at.minusSeconds(86_400L * 400));
        m.put("occurredAt", at);
        return m;
    }

    protected ResponseEntity<Map> evaluate(UUID payer, UUID payee, String amount, Instant at) {
        return http.postForEntity("/fraud/evaluations", Map.of(
                "paymentId", UUID.randomUUID(), "payerAccountId", payer, "payeeAccountId", payee,
                "amount", new BigDecimal(amount), "deviceId", "phone-" + payer, "occurredAt", at.toString()), Map.class);
    }


    /**
     * A regra external-provider e deterministica pelo hash do destinatario (~5% recebem +20).
     * Para testes previsiveis, escolhemos destinatarios fora dessa faixa.
     */
    protected static UUID quietPayee() {
        while (true) {
            UUID id = UUID.randomUUID();
            if (Math.floorMod(id.hashCode(), 100) >= 5) {
                return id;
            }
        }
    }

    protected long historyCount(UUID payer, String status) {
        return jdbc.sql("SELECT count(*) FROM payment_history WHERE payer_account_id = :p AND status = :s")
                .param("p", payer).param("s", status).query(Long.class).single();
    }
}
