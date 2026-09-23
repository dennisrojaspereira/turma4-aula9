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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * O Fraud Service com seu PROPRIO banco: schema criado pelo Flyway do serviço, histórico pela visão local.
 * Repare no que NÃO existe aqui: legacy-schema.sql. Nenhuma tabela de Payment.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.flyway.enabled=true",
        "fraud.history-source=LOCAL_VIEW"
})
public abstract class AbstractFraudStoreIT {

    protected static final PostgreSQLContainer<?> FRAUD_DB = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("fraud_db")
            .withUsername("fraud")
            .withPassword("fraud");

    static {
        FRAUD_DB.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", FRAUD_DB::getJdbcUrl);
        registry.add("spring.datasource.username", FRAUD_DB::getUsername);
        registry.add("spring.datasource.password", FRAUD_DB::getPassword);
    }

    @Autowired
    protected TestRestTemplate http;
    @Autowired
    protected JdbcClient jdbc;


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

    protected ResponseEntity<Map> evaluate(UUID payer, UUID payee, String amount, String device, Instant at) {
        return http.postForEntity("/fraud/evaluations", Map.of(
                "paymentId", UUID.randomUUID(), "payerAccountId", payer, "payeeAccountId", payee,
                "amount", new BigDecimal(amount), "deviceId", device, "occurredAt", at.toString()), Map.class);
    }
}
