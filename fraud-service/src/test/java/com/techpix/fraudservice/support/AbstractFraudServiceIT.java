package com.techpix.fraudservice.support;

import java.math.BigDecimal;
import java.sql.Timestamp;
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
 * O Fraud Service da ETAPA 8: banco compartilhado com o monólito, Flyway desligado, histórico lido do schema legado.
 * PostgreSQL real com uma cópia do schema legado (ver legacy-schema.sql e o aviso lá dentro).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.flyway.enabled=false",
        "fraud.history-source=LEGACY_SCHEMA"
})
public abstract class AbstractFraudServiceIT {

    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("techpix")
            .withUsername("techpix")
            .withPassword("techpix")
            .withInitScript("legacy-schema.sql");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected TestRestTemplate http;
    @Autowired
    protected JdbcClient jdbc;

    protected UUID legacyAccount(Instant createdAt) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO accounts (id, owner_name, balance, status, created_at) VALUES (:id, 'test', 1000, 'ACTIVE', :at)")
                .param("id", id).param("at", Timestamp.from(createdAt)).update();
        return id;
    }

    protected void legacyPayment(UUID payer, UUID payee, String amount, String device, String status, Instant at) {
        jdbc.sql("""
                INSERT INTO payments (id, payer_account_id, payee_account_id, amount, device_id, status, created_at)
                VALUES (:id, :payer, :payee, :amount, :device, :status, :at)
                """)
                .param("id", UUID.randomUUID()).param("payer", payer).param("payee", payee)
                .param("amount", new BigDecimal(amount)).param("device", device).param("status", status)
                .param("at", Timestamp.from(at)).update();
    }

    protected ResponseEntity<Map> evaluate(UUID payer, UUID payee, String amount, String device, Instant at) {
        return http.postForEntity("/fraud/evaluations", Map.of(
                "paymentId", UUID.randomUUID(), "payerAccountId", payer, "payeeAccountId", payee,
                "amount", new BigDecimal(amount), "deviceId", device, "occurredAt", at.toString()), Map.class);
    }
}
