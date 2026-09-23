package com.techpix.fraud.internal.strangler;

import static org.assertj.core.api.Assertions.assertThat;

import com.techpix.fraud.FraudMode;
import com.techpix.support.AbstractIntegrationTest;
import com.techpix.support.FakeFraudService;
import com.techpix.support.FakeFraudService.Behaviour;
import io.micrometer.core.instrument.MeterRegistry;
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

/** Canary: o novo decide de verdade para uma fração; o legado cobre o resto e as falhas. */
class CanaryIT extends AbstractIntegrationTest {

    static final FakeFraudService FRAUD_SERVICE = new FakeFraudService();

    @DynamicPropertySource
    static void remote(DynamicPropertyRegistry registry) {
        registry.add("techpix.fraud.remote.url", FRAUD_SERVICE::url);
        registry.add("techpix.fraud.remote.read-timeout-ms", () -> 500);
    }

    @Autowired
    FraudModeConfig mode;
    @Autowired
    CanaryRouter router;
    @Autowired
    MeterRegistry metrics;

    @BeforeEach
    void setUp() {
        FRAUD_SERVICE.reset();
        mode.change(FraudMode.CANARY);
    }

    @AfterEach
    void rollback() {
        router.setPercentage(0);
        mode.change(FraudMode.LEGACY);
    }

    @Test
    void atZeroPercentTheCanaryReceivesNothing() {
        router.setPercentage(0);
        UUID payer = openAccount("Canary 0", "1000.00");
        UUID payee = openAccount("Canary 0 Payee", "0.00");
        for (int i = 0; i < 5; i++) {
            pay(payer, payee, "1.00", "d");
        }
        assertThat(FRAUD_SERVICE.calls()).isZero();
    }

    @Test
    void atHundredPercentTheCanaryDecidesEverything() {
        router.setPercentage(100);
        FRAUD_SERVICE.respond(Behaviour.ok(90, "REJECTED"));
        UUID payer = openAccount("Canary 100", "1000.00");
        UUID payee = openAccount("Canary 100 Payee", "0.00");

        ResponseEntity<Map> response = pay(payer, payee, "1.00", "d");

        // Diferente do Parallel Run: aqui a decisao do novo VALE.
        assertThat(response.getBody().get("status")).isEqualTo("REJECTED");
        assertThat(response.getBody().get("fraudScore")).isEqualTo(90);
        assertThat(FRAUD_SERVICE.calls()).isEqualTo(1);
    }

    @Test
    void canaryFailureFallsBackToLegacyAndIsCounted() {
        router.setPercentage(100);
        FRAUD_SERVICE.respond(Behaviour.error(503));
        double before = metrics.counter("fraud.canary.fallback").count();
        UUID payer = openAccount("Canary Fallback", "1000.00");
        UUID payee = openAccount("Canary Fallback Payee", "0.00");

        ResponseEntity<Map> response = pay(payer, payee, "1.00", "d");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("status")).isEqualTo("APPROVED");
        assertThat(metrics.counter("fraud.canary.fallback").count()).isEqualTo(before + 1);
    }

    @Test
    void percentageIsAdjustableThroughTheAdminEndpoint() {
        http.put("/admin/fraud/canary", Map.of("percentage", 10));
        Map status = http.getForObject("/admin/fraud/canary", Map.class);
        assertThat(status.get("percentage")).isEqualTo(10);
        assertThat(router.percentage()).isEqualTo(10);
    }
}
