package com.techpix.support;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Um único PostgreSQL real, iniciado uma vez por JVM e compartilhado por todos os testes de integração.
 * Sem mocks de banco: o objetivo do laboratório é ver o comportamento real.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestClockConfig.class)
public abstract class AbstractIntegrationTest {

    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("techpix")
            .withUsername("techpix")
            .withPassword("techpix");

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

    /**
     * Abre uma conta cujo id nao dispara a regra external-provider (deterministica pelo hash do
     * destinatario, ~5% dos ids). Sem isto, um em cada vinte testes ganharia +20 pontos "do nada".
     */
    protected UUID openAccount(String owner, String balance) {
        while (true) {
            ResponseEntity<Map> response = http.postForEntity("/accounts",
                    Map.of("ownerName", owner, "initialBalance", new BigDecimal(balance)), Map.class);
            UUID id = UUID.fromString((String) response.getBody().get("id"));
            if (Math.floorMod(id.hashCode(), 100) >= 5) {
                return id;
            }
        }
    }

    protected ResponseEntity<Map> pay(UUID payer, UUID payee, String amount, String deviceId) {
        return http.postForEntity("/payments",
                Map.of("payerAccountId", payer, "payeeAccountId", payee, "amount", new BigDecimal(amount), "deviceId", deviceId),
                Map.class);
    }
}
