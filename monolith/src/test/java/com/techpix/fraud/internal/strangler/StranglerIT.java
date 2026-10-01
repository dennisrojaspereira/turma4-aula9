package com.techpix.fraud.internal.strangler;

import static org.assertj.core.api.Assertions.assertThat;

import com.techpix.fraud.FraudMode;
import com.techpix.support.AbstractIntegrationTest;
import com.techpix.support.FakeFraudService;
import com.techpix.support.FakeFraudService.Behaviour;
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
 * Strangler Fig: o mesmo pagamento, decidido pelo legado ou pelo remoto, conforme o modo.
 * O Fraud Service aqui é falso; o que está em teste é o lado do Payment.
 */
class StranglerIT extends AbstractIntegrationTest {

    static final FakeFraudService FRAUD_SERVICE = new FakeFraudService();

    @DynamicPropertySource
    static void remote(DynamicPropertyRegistry registry) {
        registry.add("techpix.fraud.remote.url", FRAUD_SERVICE::url);
        registry.add("techpix.fraud.remote.read-timeout-ms", () -> 500);
    }

    @Autowired
    FraudModeConfig mode;

    @BeforeEach
    void resetFake() {
        FRAUD_SERVICE.reset();
    }

    @AfterEach
    void rollback() {
        mode.change(FraudMode.LEGACY);
    }

    @Test
    void legacyModeNeverCallsTheRemoteService() {
        mode.change(FraudMode.LEGACY);
        UUID payer = openAccount("Strangler Legacy", "1000.00");
        UUID payee = openAccount("Strangler Legacy Payee", "0.00");

        ResponseEntity<Map> response = pay(payer, payee, "10.00", "d");

        assertThat(response.getBody().get("status")).isEqualTo("APPROVED");
        assertThat(FRAUD_SERVICE.calls()).isZero();
    }

    @Test
    void newModeUsesTheRemoteDecision() {
        mode.change(FraudMode.NEW);
        FRAUD_SERVICE.respond(Behaviour.ok(95, "REJECTED"));
        UUID payer = openAccount("Strangler New", "1000.00");
        UUID payee = openAccount("Strangler New Payee", "0.00");

        ResponseEntity<Map> response = pay(payer, payee, "10.00", "d");

        assertThat(response.getBody().get("status")).isEqualTo("REJECTED");
        assertThat(response.getBody().get("fraudScore")).isEqualTo(95);
        assertThat(FRAUD_SERVICE.calls()).isEqualTo(1);
        // O contrato: Payment envia so o necessario para decidir.
        assertThat(FRAUD_SERVICE.requests().get(0).get("payerAccountId").asText()).isEqualTo(payer.toString());
        assertThat(FRAUD_SERVICE.requests().get(0).get("amount").decimalValue()).isEqualByComparingTo("10.00");
    }

    @Test
    void rollbackIsAConfigurationChangeNotADeploy() {
        mode.change(FraudMode.NEW);
        FRAUD_SERVICE.respond(Behaviour.error(500));
        UUID payer = openAccount("Strangler Rollback", "1000.00");
        UUID payee = openAccount("Strangler Rollback Payee", "0.00");

        ResponseEntity<Map> broken = pay(payer, payee, "10.00", "d");
        assertThat(broken.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        http.put("/admin/fraud/mode", Map.of("mode", "LEGACY"));

        ResponseEntity<Map> recovered = pay(payer, payee, "10.00", "d");
        assertThat(recovered.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(recovered.getBody().get("status")).isEqualTo("APPROVED");
    }

    @Test
    void remoteTimeoutDoesNotHangPayment() {
        mode.change(FraudMode.NEW);
        FRAUD_SERVICE.respond(Behaviour.ok(0, "APPROVED").withDelay(2_000));
        UUID payer = openAccount("Strangler Timeout", "1000.00");
        UUID payee = openAccount("Strangler Timeout Payee", "0.00");

        long start = System.currentTimeMillis();
        ResponseEntity<Map> response = pay(payer, payee, "10.00", "d");
        long elapsed = System.currentTimeMillis() - start;

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        // O que esta em teste: o timeout corta cada tentativa, nao esperamos o delay de 2s do fake.
        // Pior caso legitimo: 3 tentativas x 500ms + backoff 100ms + 200ms (jitter +20%) ~= 1,9s --
        // bem abaixo dos ~6s de esperar o fake responder em cada tentativa.
        assertThat(elapsed).isLessThan(2_500);
    }
}
